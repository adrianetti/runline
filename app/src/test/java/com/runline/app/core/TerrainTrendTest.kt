package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class TerrainTrendTest {
    private fun point(meters: Double, seconds: Long, height: Double? = 100.0, segment: Int = 0) =
        RunPoint(meters / 111_195.0802, 0.0, seconds * 1_000, 3f, height, segment)
    private fun slope(grade: Double, count: Int = 31): List<RunPoint> =
        (0 until count).map { point(it * 2.0, it.toLong(), 100.0 + it * 2.0 * grade / 100) }
    private fun estimate(points: List<RunPoint>): TerrainEstimate {
        val trend = TerrainTrend(); return points.map(trend::update).last()
    }

    @Test fun identifiesMeasuredUpDownAndFlatWithBoundedDistance() {
        for ((grade, direction) in listOf(8.0 to TerrainDirection.UPHILL, -7.0 to TerrainDirection.DOWNHILL, 0.5 to TerrainDirection.FLAT)) {
            val value = estimate(slope(grade, 120))
            assertEquals(direction, value.direction)
            assertEquals(grade, value.gradePercent!!, .01)
            assertTrue(value.windowMeters in 35.0..55.0)
        }
    }

    @Test fun missingPoorAndShortWindowsAreUnknownNeverFlat() {
        assertEquals(TerrainDirection.UNKNOWN, estimate(slope(0.0, 10)).direction)
        for (bad in listOf(slope(8.0).last().copy(altitudeMeters = null),
            slope(8.0).last().copy(accuracyMeters = 21f), slope(8.0).last().copy(altitudeMeters = Double.NaN))) {
            val value = estimate(slope(8.0).dropLast(1) + bad)
            assertEquals(TerrainDirection.UNKNOWN, value.direction); assertNull(value.gradePercent)
        }
    }

    @Test fun stopsAndNoisyHeightsDoNotInheritAClimbOrInventGrade() {
        val climb = slope(8.0)
        val stopped = (31L..45L).map { point(60.0, it, 104.8) }
        assertEquals(TerrainDirection.UNKNOWN, estimate(climb + stopped).direction)
        val noisy = slope(0.0).mapIndexed { index, p -> p.copy(altitudeMeters = 100.0 + if (index % 2 == 0) 9 else -9) }
        assertNull(estimate(noisy).gradePercent)
        val stationary = (0L..90L).map { point(if (it % 2L == 0L) 0.0 else 1.0, it, 100.0) }
        assertEquals(TerrainDirection.UNKNOWN, estimate(stationary).direction)
    }

    @Test fun resetsOnGapSegmentClockRegressionAndImplausibleJump() {
        val climb = slope(8.0)
        for (next in listOf(point(62.0, 50, 105.0), point(62.0, 31, 105.0, 1),
            point(62.0, 20, 105.0), point(160.0, 31, 110.0))) {
            assertEquals(TerrainDirection.UNKNOWN, estimate(climb + next).direction)
        }
    }

    @Test fun hysteresisKeepsDirectionNearEntryThresholdButEventuallyReturnsFlat() {
        val trend = TerrainTrend()
        slope(4.0).forEach(trend::update)
        var value = TerrainEstimate()
        // Once the old slope leaves the window, 2% is below entry but above exit.
        for (i in 31..75) value = trend.update(point(i * 2.0, i.toLong(), 102.4 + (i * 2.0 - 60.0) * .02))
        assertEquals(2.0, value.gradePercent!!, .01)
        assertEquals(TerrainDirection.UPHILL, value.direction)
        for (i in 76..120) value = trend.update(point(i * 2.0, i.toLong(), 104.2))
        assertEquals(TerrainDirection.FLAT, value.direction)
    }

    @Test fun replaySeeksAreCausalDeterministicAndResetAcrossCuts() {
        val first = slope(8.0)
        val second = slope(-8.0).map { it.copy(timestampMs = it.timestampMs + 100_000, segment = 1) }
        val replay = RunReplay(RunSession("terrain", 0, 130_000, 60_000, first + second))
        val terrain = ReplayTerrain(replay)
        assertEquals(TerrainDirection.UPHILL, terrain.at(29_000, 0).direction)
        assertEquals(TerrainDirection.UNKNOWN, terrain.at(30_000, 1).direction)
        assertEquals(TerrainDirection.DOWNHILL, terrain.at(59_000, 1).direction)
        assertEquals(TerrainDirection.UPHILL, terrain.at(29_000, 0).direction)
    }
}
