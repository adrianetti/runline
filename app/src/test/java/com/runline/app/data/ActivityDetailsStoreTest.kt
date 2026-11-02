package com.runline.app.data

import com.runline.app.core.ActivityDetails
import com.runline.app.core.ActivityType
import com.runline.app.core.RoutineType
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ActivityDetailsStoreTest {
    private fun directory() = Files.createTempDirectory("activity-details-test").toFile().apply { deleteOnExit() }
    private val id = "saved-activity"
    private val photo = "11111111-2222-3333-4444-555555555555.jpg"

    @Test fun legacyActivityDefaultsAndMetadataRoundTripsWithoutChangingTheRoute() {
        val root = directory()
        val raw = DiskRunStore(File(root, "runs"))
        val run = RunSession(id, 1_000, 5_000, 4_000, listOf(RunPoint(37.0, -3.0, 2_000, 5f)), ActivityType.RUN)
        raw.save(run)
        val before = File(root, "runs/run-$id.json").readBytes()
        val store = ActivityDetailsStore(File(root, "details"))
        assertEquals(ActivityDetails(), store.get(id))
        val details = ActivityDetails("  Tres vueltas 🏃  ", "  Subida suave.\nBuen día.  ", 6, ActivityType.WALK, RoutineType.EASY)
        store.save(id, details)
        assertEquals(details.validated(), ActivityDetailsStore(File(root, "details")).get(id))
        assertArrayEquals(before, File(root, "runs/run-$id.json").readBytes())
        assertEquals(run, raw.get(id))
    }

    @Test fun unsetActivityTypeIsReadable() {
        val store = ActivityDetailsStore(directory())
        store.save(id, ActivityDetails(title = "Morning park", effort = null, activityType = null))
        assertNull(store.get(id).activityType)
        assertNull(store.get(id).effort)
    }

    @Test fun interruptedSecondCopyCommitsTheNewerBackupAndCanBeEditedAgain() {
        val root = directory()
        ActivityDetailsStore(root).save(id, ActivityDetails(title = "Before"))
        val interrupted = ActivityDetailsStore(root, fault = { if (it == "before_replace:details-$id.json") throw IOException("injected") })
        interrupted.save(id, ActivityDetails(title = "After", effort = 7))
        val reopened = ActivityDetailsStore(root)
        assertEquals("After", reopened.get(id).title)
        assertEquals(7, reopened.get(id).effort)
        reopened.save(id, ActivityDetails(title = "Third"))
        assertEquals("Third", reopened.get(id).title)
    }

    @Test fun failureBeforeFirstReplacementLeavesPreviousDetailsIntact() {
        val root = directory()
        ActivityDetailsStore(root).save(id, ActivityDetails(title = "Keep me"))
        val failing = ActivityDetailsStore(root, fault = { throw IOException("injected") })
        assertThrows(IOException::class.java) { failing.save(id, ActivityDetails(title = "Lost edit")) }
        assertEquals("Keep me", ActivityDetailsStore(root).get(id).title)
    }

    @Test fun corruptCopyFallsBackAndTwoCorruptCopiesRemainUntouched() {
        val root = directory(); val store = ActivityDetailsStore(root)
        store.save(id, ActivityDetails(title = "A lap"))
        val main = File(root, "details-$id.json"); val backup = File(main.path + ".backup")
        main.writeText("broken-main")
        assertEquals("A lap", store.get(id).title)
        backup.writeText("broken-backup")
        assertThrows(IOException::class.java) { store.get(id) }
        assertThrows(IOException::class.java) { store.save(id, ActivityDetails(title = "Do not overwrite")) }
        assertEquals("broken-main", main.readText()); assertEquals("broken-backup", backup.readText())
    }

    @Test fun invalidMetadataAndTraversalCannotWrite() {
        val store = ActivityDetailsStore(directory())
        listOf(ActivityDetails(effort = 0), ActivityDetails(effort = 11), ActivityDetails(title = "x".repeat(121)),
            ActivityDetails(description = "x".repeat(4001)), ActivityDetails(title = "line\nbreak"),
            ActivityDetails(description = "nul\u0000"), ActivityDetails(photoIds = listOf("../secret")),
            ActivityDetails(photoIds = listOf(photo, photo))).forEach {
            assertThrows(IllegalArgumentException::class.java) { store.save(id, it) }
        }
        assertThrows(IllegalArgumentException::class.java) { store.get("../outside") }
        assertThrows(IllegalArgumentException::class.java) { store.photoFile(id, "../outside") }
        assertEquals(ActivityDetails(), store.get(id))
    }

    @Test fun photosMustExistAndRemovedPhotosAreDeletedOnlyAfterCommit() {
        val root = directory(); val store = ActivityDetailsStore(root)
        assertThrows(IllegalArgumentException::class.java) { store.save(id, ActivityDetails(photoIds = listOf(photo))) }
        store.photoDirectory(id).mkdirs(); store.photoFile(id, photo).writeText("private-image")
        store.save(id, ActivityDetails(photoIds = listOf(photo)))
        val failing = ActivityDetailsStore(root, fault = { throw IOException("injected") })
        assertThrows(IOException::class.java) { failing.save(id, ActivityDetails()) }
        assertTrue(store.photoFile(id, photo).exists()); assertEquals(listOf(photo), store.get(id).photoIds)
        store.save(id, ActivityDetails())
        assertFalse(store.photoFile(id, photo).exists()); assertTrue(store.get(id).photoIds.isEmpty())
    }

    @Test fun deleteRemovesAnnotationsAndPhotosWithoutTouchingAnotherActivity() {
        val store = ActivityDetailsStore(directory())
        store.photoDirectory(id).mkdirs(); store.photoFile(id, photo).writeText("private-image")
        store.save(id, ActivityDetails(title = "Remove", photoIds = listOf(photo)))
        store.save("keep", ActivityDetails(title = "Keep"))
        store.delete(id)
        assertEquals(ActivityDetails(), store.get(id)); assertFalse(store.photoFile(id, photo).exists())
        assertEquals("Keep", store.get("keep").title)
    }
}
