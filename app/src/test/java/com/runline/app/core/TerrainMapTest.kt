package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class TerrainMapTest {
    private fun grid(key: TerrainTileKey, rise: Double = .02): TerrainGrid = TerrainGrid.parse(buildString {
        append("--wcs\nContent-Type: application/asc\n\nncols 220\nnrows 220\nxllcorner ${key.west}\nyllcorner ${key.south}\ncellsize 5\n")
        repeat(220) { row -> append(List(220) { 100 + (219.5 - row) * 5 * rise }.joinToString(" ")); append('\n') }
        append("--wcs--\n")
    })
    private fun points() = (0..150).map { i -> RunPoint(37.18 + i * 3.0 / 111195.08, -3.61, i * 1000L, 3f, 700 - i * .45) }
    private fun run(points: List<RunPoint>) = RunSession("synthetic-reference", 0, 150000, 150000, points)

    @Test fun projectionMatchesIndependentEtrs89ControlPoints() {
        for ((ll, xy) in listOf((40.4168 to -3.7038) to (440290.458 to 4474257.382),
                (37.18 to -3.61) to (445852.528 to 4115014.853), (41.3851 to 2.1734) to (932623.194 to 4594439.590),
                (39.5696 to 2.6502) to (985462.894 to 4395264.250))) {
            val actual = SpainTerrainProjection.project(ll.first, ll.second)!!
            assertEquals(xy.first, actual.first, .2); assertEquals(xy.second, actual.second, .2)
        }
        assertNull(SpainTerrainProjection.project(Double.NaN, 0.0))
        assertNull(SpainTerrainProjection.project(40.0, -75.0))
        assertNull(SpainTerrainProjection.project(28.5, -16.0)) // Canary support is not implied.
    }
    @Test fun requestsContainOnlyAlignedAreaBounds() {
        val key = TerrainTileKey.at(points().first())!!
        assertEquals(key, TerrainTileKey.at(points()[5]))
        assertTrue(key.url().startsWith("https://servicios.idee.es/wcs-inspire/mdt?"))
        assertFalse(key.url().contains("37.18")); assertFalse(key.url().contains("time"))
        assertTrue(grid(key).matches(key)); assertFalse(grid(key).matches(key.copy(eastKm = key.eastKm + 1)))
    }
    @Test fun fractionalAsciiHeightsAreBilinearlyInterpolatedInCorrectOrientation() {
        val grid = TerrainGrid.parse("ncols 2\nnrows 2\nxllcorner 100\nyllcorner 200\ncellsize 5\n10.25 20.25\n30.25 40.25\n")
        assertEquals(25.25, grid.at(105.0, 205.0)!!, .0001)
        assertEquals(10.25, grid.at(102.5, 207.5)!!, .0001)
        assertNull(grid.at(99.0, 205.0))
    }
    @Test fun noDataAndMalformedResponsesCannotInventTerrain() {
        val prefix = "ncols 2\nnrows 2\nxllcorner 100\nyllcorner 200\ncellsize 5\n"
        assertNull(TerrainGrid.parse(prefix + "NODATA_value -9999\n1 2\n3 -9999\n").at(105.0, 205.0))
        for (text in listOf("<ExceptionReport/>", prefix + "1 2\n3\n", prefix + "NaN 2\n3 4\n",
                prefix.replace("ncols 2", "ncols 999999999"), prefix.replace("cellsize 5", "cellsize 0") + "1 2\n3 4\n")) {
            assertTrue(runCatching { TerrainGrid.parse(text) }.isFailure)
        }
        assertTrue(runCatching { TerrainGrid.parse("x".repeat(TerrainGrid.MAX_BYTES + 1)) }.isFailure)
    }
    @Test fun independentClimbOverridesSmoothFalseGpsDescentWithoutChangingRecording() {
        val original = run(points()); val originalHeights = original.points.map { it.altitudeMeters }
        val key = TerrainTileKey.at(original.points.first())!!
        val projected = TerrainMapSnapshot(1, true, mapOf(key to grid(key))).project(original)
        val trend = TerrainTrend(projected.elevationSource); val estimate = projected.points.map(trend::update).last()
        assertEquals(TerrainSource.TERRAIN_MAP, estimate.source)
        assertEquals(TerrainDirection.UPHILL, estimate.direction); assertEquals(2.0, estimate.gradePercent!!, .1)
        assertEquals(originalHeights, original.points.map { it.altitudeMeters })
        assertEquals(RunMetrics.distanceMeters(original.points), RunMetrics.distanceMeters(projected.points), 0.0)
        assertEquals(RunReplay(original).durationMs, RunReplay(projected).durationMs)
        assertTrue(projected.points.all { it.verticalAccuracyMeters == null }) // Never invent sensor accuracy.
    }
    @Test fun absentDisabledOrAmbiguousMapsNeverFallBackToGpsAltitude() {
        val original = run(points()); val key = TerrainTileKey.at(original.points.first())!!
        for (snapshot in listOf(TerrainMapSnapshot(1, true, emptyMap()), TerrainMapSnapshot(2, false, mapOf(key to grid(key))),
                TerrainMapSnapshot(3, true, mapOf(key to grid(key, .8))))) {
            assertTrue(snapshot.project(original).points.all { it.altitudeMeters == null })
        }
        val snapshot = TerrainMapSnapshot(1, true, mapOf(key to grid(key)))
        assertNull(snapshot.point(original.points.first().copy(accuracyMeters = 25f)).altitudeMeters)
        assertNull(snapshot.point(original.points.first().copy(accuracyMeters = Float.NaN)).altitudeMeters)
    }
    @Test fun incrementalProjectionRetainsIdentityAndInvalidatesOnMapChange() {
        val raw = run(points()); val key = TerrainTileKey.at(raw.points.first())!!
        val map = TerrainMapSnapshot(1, true, mapOf(key to grid(key)))
        val projector = TerrainProjector(); val first = projector.project(raw.copy(points = raw.points.take(50)), map)
        val second = projector.project(raw, map)
        assertSame(first.points.last(), second.points[49])
        val cleared = projector.project(raw, TerrainMapSnapshot(2, true, emptyMap()))
        assertTrue(cleared.points.all { it.altitudeMeters == null })
        val reset = projector.project(raw.copy(points = raw.points.take(10)), map)
        assertEquals(10, reset.points.size); assertNotNull(reset.points.last().altitudeMeters)
    }
    @Test fun rawGpsHeightsCannotEnterTrustedAiElevation() {
        val original = run(points()); val scrubbed = original.withTrustedElevation()
        assertTrue(scrubbed.points.all { it.altitudeMeters == null })
        assertEquals(TerrainSource.UNKNOWN, scrubbed.elevationSource)
        assertEquals(original.points.map { it.timestampMs }, scrubbed.points.map { it.timestampMs })
    }
    @Test fun supportedSteepMapDescentRemainsPossible() {
        val raw = run(points()); val key = TerrainTileKey.at(raw.points.first())!!
        val model = TerrainMapSnapshot(1, true, mapOf(key to grid(key, -.15))).project(raw)
        val trend = TerrainTrend(model.elevationSource); val result = model.points.map(trend::update).last()
        assertEquals(TerrainDirection.DOWNHILL, result.direction); assertEquals(-15.0, result.gradePercent!!, .1)
    }
    @Test fun disablingMapInvalidatesCoachTerrainEvenWithoutANewFix() {
        val points = points(); val initial = run(emptyList()); var enabled = true; var revision = 0L
        val engine = CoachEngine(CoachConfig(enabled = true), initial,
            terrainPoint = { if (enabled) it else it.copy(altitudeMeters = null) }, terrainRevision = { revision })
        points.indices.forEach { i -> engine.update(initial.copy(points = points.take(i + 1), activeDurationMs = i * 1000L), true, true) }
        assertEquals(CoachTerrain.DOWNHILL, engine.situation.terrain)
        enabled = false; revision++
        engine.update(initial.copy(points = points, activeDurationMs = 150001L), true, true)
        assertEquals(CoachTerrain.UNKNOWN, engine.situation.terrain); assertNull(engine.situation.gradePercent)
    }
    @Test fun derivedAnalysisCannotOverwriteSavedGps() {
        val directory = kotlin.io.path.createTempDirectory("terrain-original-").toFile()
        try {
            val store = com.runline.app.data.DiskRunStore(directory); val raw = run(points()); store.save(raw)
            val before = directory.listFiles()!!.associate { it.name to it.readBytes().toList() }
            val derived = TerrainMapSnapshot(1, true, emptyMap()).project(raw)
            assertTrue(runCatching { store.save(derived) }.isFailure)
            assertEquals(before, directory.listFiles()!!.associate { it.name to it.readBytes().toList() })
            assertEquals(raw, store.get(raw.id))
        } finally { directory.deleteRecursively() }
    }
    @Test fun coachDoesNotAnnounceHillsFromUncorroboratedGps() {
        val points = points(); val initial = run(emptyList()); val engine = CoachEngine(CoachConfig(enabled = true), initial)
        val cues = points.indices.mapNotNull { i -> engine.update(initial.copy(points = points.take(i + 1), activeDurationMs = i * 1000L), true, true) }
        assertEquals(CoachTerrain.UNKNOWN, engine.situation.terrain)
        assertTrue(cues.none { it is CoachCue.Terrain || it == CoachCue.EasingAfterHill ||
            (it is CoachCue.Encouragement && it.kind in setOf(EncouragementKind.UPHILL, EncouragementKind.DOWNHILL)) })
    }
}
