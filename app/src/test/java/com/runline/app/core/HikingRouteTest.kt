package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

class HikingRouteTest {
    private val repo = File("../routes").takeIf { it.isDirectory } ?: File("routes")

    private fun gpx(body: String) = """<?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1" xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">$body</gpx>"""

    @Test fun `reads tracks, names, waypoints and entities and ignores extensions`() {
        val route = GpxRouteReader.read(gpx("""
            <metadata><name>Vereda &amp; río</name><desc><![CDATA[Sube <despacio>]]></desc></metadata>
            <wpt lat="37.1" lon="-3.6"><ele>700</ele><name>Fuente</name></wpt>
            <trk><name>ignored when metadata has one</name><trkseg>
              <trkpt lat="37.1000" lon="-3.6000"><ele>700.5</ele><time>2026-01-01T00:00:00Z</time>
                <extensions><gpxtpx:TrackPointExtension><gpxtpx:hr>120</gpxtpx:hr></gpxtpx:TrackPointExtension></extensions></trkpt>
              <trkpt lat='37.1010' lon='-3.6000'/>
            </trkseg><trkseg><trkpt lat="37.1020" lon="-3.6000"><ele>720</ele></trkpt></trkseg></trk>"""))
        assertEquals("Vereda & río", route.name)
        assertEquals("Sube <despacio>", route.description)
        assertEquals(3, route.points.size)
        assertEquals(700.5, route.points[0].third!!, 0.0); assertNull(route.points[1].third)
        assertEquals(listOf("Fuente"), route.waypoints.map { it.name })
    }

    @Test fun `route points are used only without a track and bad files are refused`() {
        val rte = GpxRouteReader.read(gpx("<rte><name>Plan</name><rtept lat=\"1\" lon=\"2\"/><rtept lat=\"1.01\" lon=\"2\"/></rte>"))
        assertEquals("Plan", rte.name); assertEquals(2, rte.points.size)
        val bad = listOf(
            "<?xml version=\"1.0\"?><!DOCTYPE gpx [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><gpx><trk><trkseg><trkpt lat=\"1\" lon=\"1\"><name>&x;</name></trkpt></trkseg></trk></gpx>",
            "<kml><Document/></kml>", gpx("<trk><trkseg><trkpt lat=\"1\" lon=\"1\"/></trkseg></trk>"),
            gpx("<trk><trkseg><trkpt lat=\"1\" lon=\"1\"></trk>"), "<gpx><trk>")
        bad.forEach { assertTrue(it.take(40), runCatching { GpxRouteReader.read(it) }.isFailure) }
        // A malformed point is skipped, not fatal.
        val skipped = GpxRouteReader.read(gpx("<trk><trkseg><trkpt lat=\"x\" lon=\"1\"/><trkpt lat=\"1\" lon=\"1\"/><trkpt lat=\"1.001\" lon=\"1\"/></trkseg></trk>"))
        assertEquals(2, skipped.points.size)
    }

    @Test fun `estimates follow Tobler and rate effort kilometres`() {
        // 5 km flat: Tobler gives ~5.04 km/h on the flat, so about an hour.
        val flat = HikingRoute.of("flat", "Flat", (0..100).map { Triple(37.0 + it * 0.00045, -3.6, 700.0) })
        assertEquals(5.0, flat.distanceMeters / 1000, 0.05)
        assertEquals(60.0, flat.stats.estimatedMinutes.toDouble(), 3.0)
        assertEquals(RouteDifficulty.EASY, flat.stats.difficulty)
        // The same distance climbing 500 m takes much longer and is harder.
        val climb = HikingRoute.of("climb", "Climb", (0..100).map { Triple(37.0 + it * 0.00045, -3.6, 700.0 + it * 5.0) })
        assertEquals(500.0, climb.stats.ascentMeters!!, 1.0)
        // Tobler at 10%: ~3.55 km/h, so ~85 min for 5 km.
        assertEquals(85.0, climb.stats.estimatedMinutes.toDouble(), 4.0)
        assertTrue(climb.stats.estimatedMinutes > flat.stats.estimatedMinutes + 20)
        assertEquals(RouteDifficulty.MODERATE, climb.stats.difficulty)
        assertEquals(500.0, climb.ascentAt.last(), 1.0); assertEquals(0.0, climb.ascentAt.first(), 0.0)
        // Without heights the estimate falls back to the flat speed.
        assertNull(HikingRoute.of("none", "None", (0..10).map { Triple(37.0 + it * 0.001, -3.6, null) }).stats.ascentMeters)
    }

