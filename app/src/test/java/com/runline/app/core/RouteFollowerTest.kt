package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class RouteFollowerTest {
    private val m = 1 / 111_195.0
    /** North 1 km, then a 300 m out-and-back spur east, then north 500 m more. */
    private fun route(): HikingRoute {
        val pts = ArrayList<Triple<Double, Double, Double?>>()
        for (i in 0..100) pts += Triple(37.0 + i * 10 * m, -3.6, 700.0 + i)
        val east = 1 / (111_195.0 * Math.cos(Math.toRadians(37.01)))
        for (i in 1..30) pts += Triple(37.0 + 1000 * m, -3.6 + i * 10 * east, 800.0)
        for (i in 29 downTo 0) pts += Triple(37.0 + 1000 * m, -3.6 + i * 10 * east, 800.0)
        for (i in 1..50) pts += Triple(37.0 + (1000 + i * 10) * m, -3.6, 800.0 - i)
        return HikingRoute.of("t", "Test", pts, listOf(RouteWaypoint(37.0 + 1000 * m, -3.6 + 300 * east, "Spur end")))
    }
    private class Walker(val follower: RouteFollower) {
        var t = 0L
        fun at(lat: Double, lon: Double, acc: Float = 5f) = follower.update(lat, lon, acc, t.also { t += 3_000 })
    }

    @Test fun `progress follows the path through an out-and-back spur`() {
        val r = route(); val w = Walker(RouteFollower(r))
        val east = 1 / (111_195.0 * Math.cos(Math.toRadians(37.01)))
        assertEquals(RouteState.ON_ROUTE, w.at(37.0, -3.6).state)
        var p: RouteProgress? = null
        for (i in 1..100) p = w.at(37.0 + i * 10 * m, -3.6)
        assertEquals(1000.0, p!!.doneMeters, 15.0)
        assertEquals("Spur end", p.nextWaypoint?.name); assertEquals(300.0, p.nextWaypointMeters!!, 20.0)
        for (i in 1..30) p = w.at(37.0 + 1000 * m, -3.6 + i * 10 * east)
        assertEquals(1300.0, p!!.doneMeters, 20.0)
        // Coming back over the same ground still counts forward, not back to 1000 m.
        for (i in 29 downTo 0) p = w.at(37.0 + 1000 * m, -3.6 + i * 10 * east)
        assertEquals(1600.0, p!!.doneMeters, 25.0)
        assertEquals(RouteState.ON_ROUTE, p.state)
        val last = (1..50).map { w.at(37.0 + (1000 + it * 10) * m, -3.6) }
        p = last.last()
        assertEquals(RouteState.FINISHED, p.state)
        assertEquals(1, last.count { it.changed && it.state == RouteState.FINISHED })
        assertEquals(0.0, p.remainingMeters, 30.0)
    }

    /** North 600 m at x = 0, east 600 m, south 300 m, west back to x = [gap], then south 300 m parallel to the first leg. */
    private fun parallel(gap: Double): HikingRoute {
        val east = 1 / (111_195.0 * Math.cos(Math.toRadians(37.0)))
        val pts = ArrayList<Triple<Double, Double, Double?>>()
        for (i in 0..60) pts += Triple(37.0 + i * 10 * m, -3.6, 700.0)
        for (i in 1..60) pts += Triple(37.0 + 600 * m, -3.6 + i * 10 * east, 700.0)
        for (i in 1..30) pts += Triple(37.0 + (600 - i * 10) * m, -3.6 + 600 * east, 700.0)
        val xs = generateSequence(590.0) { it - 10 }.takeWhile { it > gap }.toList() + gap
        xs.forEach { x -> pts += Triple(37.0 + 300 * m, -3.6 + x * east, 700.0) }
        for (i in 1..30) pts += Triple(37.0 + (300 - i * 10) * m, -3.6 + gap * east, 700.0)
        return HikingRoute.of("p", "Parallel", pts)
    }

    @Test fun `joining mid-route next to an earlier leg joins the leg you are on`() {
        val east = 1 / (111_195.0 * Math.cos(Math.toRadians(37.0)))
        // 45 m from the first leg: only the last leg is within reach, however far along it is.
        val far = parallel(45.0); val w = Walker(RouteFollower(far))
        val joined = w.at(37.0 + 250 * m, -3.6 + 45 * east)
        assertEquals(RouteState.ON_ROUTE, joined.state)
        assertEquals(far.distanceMeters - 250, joined.doneMeters, 15.0)
        // 25 m away, near where the two legs meet: it may join the first leg, but walking on
        // corrects it silently instead of reporting off route.
        val near = parallel(25.0); val v = Walker(RouteFollower(near))
        val states = (0..20).map { v.at(37.0 + (280 - it * 10) * m, -3.6 + 25 * east) }
        assertTrue(states.none { it.state == RouteState.OFF_ROUTE })
        assertEquals(near.distanceMeters - 80, states.last().doneMeters, 20.0)
    }

    @Test fun `leaving the path alerts once after ten seconds and rejoining clears it`() {
        val r = route(); val w = Walker(RouteFollower(r))
        val east = 1 / (111_195.0 * Math.cos(Math.toRadians(37.0)))
        for (i in 0..20) w.at(37.0 + i * 10 * m, -3.6)
        // Head west, away from the path (heading north before that).
        val states = (1..8).map { w.at(37.0 + 200 * m + it * 4 * m, -3.6 - (20 + it * 15) * east) }
        val firstOff = states.indexOfFirst { it.state == RouteState.OFF_ROUTE }
        assertTrue(firstOff >= 2)
        assertEquals(1, states.count { it.changed })
        val off = states.last()
        assertTrue(off.distanceFromRouteMeters > 80)
        assertNotNull(off.side)
        // Path is to the east, behind-right of a hiker walking west-north-west.
        assertTrue(off.side == RouteSide.RIGHT || off.side == RouteSide.BEHIND)
        val back = listOf(w.at(37.0 + 240 * m, -3.6), w.at(37.0 + 250 * m, -3.6))
        assertEquals(RouteState.ON_ROUTE, back.last().state); assertTrue(back.last().changed)
    }

    @Test fun `the side of the path is known even when each fix moves only a few metres`() {
        val r = route(); val w = Walker(RouteFollower(r))
        val east = 1 / (111_195.0 * Math.cos(Math.toRadians(37.0)))
        for (i in 0..60) w.at(37.0 + i * 3 * m, -3.6)          // 3 m per fix, heading north
        var p: RouteProgress? = null
        for (i in 1..8) p = w.at(37.0 + (180 + i * 3) * m, -3.6 + 90 * east)  // now 90 m east, still heading north
        assertEquals(RouteState.OFF_ROUTE, p!!.state)
        assertEquals(RouteSide.LEFT, p.side)
    }

    @Test fun `a hiker starting away from the route is guided to its start`() {
        val r = route(); val w = Walker(RouteFollower(r))
        val p = w.at(37.0 - 300 * m, -3.6)
        assertEquals(RouteState.APPROACHING, p.state); assertEquals(300.0, p.distanceFromRouteMeters, 5.0)
        assertEquals(r.distanceMeters, p.remainingMeters, 1.0)
        assertEquals(0.0, p.bearingToRouteDegrees!!, 2.0)
    }

    @Test fun `estimate adapts to the hiker's own pace after the first kilometre`() {
        val r = route()
        val slow = RouteFollower(r); val normal = RouteFollower(r)
        var a: RouteProgress? = null; var b: RouteProgress? = null
        for (i in 0..105) {
            val lat = 37.0 + minOf(i, 100) * 10 * m
            a = slow.update(lat, -3.6, 5f, i * 3000L, movingMinutes = i * 0.4)   // 1.5 km/h: much slower than plan
            b = normal.update(lat, -3.6, 5f, i * 3000L)
        }
        assertTrue(a!!.minutesRemaining > b!!.minutesRemaining * 1.5)
        assertEquals(RouteSide.LEFT, RouteFollower.sideOf(270.0, 0.0)); assertEquals(RouteSide.BEHIND, RouteFollower.sideOf(180.0, 0.0))
    }
}
