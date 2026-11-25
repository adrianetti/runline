package com.runline.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteShapeTest {
    @Test fun aRouteBecomesRunPointsInOrderWithItsHeights() {
        val raw = listOf(Triple(37.0, -3.6, 700.0), Triple(37.001, -3.6, 712.0), Triple(37.002, -3.6, null))
        val route = HikingRoute.of("fixture", "Fixture", raw)
        val points = route.runPoints()
        assertEquals(listOf(37.0, 37.001, 37.002), points.map { it.latitude })
        assertEquals(listOf(0L, 1_000L, 2_000L), points.map { it.timestampMs })
        assertEquals(700.0, points.first().altitudeMeters!!, 0.0)
    }

    @Test fun difficultyRisesWithEffort() {
        assertEquals(RouteDifficulty.EASY, RouteEstimates.difficulty(9.9))
        assertEquals(RouteDifficulty.MODERATE, RouteEstimates.difficulty(19.9))
        assertEquals(RouteDifficulty.HARD, RouteEstimates.difficulty(29.9))
        assertEquals(RouteDifficulty.VERY_HARD, RouteEstimates.difficulty(30.0))
    }

    @Test fun aResetTrackerForgetsItsStop() {
        val tracker = StillnessTracker()
        repeat(8) { tracker.update(37.17, -3.6, 1_000L * (it + 1), 5f, .2f) }
        tracker.reset()
        assertNull(tracker.stoppedSinceMs)
    }
}
