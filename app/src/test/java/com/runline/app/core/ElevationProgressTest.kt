package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class ElevationProgressTest {
    @Test fun appendedTwoHourTrackMatchesReplayAcrossGapsPausesAndMissingElevation() {
        val points = (0..7200).map { i -> RunPoint(37.0 + i * 0.000001, -3.5,
            i * 1000L + if (i >= 5000) 30_000L else 0L, 4f,
            if (i in 200..240) null else 600.0 + 10.0 * kotlin.math.sin(i / 20.0), if (i < 3000) 0 else 1) }
        val progress = ElevationProgress()
        for (count in 1..points.size step 19) progress.update(points.subList(0, count))
        progress.update(points)
        val expected = RunReplay(RunSession("test", 0, 7_230_000, 7_200_000, points)).summary
        assertEquals(expected.elevationGainMeters!!, progress.climbedMeters!!, 0.000001)
        assertEquals(expected.elevationLossMeters!!, progress.descendedMeters!!, 0.000001)
        progress.update(points)
        assertEquals(expected.elevationGainMeters!!, progress.climbedMeters!!, 0.000001)
    }
    @Test fun replacedTrackAndNoElevationResetTotals() {
        val a = RunPoint(0.0, 0.0, 0, 3f, 100.0)
        val progress = ElevationProgress()
        progress.update(listOf(a, a.copy(timestampMs = 1000, altitudeMeters = 110.0)))
        assertEquals(10.0, progress.climbedMeters!!, 0.0)
        progress.update(listOf(a.copy(altitudeMeters = null)))
        assertNull(progress.climbedMeters); assertNull(progress.descendedMeters)
    }
}
