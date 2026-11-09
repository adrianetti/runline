package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class RouteImportTest {
    private fun route(points: List<Pair<Double, Double>>) = HikingRoute.of("fixture", "Fixture", points.map { Triple(it.first, it.second, null) })
    private val outbound = (0..50).map { 37.0 + it * .0002 to -3.6 }
    private val outBack get() = route(outbound + outbound.reversed())
    private val loop get() = route(listOf(37.0 to -3.6, 37.01 to -3.6, 37.01 to -3.59, 37.0 to -3.59, 37.0 to -3.6))
    private val points = "<trk><trkseg><trkpt lat='37' lon='-3.6'/><trkpt lat='37.01' lon='-3.6'/></trkseg></trk>"

    @Test fun `names prefer GPX then readable filename and sanitize controls`() {
        assertEquals("Granada & Genil", RouteImportNaming.suppliedName(" Granada & Genil ", "download.gpx"))
        assertEquals("Pinos Puente", RouteImportNaming.suppliedName(null, "Pinos_Puente.GPX"))
        assertEquals("my.route", RouteImportNaming.suppliedName(null, "my.route.gpx"))
        assertEquals("Madrid Prado", RouteImportNaming.clean("Madrid\n\u202ePrado"))
        assertEquals(120, RouteImportNaming.clean("x".repeat(200)).length)
        assertEquals("", RouteImportNaming.clean(" \u200b \n"))
    }

    @Test fun `generic and opaque filenames fall back to route geometry`() {
        listOf(null, "", "GPX", "route.gpx", "track_20261108.gpx", "download (1).gpx", "2026-11-08.gpx",
            "12345", "aee902da-65a6-4baa-8a2c-65a61ba4a288.gpx").forEach {
            assertNull(it, RouteImportNaming.suppliedName(null, it))
        }
    }

    @Test fun `shape distinguishes loops from reversed paths without depending on point spacing`() {
        assertEquals(RouteImportNaming.Shape.ROUTE, RouteImportNaming.shape(route(outbound)))
        assertEquals(RouteImportNaming.Shape.LOOP, RouteImportNaming.shape(loop))
        assertEquals(RouteImportNaming.Shape.OUT_AND_BACK, RouteImportNaming.shape(outBack))
        // Return only records a start, midpoint and end: sampling by vertex index would fail.
        assertEquals(RouteImportNaming.Shape.OUT_AND_BACK, RouteImportNaming.shape(route(outbound + listOf(outbound[25], outbound[0]))))
        assertEquals(RouteImportNaming.Shape.ROUTE, RouteImportNaming.shape(route(listOf(37.0 to -3.6, 37.0001 to -3.6, 37.0 to -3.6))))
    }

    @Test fun `nearby parallel return is a loop not a retraced route`() {
        val returnPath = outbound.reversed().map { it.first to it.second + .0007 }
        assertEquals(RouteImportNaming.Shape.LOOP, RouteImportNaming.shape(route(outbound + returnPath + outbound.first())) )
    }

    @Test fun `place labels must come from waypoints close to endpoints or turnaround`() {
        val original = outBack
        val named = HikingRoute(original.id, original.name, original.points, listOf(
            RouteWaypoint(37.0, -3.6, "Gran Vía"), RouteWaypoint(37.01, -3.6, "Museo"), RouteWaypoint(40.0, -4.0, "Unrelated")))
        assertEquals("Gran Vía – Museo", RouteImportNaming.places(named, RouteImportNaming.Shape.OUT_AND_BACK))
        assertNull(RouteImportNaming.places(original, RouteImportNaming.Shape.OUT_AND_BACK))
    }

    @Test fun `parser does not interpret GPX lookalikes inside extensions or foreign namespaces`() {
        val gpx = """<gpx xmlns="http://www.topografix.com/GPX/1/1" xmlns:x="urn:other">
            <extensions><metadata><name>Fake title</name></metadata>$points</extensions>
            <x:trk><x:trkseg><x:trkpt lat="1" lon="2"/><x:trkpt lat="3" lon="4"/></x:trkseg></x:trk>
            $points</gpx>"""
        val result = GpxRouteReader.read(gpx)
        assertNull(result.name); assertEquals(2, result.points.size); assertEquals(37.0, result.points.first().first, 0.0)
        assertTrue(runCatching { GpxRouteReader.read("<gpx><extensions>$points</extensions></gpx>") }.isFailure)
    }

    @Test fun `malformed documents and external entities are refused`() {
        listOf("<gpx>$points</gpx><gpx>$points</gpx>", "<gpx>$points</gpx>junk", "<gpx>$points<oops></gpx>",
            "<gpx><metadata><name>&unknown;</name></metadata>$points</gpx>",
            "<!DOCTYPE gpx SYSTEM 'https://example.invalid/private'><gpx>$points</gpx>",
            "<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///private'>]><gpx>$points</gpx>",
            "<gpx xmlns='urn:wrong'>$points</gpx>").forEach { assertTrue(it.take(70), runCatching { GpxRouteReader.read(it) }.isFailure) }
    }

    @Test fun `CDATA stays literal while predefined and numeric entities decode`() {
        assertEquals("A & B 🚶", GpxRouteReader.read("<gpx><metadata><name>A &amp; B &#x1f6b6;</name></metadata>$points</gpx>").name)
        assertEquals("A &amp; B", GpxRouteReader.read("<gpx><metadata><name><![CDATA[A &amp; B]]></name></metadata>$points</gpx>").name)
    }

    @Test fun `self closing points and combined tracks share the point limit`() {
        val many = "<trkpt lat='37' lon='-3.6'/>".repeat(HikingRoute.MAX_POINTS + 1)
        assertTrue(runCatching { GpxRouteReader.read("<gpx><trk><trkseg>$many</trkseg></trk></gpx>") }.isFailure)
        assertTrue(runCatching { GpxRouteReader.read(ByteArray(GpxRouteReader.MAX_BYTES + 1)) }.isFailure)
    }
}
