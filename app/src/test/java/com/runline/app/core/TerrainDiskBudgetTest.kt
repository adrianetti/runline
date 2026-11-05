package com.runline.app.core

import com.runline.app.terrain.TerrainDiskBudget
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TerrainDiskBudgetTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file(folder: File, name: String, size: Int, time: Long = 1) = File(folder, name).apply { writeBytes(ByteArray(size)); setLastModified(time) }
    @Test fun temporaryFilesCountAndOlderTerrainIsEvictedBeforePinnedArea() {
        val folder = temp.newFolder("terrain")
        val pinned = file(folder, "pinned.png", 50); val old = file(folder, "old.png", 30)
        val partial = file(folder, "tile.png.new", 30, 2)
        assertEquals(110, TerrainDiskBudget.used(folder))
        TerrainDiskBudget.reserve(folder, 40, "incoming.png", setOf(pinned.name), 100, 10_000_000)
        assertTrue(pinned.exists()); assertFalse(old.exists()); assertFalse(partial.exists())
        assertTrue(TerrainDiskBudget.used(folder) + 40 <= 100)
    }
    @Test fun pinnedAreaCannotDefeatBudgetAndSiblingRecordingsAreUntouched() {
        val folder = temp.newFolder("terrain"); val run = file(temp.root, "recording.gpx", 200)
        val a = file(folder, "a.png", 60); val b = file(folder, "b.png", 30, 2)
        TerrainDiskBudget.reserve(folder, 50, "next.png", setOf(a.name, b.name), 100, 10_000_000)
        assertFalse(a.exists()); assertTrue(b.exists()); assertEquals(200, run.length())
    }
    @Test fun insufficientFilesystemSpaceFailsBeforeDeletingUsableTerrain() {
        val folder = temp.newFolder("terrain"); val cached = file(folder, "keep.png", 90)
        try { TerrainDiskBudget.reserve(folder, 20, "next.png", emptySet(), 100, 1); fail() }
        catch (_: IllegalArgumentException) { }
        assertTrue(cached.exists())
    }
    @Test fun currentFileIsPreservedForAtomicReplacementAndNeverOvercommits() {
        val folder = temp.newFolder("terrain"); val current = file(folder, "current.png", 80)
        try { TerrainDiskBudget.reserve(folder, 30, current.name, emptySet(), 100, 10_000_000); fail() }
        catch (_: IllegalArgumentException) { }
        assertTrue(current.exists()); assertEquals(80, TerrainDiskBudget.used(folder))
    }
}
