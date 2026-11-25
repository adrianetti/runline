package com.runline.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class WeekProgress(
    val distanceMeters: Double,
    val activeDurationMs: Long,
    val activityCount: Int,
    val activeDays: List<Boolean>,
    val todayIndex: Int,
) {
    val activeDayCount: Int get() = activeDays.count { it }

    companion object {
        const val DAYS = 7

        fun of(
            activities: List<HistoryActivity>,
            nowMs: Long,
            zone: ZoneId,
        ): WeekProgress {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val startMs = monday.atStartOfDay(zone).toInstant().toEpochMilli()
            val included = activities.filter { it.startedAtMs in startMs..nowMs }
            val days =
                included
                    .map { ChronoUnit.DAYS.between(monday, Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate()).toInt() }
                    .toSet()
            return WeekProgress(
                distanceMeters = included.sumOf { it.distanceMeters },
                activeDurationMs = included.sumOf { it.activeDurationMs },
                activityCount = included.size,
                activeDays = (0 until DAYS).map { it in days },
                todayIndex = ChronoUnit.DAYS.between(monday, today).toInt(),
            )
        }
    }
}
