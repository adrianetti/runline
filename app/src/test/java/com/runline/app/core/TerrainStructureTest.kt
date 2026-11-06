package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class TerrainStructureTest {
    private fun line(count: Int, model: (Int) -> TerrainModel?, height: (Int) -> Double? = { 650.0 }) = (0 until count).map { index ->
        val m = model(index)
        RunPoint(37.17, -3.6 + index * 3.0 / (111195.0802335329 * Math.cos(Math.toRadians(37.17))), 1_000L + index * 1_000, 4f,
            if (m == TerrainModel.STRUCTURE) null else height(index), terrainModel = m)
    }

    @Test fun `structure hides heights fifteen metres either side and nothing else`() {
        val points = line(40, { if (it == 20) TerrainModel.STRUCTURE else TerrainModel.IGN_MDT05 })
        val guarded = TerrainStructureGuard.apply(points)
        val hidden = guarded.indices.filter { guarded[it].altitudeMeters == null }
        // 3 m spacing: indices 15..25 lie within 15 m of index 20.
        assertEquals((15..25).toList(), hidden)
        assertTrue(hidden.all { guarded[it].terrainModel == TerrainModel.STRUCTURE })
        assertSame(points[0], guarded[0])
        // Original GPS fixes are never changed: guarding only returns a derived copy.
        assertEquals(650.0, points[16].altitudeMeters!!, 0.0)
    }

    @Test fun `no structure returns the same list and a gap stops the margin`() {
        val clean = line(10, { TerrainModel.IGN_MDT05 })
        assertSame(clean, TerrainStructureGuard.apply(clean))
        val gap = line(10, { if (it == 4) TerrainModel.STRUCTURE else TerrainModel.IGN_MDT05 }).mapIndexed { index, point ->
            if (index >= 5) point.copy(segment = 1) else point
        }
        assertEquals(650.0, TerrainStructureGuard.apply(gap)[5].altitudeMeters!!, 0.0)
    }

    @Test fun `live guard hides only the distance after a structure`() {
        val guard = TerrainStructureGuard.Live()
        val points = line(20, { if (it == 5) TerrainModel.STRUCTURE else TerrainModel.IGN_MDT05 })
        val result = points.map(guard::apply)
        assertNotNull(result[4].altitudeMeters)
        assertTrue((5..10).all { result[it].altitudeMeters == null })
        assertNotNull(result[11].altitudeMeters)
    }

    @Test fun `structure ends a terrain estimate instead of mixing height models`() {
        val trend = TerrainTrend(TerrainSource.TERRAIN_MAP)
        val climbing = line(60, { if (it in 40..44) TerrainModel.STRUCTURE else TerrainModel.IGN_MDT05 }, { 650.0 + it * .15 })
        val estimates = TerrainStructureGuard.apply(climbing).map(trend::update)
        assertEquals(TerrainDirection.UPHILL, estimates[30].direction)
        assertEquals(TerrainDirection.UNKNOWN, estimates[42].direction)
    }

    @Test fun `mini profile keeps a true aspect over long stretches`() {
        val flatish = SlopeProfile(List(SlopeProfile.COUNT) { 650.0 + if (it == 16) 2.0 else 0.0 }, spanMeters = 130.0)
        assertEquals(130.0 * SlopeProfile.MAX_DRAWN_GRADE, flatish.drawRange(), 1e-9)
        val steep = SlopeProfile(List(SlopeProfile.COUNT) { 650.0 + it * 1.0 }, spanMeters = 100.0)
        assertEquals(31.0, steep.drawRange(), 1e-9)
        assertEquals(3.0, SlopeProfile(List(SlopeProfile.COUNT) { 650.0 }).drawRange(), 1e-9)
    }
}
