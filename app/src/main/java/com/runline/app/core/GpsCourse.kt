package com.runline.app.core

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Direction of travel, clockwise from true north, derived only from accepted GPS fixes.
 * This is not the direction the phone is facing. A distance/accuracy deadband prevents
 * stationary GPS jitter from spinning the arrow; the last observed course survives a stop.
 * Pauses, missing GPS, invalid samples and clock regressions require a fresh baseline.
 */
class GpsCourse {
    var bearingDegrees: Double? = null
        private set
    private var anchor: RunPoint? = null
    private var previous: RunPoint? = null

    fun reset() {
        bearingDegrees = null
        anchor = null
        previous = null
    }

    fun update(point: RunPoint) {
        if (!point.hasValidCoordinates() || point.timestampMs < 0L ||
            !point.accuracyMeters.isFinite() || point.accuracyMeters !in 0f..30f) {
            reset()
            return
        }
        val last = previous
        if (point == last) return // UI timer updates may repeat the same accepted GPS fix.
        val elapsed = last?.let { point.timestampMs - it.timestampMs }
        if (last == null || point.segment != last.segment || elapsed == null || elapsed !in 1L..15_000L) {
            restartAt(point)
            return
        }
        // Defensive guard: a discontinuity must not appear as a direction of travel.
        if (distance(last, point) > 15.0 * elapsed / 1_000.0 + last.accuracyMeters + point.accuracyMeters) {
            restartAt(point)
            return
        }
        previous = point
        val baseline = anchor ?: point.also { anchor = it }
        val duration = point.timestampMs - baseline.timestampMs
        if (duration > 30_000L) {
            anchor = point
            return
        }
        val distance = distance(baseline, point)
        val minimumDistance = max(6.0, 1.5 * hypot(baseline.accuracyMeters.toDouble(), point.accuracyMeters.toDouble()))
        if (distance < minimumDistance) return
        // Slow wandering across the deadband while stopped is not reliable heading.
        if (duration > 0L && distance / (duration / 1_000.0) >= 0.6) {
            val start = Math.toRadians(baseline.latitude)
            val end = Math.toRadians(point.latitude)
            val longitude = Math.toRadians(point.longitude - baseline.longitude)
            val y = sin(longitude) * cos(end)
            val x = cos(start) * sin(end) - sin(start) * cos(end) * cos(longitude)
            bearingDegrees = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }
        anchor = point
    }

    private fun restartAt(point: RunPoint) {
        reset()
        anchor = point
        previous = point
    }

    private fun distance(a: RunPoint, b: RunPoint): Double {
        val latitudeA = Math.toRadians(a.latitude)
        val latitudeB = Math.toRadians(b.latitude)
        val latitudeDelta = latitudeB - latitudeA
        val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(latitudeA) * cos(latitudeB) * sin(longitudeDelta / 2).let { it * it }
        return 2 * 6_371_008.8 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}
