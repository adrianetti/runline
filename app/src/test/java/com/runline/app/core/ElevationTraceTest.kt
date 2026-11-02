package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class ElevationTraceTest {
    private fun point(time: Long, height: Double? = 100.0, segment: Int = 0) =
        RunPoint(40.0, -3.0, time, 3f, height, segment)

    @Test fun preservesMeasuredClimbAndDescentWithoutSyntheticSmoothing() {
        val trace = ElevationTrace.from(listOf(point(0, 100.0), point(1_000, 108.0), point(2_000, 103.0)))
        assertEquals(listOf(100.0, 108.0, 103.0), trace.sections.single().map { it.heightMeters })
        assertEquals(2_000L, trace.durationMs)
    }

    @Test fun missingHeightsAndGpsGapsAreSeparatePaths() {
        val trace = ElevationTrace.from(listOf(point(0), point(1_000), point(2_000, null),
            point(3_000), point(4_000), point(30_000), point(31_000), point(32_000, segment = 1), point(33_000, segment = 1)))
        assertEquals(4, trace.sections.size)
        assertEquals(listOf(0L, 3_000L, 4_000L, 5_000L), trace.sections.map { it.first().positionMs })
    }

    @Test fun unknownIsolatedAndPoorGpsDataDoNotBecomeAnIllustration() {
        assertTrue(ElevationTrace.from(listOf(point(0))).sections.isEmpty())
        assertTrue(ElevationTrace.from(listOf(point(0, null), point(1_000, Double.NaN))).sections.isEmpty())
        assertTrue(ElevationTrace.from(listOf(point(0), point(1_000).copy(accuracyMeters = 31f))).sections.isEmpty())
        assertTrue(ElevationTrace.from(listOf(point(0), point(1_000).copy(longitude = 30.0))).sections.isEmpty())
    }

    @Test fun repeatedTimestampsBreakRatherThanConnectingUnobservedData() {
        val trace = ElevationTrace.from(listOf(point(0), point(1_000), point(1_000, 200.0), point(2_000, 202.0)))
        assertEquals(2, trace.sections.size)
        assertEquals(listOf(100.0, 100.0), trace.sections[0].map { it.heightMeters })
        assertEquals(listOf(200.0, 202.0), trace.sections[1].map { it.heightMeters })
    }
}
