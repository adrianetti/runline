package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RouteGuidanceTest {
    private val m = 1 / 110_574.0

    /** Due north, one vertex every 10 m, with heights from [height] (metres along → metres). */
    private fun north(length: Int, height: (Double) -> Double): HikingRoute =
        HikingRoute.of("n", "North", (0..length / 10).map { Triple(37.0 + it * 10 * m, -3.6, height(it * 10.0)) })

    private fun progress(done: Double, state: RouteState = RouteState.ON_ROUTE) =
        RouteProgress(state, done, 5000 - done, 3.0, null, 60.0, null, null, null, null, false)

    private fun turn(at: Double, kind: TurnKind = TurnKind.LEFT) = RouteManeuver(at, kind, if (kind == TurnKind.LEFT) -90.0 else 90.0)

    @Test fun `climbs and descents need 30 m at 1,5 percent and small bumps do not split them`() {
        val route = north(6000) { d ->
            when {
                d < 1000 -> 700.0
                d < 2200 -> 700 + (d - 1000) / 1200 * 110 + if (d.toInt() % 200 == 0) 4.0 else 0.0   // 110 m over 1.2 km, with bumps
                d < 3500 -> 810.0
                d < 4500 -> 810 - (d - 3500) / 1000 * 60      // 60 m down over 1 km
                d < 5000 -> 750 + (d - 4500) / 500 * 20        // only 20 m: not a climb
                else -> 770.0
            }
        }
        val climbs = RouteClimbs.detect(route)
        assertEquals(2, climbs.size)
        val up = climbs[0]; val down = climbs[1]
        assertTrue(up.uphill); assertEquals(110.0, up.heightMeters, 6.0)
        assertEquals(1000.0, up.startMeters, 60.0); assertEquals(2200.0, up.endMeters, 60.0)
        assertEquals(9.0, up.gradePercent, 1.0)
        assertFalse(down.uphill); assertEquals(-60.0, down.heightMeters, 3.0); assertEquals(6.0, down.gradePercent, 0.6)
        // 30 m over 3 km is 1%: a long incline, not a climb.
        assertTrue(RouteClimbs.detect(north(4000) { 700 + it / 4000 * 40 }).isEmpty())
        assertEquals(up, RouteClimbs.next(climbs, 0.0)); assertNull(RouteClimbs.next(climbs, 0.0, lookAhead = 500.0))
        assertEquals(down, RouteClimbs.next(climbs, 2300.0))
    }

    @Test fun `each turn is spoken at about 80 m and again at 20 m, once`() {
        val g = RouteGuidance(listOf(turn(500.0), turn(900.0, TurnKind.RIGHT)), emptyList(), emptyList())
        val cues = (0..1000 step 2).mapNotNull { d -> g.update(progress(d.toDouble()))?.let { d to it } }
        val turns = cues.map { it.second as RouteCue.Turn }
        assertEquals(4, turns.size)
        assertEquals(listOf(false, true, false, true), turns.map { it.now })
        assertEquals(80, turns[0].meters); assertEquals(416.0, cues[0].first.toDouble(), 2.0)
        assertTrue(cues[1].first in 478..480)
        assertEquals(TurnKind.RIGHT, turns[3].maneuver.kind)
    }

    @Test fun `a turn right after another is named with it, and joining close to a turn skips the early cue`() {
        val g = RouteGuidance(listOf(turn(500.0), turn(540.0, TurnKind.RIGHT)), emptyList(), emptyList())
        val cues = (470..600 step 2).mapNotNull { g.update(progress(it.toDouble())) as RouteCue.Turn? }
        assertEquals(2, cues.size)
        assertTrue(cues[0].now); assertEquals(TurnKind.RIGHT, cues[0].then?.kind)
        // The second turn still gets its own "now" cue, but no early one.
        assertTrue(cues[1].now); assertEquals(540.0, cues[1].maneuver.alongMeters, 0.0)
    }

    @Test fun `nothing behind you is spoken, nothing is spoken off route, and stale cues are dropped`() {
        val g = RouteGuidance(listOf(turn(300.0), turn(1500.0)), emptyList(), emptyList())
        assertNull(g.update(progress(100.0)))
        assertNull(g.update(progress(250.0, RouteState.OFF_ROUTE)))
        // Rejoining further on: the first turn is behind and stays silent.
        assertNull(g.update(progress(1000.0)))
        val far = g.update(progress(1420.0)) as RouteCue.Turn
        assertEquals(1500.0, far.maneuver.alongMeters, 0.0)
        assertTrue(g.stillAhead(far))
        g.update(progress(1485.0))
        assertFalse(g.stillAhead(far))
        val near = RouteCue.Turn(far.maneuver, 15, true, null)
        assertTrue(g.stillAhead(near))
        g.update(progress(1510.0)); assertFalse(g.stillAhead(near))
        g.update(progress(1520.0, RouteState.OFF_ROUTE)); assertFalse(g.stillAhead(near))
    }

    @Test fun `climbs are announced once about 200 m ahead and places once as you reach them`() {
        val climb = RouteClimb(1000.0, 2200.0, 110.0)
        val place = RoutePlace("Mirador", "A viewpoint.", 1600.0)
        val g = RouteGuidance(emptyList(), listOf(climb), listOf(place))
        val cues = (0..2400 step 5).mapNotNull { d -> g.update(progress(d.toDouble()))?.let { d to it } }
        assertEquals(2, cues.size)
        val (climbAt, climbCue) = cues[0]
        assertEquals(780, climbAt); assertEquals(200, (climbCue as RouteCue.Climb).meters)
        val (placeAt, placeCue) = cues[1]
        assertEquals(1540, placeAt); assertEquals("Mirador", (placeCue as RouteCue.Place).place.name)
        // Joining inside the climb or past the place says nothing about them.
        val late = RouteGuidance(emptyList(), listOf(climb), listOf(place))
        assertNull(late.update(progress(1800.0)))
    }

    @Test fun `a place note at a turn waits until the turn is called`() {
        val g = RouteGuidance(listOf(turn(1000.0)), emptyList(), listOf(RoutePlace("River", "A long note about the river.", 995.0)))
        val cues = (900..1200 step 2).mapNotNull { d -> g.update(progress(d.toDouble()))?.let { d to it } }
        assertEquals(listOf("turn", "turn", "place"), cues.map { (_, c) -> if (c is RouteCue.Place) "place" else "turn" })
        assertTrue((cues[1].second as RouteCue.Turn).now)
        assertTrue(cues[2].first > cues[1].first)
        // Passing the place far behind without a chance to speak settles it for good.
        val skipped = RouteGuidance(emptyList(), emptyList(), listOf(RoutePlace("Gone", "Behind.", 500.0)))
        assertNull(skipped.update(progress(700.0)))
    }

    @Test fun `the island shows the next manoeuvre and the climb ahead or underway`() {
        val climb = RouteClimb(1000.0, 2200.0, 110.0)
        val g = RouteGuidance(listOf(turn(500.0)), listOf(climb), emptyList())
        val v = g.view(420.0)
        assertEquals(80.0, v.maneuverMeters!!, 0.01); assertEquals(climb, v.climb); assertEquals(580.0, v.climbMeters!!, 0.01)
        val inside = g.view(1500.0)
        assertNull(inside.maneuver); assertTrue(inside.climbMeters!! < 0)
    }

    @Test fun `the Granada notes land on their waypoints along the route`() {
        val repo = File("../routes").takeIf { it.isDirectory } ?: File("routes")
        val entry = RouteCatalog.parse(File(repo, "index.json").readText()).routes.single { it.id == "es-granada-alhambra-dehesa-genil" }
        val gpx = GpxRouteReader.read(File(repo, entry.file).readBytes())
        val route = HikingRoute.of(entry.id, entry.name("en"), gpx.points, gpx.waypoints)
        val places = RouteGuidance.places(route, entry.notes("es"))
        assertEquals(listOf("Puerta de las Granadas", "Sendero de la Acequia Real", "Barranco del Olivar", "Mirador de las cárcavas", "Río Genil, Centro Hípico"),
            places.map { it.name })
        assertTrue(places.zipWithNext().all { (a, b) -> b.alongMeters > a.alongMeters })
        assertTrue(places.all { it.note.length in 40..260 })
        assertTrue(entry.notes("en").getValue("Puerta de las Granadas").contains("pomegranates"))
        val climbs = RouteClimbs.detect(route)
        println("Granada climbs: " + climbs.joinToString { "%.0f–%.0f m %+.0f m %.0f%%".format(it.startMeters, it.endMeters, it.heightMeters, it.gradePercent) })
        assertTrue(climbs.any { it.uphill && it.heightMeters >= 100 })
        assertTrue(climbs.any { !it.uphill && it.heightMeters <= -50 })
        // Walking the route speaks every note once, in order.
        val g = RouteGuidance(emptyList(), emptyList(), places)
        val spoken = (0..route.distanceMeters.toInt() step 5).mapNotNull { (g.update(progress(it.toDouble())) as? RouteCue.Place)?.place?.name }
        assertEquals(places.map { it.name }, spoken)
    }
}
