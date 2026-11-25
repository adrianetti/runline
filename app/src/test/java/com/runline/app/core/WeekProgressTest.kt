package com.runline.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WeekProgressTest {
    private val zone = ZoneId.of("Europe/Madrid")

    private fun at(
        day: Int,
        hour: Int = NOON,
    ) = LocalDateTime
        .of(YEAR, NOVEMBER, day, hour, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun activity(
        day: Int,
        metres: Double,
        hour: Int = NOON,
    ) = HistoryActivity("run-$day-$hour", ActivityType.RUN, at(day, hour), MINUTES_30, metres, PACE)

    @Test fun sumsOnlyTheCurrentMondayWeek() {
        val week = WeekProgress.of(listOf(activity(SUNDAY_BEFORE, FIVE_KM), activity(MONDAY, FIVE_KM), activity(WEDNESDAY, TEN_KM)), at(THURSDAY), zone)
        assertEquals(FIVE_KM + TEN_KM, week.distanceMeters, 0.0)
        assertEquals(2 * MINUTES_30, week.activeDurationMs)
        assertEquals(2, week.activityCount)
        assertEquals(listOf(true, false, true, false, false, false, false), week.activeDays)
        assertEquals(2, week.activeDayCount)
        assertEquals(THURSDAY - MONDAY, week.todayIndex)
    }

    @Test fun twoActivitiesOnOneDayCountAsOneActiveDay() {
        val week = WeekProgress.of(listOf(activity(MONDAY, FIVE_KM, MORNING), activity(MONDAY, FIVE_KM)), at(MONDAY, EVENING), zone)
        assertEquals(2, week.activityCount)
        assertEquals(1, week.activeDayCount)
        assertEquals(0, week.todayIndex)
    }

    @Test fun ignoresActivitiesAfterNow() {
        val week = WeekProgress.of(listOf(activity(THURSDAY, FIVE_KM, EVENING)), at(THURSDAY), zone)
        assertEquals(0, week.activityCount)
        assertEquals(0.0, week.distanceMeters, 0.0)
        assertEquals(List(WeekProgress.DAYS) { false }, week.activeDays)
    }

    @Test fun emptyHistoryIsAnEmptyWeek() {
        val week = WeekProgress.of(emptyList(), at(SUNDAY), zone)
        assertEquals(0, week.activeDayCount)
        assertEquals(WeekProgress.DAYS - 1, week.todayIndex)
    }

    private companion object {
        const val YEAR = 2026
        const val NOVEMBER = 11
        const val SUNDAY_BEFORE = 15
        const val MONDAY = 16
        const val WEDNESDAY = 18
        const val THURSDAY = 19
        const val SUNDAY = 22
        const val MORNING = 7
        const val NOON = 12
        const val EVENING = 20
        const val MINUTES_30 = 1_800_000L
        const val FIVE_KM = 5_000.0
        const val TEN_KM = 10_000.0
        const val PACE = 360.0
    }
}
