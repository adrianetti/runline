package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class MarkerMotionTest {
    private val start = RunPoint(37.0, -3.0, 1_000L, 3f, 600.0, 1)
    private val end = start.copy(latitude = 37.00002, longitude = -2.99998, timestampMs = 2_000L, altitudeMeters = 604.0)

    @Test fun movesThroughObservedPositionsWithoutChangingOriginalFixes() {
        assertTrue(MarkerMotion.canInterpolate(start, end))
        val middle = MarkerMotion.interpolate(start, end, 0.5)
        assertEquals(37.00001, middle.latitude, 0.0000001)
        assertEquals(-2.99999, middle.longitude, 0.0000001)
        assertEquals(602.0, middle.altitudeMeters!!, 0.001)
        assertEquals(1_500L, middle.timestampMs)
        assertSame(start, MarkerMotion.interpolate(start, end, 0.0))
        assertSame(end, MarkerMotion.interpolate(start, end, 1.0))
        assertEquals(37.0, start.latitude, 0.0)
    }

    @Test fun pausesMissingSamplesClockRegressionsAndTeleportsSnapInstead() {
        for (point in listOf(end.copy(segment = 2), end.copy(timestampMs = 16_001L),
            end.copy(timestampMs = 1_000L), end.copy(timestampMs = 0L), end.copy(longitude = 30.0))) {
            assertFalse(MarkerMotion.canInterpolate(start, point))
        }
        assertFalse(MarkerMotion.canInterpolate(null, end))
        assertFalse(MarkerMotion.canInterpolate(start, null))
    }

    @Test fun malformedAccuracyCoordinatesAndTimesNeverAnimate() {
        for (point in listOf(end.copy(latitude = Double.NaN), end.copy(longitude = 181.0),
            end.copy(latitude = 89.0), end.copy(accuracyMeters = Float.NaN),
            end.copy(accuracyMeters = 31f), end.copy(timestampMs = -1))) {
            assertFalse(MarkerMotion.canInterpolate(start, point))
            assertFalse(MarkerMotion.canInterpolate(point, end))
        }
    }

    @Test fun dateLineTakesShortPath() {
        val a = start.copy(latitude = 0.0, longitude = 179.99998)
        val b = a.copy(longitude = -179.99998, timestampMs = 2_000L)
        assertTrue(MarkerMotion.canInterpolate(a, b))
        assertEquals(180.0, kotlin.math.abs(MarkerMotion.interpolate(a, b, 0.5).longitude), 0.000001)
    }

    @Test fun bearingWrapsThroughNorthAndNeverInventsAnUnknownCourse() {
        assertEquals(0.0, MarkerMotion.interpolateBearing(350.0, 10.0, 0.5)!!, 0.001)
        assertEquals(0.0, MarkerMotion.interpolateBearing(10.0, 350.0, 0.5)!!, 0.001)
        assertEquals(90.0, MarkerMotion.interpolateBearing(null, 90.0, 0.25)!!, 0.001)
        assertNull(MarkerMotion.interpolateBearing(90.0, null, 0.5))
        assertNull(MarkerMotion.interpolateBearing(90.0, Double.NaN, 0.5))
    }

    @Test fun animationDurationFollowsGpsCadenceWithBoundedCatchUp() {
        assertEquals(1_000L, MarkerMotion.durationMs(start, end))
        assertEquals(300L, MarkerMotion.durationMs(start, end.copy(timestampMs = 1_050L)))
        assertEquals(1_500L, MarkerMotion.durationMs(start, end.copy(timestampMs = 10_000L)))
    }

    @Test fun missingAltitudeDoesNotBecomeFabricatedElevation() {
        assertNull(MarkerMotion.interpolate(start, end.copy(altitudeMeters = null), 0.5).altitudeMeters)
    }
}