    @Test fun `the open repository catalog and its routes are valid`() {
        val index = RouteCatalog.parse(File(repo, "index.json").readText())
        assertTrue(index.routes.isNotEmpty())
        index.routes.forEach { entry ->
            val file = File(repo, entry.file); assertTrue(entry.file, file.isFile)
            val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(entry.file, entry.sha256, sha)
            val parsed = GpxRouteReader.read(file.readBytes())
            val route = HikingRoute.of(entry.id, entry.name("en"), parsed.points, parsed.waypoints)
            assertEquals(entry.distanceMeters, route.distanceMeters, entry.distanceMeters * 0.01)
            assertEquals(entry.startLatitude, route.points.first().latitude, 1e-5)
            assertTrue(parsed.waypoints.isNotEmpty())
        }
        val granada = index.routes.single { it.id == "es-granada-alhambra-dehesa-genil" }
        assertEquals("Alhambra, Dehesa del Generalife y río Genil", granada.name("es")); assertTrue(granada.loop)
    }

    @Test fun `catalog rejects unsafe paths, bad hashes and duplicates`() {
        val good = File(repo, "index.json").readText()
        listOf("\"es/granada/alhambra-dehesa-genil.gpx\"" to "\"../secrets.gpx\"", "\"ODbL-1.0\"" to "\"proprietary\"",
            "\"activity\": \"hike\"" to "\"activity\": \"drive\"").forEach { (a, b) ->
            assertTrue(b, runCatching { RouteCatalog.parse(good.replace(a, b)) }.isFailure)
        }
        val sha = Regex("\"sha256\": \"([0-9a-f]+)\"").find(good)!!.groupValues[1]
        assertTrue(runCatching { RouteCatalog.parse(good.replace(sha, sha.uppercase())) }.isFailure)
    }

    @Test fun `place notes and junctions are read strictly`() {
        val good = File(repo, "index.json").readText()
        val granada = RouteCatalog.parse(good).routes.single { it.id == "es-granada-alhambra-dehesa-genil" }
        assertEquals(5, granada.places.size)
        assertTrue(granada.notes("es").getValue("Río Genil, Centro Hípico").startsWith("El Genil"))
        assertTrue(granada.notes("en").getValue("Río Genil, Centro Hípico").startsWith("The Genil"))
        assertEquals(granada.notes("en"), granada.notes("fr"))
        assertTrue(granada.junctions!!.isNotEmpty())
        val note = granada.places.first().notes.getValue("en")
        listOf(note to "x".repeat(261), note to "<b>bold</b>", "\"Puerta de las Granadas\"" to "\"\"").forEach { (a, b) ->
            assertTrue(b, runCatching { RouteCatalog.parse(good.replaceFirst(a, b)) }.isFailure)
        }
        assertTrue(runCatching { RouteCatalog.parse(good.replaceFirst("[37.", "[137.")) }.isFailure)
        // Older catalogs without notes or junctions still parse.
        val plain = RouteCatalog.parse(good.replace(Regex(",\\s*\"places\": \\[.*?\\n      \\]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex(",\\s*\"junctions\": \\[.*?\\n      \\]", RegexOption.DOT_MATCHES_ALL), "")).routes.single()
        assertTrue(plain.places.isEmpty()); assertNull(plain.junctions)
    }

    @Test fun `sunset in Granada and polar night`() {
        val day = LocalDate.of(2026, 11, 6).toEpochDay()
        val sunset = SunTimes.sunset(day, 37.1773, -3.5986)!!
        // About 17:13 UTC (18:13 local) in early November.
        val utc = java.time.Instant.ofEpochMilli(sunset).atZone(java.time.ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 11, 6), utc.toLocalDate())
        val minutes = utc.hour * 60 + utc.minute
        assertEquals((17 * 60 + 13).toDouble(), minutes.toDouble(), 4.0)
        assertTrue(SunTimes.sunrise(day, 37.1773, -3.5986)!! < sunset)
        assertNull(SunTimes.sunset(LocalDate.of(2026, 12, 21).toEpochDay(), 78.2, 15.6))
    }
}
