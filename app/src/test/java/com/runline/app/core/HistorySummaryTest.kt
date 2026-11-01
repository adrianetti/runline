package com.runline.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistorySummaryTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun instant(value: String) = Instant.parse(value).toEpochMilli()
    private fun activity(id: String, at: String, meters: Double = 1000.0, duration: Long = 300_000,
        type: ActivityType = ActivityType.RUN) = HistoryActivity(id, type, instant(at), duration, meters,
            RunMetrics.paceSecondsPerKm(meters, duration))

    @Test fun localMondayBoundariesAndDstKeepTheCurrentPartialWeekAndExcludeFutureData() {
        val now = instant("2026-11-01T22:30:00Z") // Sunday 23:30, Madrid's clocks changed last week.
        val summary = HistorySummary.forPeriod(listOf(
            activity("before", "2026-10-04T21:59:59Z"),
            activity("first", "2026-10-04T22:00:00Z"),
            activity("week2", "2026-10-11T22:00:00Z"),
            activity("dstSunday", "2026-10-25T22:59:59Z"),
            activity("week4", "2026-10-25T23:00:00Z"),
            activity("laterTonight", "2026-11-01T22:31:00Z"),
            activity("nextMonday", "2026-11-01T23:00:00Z"),
        ), now, zone)
        assertEquals(LocalDate.of(2026, 10, 5), summary.startsOn)
        assertEquals(listOf(1, 1, 1, 1), summary.weeks.map { it.activityCount })
        assertEquals(4000.0, summary.distanceMeters, 0.001)
        assertEquals(listOf("first", "week2", "dstSunday", "week4"), summary.activities.map { it.id })
    }

    @Test fun weightedPaceUsesActiveTimerAndUsableRoutesWithoutMixingChosenActivityTypes() {
        val prepared = listOf(
            activity("short", "2026-10-31T08:00:00Z", meters = 1000.0, duration = 300_000),
            activity("long", "2026-10-31T09:00:00Z", meters = 9000.0, duration = 3_600_000),
            activity("noGps", "2026-10-31T10:00:00Z", meters = 0.0, duration = 600_000),
            activity("walk", "2026-10-31T11:00:00Z", duration = 900_000, type = ActivityType.WALK),
        )
        val summary = HistorySummary.forPeriod(prepared, instant("2026-11-01T12:00:00Z"), zone, ActivityType.RUN)
        assertEquals(3, summary.activities.size)
        assertEquals(4_500_000L, summary.activeDurationMs) // No-GPS activity still contributes its timer.
        assertEquals(390.0, summary.paceSecondsPerKm!!, 0.001) // 65 minutes / 10 km, not mean(5:00, 6:40).
        assertEquals(listOf("short", "long"), summary.paceTrend.map { it.id })
    }

    @Test fun storedActiveDurationExcludesPauseTimeAndMissingRouteDoesNotBecomeZeroPace() {
        val start = instant("2026-10-31T10:00:00Z")
        val run = RunSession("paused", start, start + 3_600_000, 600_000, listOf(
            RunPoint(0.0, 0.0, start, 5f),
            RunPoint(0.0, 0.005, start + 100_000, 5f),
            RunPoint(1.0, 1.0, start + 3_500_000, 5f, segment = 1),
            RunPoint(1.0, 1.005, start + 3_600_000, 5f, segment = 1),
        ))
        val measured = HistorySummary.prepare(listOf(run, run.copy(id = "empty", points = emptyList())))
        assertEquals(600_000L, measured.first().activeDurationMs)
        assertEquals(RunMetrics.paceSecondsPerKm(RunMetrics.distanceMeters(run.points), 600_000),
            measured.first().paceSecondsPerKm)
        assertNull(measured.last().paceSecondsPerKm)
    }

    @Test fun emptyPeriodRetainsFourHonestZeroWeeksAndNoPace() {
        val summary = HistorySummary.forPeriod(emptyList(), instant("2026-11-01T12:00:00Z"), zone)
        assertEquals(4, summary.weeks.size)
        assertEquals(0.0, summary.distanceMeters, 0.0)
        assertEquals(0L, summary.activeDurationMs)
        assertNull(summary.paceSecondsPerKm)
    }

    @Test fun trendKeepsLatestEightEligibleActivitiesInTimeOrder() {
        val runs = (1..12).map { day -> activity("$day", "2026-10-${day.toString().padStart(2, '0')}T12:00:00Z") }.reversed()
        val summary = HistorySummary.forPeriod(runs, instant("2026-10-25T12:00:00Z"), zone)
        assertEquals((5..12).map(Int::toString), summary.paceTrend.map { it.id })
    }
}
