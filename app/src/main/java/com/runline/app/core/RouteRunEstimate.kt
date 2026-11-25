package com.runline.app.core

import java.time.ZoneId
import kotlin.math.roundToInt

object RouteRunEstimate {
    const val FLAT_METERS_PER_ASCENT_METER = 7.92
    private const val METERS_PER_KM = 1_000.0
    private const val SECONDS_PER_MINUTE = 60.0

    fun minutes(
        distanceMeters: Double,
        ascentMeters: Double?,
        paceSecondsPerKm: Double,
    ): Int? {
        val usable = distanceMeters.isFinite() && distanceMeters > 0.0 && paceSecondsPerKm.isFinite() && paceSecondsPerKm > 0.0
        if (!usable) return null
        val climb = ascentMeters?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        val flatMeters = distanceMeters + climb * FLAT_METERS_PER_ASCENT_METER
        return (flatMeters / METERS_PER_KM * paceSecondsPerKm / SECONDS_PER_MINUTE).roundToInt()
    }

    fun forRoute(
        stats: RouteStats,
        runPaceSecondsPerKm: Double?,
    ): Int {
        val running = runPaceSecondsPerKm?.let { minutes(stats.distanceMeters, stats.ascentMeters, it) }
        return running?.coerceAtMost(stats.estimatedMinutes) ?: stats.estimatedMinutes
    }

    fun recentPace(
        activity: ActivityType,
        history: List<HistoryActivity>,
        nowMs: Long,
        zone: ZoneId,
    ): Double? =
        if (activity == ActivityType.RUN) {
            HistorySummary.forPeriod(history, nowMs, zone, ActivityType.RUN).paceSecondsPerKm
        } else {
            null
        }
}
