package com.runline.app.core

import kotlin.math.abs
import kotlin.math.sqrt

enum class TerrainDirection { UPHILL, FLAT, DOWNHILL, UNKNOWN }

/** A trailing GPS-height estimate, not surveyed terrain, vertical accuracy or effort. */
data class TerrainEstimate(
    val direction: TerrainDirection = TerrainDirection.UNKNOWN,
    val gradePercent: Double? = null,
    val windowMeters: Double = 0.0,
)

/**
 * Fits height against distance over approximately the last 50 m. A short or noisy
 * window is unknown, including a stop after a hill. Only original fixes enter it.
 * Bounded storage/work lets the recording service and UI use the same estimator.
 */
class TerrainTrend {
    private data class Sample(val point: RunPoint, val distance: Double)
    private val samples = ArrayDeque<Sample>()
    private var previous: RunPoint? = null
    private var traveled = 0.0
    private var direction = TerrainDirection.UNKNOWN
    private var estimate = TerrainEstimate()

    fun reset() {
        samples.clear(); previous = null; traveled = 0.0
        direction = TerrainDirection.UNKNOWN; estimate = TerrainEstimate()
    }

    fun update(point: RunPoint): TerrainEstimate {
        if (point == previous) return estimate
        if (!usable(point)) { reset(); return estimate }
        val prior = previous
        if (prior == null || !MarkerMotion.canInterpolate(prior, point)) {
            reset(); previous = point; samples.addLast(Sample(point, 0.0)); return estimate
        }
        val distance = RunMetrics.distanceMeters(listOf(prior, point))
        val elapsed = point.timestampMs - prior.timestampMs
        // Accuracy allowances useful for drawing are too permissive for a slope estimate.
        if (distance * 1_000 / elapsed > 12.0) {
            reset(); previous = point; samples.addLast(Sample(point, 0.0)); return estimate
        }
        previous = point
        traveled += distance
        samples.addLast(Sample(point, traveled))
        while (samples.size > 1 && (traveled - samples.first().distance > 55.0 ||
                point.timestampMs - samples.first().point.timestampMs > 120_000L || samples.size > 180)) samples.removeFirst()
        val first = samples.first()
        val span = traveled - first.distance
        fun unknown(): TerrainEstimate {
            direction = TerrainDirection.UNKNOWN
            return TerrainEstimate(windowMeters = span).also { estimate = it }
        }
        if (samples.size < 6 || span < 35.0 || point.timestampMs - first.point.timestampMs < 10_000L ||
            RunMetrics.distanceMeters(listOf(first.point, point)) < 20.0) return unknown()
        // A previous 50 m window must not keep describing a climb after movement stops.
        val recent = samples.firstOrNull { point.timestampMs - it.point.timestampMs <= 10_000L } ?: return unknown()
        val recentSeconds = (point.timestampMs - recent.point.timestampMs) / 1_000.0
        if (recentSeconds < 4.0 || RunMetrics.distanceMeters(listOf(recent.point, point)) / recentSeconds < .6) return unknown()
        val meanDistance = samples.sumOf { it.distance - first.distance } / samples.size
        val meanHeight = samples.sumOf { it.point.altitudeMeters!! } / samples.size
        val variance = samples.sumOf { val x = it.distance - first.distance - meanDistance; x * x }
        if (variance <= 0.0) return unknown()
        val slope = samples.sumOf { (it.distance - first.distance - meanDistance) * (it.point.altitudeMeters!! - meanHeight) } / variance
        val grade = slope * 100.0
        val residual = sqrt(samples.sumOf {
            val error = it.point.altitudeMeters!! - meanHeight - slope * (it.distance - first.distance - meanDistance)
            error * error
        } / samples.size)
        if (!grade.isFinite() || abs(grade) > 40.0 || residual > 1.8) return unknown()
        direction = when {
            grade >= 3.0 -> TerrainDirection.UPHILL
            grade <= -3.0 -> TerrainDirection.DOWNHILL
            direction == TerrainDirection.UPHILL && grade > 1.5 -> TerrainDirection.UPHILL
            direction == TerrainDirection.DOWNHILL && grade < -1.5 -> TerrainDirection.DOWNHILL
            else -> TerrainDirection.FLAT
        }
        return TerrainEstimate(direction, grade, span).also { estimate = it }
    }

    private fun usable(point: RunPoint) = point.hasValidCoordinates() && point.timestampMs >= 0 &&
        point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..20f &&
        point.altitudeMeters?.isFinite() == true
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
