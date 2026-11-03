package com.runline.app.ai

import com.runline.app.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil

class AiPaceWindowsTest {
    /** Synthetic straight path; the recorded failure is reproduced using distances/times only. */
    private fun route(vararg sections: Pair<Double, Long>): RunSession {
        val points = mutableListOf(RunPoint(0.0, 0.0, 1_000L, 3f, 0.0))
        var distance = 0.0; var elapsed = 1_000L
        for ((meters, duration) in sections) {
            val steps = ceil(duration / 2_000.0).toInt()
            for (step in 1..steps) points += RunPoint((distance + meters * step / steps) / 111_195.0802335329,
                0.0, elapsed + duration * step / steps, 3f, 0.0)
            distance += meters; elapsed += duration
        }
        return RunSession("synthetic", 1_000L, elapsed, elapsed - 1_000L, points)
    }

    @Test fun shorterPartialSplitIsNotFasterThanThePreviousKilometre() {
        val summary = AiRunSummary.of(route(1000.0 to 352_789L, 1000.0 to 338_993L, 801.737 to 288_218L))
        val splits = summary.getJSONArray("splits")
        val full = splits.getJSONObject(1); val partial = splits.getJSONObject(2)
        assertTrue(partial.getDouble("duration_seconds") < full.getDouble("duration_seconds"))
        assertTrue(partial.getDouble("pace_seconds_per_km") > full.getDouble("pace_seconds_per_km"))
        assertEquals(338.993, full.getDouble("pace_seconds_per_km"), .002)
        assertEquals(359.49, partial.getDouble("pace_seconds_per_km"), .02)
        assertTrue(full.getBoolean("is_full_kilometre")); assertFalse(partial.getBoolean("is_full_kilometre"))
        assertNotEquals("FASTER", summary.getJSONObject("recent_pace_comparison").getString("trend"))
    }

    @Test fun comparisonUsesTwoObservedWindowsWithCorrectPaceDirection() {
        fun comparison(beforeMeters: Double, afterMeters: Double) = AiRunSummary.of(
            route(beforeMeters to 120_000L, afterMeters to 120_000L)).getJSONObject("recent_pace_comparison")
        val slower = comparison(400.0, 300.0)
        assertEquals("SLOWER", slower.getString("trend"))
        assertEquals(300.0, slower.getDouble("previous_pace_seconds_per_km"), .001)
        assertEquals(400.0, slower.getDouble("recent_pace_seconds_per_km"), .001)
        assertEquals(120.0, slower.getDouble("previous_observed_seconds"), .001)
        assertEquals(120.0, slower.getDouble("recent_observed_seconds"), .001)
        assertEquals("FASTER", comparison(300.0, 400.0).getString("trend"))
        assertEquals("SIMILAR", comparison(333.0, 326.0).getString("trend"))
    }

    @Test fun pauseOrGpsGapDoesNotSupplyAnOldComparisonBaseline() {
        val continuous = route(800.0 to 240_000L)
        val paused = continuous.copy(points = continuous.points.map { if (it.timestampMs > 121_000) it.copy(segment = 1) else it })
        assertTrue(AiRunSummary.of(paused).isNull("recent_pace_comparison"))
        val gap = continuous.copy(points = continuous.points.map { if (it.timestampMs > 121_000) it.copy(timestampMs = it.timestampMs + 30_000) else it })
        assertTrue(AiRunSummary.of(gap).isNull("recent_pace_comparison"))
    }

    @Test fun briefOrMostlyStationaryWindowsDoNotImplyAPaceTrend() {
        assertTrue(AiRunSummary.of(route(400.0 to 120_000L)).isNull("recent_pace_comparison"))
        assertTrue(AiRunSummary.of(route(80.0 to 120_000L, 80.0 to 120_000L)).isNull("recent_pace_comparison"))
    }
}
