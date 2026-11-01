package com.runline.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunReplayTest {
    private fun point(longitude: Double, time: Long, altitude: Double? = null, segment: Int = 0) =
        RunPoint(0.0, longitude, time, 5f, altitude, segment)

    private fun replay(points: List<RunPoint>, activeDurationMs: Long = 60_000L) = RunReplay(
        RunSession("test", 0L, points.lastOrNull()?.timestampMs ?: 0L, activeDurationMs, points),
    )

    @Test fun interpolatesPositionStatsAndAltitudeOnAnObservedInterval() {
        val replay = replay(listOf(point(0.0, 1_000L, 100.0), point(0.0001, 11_000L, 110.0)))
        val frame = replay.at(5_000L)!!
        assertEquals(10_000L, replay.durationMs)
        assertEquals(0.00005, frame.point.longitude, 1e-10)
        assertEquals(6_000L, frame.point.timestampMs)
        assertEquals(105.0, frame.point.altitudeMeters!!, 1e-10)
        assertEquals(5.55975, frame.distanceMeters, 0.001)
        assertEquals(1.11195, frame.speedMetersPerSecond!!, 0.001)
        assertEquals(899.320, frame.paceSecondsPerKm!!, 0.01)
        assertEquals(5.0, frame.elevationGainMeters!!, 0.0)
        assertEquals(11.1195 / 60, replay.summary.averageSpeedMetersPerSecond!!, 0.001)
        assertEquals(1.11195, replay.summary.maxSpeedMetersPerSecond!!, 0.001)
    }

    @Test fun explicitPauseCutsToNewSegmentWithoutAddingRelocationOrPausedTime() {
        val replay = replay(listOf(
            point(0.0, 0L, 100.0), point(0.0001, 10_000L, 105.0),
            point(1.0, 100_000L, 200.0, 7), point(1.0001, 110_000L, 205.0, 7),
        ))
        assertEquals(20_000L, replay.durationMs)
        assertEquals(22.239, replay.summary.distanceMeters, 0.001)
        val before = replay.at(9_999L)!!
        val after = replay.at(10_000L)!!
        assertEquals(0, before.segmentIndex)
        assertEquals(1, after.segmentIndex)
        assertEquals(1, after.point.segment)
        assertEquals(1.0, after.point.longitude, 1e-10)
        assertNull(after.speedMetersPerSecond)
        assertEquals(100_000L, after.point.timestampMs)
        assertEquals(10.0, replay.summary.elevationGainMeters!!, 0.0)
        assertEquals(0.0, replay.summary.elevationLossMeters!!, 0.0)
    }

    @Test fun longGpsGapAlsoBreaksLegacyTracksWithoutAnExplicitSegmentChange() {
        val replay = replay(listOf(
            point(0.0, 0L), point(0.0001, 10_000L),
            point(1.0, 80_000L), point(1.0001, 90_000L),
        ))
        assertEquals(2, replay.segments.size)
        assertEquals(20_000L, replay.durationMs)
        assertEquals(22.239, replay.summary.distanceMeters, 0.001)
        assertEquals(1.0, replay.at(10_000L)!!.point.longitude, 1e-10)
        val flattened = replay.segments.flatten()
        assertEquals(22.239, RunMetrics.distanceMeters(flattened), 0.001)
        assertFalse(flattened[1].segment == flattened[2].segment)
    }

    @Test fun datelineInterpolationTakesTheShortPathAndDoesNotCrossTheWorld() {
        val replay = replay(listOf(point(179.99999, 0L), point(-179.99999, 10_000L)))
        val mid = replay.at(5_000L)!!
        assertEquals(180.0, kotlin.math.abs(mid.point.longitude), 1e-8)
        assertEquals(2.2239, replay.summary.distanceMeters, 0.001)
        assertTrue(mid.speedMetersPerSecond!! < 1.0)
    }

    @Test fun missingAltitudeDoesNotBecomeZeroOrConnectAcrossMissingValues() {
        val replay = replay(listOf(
            point(0.0, 0L, 100.0), point(0.0001, 5_000L),
            point(0.0002, 10_000L, 200.0), point(0.0003, 15_000L, 210.0),
        ))
        assertNull(replay.at(2_500L)!!.point.altitudeMeters)
        assertNull(replay.at(7_500L)!!.point.altitudeMeters)
        assertEquals(200.0, replay.at(10_000L)!!.point.altitudeMeters!!, 0.0)
        assertEquals(10.0, replay.summary.elevationGainMeters!!, 0.0)
        assertEquals(100.0, replay.summary.minAltitudeMeters!!, 0.0)
        assertEquals(210.0, replay.summary.maxAltitudeMeters!!, 0.0)
        val noAltitude = replay(listOf(point(0.0, 0L), point(0.0001, 5_000L)))
        assertNull(noAltitude.summary.elevationGainMeters)
        assertNull(noAltitude.summary.elevationLossMeters)
        assertNull(noAltitude.summary.minAltitudeMeters)
        assertNull(noAltitude.at(2_000L)!!.point.altitudeMeters)
    }

    @Test fun altitudeDeadbandSuppressesSmallOscillationsButKeepsSustainedClimbAndDescent() {
        val altitudes = listOf(100.0, 101.0, 100.0, 102.0, 104.0, 105.0, 103.0, 100.0)
        val replay = replay(altitudes.mapIndexed { index, altitude ->
            point(index * 0.00001, index * 1_000L, altitude)
        })
        assertEquals(4.0, replay.summary.elevationGainMeters!!, 0.0)
        assertEquals(4.0, replay.summary.elevationLossMeters!!, 0.0)
        assertEquals(100.0, replay.summary.minAltitudeMeters!!, 0.0)
        assertEquals(105.0, replay.summary.maxAltitudeMeters!!, 0.0)
    }

    @Test fun stationarySamplesHaveZeroSpeedAndNoPace() {
        val replay = replay(listOf(point(0.0, 0L), point(0.0, 10_000L)))
        assertEquals(10_000L, replay.durationMs)
        assertEquals(0.0, replay.at(5_000L)!!.speedMetersPerSecond!!, 0.0)
        assertNull(replay.at(5_000L)!!.paceSecondsPerKm)
        assertEquals(0.0, replay.summary.maxSpeedMetersPerSecond!!, 0.0)
        assertNull(replay.summary.averageSpeedMetersPerSecond)
    }

    @Test fun nonFiniteAltitudeIsUnknownAtEndpointsAsWellAsBetweenThem() {
        val replay = replay(listOf(point(0.0, 0L, Double.NaN), point(0.0001, 5_000L, Double.POSITIVE_INFINITY)))
        assertNull(replay.at(0L)!!.point.altitudeMeters)
        assertNull(replay.at(2_500L)!!.point.altitudeMeters)
        assertNull(replay.at(5_000L)!!.point.altitudeMeters)
        assertNull(replay.summary.minAltitudeMeters)
        assertNull(replay.summary.elevationGainMeters)
    }

    @Test fun emptyAndSinglePointRunsRemainSeekableWithoutInventingMovement() {
        val empty = replay(emptyList())
        assertEquals(0L, empty.durationMs)
        assertNull(empty.at(0L))
        val only = replay(listOf(point(12.0, 100L, 9.0)))
        assertEquals(0L, only.durationMs)
        assertEquals(12.0, only.at(1_000L)!!.point.longitude, 0.0)
        assertEquals(0L, only.at(1_000L)!!.positionMs)
        assertNull(only.summary.maxSpeedMetersPerSecond)
        assertNull(only.summary.elevationGainMeters)
        assertEquals(9.0, only.summary.minAltitudeMeters!!, 0.0)
    }

    @Test fun outOfOrderOrInvalidSamplesBreakThePathAndCannotRewindReplayClock() {
        val replay = replay(listOf(
            point(0.0, 0L), point(0.0001, 10_000L), point(0.0002, 5_000L),
            point(0.0003, 15_000L), point(0.0004, 20_000L),
            point(Double.NaN, 25_000L), point(0.0005, 30_000L),
        ))
        assertEquals(15_000L, replay.durationMs)
        assertEquals(3, replay.segments.size)
        assertEquals(22.239, replay.summary.distanceMeters, 0.001)
        for (clock in 0L..15_000L step 300L) {
            val frame = replay.at(clock)!!
            assertEquals(clock, frame.positionMs)
            assertTrue(frame.point.longitude.isFinite())
        }
        assertEquals(0.0005, replay.at(15_000L)!!.point.longitude, 0.0)
        assertNull(replay.at(15_000L)!!.speedMetersPerSecond)
    }

    @Test fun seekClampsToTimelineBoundsAndUsesFiveSecondTrailingSpeed() {
        val replay = replay(listOf(
            point(0.0, 0L), point(0.0001, 5_000L), point(0.0001, 10_000L),
        ))
        assertEquals(0L, replay.at(-5L)!!.positionMs)
        assertEquals(10_000L, replay.at(Long.MAX_VALUE)!!.positionMs)
        assertNull(replay.at(0L)!!.speedMetersPerSecond)
        assertEquals(2.2239, replay.at(5_000L)!!.speedMetersPerSecond!!, 0.001)
        assertEquals(1.11195, replay.at(7_500L)!!.speedMetersPerSecond!!, 0.001)
        assertEquals(0.0, replay.at(10_000L)!!.speedMetersPerSecond!!, 0.0)
    }

    @Test fun implausibleGpsSpeedIsUnknownInsteadOfDisplayedAsAPersonalBest() {
        val replay = replay(listOf(point(0.0, 0L), point(1.0, 1_000L)))
        assertNull(replay.at(500L)!!.speedMetersPerSecond)
        assertNull(replay.at(500L)!!.paceSecondsPerKm)
        assertNull(replay.summary.maxSpeedMetersPerSecond)
    }

    @Test fun peakSpeedIncludesTrailingWindowBoundariesBetweenGpsFixes() {
        val degreesPerMeter = 180.0 / (Math.PI * 6_371_008.8)
        val replay = replay(listOf(
            point(0.0, 0L), point(0.0, 2_000L),
            point(20.0 * degreesPerMeter, 4_000L), point(74.0 * degreesPerMeter, 10_000L),
        ))
        // At 7s the five-second window has just dropped its initial stationary time:
        // 20m at 10m/s followed by 27m at 9m/s. No GPS fix occurs at this peak.
        assertEquals(9.4, replay.at(7_000L)!!.speedMetersPerSecond!!, 0.000001)
        assertEquals(9.4, replay.summary.maxSpeedMetersPerSecond!!, 0.000001)
    }

    @Test fun segmentClockChangesRemainSeparateAndSeekWorkIsIndependentOfWallClock() {
        val replay = replay(listOf(
            point(0.0, 100_000L), point(0.0001, 105_000L),
            point(1.0, 1_000L, segment = 1), point(1.0001, 6_000L, segment = 1),
        ))
        assertEquals(10_000L, replay.durationMs)
        val after = replay.at(7_000L)!!
        assertEquals(7_000L, after.positionMs)
        assertEquals(3_000L, after.point.timestampMs)
        assertEquals(1, after.segmentIndex)
        assertNotNull(after.speedMetersPerSecond)
    }
}
