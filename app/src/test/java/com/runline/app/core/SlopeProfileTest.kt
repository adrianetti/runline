package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class SlopeProfileTest {
    private fun points() = (0..100).map { i -> RunPoint(i * 2.0 / 111195.0, 0.0, i * 1000L, 3f, when { i < 30 -> 100.0; i < 60 -> 100.0 + (i - 30) * .15; else -> 104.5 - (i - 60) * .1 }) }
    @Test fun recentShapeShowsTransitionsWithoutBorrowingTheFuture() {
        val points = points(); val full = ObservedElevationTrace.fromPoints(points)
        val before = SlopeProfile.at(full, 30_000, 0)
        assertTrue(before.heights.filterNotNull().all { it == 100.0 })
        val a = SlopeProfile.at(full, 70_000, 0)
        val b = SlopeProfile.at(ObservedElevationTrace.fromPoints(points.take(71)), 70_000, 0)
        assertEquals(a, b); assertTrue(a.heights.filterNotNull().max() > a.heights.last()!!)
        assertEquals(a, SlopeProfile.at(full, 70_000, 0))
    }
    @Test fun gapsMissingHeightsAndSegmentStartsStayBroken() {
        val points = points().mapIndexed { i, p -> if (i in 65..72) p.copy(altitudeMeters = null) else p }
        val profile = SlopeProfile.at(ObservedElevationTrace.fromPoints(points), 80_000, 0)
        assertTrue(profile.heights.any { it == null }); assertNotNull(profile.heights.last())
        val replay = RunReplay(RunSession("run", 0, 100_000, 100_000, points.mapIndexed { i, p -> p.copy(segment = if (i < 60) 0 else 1) }))
        val trace = ObservedElevationTrace.fromReplay(replay)
        val start = SlopeProfile.at(trace, 59_000, 1)
        assertTrue(start.heights.dropLast(1).all { it == null })
    }
    @Test fun directionIsWithheldQuicklyAtAReversal() {
        val trend = TerrainTrend(); var estimate = TerrainEstimate()
        val route = (0..65).map { i -> RunPoint(i * 2.0 / 111195.0, 0.0, i * 1000L, 3f,
            if (i <= 50) 100.0 - i * .12 else 94.0 + (i - 50) * .18) }
        route.take(51).forEach { estimate = trend.update(it) }
        assertEquals(TerrainDirection.DOWNHILL, estimate.direction)
        route.drop(51).take(8).forEach { estimate = trend.update(it) }
        assertNotEquals(TerrainDirection.DOWNHILL, estimate.direction)
    }
}
