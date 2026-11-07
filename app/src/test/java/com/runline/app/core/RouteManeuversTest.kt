package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.*

class RouteManeuversTest {
    private val lat0 = 37.0
    private val lon0 = -3.6
    private val m = 1 / 110_574.0
    private val e = 1 / (111_320.0 * cos(Math.toRadians(lat0)))

    /** Builds a route from legs of (bearing degrees, metres), one vertex every 5 m. */
    private fun legs(vararg legs: Pair<Double, Double>, wiggle: Double = 0.0): HikingRoute {
        var x = 0.0; var y = 0.0
        val pts = arrayListOf(Triple(lat0, lon0, 700.0 as Double?))
        var n = 0
        for ((bearing, length) in legs) {
            val steps = (length / 5).roundToInt()
            repeat(steps) {
                x += 5 * sin(Math.toRadians(bearing)); y += 5 * cos(Math.toRadians(bearing))
                val jitter = if (wiggle > 0) wiggle * (if (n++ % 2 == 0) 1 else -1) else 0.0
                val jx = jitter * cos(Math.toRadians(bearing)); val jy = -jitter * sin(Math.toRadians(bearing))
                pts += Triple(lat0 + (y + jy) * m, lon0 + (x + jx) * e, 700.0)
            }
        }
        return HikingRoute.of("t", "Test", pts)
    }
    private fun at(route: HikingRoute, along: Double): Pair<Double, Double> =
        route.points.minByOrNull { abs(it.distanceMeters - along) }!!.let { it.latitude to it.longitude }

    @Test fun `straight paths, gentle curves and GPS wiggle are not manoeuvres`() {
        assertTrue(RouteManeuvers.detect(legs(0.0 to 1000.0)).isEmpty())
        // 90° spread over 300 m of curve: a bend you follow, not a turn.
        val curve = legs(0.0 to 200.0, *(1..30).map { (it * 3.0) to 10.0 }.toTypedArray(), 90.0 to 200.0)
        assertTrue(RouteManeuvers.detect(curve).isEmpty())
        assertTrue(RouteManeuvers.detect(legs(0.0 to 1000.0, wiggle = 2.5)).isEmpty())
    }

    @Test fun `turn kinds follow the angle and sit at the corner`() {
        fun one(angle: Double) = RouteManeuvers.detect(legs(0.0 to 300.0, angle to 300.0)).single()
        assertEquals(TurnKind.LEFT, one(-90.0).kind); assertEquals(TurnKind.RIGHT, one(90.0).kind)
        assertEquals(TurnKind.BEAR_RIGHT, one(45.0).kind); assertEquals(TurnKind.BEAR_LEFT, one(-45.0).kind)
        assertEquals(TurnKind.SHARP_RIGHT, one(135.0).kind); assertEquals(TurnKind.SHARP_LEFT, one(-140.0).kind)
        assertEquals(300.0, one(90.0).alongMeters, 10.0)
        assertEquals(90.0, one(90.0).angleDegrees, 8.0)
    }

    @Test fun `close turns merge and opposite jogs cancel`() {
        val doubled = RouteManeuvers.detect(legs(0.0 to 300.0, 45.0 to 25.0, 90.0 to 300.0)).single()
        assertEquals(TurnKind.RIGHT, doubled.kind); assertEquals(90.0, doubled.angleDegrees, 12.0)
        // A short step sideways around an obstacle keeps the same heading.
        assertTrue(RouteManeuvers.detect(legs(0.0 to 300.0, 45.0 to 20.0, 0.0 to 300.0)).isEmpty())
    }

    @Test fun `an out-and-back end is a turn around, a hairpin bend is not`() {
        val spur = legs(0.0 to 400.0, 180.0 to 400.0)
        val reversal = RouteManeuvers.detect(spur).single()
        assertEquals(TurnKind.TURN_AROUND, reversal.kind); assertEquals(400.0, reversal.alongMeters, 15.0)
        // Into a ravine and back out on the other side, 20 m away: a sharp bend, not a reversal.
        val hairpin = RouteManeuvers.detect(legs(0.0 to 300.0, 90.0 to 20.0, 180.0 to 300.0))
        assertTrue(hairpin.isNotEmpty())
        assertTrue(hairpin.none { it.kind == TurnKind.TURN_AROUND })
        assertTrue(hairpin.all { it.kind == TurnKind.SHARP_RIGHT || it.kind == TurnKind.RIGHT })
    }

