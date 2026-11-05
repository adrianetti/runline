package com.runline.app.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor

// UNKNOWN is deliberately distinct from a measured flat section.
enum class TerrainDirection { UPHILL, FLAT, DOWNHILL, UNKNOWN }

data class TerrainEstimate(
    val direction: TerrainDirection = TerrainDirection.UNKNOWN,
    val gradePercent: Double? = null,
    val windowMeters: Double = 0.0,
)

/**
 * Causal height/distance estimate shared by recording, replay and coaching.
 * Median distance bins and a robust fit reject individual height spikes. A longer
 * 55–90 m window reduces GNSS noise; a recent-window veto avoids stale hill calls.
 * Vertical accuracy is a separate measurement from horizontal GPS accuracy.
 * This cannot distinguish smooth altitude drift from a real hill without another
 * height source. Original recordings are never changed or terrain-corrected here.
 */
class TerrainTrend {
    private data class Sample(val point: RunPoint, val distance: Double)
    private data class Bin(val distance: Double, val height: Double)
    private data class Fit(val grade: Double, val residual: Double)
    private val samples = ArrayDeque<Sample>()
    private var previous: RunPoint? = null
    private var traveled = 0.0
    private var direction = TerrainDirection.UNKNOWN
    private var smoothedGrade: Double? = null
    private var estimate = TerrainEstimate()

    fun reset() {
        samples.clear(); previous = null; traveled = 0.0
        direction = TerrainDirection.UNKNOWN; smoothedGrade = null; estimate = TerrainEstimate()
    }

    fun update(point: RunPoint): TerrainEstimate {
        if (point == previous) return estimate
        if (!usable(point)) { reset(); return estimate }
        val prior = previous
        fun restart(): TerrainEstimate {
            reset(); previous = point; samples.addLast(Sample(point, 0.0)); return estimate
        }
        if (prior == null || !MarkerMotion.canInterpolate(prior, point)) return restart()
        val distance = RunMetrics.distanceMeters(listOf(prior, point))
        val elapsed = point.timestampMs - prior.timestampMs
        if (distance * 1_000 / elapsed > 12.0) return restart()
        // An abrupt height relocation is not a downhill/uphill observation.
        if (abs(point.altitudeMeters!! - prior.altitudeMeters!!) > maxOf(4.0, distance * .5)) return restart()
        previous = point; traveled += distance
        samples.addLast(Sample(point, traveled))
        while (samples.size > 1 && (traveled - samples.first().distance > 90.0 ||
                point.timestampMs - samples.first().point.timestampMs > 120_000L || samples.size > 180)) samples.removeFirst()
        val first = samples.first()
        val span = traveled - first.distance
        fun unknown(): TerrainEstimate {
            direction = TerrainDirection.UNKNOWN; smoothedGrade = null
            return TerrainEstimate(windowMeters = span).also { estimate = it }
        }
        if (samples.size < 8 || span < 55.0 || point.timestampMs - first.point.timestampMs < 20_000L ||
            RunMetrics.distanceMeters(listOf(first.point, point)) < 25.0) return unknown()
        val moving = samples.firstOrNull { point.timestampMs - it.point.timestampMs <= 10_000L } ?: return unknown()
        val seconds = (point.timestampMs - moving.point.timestampMs) / 1_000.0
        if (seconds < 4.0 || RunMetrics.distanceMeters(listOf(moving.point, point)) / seconds < .6) return unknown()
        val bins = samples.groupBy { floor((it.distance - first.distance) / 5.0).toInt() }.values.map {
            Bin(it.map { sample -> sample.distance }.median(), it.map { sample -> sample.point.altitudeMeters!! }.median())
        }
        val fit = fit(bins, 20.0) ?: return unknown()
        if (!fit.grade.isFinite() || abs(fit.grade) > 40.0 || fit.residual > 1.2) return unknown()
        // Opposite recent observations, a crest, or leveling invalidate the old
        // window immediately. Do not average a known reversal into the old sign.
        val tail = bins.filter { traveled - it.distance <= 35.0 }
        val recent = fit(tail, 10.0) ?: return unknown()
        if (recent.residual > 1.2) return unknown()
        if (abs(fit.grade) >= 1.5 && (fit.grade * recent.grade < 0 || abs(recent.grade) < 1.0)) return unknown()
        if (abs(fit.grade - recent.grade) > 8.0) return unknown()
        val grade = smoothedGrade?.let { it + (fit.grade - it) * (1 - exp(-distance / 18.0)) } ?: fit.grade
        smoothedGrade = grade
        direction = when {
            grade >= 3.0 -> TerrainDirection.UPHILL
            grade <= -3.0 -> TerrainDirection.DOWNHILL
            direction == TerrainDirection.UPHILL && grade > 1.5 -> TerrainDirection.UPHILL
            direction == TerrainDirection.DOWNHILL && grade < -1.5 -> TerrainDirection.DOWNHILL
            else -> TerrainDirection.FLAT
        }
        return TerrainEstimate(direction, grade, span).also { estimate = it }
    }

    private fun fit(bins: List<Bin>, separation: Double): Fit? {
        if (bins.size < 3) return null
        val slopes = ArrayList<Double>()
        for (a in bins.indices) for (b in a + 1 until bins.size) {
            val dx = bins[b].distance - bins[a].distance
            if (dx >= separation) slopes += (bins[b].height - bins[a].height) / dx
        }
        if (slopes.isEmpty()) return null
        val slope = slopes.median()
        val intercept = bins.map { it.height - slope * it.distance }.median()
        val errors = bins.map { abs(it.height - intercept - slope * it.distance) }.sorted()
        // Upper-quartile residual rejects persistent noise but tolerates one outlier.
        return Fit(slope * 100, errors[(errors.lastIndex * .75).toInt()])
    }

    private fun List<Double>.median(): Double {
        val values = sorted(); val middle = size / 2
        return if (size % 2 == 0) (values[middle - 1] + values[middle]) / 2 else values[middle]
    }
    private fun usable(point: RunPoint) = point.hasValidCoordinates() && point.timestampMs >= 0 &&
        point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..20f && point.hasUsableAltitude()
}

/** Precomputed causal estimates: seeking never depends on the previously played frame. */
class ReplayTerrain(replay: RunReplay) {
    private data class Sample(val position: Long, val estimate: TerrainEstimate)
    private val bySegment: List<List<Sample>>

    init {
        var offset = 0L
        bySegment = replay.segments.map { points ->
            val trend = TerrainTrend()
            val start = points.first().timestampMs
            points.map { Sample(offset + it.timestampMs - start, trend.update(it)) }.also {
                offset += points.last().timestampMs - start
            }
        }
    }

    fun at(positionMs: Long, segmentIndex: Int): TerrainEstimate {
        val samples = bySegment.getOrNull(segmentIndex) ?: return TerrainEstimate()
        if (samples.isEmpty() || positionMs < samples.first().position || positionMs > samples.last().position) return TerrainEstimate()
        var low = 0; var high = samples.lastIndex
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (samples[middle].position <= positionMs) low = middle else high = middle - 1
        }
        // Missing/poor data at the other end makes this entire interpolated interval unknown.
        val next = samples.getOrNull(low + 1)
        if (positionMs > samples[low].position && next?.estimate?.direction == TerrainDirection.UNKNOWN) return TerrainEstimate()
        return samples[low].estimate
    }
}
