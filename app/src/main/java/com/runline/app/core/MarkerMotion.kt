package com.runline.app.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Display-only movement between observed fixes. Never changes a recorded sample. */
object MarkerMotion {
    fun canInterpolate(previous: RunPoint?, next: RunPoint?): Boolean {
        if (previous == null || next == null || previous.segment != next.segment) return false
        if (!valid(previous) || !valid(next)) return false
        val span = next.timestampMs - previous.timestampMs
        if (span !in 1L..15_000L) return false
        val latitudeA = Math.toRadians(previous.latitude)
        val latitudeB = Math.toRadians(next.latitude)
        val latitudeDelta = latitudeB - latitudeA
        val longitudeDelta = Math.toRadians(next.longitude - previous.longitude)
        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(latitudeA) * cos(latitudeB) * sin(longitudeDelta / 2).let { it * it }
        val distance = 2 * 6_371_008.8 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
        return distance <= 15.0 * span / 1_000.0 + previous.accuracyMeters + next.accuracyMeters
    }

    /** One fix of visual latency; avoid lengthy catch-up after a slower GPS interval. */
    fun durationMs(previous: RunPoint, next: RunPoint): Long =
        (next.timestampMs - previous.timestampMs).coerceIn(300L, 1_500L)

    fun interpolate(from: RunPoint, to: RunPoint, fraction: Double): RunPoint {
        val amount = fraction.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 1.0
        if (amount == 0.0) return from
        if (amount == 1.0) return to
        val longitudeDelta = ((to.longitude - from.longitude + 540.0) % 360.0) - 180.0
        val altitudeA = from.altitudeMeters?.takeIf(Double::isFinite)
        val altitudeB = to.altitudeMeters?.takeIf(Double::isFinite)
        return RunPoint(
            latitude = from.latitude + (to.latitude - from.latitude) * amount,
            longitude = ((from.longitude + longitudeDelta * amount + 540.0) % 360.0) - 180.0,
            timestampMs = from.timestampMs + ((to.timestampMs - from.timestampMs) * amount).roundToLong(),
            accuracyMeters = maxOf(from.accuracyMeters, to.accuracyMeters),
            altitudeMeters = if (altitudeA != null && altitudeB != null) altitudeA + (altitudeB - altitudeA) * amount else null,
            segment = to.segment,
            verticalAccuracyMeters = if (from.verticalAccuracyMeters != null && to.verticalAccuracyMeters != null)
                maxOf(from.verticalAccuracyMeters, to.verticalAccuracyMeters) else null,
        )
    }

    fun interpolateBearing(from: Double?, to: Double?, fraction: Double): Double? {
        val end = to?.takeIf(Double::isFinite) ?: return null
        val start = from?.takeIf(Double::isFinite) ?: return normalize(end)
        val amount = fraction.takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 1.0
        val delta = ((end - start + 540.0) % 360.0) - 180.0
        return normalize(start + delta * amount)
    }

    private fun normalize(value: Double) = ((value % 360.0) + 360.0) % 360.0
    private fun valid(point: RunPoint) = point.hasValidCoordinates() && abs(point.latitude) < 85.051129 &&
        point.timestampMs >= 0 && point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..30f
}
