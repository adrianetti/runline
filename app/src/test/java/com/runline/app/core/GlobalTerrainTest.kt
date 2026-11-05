package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GlobalTerrainTest {
    private fun argb(height: Double): Int {
        val value = height + 32768; val r = floor(value / 256).toInt(); val g = floor(value % 256).toInt(); val b = ((value - floor(value)) * 256).toInt()
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }
    private fun grid(height: Double) = WorldTerrainGrid.fromArgb(IntArray(65536) { argb(height) })
    private fun grids(latitude: Double, longitude: Double, height: Double) = TerrainDownloadArea(latitude, longitude).requiredKeys.associate { it.key to grid(height) }
    @Test fun terrariumDecodesFractionalAndNegativeHeights() {
        assertEquals(2523.265625, grid(2523.265625).at(0, 0)!!, 0.0)
        assertEquals(-430.25, grid(-430.25).at(12, 255)!!, 0.0)
        assertNull(grid(-32768.0).at(0, 0)); assertNull(grid(9500.0).at(0, 0))
        assertTrue(runCatching { WorldTerrainGrid.fromArgb(IntArray(1)) }.isFailure)
        assertTrue(runCatching { WorldTerrainGrid.fromArgb(IntArray(65536)) }.isFailure)
    }
    @Test fun knownMercatorCoordinatesAndLatitudeLimits() {
        assertEquals(WorldTileKey(12, 2048, 2048), WorldTileKey.at(0.0, 0.0))
        assertEquals(WorldTileKey.at(10.0, -180.0), WorldTileKey.at(10.0, 180.0))
        assertNull(WorldTileKey.at(86.0, 0.0)); assertNull(WorldTileKey.at(Double.NaN, 0.0))
        assertTrue(runCatching { TerrainDownloadArea(85.1, 0.0) }.isFailure)
    }
    @Test fun venezuelaUsesWorldDataAndNoSpanishRequests() {
        val area = TerrainDownloadArea(10.4806, -66.9036)
        assertEquals(9, area.keys.size); assertEquals(area.keys, area.requiredKeys)
        assertTrue(area.keys.all { it.url.startsWith("https://elevation-tiles-prod.s3.amazonaws.com/terrarium/") })
        assertTrue(area.keys.none { it.url.contains("10.4806") || it.url.contains("time") })
        val original = RunPoint(area.latitude, area.longitude, 1234, 3f, 9876.0)
        val derived = TerrainMapSnapshot(1, true, emptyMap(), grids(area.latitude, area.longitude, 860.25)).point(original)
        assertEquals(860.25, derived.altitudeMeters!!, .00001); assertEquals(TerrainModel.MAPZEN, derived.terrainModel)
        assertEquals(original.copy(altitudeMeters = derived.altitudeMeters, verticalAccuracyMeters = null, terrainModel = derived.terrainModel), derived)
        assertEquals(9876.0, original.altitudeMeters!!, 0.0)
    }
    @Test fun neighboringTilesAreSampledAcrossDateLine() {
        val keys = WorldTileKey.needed(RunPoint(0.0, 180.0, 0, 3f))
        assertEquals(4, keys.size)
        val grids = keys.associateWith { grid(if (it.x == 0) 100.0 else 102.0) }
        assertEquals(101.0, sampleWorld(grids, 0.0, 180.0)!!, .00001)
        assertEquals(sampleWorld(grids, 0.0, -180.0), sampleWorld(grids, 0.0, 180.0))
        assertNull(sampleWorld(grids.filterKeys { it.x == 0 }, 0.0, 180.0))
    }
    @Test fun nearbyPlanCoversFiveKmIncludingZoomBandsDateLineAndHemispheres() {
        for ((lat, lon) in listOf(10.4806 to -66.9036, 37.18 to -3.61, -33.87 to 151.21, -33.9 to 18.4,
                40.0 to -73.0, 84.99 to 179.999, -84.99 to -179.999, 0.0 to 0.0, 38.28 to 20.0)) {
            val area = TerrainDownloadArea(lat, lon); val keys = area.requiredKeys
            assertTrue(area.keys.size <= 27); assertTrue(area.maxBytes <= 30_000_000)
            for (degrees in 0 until 360 step 10) {
                val angle = Math.toRadians(degrees.toDouble()); val targetLat = lat + sin(angle) * 5000 / 111195.08
                val targetLon = ((lon + cos(angle) * 5000 / (111195.08 * cos(Math.toRadians(lat))) + 540) % 360) - 180
                val needed = WorldTileKey.needed(RunPoint(targetLat, targetLon, 0, 250f)).map { TerrainDownloadKey.World(it) }
                assertTrue("Missing area edge at $lat, $lon / $degrees", keys.containsAll(needed))
            }
        }
    }
    @Test fun missingDisabledAndPoorPositionNeverReuseGpsAltitude() {
        val point = RunPoint(10.48, -66.90, 0, 3f, 4000.0)
        val map = grids(point.latitude, point.longitude, 860.0)
        assertNull(TerrainMapSnapshot(1, false, emptyMap(), map).point(point).altitudeMeters)
        assertNull(TerrainMapSnapshot(1, true, emptyMap()).point(point).altitudeMeters)
        assertNull(TerrainMapSnapshot(1, true, emptyMap(), map).point(point.copy(accuracyMeters = 100f)).altitudeMeters)
    }
    @Test fun uncertaintyFootprintUsesOneResolutionAtZoomBandBoundary() {
        val latitude = Math.toDegrees(acos(4096.0 * 256 * 30 / 40075016.686))
        val point = RunPoint(latitude, 20.0, 0, 5f)
        val tiles = WorldTileKey.needed(point).associateWith { grid(100.0) }
        assertEquals(1, tiles.keys.map { it.zoom }.distinct().size)
        assertEquals(100.0, TerrainMapSnapshot(1, true, emptyMap(), tiles).point(point).altitudeMeters!!, .00001)
    }
    @Test fun providerSwitchCannotInventGainOrInterpolateDifferentDatums() {
        val points = (0..150).map { i -> RunPoint(10.48 + i * 3.0 / 111195.08, -66.9, i * 1000L, 3f,
            if (i < 75) 100.0 else 140.0, terrainModel = if (i < 75) TerrainModel.IGN_MDT05 else TerrainModel.MAPZEN) }
        val run = RunSession("synthetic-source-change", 0, 150000, 150000, points, elevationSource = TerrainSource.TERRAIN_MAP)
        val replay = RunReplay(run)
        assertEquals(0.0, replay.summary.elevationGainMeters!!, 0.0)
        assertEquals(0.0, ElevationProgress().apply { update(points) }.climbedMeters!!, 0.0)
        assertEquals(0.0, RunMetrics.elevationGainMeters(points)!!, 0.0)
        assertNull(replay.at(74500)?.point?.altitudeMeters)
        assertNull(MarkerMotion.interpolate(points[74], points[75], .5).altitudeMeters)
        val trend = TerrainTrend(TerrainSource.TERRAIN_MAP)
        points.take(76).forEach(trend::update)
        assertEquals(TerrainDirection.UNKNOWN, trend.update(points[75]).direction)
        assertEquals(150000L, replay.durationMs)
    }
    @Test fun globalGradesNeedLongerEvidenceButRealSteepClimbsAreNotFlattened() {
        val points = (0..150).map { i -> RunPoint(10.48 + i * 3.0 / 111195.08, -66.9, i * 1000L, 3f,
            100.0 + i * .45, terrainModel = TerrainModel.MAPZEN) }
        val trend = TerrainTrend(TerrainSource.TERRAIN_MAP)
        val estimates = points.map(trend::update)
        assertNull(estimates[30].gradePercent)
        assertEquals(TerrainDirection.UPHILL, estimates.last().direction)
        assertEquals(15.0, estimates.last().gradePercent!!, .1)
    }
}
