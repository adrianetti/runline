package com.runline.app.core

import kotlin.math.abs
import kotlin.math.roundToLong

/** GPS estimates, not terrain-corrected elevation or sensor-measured speed. */
data class RunAnalysis(
    val distanceMeters: Double,
    val observedDurationMs: Long,
    val averageSpeedMetersPerSecond: Double?,
    val maxSpeedMetersPerSecond: Double?,
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val minAltitudeMeters: Double?,
    val maxAltitudeMeters: Double?,
)

/** [point] is interpolated only inside one observed, continuous GPS segment. */
data class ReplayFrame(
    val point: RunPoint,
    val positionMs: Long,
    val distanceMeters: Double,
    val speedMetersPerSecond: Double?,
    val paceSecondsPerKm: Double?,
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val segmentIndex: Int,
)

/**
 * A seekable GPS timeline. Its clock includes only observed intervals, not the run's
 * active timer, pauses, time awaiting a first fix, or gaps over 15 seconds. A gap is
 * a cut to the next segment: it never becomes an invented connecting path.
 *
 * Construction is O(n log n); a frame seek is O(log n). Build once for a saved run, then
 * advance position using a monotonic UI clock and the selected playback multiplier.
 */
class RunReplay(val session: RunSession) {
    private val run = session
    val segments: List<List<RunPoint>> = safeSegments(run.points)
    val durationMs: Long
    val summary: RunAnalysis

    private data class Edge(
        val a: RunPoint,
        val b: RunPoint,
        val segmentIndex: Int,
        val startMs: Long,
        val endMs: Long,
        val segmentStartMs: Long,
        val startDistance: Double,
        val length: Double,
        val gainBefore: Double,
        val gainAfter: Double,
        val lossBefore: Double,
        val lossAfter: Double,
    )

    private val edges: List<Edge>
    private val hasAltitudePairs: Boolean

    init {
        val built = ArrayList<Edge>()
        var clock = 0L
        var distance = 0.0
        var gain = 0.0
        var loss = 0.0
        var altitudePairs = false
        var minimum: Double? = null
        var maximum: Double? = null
        for ((segmentIndex, points) in segments.withIndex()) {
            val segmentStart = clock
            var altitudeAnchor: Double? = null
            for ((index, point) in points.withIndex()) {
                val altitude = point.altitudeMeters?.takeIf { it.isFinite() }
                if (altitude != null) {
                    minimum = minimum?.let { minOf(it, altitude) } ?: altitude
                    maximum = maximum?.let { maxOf(it, altitude) } ?: altitude
                }
                val gainBefore = gain
                val lossBefore = loss
                val anchor = altitudeAnchor
                if (altitude == null) altitudeAnchor = null
                else if (anchor == null) altitudeAnchor = altitude
                else {
                    altitudePairs = true
                    val change = altitude - anchor
                    // Same simple three-metre deadband used by the recorder's metrics.
                    // Missing altitude and segment breaks reset it; small GPS noise is ignored.
                    if (abs(change) >= ALTITUDE_DEADBAND_METERS) {
                        if (change > 0) gain += change else loss -= change
                        altitudeAnchor = altitude
                    }
                }
                if (index == 0) continue
                val previous = points[index - 1]
                val span = point.timestampMs - previous.timestampMs
                val length = RunMetrics.distanceMeters(listOf(previous, point))
                built += Edge(previous, point, segmentIndex, clock, clock + span, segmentStart,
                    distance, length, gainBefore, gain, lossBefore, loss)
                clock += span
                distance += length
            }
        }
        edges = built
        hasAltitudePairs = altitudePairs
        durationMs = clock
        var peakSpeed: Double? = null
        fun considerPeak(position: Long, edge: Edge) {
            speedAt(position, edge)?.let { speed ->
                peakSpeed = peakSpeed?.let { maxOf(it, speed) } ?: speed
            }
        }
        for (edge in edges) {
            considerPeak(edge.endMs, edge)
            // A trailing window changes slope when either end crosses a fix. Its
            // maximum can therefore occur five seconds after a fix, between samples.
            if (durationMs - edge.startMs >= SPEED_WINDOW_MS) {
                val position = edge.startMs + SPEED_WINDOW_MS
                val containingEdge = edgeAt(position)
                if (containingEdge.segmentIndex == edge.segmentIndex) {
                    considerPeak(position, containingEdge)
                }
            }
        }
        summary = RunAnalysis(
            distanceMeters = distance,
            observedDurationMs = clock,
            averageSpeedMetersPerSecond = if (run.activeDurationMs > 0L && distance > 0.0)
                (distance * 1000.0 / run.activeDurationMs).takeIf { it.isFinite() } else null,
            maxSpeedMetersPerSecond = peakSpeed,
            elevationGainMeters = gain.takeIf { altitudePairs },
            elevationLossMeters = loss.takeIf { altitudePairs },
            minAltitudeMeters = minimum,
            maxAltitudeMeters = maximum,
        )
    }

