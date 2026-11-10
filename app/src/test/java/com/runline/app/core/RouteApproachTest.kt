package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class RouteApproachTest {
    private fun at(meters: Double, side: RouteSide? = RouteSide.AHEAD, bearing: Double? = 0.0, state: RouteState = RouteState.APPROACHING) =
        RouteProgress(state, 0.0, 5_000.0, meters, null, 60.0, side, bearing, null, null, false)

    @Test fun `says where the start is when following begins, then as it gets closer`() {
        val a = RouteApproach(); var t = 0L
        fun step(m: Double, side: RouteSide? = RouteSide.AHEAD) = a.update(at(m, side), t.also { t += 5_000 })
        assertEquals(ApproachCue(1_200, RouteSide.AHEAD, null), step(1_234.0))
        assertNull(step(1_100.0))
        assertEquals(950, step(960.0)?.meters)
        assertNull(step(900.0)); assertNull(step(990.0)) // drifting back out does not repeat
        assertEquals(450, step(470.0)?.meters)
        assertEquals(200, step(199.0)?.meters)
        assertEquals(90, step(91.0)?.meters)
        assertEquals(40, step(44.0)?.meters)
        assertNull(step(25.0)) // joining: "On the route" follows
    }

    @Test fun `compass point when there is no direction of travel yet`() {
        val cue = RouteApproach().update(at(600.0, side = null, bearing = 47.0), 0)!!
        assertNull(cue.side); assertEquals(Compass.NORTH_EAST, cue.compass)
    }

    @Test fun `turning away from the start is said once it lasts, and a reminder comes every few minutes`() {
        val a = RouteApproach()
        assertNotNull(a.update(at(700.0), 0))
        assertNull(a.update(at(700.0, RouteSide.BEHIND), 10_000))
        val behind = a.update(at(720.0, RouteSide.BEHIND), 50_000)
        assertEquals(RouteSide.BEHIND, behind?.side)
        assertNull(a.update(at(740.0, RouteSide.BEHIND), 90_000))
        assertNotNull(a.update(at(760.0, RouteSide.BEHIND), 50_000 + 180_000))
    }

    @Test fun `stops on the route and starts over if the start is needed again`() {
        val a = RouteApproach()
        assertNotNull(a.update(at(300.0), 0))
        assertNull(a.update(at(0.0, state = RouteState.ON_ROUTE), 1_000))
        assertNotNull(a.update(at(300.0), 2_000))
    }

    @Test fun `distances and compass points round sensibly`() {
        assertEquals(40, RouteApproach.round(44.0)); assertEquals(450, RouteApproach.round(470.0)); assertEquals(1_200, RouteApproach.round(1_234.0))
        assertEquals(Compass.NORTH, Compass.of(350.0)); assertEquals(Compass.EAST, Compass.of(90.0))
        assertEquals(Compass.SOUTH, Compass.of(180.0)); assertEquals(Compass.WEST, Compass.of(-90.0))
    }
}
