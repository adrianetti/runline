package com.runline.app.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object RunMetrics {
    private const val EARTH_RADIUS_METERS = 6_371_008.8
    private const val SPLIT_METERS = 1_000.0
    private const val EPSILON = 0.000001

    /** Horizontal GPS distance. Pauses and missing/invalid fixes never add connecting lines. */
    fun distanceMeters(points: List<RunPoint>): Double =
        continuousSegments(points).sumOf { segment ->
            segment.zipWithNext().sumOf { (a, b) -> between(a, b) }
        }

    /** Average timer pace, not instantaneous pace. Null means there is not enough data. */
    fun paceSecondsPerKm(distanceMeters: Double, durationMs: Long): Double? {
        if (!distanceMeters.isFinite() || distanceMeters <= 0.0 || durationMs <= 0L) return null
        return (durationMs.toDouble() / distanceMeters).takeIf { it.isFinite() }
    }

    /**
     * Interpolates kilometre boundaries between fixes. Durations use sample timestamps,
     * including stationary samples, but exclude pauses and periods without a GPS baseline.
     * They intentionally do not invent time to make their sum equal the run's timer.
     */
    fun splits(run: RunSession): List<RunSplit> {
        val splits = mutableListOf<RunSplit>()
        var splitDistance = 0.0
        var splitDuration = 0.0
        var totalDuration = 0.0
        var emittedDuration = 0L

        fun emit() {
            // Round cumulative time, so rounding per kilometre cannot create or lose time.
            val roundedTotal = totalDuration.roundToLong()
            splits.add(RunSplit(splits.size + 1, splitDistance, max(0L, roundedTotal - emittedDuration)))
            emittedDuration = roundedTotal
            splitDistance = 0.0
            splitDuration = 0.0
        }

        for (segment in continuousSegments(run.points)) {
            for ((a, b) in segment.zipWithNext()) {
                var remainingDistance = between(a, b)
                var remainingDuration = b.timestampMs.toDouble() - a.timestampMs.toDouble()
                if (remainingDistance <= EPSILON) {
                    splitDuration += remainingDuration
                    totalDuration += remainingDuration
                    continue
                }
                while (remainingDistance > EPSILON) {
                    val distance = min(SPLIT_METERS - splitDistance, remainingDistance)
                    val duration = remainingDuration * distance / remainingDistance
                    splitDistance += distance
                    splitDuration += duration
                    totalDuration += duration
                    remainingDistance -= distance
                    remainingDuration -= duration
                    if (splitDistance >= SPLIT_METERS - EPSILON) {
                        splitDistance = SPLIT_METERS
                        emit()
                    }
                }
            }
        }
        if (splitDistance > EPSILON) {
            emit()
        } else if (splitDuration > 0.0 && splits.isNotEmpty()) {
            // A stop exactly on a kilometre boundary belongs to that final kilometre.
            val last = splits.last()
            splits[splits.lastIndex] = last.copy(
                durationMs = last.durationMs + max(0L, totalDuration.roundToLong() - emittedDuration),
            )
        }
        return splits
    }

    /**
     * A rough GPS ascent estimate, never corrected terrain elevation. A three-metre
     * deadband suppresses small altitude oscillations. Null means insufficient altitude data.
     */
    fun elevationGainMeters(points: List<RunPoint>): Double? {
        var gain = 0.0
        var hasAltitudePair = false
        for (segment in continuousSegments(points)) {
            var anchor: Double? = null
            for (point in segment) {
                val altitude = point.altitudeMeters?.takeIf { it.isFinite() }
                if (altitude == null) {
                    anchor = null
                    continue
                }
                val previous = anchor
                if (previous == null) {
                    anchor = altitude
                    continue
                }
                hasAltitudePair = true
                val difference = altitude - previous
                if (difference >= 3.0) {
                    gain += difference
                    anchor = altitude
                } else if (difference <= -3.0) {
                    anchor = altitude
                }
            }
        }
        return gain.takeIf { hasAltitudePair && it.isFinite() }
    }

    private fun between(a: RunPoint, b: RunPoint): Double {
        val latitudeA = Math.toRadians(a.latitude)
        val latitudeB = Math.toRadians(b.latitude)
        val latitudeDelta = latitudeB - latitudeA
        val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(latitudeA) * cos(latitudeB) * sin(longitudeDelta / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}