    fun at(positionMs: Long): ReplayFrame? {
        val first = segments.firstOrNull()?.firstOrNull() ?: return null
        val position = positionMs.coerceIn(0L, durationMs)
        if (edges.isEmpty() || position == 0L) {
            return frame(first, position, 0.0, null, 0.0, 0.0, first.segment)
        }
        // Keep a trailing isolated fix as the endpoint, without drawing/animating a
        // connection to it. Its timestamp and altitude are genuine, speed is unknown.
        if (position == durationMs) {
            val last = segments.last().last()
            val edge = edges.last()
            val speed = if (last == edge.b) speedAt(position, edge) else null
            return frame(last, position, summary.distanceMeters, speed,
                summary.elevationGainMeters ?: 0.0, summary.elevationLossMeters ?: 0.0, last.segment)
        }
        val edge = edgeAt(position)
        val fraction = (position - edge.startMs).toDouble() / (edge.endMs - edge.startMs)
        val altitudeA = edge.a.altitudeMeters?.takeIf { it.isFinite() }
        val altitudeB = edge.b.altitudeMeters?.takeIf { it.isFinite() }
        val altitude = when {
            fraction == 0.0 -> altitudeA
            altitudeA != null && altitudeB != null -> lerp(altitudeA, altitudeB, fraction)
            else -> null
        }
        val point = RunPoint(
            latitude = lerp(edge.a.latitude, edge.b.latitude, fraction),
            longitude = interpolateLongitude(edge.a.longitude, edge.b.longitude, fraction),
            timestampMs = edge.a.timestampMs + ((edge.b.timestampMs - edge.a.timestampMs) * fraction).roundToLong(),
            accuracyMeters = maxOf(edge.a.accuracyMeters, edge.b.accuracyMeters),
            altitudeMeters = altitude,
            segment = edge.segmentIndex,
        )
        return frame(point, position, edge.startDistance + edge.length * fraction, speedAt(position, edge),
            lerp(edge.gainBefore, edge.gainAfter, fraction), lerp(edge.lossBefore, edge.lossAfter, fraction), edge.segmentIndex)
    }

    private fun frame(point: RunPoint, position: Long, distance: Double, speed: Double?, gain: Double, loss: Double, segment: Int) =
        ReplayFrame(point, position, distance, speed, speed?.takeIf { it > 0.0 }?.let { 1000.0 / it },
            gain.takeIf { hasAltitudePairs }, loss.takeIf { hasAltitudePairs }, segment)

    /** Trailing five-second GPS speed, kept within one segment; no motion through pauses. */
    private fun speedAt(position: Long, edge: Edge): Double? {
        val start = maxOf(edge.segmentStartMs, position - SPEED_WINDOW_MS)
        val span = position - start
        if (span <= 0L) return null
        val traveled = distanceAt(position, edge) - distanceAt(start, edgeAt(start))
        val speed = traveled.coerceAtLeast(0.0) * 1000.0 / span
        // GPS jitter can survive the recorder's horizontal-accuracy gate. Do not turn
        // an implausible running speed into a headline peak or an invented pace.
        return speed.takeIf { it.isFinite() && it <= MAX_RUNNING_SPEED_METERS_PER_SECOND }
    }

    private fun distanceAt(position: Long, edge: Edge): Double {
        val fraction = ((position - edge.startMs).toDouble() / (edge.endMs - edge.startMs)).coerceIn(0.0, 1.0)
        return edge.startDistance + edge.length * fraction
    }

    /** Last edge beginning at/before this position. Equal boundaries select the new segment. */
    private fun edgeAt(position: Long): Edge {
        var low = 0
        var high = edges.lastIndex
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (edges[middle].startMs <= position) low = middle else high = middle - 1
        }
        return edges[low]
    }

    companion object {
        const val MAX_GPS_GAP_MS = 15_000L
        private const val SPEED_WINDOW_MS = 5_000L
        private const val ALTITUDE_DEADBAND_METERS = 3.0
        private const val MAX_RUNNING_SPEED_METERS_PER_SECOND = 15.0

        private fun safeSegments(points: List<RunPoint>): List<List<RunPoint>> {
            val result = mutableListOf<List<RunPoint>>()
            for (source in continuousSegments(points)) {
                var current = mutableListOf<RunPoint>()
                for (point in source) {
                    if (current.isNotEmpty() && point.timestampMs - current.last().timestampMs > MAX_GPS_GAP_MS) {
                        result += current
                        current = mutableListOf()
                    }
                    current += point.copy(segment = result.size,
                        altitudeMeters = point.altitudeMeters?.takeIf { it.isFinite() })
                }
                if (current.isNotEmpty()) result += current
            }
            return result
        }

        private fun lerp(a: Double, b: Double, fraction: Double) = a + (b - a) * fraction
        private fun interpolateLongitude(a: Double, b: Double, fraction: Double): Double {
            val delta = ((b - a + 540.0) % 360.0) - 180.0
            val value = a + delta * fraction
            return ((value + 540.0) % 360.0) - 180.0
        }
    }
}
