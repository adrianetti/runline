package com.runline.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.log2

class RouteFitTest {
    private fun point(
        latitude: Double,
        longitude: Double,
    ) = RunPoint(latitude, longitude, 0L, 5f)

    @Test fun anEmptyOrInvalidRouteHasNoFrame() {
        assertNull(RouteFit.frame(emptyList(), 300.0, 300.0))
        assertNull(RouteFit.frame(listOf(point(Double.NaN, 0.0), point(89.0, 0.0)), 300.0, 300.0))
    }

    @Test fun aWideRouteIsLimitedByTheUsableWidth() {
        val frame = RouteFit.frame(listOf(point(0.0, -0.1), point(0.0, 0.1)), 512.0, 4_000.0)!!
        assertEquals(0.0, frame.latitude, 1e-9)
        assertEquals(0.0, frame.longitude, 1e-9)
        assertEquals(log2(360.0 / 0.2), frame.zoom, 1e-9)
    }

    @Test fun aTallRouteIsLimitedByTheUsableHeight() {
        val wide = RouteFit.frame(listOf(point(-0.1, 0.0), point(0.1, 0.0)), 4_000.0, 512.0)!!
        val narrow = RouteFit.frame(listOf(point(-0.1, 0.0), point(0.1, 0.0)), 4_000.0, 256.0)!!
        assertEquals(0.0, wide.latitude, 1e-6)
        assertEquals(1.0, wide.zoom - narrow.zoom, 1e-9)
    }

    @Test fun zoomStaysInsideTheMapLimits() {
        assertEquals(RouteFit.MAX_ZOOM, RouteFit.frame(listOf(point(37.0, -3.6)), 300.0, 300.0)!!.zoom, 0.0)
        assertEquals(RouteFit.MIN_ZOOM, RouteFit.frame(listOf(point(-80.0, -170.0), point(80.0, 170.0)), 300.0, 300.0)!!.zoom, 0.0)
    }

    @Test fun aRouteAcrossTheDateLineIsCentredOnIt() {
        val frame = RouteFit.frame(listOf(point(0.0, 179.9), point(0.0, -179.9)), 512.0, 4_000.0)!!
        assertEquals(180.0, kotlin.math.abs(frame.longitude), 1e-6)
        assertEquals(log2(360.0 / 0.2), frame.zoom, 1e-6)
    }

    @Test fun usableSpaceKeepsFortyEightDpClearOnEachSide() {
        assertEquals(304.0, RouteFit.usable(400.0, 0.0, 0.0), 0.0)
        assertEquals(315.0, RouteFit.usable(411.0, 16.0, 0.0), 0.0)
        assertEquals(174.0, RouteFit.usable(795.0, 589.0, 525.0), 0.0)
        assertEquals(206.0, RouteFit.usable(795.0, 589.0, 400.0), 0.0)
        assertEquals(80.0, RouteFit.usable(100.0, 0.0, 0.0), 0.0)
    }

    @Test fun theEquatorIsTheMiddleOfTheMercatorSquare() {
        assertEquals(0.5, RouteFit.mercator(0.0), 1e-12)
    }
}
