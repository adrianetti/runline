package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class ReplayPresentationTest {
    @Test fun choosesClosestPlaybackDurationIncludingLongActivities() {
        assertEquals(1, ReplayPresentation.defaultSpeed(0))
        assertEquals(1, ReplayPresentation.defaultSpeed(30_000))
        assertEquals(5, ReplayPresentation.defaultSpeed(150_000))
        assertEquals(60, ReplayPresentation.defaultSpeed(1_800_000))
        assertEquals(240, ReplayPresentation.defaultSpeed(7_200_000))
        for (duration in listOf(45_000L, 320_000L, 2_400_000L, 5_000_000L)) {
            val speed = ReplayPresentation.defaultSpeed(duration)
            assertTrue(ReplayPresentation.speeds.all {
                kotlin.math.abs(duration.toDouble() / speed - 30_000) <= kotlin.math.abs(duration.toDouble() / it - 30_000)
            })
        }
    }
    @Test fun bearingFollowsObservedTravelAndDoesNotJoinGap() {
        val east = listOf(p(0.0, 0.0, 0), p(0.0, .0005, 5_000), p(0.0, .001, 10_000))
        val replay = replay(east + listOf(p(1.0, 1.0, 60_000, 1), p(1.0005, 1.0, 65_000, 1)))
        assertEquals(90.0, ReplayPresentation.bearing(replay, 5_000)!!, .1)
        assertEquals(0.0, ReplayPresentation.bearing(replay, 11_000)!!, .1)
    }
    @Test fun stationaryAndIsolatedFixesHaveNoInventedHeading() {
        assertNull(ReplayPresentation.bearing(replay(listOf(p(0.0, 0.0, 0))), 0))
        assertNull(ReplayPresentation.bearing(replay(listOf(p(0.0, 0.0, 0), p(0.0, 0.0, 5_000))), 2_000))
        val gap = replay(listOf(p(0.0, 0.0, 0), p(0.0, .0005, 5_000), p(1.0, 1.0, 60_000)))
        assertNull(ReplayPresentation.bearing(gap, gap.durationMs))
        assertEquals(1, gap.at(gap.durationMs)!!.point.segment)
    }
    private fun p(lat: Double, lon: Double, time: Long, segment: Int = 0) = RunPoint(lat, lon, time, 4f, null, segment)
    private fun replay(points: List<RunPoint>) = RunReplay(RunSession("test", 0, points.last().timestampMs, 65_000, points))
}
