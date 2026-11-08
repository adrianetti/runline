package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TerrainFootprintTest {
    /** [height] at metres (east, north) from the fix, probed as the terrain map does. */
    private fun hillside(radius: Double, height: (Double, Double) -> Double): Boolean {
        val offsets = TerrainFootprint.offsets(radius)
        return TerrainFootprint.hillside(offsets, offsets.map { (e, n) -> height(e, n) }) { e, n -> height(e, n) }
    }

    @Test fun `an even slope is a hillside at any GPS radius`() {
        assertTrue(hillside(5.0) { _, n -> 700 + n * .4 })
        assertTrue(hillside(15.0) { e, n -> 700 + e * .3 - n * .2 })
        // Gently curving ground, as on a real mountainside, still fits a plane.
        assertTrue(hillside(15.0) { _, n -> 700 + n * .4 + n * n * .003 })
    }

    @Test fun `cliffs, banks, ravines and ridges are not`() {
        assertFalse(hillside(10.0) { _, n -> 700 + n * 1.2 })
        // A 6 m bank between flat ground: a bridge deck, an underpass or an embankment.
        assertFalse(hillside(5.0) { _, n -> 700 + (n + 3).coerceIn(0.0, 6.0) })
        assertFalse(hillside(10.0) { _, n -> 700 + (n + 3).coerceIn(0.0, 6.0) })
        // The bottom of a ravine and the crest of a ridge.
        assertFalse(hillside(10.0) { e, _ -> 700 + abs(e) * .4 })
        assertFalse(hillside(10.0) { e, _ -> 700 - abs(e) * .4 })
        // A slope that stops just past the footprint is a bank too.
        assertFalse(hillside(5.0) { _, n -> 700 + n.coerceIn(-8.0, 8.0) * .5 })
        // Missing ground ahead is not evidence of a hillside.
        val offsets = TerrainFootprint.offsets(5.0)
        assertFalse(TerrainFootprint.hillside(offsets, offsets.map { (_, n) -> 700 + n * .4 }) { _, _ -> null })
        assertFalse(TerrainFootprint.hillside(offsets, offsets.map { 700.0 }) { _, _ -> 700.0 })
    }
}
