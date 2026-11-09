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

    private val en = RouteImportNaming.Words("loop", "out and back", "Route")
    private val es = RouteImportNaming.Words("circular", "ida y vuelta", "Ruta")
    private val spanish = java.util.Locale.forLanguageTag("es-ES")

    @Test fun `suggestion reads town, start and far point, shape and distance`() {
        val places = listOf(RouteImportNaming.Place("Madrid", "Gran Vía"), RouteImportNaming.Place("Madrid", "Museo del Prado"))
        assertEquals("Madrid, Gran Vía – Museo del Prado out and back 2.2K",
            RouteImportNaming.suggestion(outBack, RouteImportNaming.Shape.OUT_AND_BACK, places, en, java.util.Locale.UK))
        assertEquals("Madrid, Gran Vía – Museo del Prado ida y vuelta 2,2K",
            RouteImportNaming.suggestion(outBack, RouteImportNaming.Shape.OUT_AND_BACK, places, es, spanish))
        val far = listOf(RouteImportNaming.Place("Granada", "Los Pinos"), RouteImportNaming.Place("Granada", "Puente Romano"))
        assertEquals("Granada, Los Pinos – Puente Romano circular 4K", RouteImportNaming.suggestion(loop, RouteImportNaming.Shape.LOOP, far, es, spanish))
    }

    @Test fun `suggestion drops what it does not know`() {
        assertEquals("Loop 4K", RouteImportNaming.suggestion(loop, RouteImportNaming.Shape.LOOP, emptyList(), en, java.util.Locale.UK))
        assertEquals("Ruta 1,1K", RouteImportNaming.suggestion(route(outbound), RouteImportNaming.Shape.ROUTE, listOf(null, null), es, spanish))
        // A town alone, a spot that is the town, and the same spot at both ends.
        assertEquals("Granada loop 4K", RouteImportNaming.suggestion(loop, RouteImportNaming.Shape.LOOP,
            listOf(RouteImportNaming.Place("Granada", "Granada"), RouteImportNaming.Place("Granada", null)), en, java.util.Locale.UK))
        assertEquals("Granada, Zaidín loop 4K", RouteImportNaming.suggestion(loop, RouteImportNaming.Shape.LOOP,
            listOf(RouteImportNaming.Place("Granada", "Zaidín"), RouteImportNaming.Place(null, "zaidín")), en, java.util.Locale.UK))
        assertEquals("Alhambra – Sacromonte 1.1K", RouteImportNaming.suggestion(route(outbound), RouteImportNaming.Shape.ROUTE,
            listOf(RouteImportNaming.Place(null, "Alhambra"), RouteImportNaming.Place(null, "Sacromonte")), en, java.util.Locale.UK))
    }

    @Test fun `named waypoints near the start or turnaround beat the geocoder`() {
        val named = HikingRoute(outBack.id, outBack.name, outBack.points, listOf(
            RouteWaypoint(37.0, -3.6, "Fuente"), RouteWaypoint(37.01, -3.6, "Mirador"), RouteWaypoint(40.0, -4.0, "Unrelated")))
        val places = listOf(RouteImportNaming.Place("Granada", "Calle Real"), RouteImportNaming.Place("Granada", "Camino"))
        assertEquals("Granada, Fuente – Mirador out and back 2.2K",
            RouteImportNaming.suggestion(named, RouteImportNaming.Shape.OUT_AND_BACK, places, en, java.util.Locale.UK))
    }

    @Test fun `naming points are the start and the turnaround or farthest point`() {
        assertEquals(37.01, RouteImportNaming.namingPoints(outBack, RouteImportNaming.Shape.OUT_AND_BACK)[1].latitude, 1e-9)
        val far = RouteImportNaming.namingPoints(loop, RouteImportNaming.Shape.LOOP)[1]
        assertEquals(37.01, far.latitude, 1e-9); assertEquals(-3.59, far.longitude, 1e-9)
        assertEquals(outbound.last().first, RouteImportNaming.namingPoints(route(outbound), RouteImportNaming.Shape.ROUTE)[1].latitude, 1e-9)
    }

    @Test fun `geocoder results keep a specific label and skip numbers, plus codes and broad areas`() {
        val street = RouteImportNaming.AddressParts(feature = "29", street = "Gran Vía", district = "Centro", town = "Madrid", area = "Madrid", region = "Comunidad de Madrid", country = "España")
        assertEquals(RouteImportNaming.Place("Madrid", "Gran Vía"), RouteImportNaming.place(listOf(street)))
        val poi = RouteImportNaming.AddressParts(feature = "Museo Nacional del Prado", street = "Paseo del Prado", town = "Madrid")
        assertEquals("Museo Nacional del Prado", RouteImportNaming.place(listOf(poi))!!.spot)
        val trail = RouteImportNaming.AddressParts(feature = "9F3W+X6", street = "Unnamed Road", town = "Güéjar Sierra", area = "Granada", region = "Andalucía")
        assertEquals(RouteImportNaming.Place("Güéjar Sierra", null), RouteImportNaming.place(listOf(trail)))
        assertEquals(RouteImportNaming.Place("Granada", "Zaidín"), RouteImportNaming.place(listOf(
            RouteImportNaming.AddressParts(feature = "Granada", district = "Zaidín", town = "Granada"))))
        assertEquals(RouteImportNaming.Place("Granada", null), RouteImportNaming.place(listOf(RouteImportNaming.AddressParts(feature = "s/n", area = "Granada"))))
        assertNull(RouteImportNaming.place(emptyList()))
    }

    @Test fun `distance reads like race distances`() {
        assertEquals("10.3K", RouteImportNaming.distance(10_290.0, java.util.Locale.UK))
        assertEquals("10,3K", RouteImportNaming.distance(10_290.0, spanish))
        assertEquals("5K", RouteImportNaming.distance(4_990.0, spanish))
        assertEquals("21,1K", RouteImportNaming.distance(21_097.5, spanish))
    }

    @Test fun `default activity titles are not route names`() {
        listOf("Morning Run", "Afternoon run", "Hike", "Run 3f2a9c1e-12ab-4cde-8f00-123456789abc", "Carrera de mañana",
            "Carrera matutina", "Caminata por la tarde", "Senderismo", "Walk 2").forEach {
            assertNull(it, RouteImportNaming.suppliedName(it, null))
        }
        assertEquals("Run Granada", RouteImportNaming.suppliedName("Run Granada", null))
        assertEquals("Vereda de la Estrella", RouteImportNaming.suppliedName("Morning Run", "Vereda_de_la_Estrella.gpx"))
        // Runline's own exports and download copies.
        assertNull(RouteImportNaming.suppliedName("Walk 3f2a9c1e-12ab-4cde-8f00-123456789abc", "Caminata 8,42 km - 2026-11-01 10-30.gpx"))
        assertEquals("Vuelta al parque", RouteImportNaming.suppliedName(null, "Vuelta al parque 10.05 km - 2026-11-04 09-15 (2).gpx"))
        assertEquals("Alhambra", RouteImportNaming.suppliedName(null, "Alhambra (1).gpx"))
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
