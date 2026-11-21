package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TerrainFootprintTest {
    private fun hillside(radius: Double, height: (Double, Double) -> Double): Boolean {
        val offsets = TerrainFootprint.offsets(radius)
        return TerrainFootprint.hillside(offsets, offsets.map { (e, n) -> height(e, n) }) { e, n -> height(e, n) }
    }

    @Test fun `an even slope is a hillside at any GPS radius`() {
        assertTrue(hillside(5.0) { _, n -> 700 + n * .4 })
        assertTrue(hillside(15.0) { e, n -> 700 + e * .3 - n * .2 })
        assertTrue(hillside(15.0) { _, n -> 700 + n * .4 + n * n * .003 })
    }

    @Test fun `cliffs, banks, ravines and ridges are not`() {
        assertFalse(hillside(10.0) { _, n -> 700 + n * 1.2 })
        assertFalse(hillside(5.0) { _, n -> 700 + (n + 3).coerceIn(0.0, 6.0) })
        assertFalse(hillside(10.0) { _, n -> 700 + (n + 3).coerceIn(0.0, 6.0) })
        assertFalse(hillside(10.0) { e, _ -> 700 + abs(e) * .4 })
        assertFalse(hillside(10.0) { e, _ -> 700 - abs(e) * .4 })
        assertFalse(hillside(5.0) { _, n -> 700 + n.coerceIn(-8.0, 8.0) * .5 })
        val offsets = TerrainFootprint.offsets(5.0)
        assertFalse(TerrainFootprint.hillside(offsets, offsets.map { (_, n) -> 700 + n * .4 }) { _, _ -> null })
        assertFalse(TerrainFootprint.hillside(offsets, offsets.map { 700.0 }) { _, _ -> 700.0 })
    }
}
