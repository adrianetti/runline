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
    }
}
