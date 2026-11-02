package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class LapAnalysisTest {
    private fun loops(count: Int, centreLat: Double = 37.0, pauseLap: Int? = null, noisy: Boolean = false): List<RunPoint> {
        val out = mutableListOf<RunPoint>(); var timestamp = 1_000L
        for (i in 0..count * 120) {
            val lap = (i / 120).coerceAtMost(count - 1)
            val angle = i * 2 * PI / 120
            // A ~630m loop with different quarter pacing on successive laps.
            timestamp += if (lap > 0 && i % 120 in 30..59) 6000 else 3000
            out += RunPoint(centreLat + .0009 * sin(angle), -3.0 + .00113 * cos(angle), timestamp,
                if (noisy) 45f else 4f, 700 + 6 * sin(angle), if (pauseLap != null && i >= pauseLap * 120) 1 else 0)
        }
        return out
    }
    @Test fun `repeated shapes produce laps and a slower quarter comparison`() {
        val report = LapAnalysis.analyze(loops(3))
        assertTrue(report.laps.size >= 2)
        assertTrue(report.laps.all { it.distanceMeters in 550.0..700.0 && it.durationMs > 300_000 })
        assertTrue(report.laps.all { it.sectors.size == 4 && it.ascentMeters != null })
        assertNotNull(report.latestComparison)
        // Compare first two because the third repeats the second's pacing.
        val firstPair = LapAnalysis.analyze(loops(2))
        assertNotNull(firstPair.latestComparison?.slowerSector)
        assertTrue(firstPair.latestComparison!!.slowerSector!!.paceChangePercent > 8)
    }
    @Test fun `one loop unclosed routes and weak fixes do not claim repeated laps`() {
        assertTrue(LapAnalysis.analyze(loops(1)).laps.isEmpty())
        assertTrue(LapAnalysis.analyze(loops(3, noisy = true)).laps.isEmpty())
        val line = (0..500).map { RunPoint(0.0, it * .00003, 1000L + it * 3000, 4f) }
        assertTrue(LapAnalysis.analyze(line).laps.isEmpty())
    }
    @Test fun `pause and long gaps break loop comparisons`() {
        assertTrue(LapAnalysis.analyze(loops(2, pauseLap = 1)).laps.isEmpty())
        val points = loops(2).mapIndexed { i, p -> if (i >= 120) p.copy(timestampMs = p.timestampMs + 60_000) else p }
        assertTrue(LapAnalysis.analyze(points).laps.isEmpty())
    }
    @Test fun `incremental analysis matches batch and resets on replacement`() {
        val points = loops(3); val tracker = LapTracker()
        for (n in 1..points.size) tracker.update(points.take(n))
        assertEquals(LapAnalysis.analyze(points), tracker.report())
        tracker.update(points.take(50)); assertTrue(tracker.report().laps.isEmpty())
    }
    @Test fun `different nearby shapes are not compared as the same loop`() {
        val a = loops(1); val b = loops(1).map { it.copy(latitude = 37 + (it.latitude - 37) * 1.6, timestampMs = it.timestampMs + a.last().timestampMs) }
        assertTrue(LapAnalysis.analyze(a + b).laps.isEmpty())
    }
}
