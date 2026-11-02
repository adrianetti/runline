package com.runline.app.ai

import com.runline.app.core.*
import org.junit.Assert.*
import org.junit.Test

class AiRunSummaryTest {
    @Test fun summaryOmitsCoordinatesAndPhotosAndRequiresNotesOptIn() {
        val run = RunSession("test", 1000, 5000, 4000, listOf(
            RunPoint(37.123456, -3.123456, 1000, 3f, 700.0), RunPoint(37.123556, -3.123456, 5000, 3f, 704.0)))
        val details = ActivityDetails("Private title", "Private note", 6, routine = RoutineType.LONG,
            photoIds = listOf("12345678-1234-1234-1234-123456789abc.jpg"))
        val data = AiRunSummary.of(run, details)
        assertEquals(6, data.getInt("self_reported_effort_1_to_10")); assertEquals("long", data.getString("routine"))
        assertFalse(data.has("user_notes")); assertFalse(data.has("user_title"))
        assertFalse(data.toString().contains("37.123456")); assertFalse(data.toString().contains("123456789abc"))
        assertEquals("Private note", AiRunSummary.of(run, details, true).getString("user_notes"))
    }
    @Test fun pausedGapDoesNotCreateSlowRecentPace() {
        val run = RunSession("test", 1000, 101000, 10000, listOf(
            RunPoint(37.0, -3.0, 1000, 3f, segment = 0), RunPoint(37.0001, -3.0, 6000, 3f, segment = 0),
            RunPoint(37.001, -3.0, 96000, 3f, segment = 1), RunPoint(37.0011, -3.0, 101000, 3f, segment = 1)))
        val data = AiRunSummary.of(run)
        assertEquals(10.0, data.getDouble("gps_observed_seconds"), 0.001)
        assertEquals(RunMetrics.paceSecondsPerKm(RunMetrics.distanceMeters(run.points), 10000)!!, data.getDouble("recent_pace_seconds_per_km"), .001)
    }
}