    @Test fun `a run of switchbacks is one zig-zag cue`() {
        // Six 60 m legs folding back on each other at 160°, like a path up a steep slope.
        val zig = legs(0.0 to 300.0, *(0 until 6).map { (if (it % 2 == 0) 100.0 else 260.0) to 60.0 }.toTypedArray(), 0.0 to 300.0)
        val cues = RouteManeuvers.detect(zig)
        val zz = cues.single { it.kind == TurnKind.ZIGZAG }
        assertEquals(240.0, zz.lengthMeters, 10.0)
        assertEquals(3, cues.size)
        // Right-angle zig-zags are ordinary turns, each worth its own cue.
        val square = legs(0.0 to 300.0, *(0 until 6).map { (if (it % 2 == 0) 90.0 else 0.0) to 60.0 }.toTypedArray())
        assertTrue(RouteManeuvers.detect(square).none { it.kind == TurnKind.ZIGZAG })
    }

    @Test fun `with junctions, bends of a single path are dropped and reversals stay`() {
        val route = legs(0.0 to 300.0, 90.0 to 300.0, 180.0 to 300.0, 0.0 to 300.0)
        val all = RouteManeuvers.detect(route)
        assertEquals(3, all.size)
        val corner = at(route, 300.0)
        val kept = RouteManeuvers.detect(route, listOf(corner))
        assertEquals(listOf(TurnKind.RIGHT, TurnKind.TURN_AROUND), kept.map { it.kind })
        assertTrue(RouteManeuvers.detect(route, emptyList()).all { it.kind == TurnKind.TURN_AROUND })
    }

    @Test fun `no manoeuvre within 15 m of either end`() {
        val cues = RouteManeuvers.detect(legs(90.0 to 10.0, 0.0 to 400.0, 90.0 to 10.0))
        assertTrue(cues.all { it.alongMeters in 15.0..405.0 })
    }

    @Test fun `the Granada route turns at junctions, turns around once at the detour and keeps its hairpins`() {
        val repo = File("../routes").takeIf { it.isDirectory } ?: File("routes")
        val entry = RouteCatalog.parse(File(repo, "index.json").readText()).routes.single { it.id == "es-granada-alhambra-dehesa-genil" }
        val gpx = GpxRouteReader.read(File(repo, entry.file).readBytes())
        val route = HikingRoute.of(entry.id, entry.name("en"), gpx.points, gpx.waypoints)
        val junctions = entry.junctions!!
        assertTrue(junctions.size > 50)
        val cues = RouteManeuvers.detect(route, junctions)
        val geometry = RouteManeuvers.detect(route)
        println("Granada: ${cues.size} cues at junctions, ${geometry.size} from geometry alone")
        cues.forEach { println("  %6.0f m  %-12s %4.0f°".format(it.alongMeters, it.kind, it.angleDegrees)) }
        // Geometry alone would announce every ravine bend of the Acequia Real path.
        assertTrue(geometry.size > cues.size + 20)
        assertTrue(cues.size in 20..70)
        // The single out-and-back reverses at its named end.
        val end = route.waypoints.single { it.name == "Fin del desvío" }
        val endAlong = route.points.minByOrNull { RouteGeometry.meters(it.latitude, it.longitude, end.latitude, end.longitude) }!!.distanceMeters
        val around = cues.filter { it.kind == TurnKind.TURN_AROUND }
        assertEquals(1, around.size); assertEquals(endAlong, around.single().alongMeters, 25.0)
        assertEquals(1, geometry.count { it.kind == TurnKind.TURN_AROUND })
        // The acequia's hairpins into each ravine are bends, never reversals.
        assertTrue(geometry.count { it.kind == TurnKind.SHARP_LEFT || it.kind == TurnKind.SHARP_RIGHT } >= 3)
        // Cues are in order and none is closer than a few metres to the next.
        assertTrue(cues.zipWithNext().all { (a, b) -> b.alongMeters - a.alongMeters >= 20 })
        assertTrue(cues.all { it.alongMeters in 15.0..(route.distanceMeters - 15) })
    }
}
