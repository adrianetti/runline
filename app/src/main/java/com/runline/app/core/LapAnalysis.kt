package com.runline.app.core

import kotlin.math.*

/** GPS estimates of repeated loops, not named tracks or official lap timing. */
data class LapSector(val number: Int, val distanceMeters: Double, val durationMs: Long, val elevationChangeMeters: Double?)
data class DetectedLap(val number: Int, val distanceMeters: Double, val durationMs: Long,
                       val ascentMeters: Double?, val descentMeters: Double?, val sectors: List<LapSector>)
data class LapSectorComparison(val number: Int, val paceChangePercent: Double, val elevationChangeMeters: Double?)
data class LapComparison(val paceChangePercent: Double, val slowerSector: LapSectorComparison?)
data class LapReport(val laps: List<DetectedLap>, val latestComparison: LapComparison?)

object LapAnalysis {
    fun analyze(points: List<RunPoint>): LapReport = LapTracker().apply { update(points) }.report()
}

/** Incremental loop detection; work is proportional to new fixes, not the full run on every tick. */
class LapTracker {
    private data class Sample(val point: RunPoint, val distance: Double, val bearing: Double?)
    private data class Candidate(val samples: List<Sample>, val lap: DetectedLap)
    private val path = mutableListOf<Sample>()
    private val cells = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    private val completed = mutableListOf<DetectedLap>()
    private var prior: Candidate? = null
    private var anchorIndex: Int? = null
    private var lapStart = 0
    private var departed = false
    private var count = 0
    private var lastInput: RunPoint? = null
    private var previous: RunPoint? = null
    private var comparison: LapComparison? = null

    fun update(points: List<RunPoint>) {
        if (points.size < count || (count > 0 && points.getOrNull(count - 1) != lastInput)) reset()
        for (index in count until points.size) accept(points[index])
        count = points.size; lastInput = points.lastOrNull()
    }
    fun report() = LapReport(completed.toList(), comparison)

    private fun accept(point: RunPoint) {
        val previousPoint = previous
        val dt = previousPoint?.let { point.timestampMs - it.timestampMs }
        val usable = point.hasValidCoordinates() && point.timestampMs >= 0 && point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..15f
        if (!usable) { breakPath(); return }
        val d = previousPoint?.let { distance(it, point) } ?: 0.0
        if (previousPoint == null || dt == null || dt !in 1..15_000 || point.segment != previousPoint.segment || d > 15.0 * dt / 1000 + 15) breakPath()
        previous = point
        val baseline = path.lastOrNull()
        val heading = if (baseline != null && d >= 3) bearing(baseline.point, point) else baseline?.bearing
        val sample = Sample(point, (baseline?.distance ?: 0.0) + if (baseline == null) 0.0 else d, heading)
        path += sample
        if (path.size == 2 && path[0].bearing == null) path[0] = path[0].copy(bearing = heading)
        val end = path.lastIndex
        val anchor = anchorIndex
        if (anchor != null) {
            val a = path[anchor]
            if (distance(a.point, point) > 65) departed = true
            val start = path[lapStart]
            if (departed && sample.distance - start.distance >= 300 && point.timestampMs - start.point.timestampMs >= 90_000 &&
                distance(a.point, point) <= 22 && aligned(a.bearing, heading)) {
                complete(lapStart, end)
                lapStart = end; departed = false
            }
            // Bound an abandoned route search after moving away for too long.
            if (point.timestampMs - start.point.timestampMs > 90 * 60_000 || sample.distance - start.distance > 15_000) breakPath()
        } else if (sample.distance >= 300) {
            val key = cell(point)
            var best: Int? = null
            var nearest = 23.0
            for (dx in -1..1) for (dy in -1..1) {
                for (index in cells[key.first + dx to key.second + dy].orEmpty()) {
                    val candidate = path[index]
                    if (sample.distance - candidate.distance < 300 || point.timestampMs - candidate.point.timestampMs < 90_000 || !aligned(candidate.bearing, heading)) continue
                    val separation = distance(candidate.point, point)
                    if (separation < nearest) { nearest = separation; best = index }
                }
            }
            best?.let {
                anchorIndex = it; lapStart = end; departed = false; complete(it, end)
            }
        }
        if (path.isNotEmpty()) {
            // At most one search anchor per ~15m; the complete path still supports sector timing.
            val cell = cell(point)
            val bucket = cells.getOrPut(cell) { mutableListOf() }
            if (bucket.lastOrNull()?.let { sample.distance - path[it].distance >= 15 } != false) bucket += path.lastIndex
            if (path.size > 20_000) breakPath()
        }
    }

