package com.runline.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Derived once per saved activity; chart filtering never re-scans its GPS samples. */
data class HistoryActivity(
    val id: String,
    val activityType: ActivityType,
    val startedAtMs: Long,
    val activeDurationMs: Long,
    val distanceMeters: Double,
    val paceSecondsPerKm: Double?,
)

data class HistoryWeek(val startsOn: LocalDate, val distanceMeters: Double, val activityCount: Int)

data class HistoryPeriod(
    val startsOn: LocalDate,
    val weeks: List<HistoryWeek>,
    val activities: List<HistoryActivity>,
    val distanceMeters: Double,
    val activeDurationMs: Long,
    /** Distance-weighted pace of activities with both usable distance and active time. */
    val paceSecondsPerKm: Double?,
) {
    val paceTrend: List<HistoryActivity> get() = activities.filter { it.paceSecondsPerKm != null }.takeLast(8)
}

object HistorySummary {
    fun prepare(runs: List<RunSession>): List<HistoryActivity> = runs.map { run ->
        val distance = RunMetrics.distanceMeters(run.points)
        HistoryActivity(run.id, run.activityType, run.startedAtMs, run.activeDurationMs,
            distance, RunMetrics.paceSecondsPerKm(distance, run.activeDurationMs))
    }

    /** Four Monday-starting local calendar weeks, including the current week so far. */
    fun forPeriod(
        prepared: List<HistoryActivity>,
        nowMs: Long,
        zone: ZoneId,
        activityType: ActivityType? = null,
    ): HistoryPeriod {
        val thisMonday = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstMonday = thisMonday.minusWeeks(3)
        val startMs = firstMonday.atStartOfDay(zone).toInstant().toEpochMilli()
        val included = prepared.filter {
            it.startedAtMs in startMs..nowMs && (activityType == null || it.activityType == activityType)
        }.sortedBy { it.startedAtMs }
        val weeks = (0L..3L).map { index ->
            val monday = firstMonday.plusWeeks(index)
            val weekStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
            val weekEnd = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val activities = included.filter { it.startedAtMs >= weekStart && it.startedAtMs < weekEnd }
            HistoryWeek(monday, activities.sumOf { it.distanceMeters }, activities.size)
        }
        val timedRoutes = included.filter { it.paceSecondsPerKm != null }
        val timedDistance = timedRoutes.sumOf { it.distanceMeters }
        val timedDuration = timedRoutes.sumOf { it.activeDurationMs }
        return HistoryPeriod(firstMonday, weeks, included,
            included.sumOf { it.distanceMeters }, included.sumOf { it.activeDurationMs },
            RunMetrics.paceSecondsPerKm(timedDistance, timedDuration))
    }
}
