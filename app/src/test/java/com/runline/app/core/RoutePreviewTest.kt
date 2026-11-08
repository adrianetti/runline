package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RoutePreviewTest {
    @Test fun `a route previews as one continuous replay timed by Tobler`() {
        val repo = File("../routes").takeIf { it.isDirectory } ?: File("routes")
        val entry = RouteCatalog.parse(File(repo, "index.json").readText()).routes.single { it.id == "es-granada-alhambra-dehesa-genil" }
        val gpx = GpxRouteReader.read(File(repo, entry.file).readBytes())
        val route = HikingRoute.of(entry.id, entry.name("en"), gpx.points, gpx.waypoints)
        val session = RoutePreview.session(route)
        assertEquals(route.stats.estimatedMinutes * 60_000.0, session.activeDurationMs.toDouble(), 60_000.0)
        assertTrue(session.points.zipWithNext().all { (a, b) -> b.timestampMs - a.timestampMs in 1..RoutePreview.SAMPLE_MS })
        assertEquals(route.points.first().latitude, session.points.first().latitude, 1e-9)
        assertEquals(route.points.last().longitude, session.points.last().longitude, 1e-9)
        assertTrue(session.points.all { it.altitudeMeters != null })
        val replay = RunReplay(session)
        assertEquals(1, replay.segments.size)
        assertEquals(route.distanceMeters, replay.summary.distanceMeters, route.distanceMeters * 0.01)
        assertEquals(session.activeDurationMs, replay.durationMs)
        // The Granada route climbs about 200 m through the Dehesa: its own profile, gap-free.
        assertTrue(RoutePreview.hasHeights(session))
        assertTrue(replay.summary.maxAltitudeMeters!! - replay.summary.minAltitudeMeters!! > 200)
        assertTrue(replay.summary.elevationGainMeters!! > 200)
    }

    @Test fun `a route without heights everywhere falls back to terrain maps`() {
        val m = 1 / 110_574.0
        val none = RoutePreview.session(HikingRoute.of("n", "None", (0..50).map { Triple(37.0 + it * 20 * m, -3.6, null as Double?) }))
        assertFalse(RoutePreview.hasHeights(none))
        assertFalse(RoutePreview.hasHeights(none.copy(points = emptyList())))
        val with = RoutePreview.session(HikingRoute.of("w", "With", (0..50).map { Triple(37.0 + it * 20 * m, -3.6, 700.0 + it) }))
        assertFalse(RoutePreview.hasHeights(with.copy(points = with.points.mapIndexed { i, p -> if (i == 3) p.copy(altitudeMeters = null) else p })))
    }
}