    private fun complete(start: Int, end: Int) {
        val samples = path.subList(start, end + 1).toList()
        if (samples.size < 20) return
        val total = samples.last().distance - samples.first().distance
        val duration = samples.last().point.timestampMs - samples.first().point.timestampMs
        val sectors = (0..3).map { index ->
            val a = at(samples, index / 4.0); val b = at(samples, (index + 1) / 4.0)
            LapSector(index + 1, total / 4, b.timestampMs - a.timestampMs,
                if (a.altitudeMeters != null && b.altitudeMeters != null) b.altitudeMeters - a.altitudeMeters else null)
        }
        val elevation = ElevationProgress().apply { update(samples.map { it.point }) }
        val lap = DetectedLap(0, total, duration, elevation.climbedMeters, elevation.descendedMeters, sectors)
        val candidate = Candidate(samples, lap)
        val old = prior
        if (old != null && matches(old, candidate)) {
            if (!priorWasConfirmed) completed += old.lap.copy(number = completed.size + 1)
            completed += lap.copy(number = completed.size + 1)
            val oldPace = old.lap.durationMs / old.lap.distanceMeters
            val change = ((duration / total) / oldPace - 1) * 100
            val slower = lap.sectors.zip(old.lap.sectors).map { (now, before) ->
                val priorPace = before.durationMs / before.distanceMeters
                val pace = now.durationMs / now.distanceMeters
                LapSectorComparison(now.number, (pace / priorPace - 1) * 100, now.elevationChangeMeters)
            }.filter { it.paceChangePercent >= 8 }.maxByOrNull { it.paceChangePercent }
            comparison = LapComparison(change, slower)
            priorWasConfirmed = true
        } else { priorWasConfirmed = false; comparison = null }
        prior = candidate
    }
    private var priorWasConfirmed = false

    private fun matches(a: Candidate, b: Candidate): Boolean {
        if (abs(a.lap.distanceMeters - b.lap.distanceMeters) / a.lap.distanceMeters > .15) return false
        val separations = (0..12).map { distance(at(a.samples, it / 12.0), at(b.samples, it / 12.0)) }
        return separations.count { it <= 35 } >= 11 && separations.max() <= 60
    }
    private fun at(samples: List<Sample>, fraction: Double): RunPoint {
        val target = samples.first().distance + (samples.last().distance - samples.first().distance) * fraction
        var lo = 0; var hi = samples.lastIndex
        while (lo < hi) { val mid = (lo + hi) / 2; if (samples[mid].distance < target) lo = mid + 1 else hi = mid }
        val b = samples[lo]; val a = samples[(lo - 1).coerceAtLeast(0)]
        val share = if (b.distance <= a.distance) 0.0 else (target - a.distance) / (b.distance - a.distance)
        return a.point.copy(latitude = a.point.latitude + (b.point.latitude - a.point.latitude) * share,
            longitude = a.point.longitude + (b.point.longitude - a.point.longitude) * share,
            timestampMs = a.point.timestampMs + ((b.point.timestampMs - a.point.timestampMs) * share).roundToLong(),
            altitudeMeters = if (a.point.altitudeMeters?.isFinite() == true && b.point.altitudeMeters?.isFinite() == true)
                a.point.altitudeMeters + (b.point.altitudeMeters - a.point.altitudeMeters) * share else null)
    }
    private fun breakPath() { path.clear(); cells.clear(); prior = null; anchorIndex = null; departed = false; lapStart = 0; previous = null; priorWasConfirmed = false }
    private fun reset() { breakPath(); count = 0; lastInput = null; completed.clear(); comparison = null }
    private fun cell(p: RunPoint) = floor(p.latitude / .0005).toInt() to floor(p.longitude / .0005).toInt()
    private fun aligned(a: Double?, b: Double?) = a != null && b != null && abs((b - a + 540) % 360 - 180) <= 55
    private fun bearing(a: RunPoint, b: RunPoint): Double {
        val x = Math.toRadians(a.latitude); val y = Math.toRadians(b.latitude); val d = Math.toRadians(b.longitude - a.longitude)
        return (Math.toDegrees(atan2(sin(d) * cos(y), cos(x) * sin(y) - sin(x) * cos(y) * cos(d))) + 360) % 360
    }
    private fun distance(a: RunPoint, b: RunPoint): Double {
        val x = Math.toRadians(a.latitude); val y = Math.toRadians(b.latitude)
        val h = sin((y - x) / 2).pow(2) + cos(x) * cos(y) * sin(Math.toRadians(b.longitude - a.longitude) / 2).pow(2)
        return 12_742_017.6 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
