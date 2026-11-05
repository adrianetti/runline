package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class TerrainConfidenceTest {
    private fun point(index: Int, height: Double, vertical: Float? = null) =
        RunPoint(index * 3.0 / 111195.0802, 0.0, index * 1_000L, 3f, height, verticalAccuracyMeters = vertical)
    private fun track(grade: Double, count: Int = 160, vertical: Float? = null) =
        (0 until count).map { point(it, 100 + it * 3 * grade / 100, vertical) }
    private fun estimates(points: List<RunPoint>): List<TerrainEstimate> {
        val trend = TerrainTrend(); return points.map(trend::update)
    }
    @Test fun verticalUncertaintyCannotBeReplacedByGoodHorizontalAccuracy() {
        val points = track(-15.0, vertical = 25f)
        assertTrue(estimates(points).all { it.direction == TerrainDirection.UNKNOWN })
        assertTrue(ObservedElevationTrace.fromPoints(points).sections.isEmpty())
        assertEquals(TerrainDirection.DOWNHILL, estimates(track(-15.0, vertical = 2f)).last().direction)
        // Legacy data remains readable and approximate; no invented accuracy.
        assertEquals(TerrainDirection.UPHILL, estimates(track(5.0)).last().direction)
    }
    @Test fun heightRelocationDoesNotBecomeASteepDescent() {
        val source = track(4.0)
        val shifted = source.mapIndexed { i, p -> if (i >= 80) p.copy(altitudeMeters = p.altitudeMeters!! - 12) else p }
        val values = estimates(shifted)
        assertEquals(TerrainDirection.UNKNOWN, values[80].direction)
        assertTrue(values.drop(80).none { it.direction == TerrainDirection.DOWNHILL })
        assertEquals(TerrainDirection.UPHILL, values.last().direction)
        val trace = ObservedElevationTrace.fromPoints(shifted)
        assertEquals(2, trace.sections.size)
        assertEquals(159_000L, trace.durationMs) // Quality never deletes elapsed observed time.
        assertNull(trace.at(79_500, 0))
    }
    @Test fun isolatedSpikesDoNotInventHillsOnFlatOrInvertAClimb() {
        for (grade in listOf(0.0, 4.0)) {
            val points = track(grade).mapIndexed { i, p -> if (i == 80) p.copy(altitudeMeters = p.altitudeMeters!! - 3.5) else p }
            val values = estimates(points).drop(40)
            assertTrue(values.none { it.direction == TerrainDirection.DOWNHILL })
            assertEquals(if (grade == 0.0) TerrainDirection.FLAT else TerrainDirection.UPHILL, values.last().direction)
        }
    }
    @Test fun supportedSteepTerrainIsNotClampedToAnArbitraryParkGrade() {
        val value = estimates(track(-15.0)).last()
        assertEquals(-15.0, value.gradePercent!!, .01)
    }
    @Test fun sustainedGradeVariationChangesGraduallyAndSeeksStayDeterministic() {
        val points = (0..180).map { i ->
            val meters = i * 3.0
            point(i, 100 + .03 * meters + .000025 * meters * meters)
        }
        val values = estimates(points).mapNotNull { it.gradePercent }
        assertTrue(values.size > 100)
        assertTrue(values.zipWithNext().all { (a, b) -> abs(b - a) < .25 })
        val replay = RunReplay(RunSession("synthetic-terrain", 0, 180_000, 180_000, points))
        val terrain = ReplayTerrain(replay)
        val middle = terrain.at(90_000, 0)
        terrain.at(160_000, 0)
        assertEquals(middle, terrain.at(90_000, 0))
    }
    private fun abs(value: Double) = kotlin.math.abs(value)
}
