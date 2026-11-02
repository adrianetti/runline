package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class ObservedElevationTraceTest {
    private fun point(time: Long, height: Double? = 100.0, segment: Int = 0) = RunPoint(40.0, -3.0, time, 3f, height, segment)
    private fun replay(points: List<RunPoint>) = RunReplay(RunSession("trace", 0, points.lastOrNull()?.timestampMs ?: 0, 0, points))

    @Test fun heightQualityDoesNotShortenObservedPlaybackClock() {
        val points = listOf(point(0), point(1_000, 101.0), point(2_000).copy(accuracyMeters = 40f),
            point(3_000, 103.0), point(4_000, 104.0), point(5_000, null), point(6_000, 106.0), point(7_000, 107.0))
        val replay = replay(points); val trace = ObservedElevationTrace.fromReplay(replay)
        assertEquals(replay.durationMs, trace.durationMs)
        assertEquals(7_000L, trace.durationMs)
        assertNull(trace.at(1_500, 0)); assertNull(trace.at(2_000, 0)); assertNull(trace.at(5_500, 0))
        assertEquals(103.5, trace.at(3_500, 0)!!.heightMeters, .001)
        assertEquals(106.5, trace.at(6_500, 0)!!.heightMeters, .001)
    }

    @Test fun cutBoundarySelectsActualSegmentAndIsolatedEndpointHasNoDot() {
        val points = listOf(point(0, 100.0), point(1_000, 102.0), point(30_000, 200.0), point(31_000, 204.0), point(50_000, 400.0))
        val replay = replay(points); val trace = ObservedElevationTrace.fromReplay(replay)
        assertEquals(2_000L, trace.durationMs)
        val atCut = replay.at(1_000)!!
        assertEquals(200.0, trace.at(atCut.positionMs, atCut.segmentIndex)!!.heightMeters, .001)
        val endpoint = replay.at(replay.durationMs)!!
        assertNull(trace.at(endpoint.positionMs, endpoint.segmentIndex)); assertNull(trace.latestCursor)
    }

    @Test fun liveAndReplayTimelinesAgreeIncludingBadCoordinatesAndPauses() {
        val points = listOf(point(0), point(1_000, 102.0), point(2_000).copy(latitude = Double.NaN),
            point(3_000), point(4_000, 104.0), point(10_000, 108.0, 1), point(11_000, 110.0, 1))
        val live = ObservedElevationTrace.fromPoints(points)
        val saved = ObservedElevationTrace.fromReplay(replay(points))
        assertEquals(saved.durationMs, live.durationMs); assertEquals(saved.sections, live.sections)
        assertEquals(110.0, live.latestCursor!!.heightMeters, .001)
        assertNull(ObservedElevationTrace.fromPoints(points + point(12_000).copy(latitude = Double.NaN)).latestCursor)
    }

    @Test fun jumpsAndSingleHeightsDoNotCreateDotOrConnectingProfile() {
        val points = listOf(point(0), point(1_000, 101.0), point(2_000, 102.0).copy(longitude = 20.0), point(3_000, null))
        val trace = ObservedElevationTrace.fromReplay(replay(points))
        assertNull(trace.at(1_500, 0)); assertNull(trace.at(2_000, 0)); assertNull(trace.latestCursor)
        assertEquals(1, trace.sections.size)
        assertTrue(ObservedElevationTrace.fromPoints(listOf(point(0))).sections.isEmpty())
    }
}
