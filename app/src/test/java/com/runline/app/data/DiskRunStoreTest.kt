package com.runline.app.data

import com.runline.app.core.ActivityType
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.CRC32

class DiskRunStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val base = RunSession("test-run", 1_000_000, 1_000_000, 0, emptyList())
    private fun point(i: Int, segment: Int = 0) = RunPoint(37.0 + i * 0.0001, -3.6, 1_000_000L + i * 1000, 5f, segment = segment)
    private fun record(ms: Long, points: List<RunPoint> = emptyList()) = DraftRecord(base.copy(endedAtMs = base.startedAtMs + ms, activeDurationMs = ms, points = points), true, 7, 100_000 + ms)

    @Test fun initialSessionSurvivesBeforeAnyGpsFixOrTimerCheckpoint() {
        val dir = temporary.newFolder(); DiskRunStore(dir).beginDraft(base)
        val recovered = DiskRunStore(dir).loadDraftRecord()!!
        assertEquals(base, recovered.run); assertFalse(recovered.wantsRecording)
    }

    @Test fun journalRecoversIdentityPointsAndTimerWhenBothMutableHeadersAreCorrupt() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base)
        store.appendPoint(point(1), record(1000)); store.appendPoint(point(2), record(2000))
        File(dir, "draft.json").writeText("{broken"); File(dir, "draft-checkpoint.json").writeText("{broken")
        val recovered = DiskRunStore(dir).loadDraftRecord()!!
        assertEquals(listOf(point(1), point(2)), recovered.run.points)
        assertEquals(2000L, recovered.run.activeDurationMs); assertEquals("test-run", recovered.run.id)
        assertTrue(recovered.recoveredWithDamage)
    }

    @Test fun journalSeedRecoversRunEvenIfEverySeparateMetadataFileIsLost() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base)
        store.appendPoint(point(1), record(1000))
        listOf("draft.json", "draft-checkpoint.json", "draft-seed.json").forEach { File(dir, it).writeText("corrupt") }
        val recovered = DiskRunStore(dir).loadDraftRecord()!!
        assertEquals(base.id, recovered.run.id); assertEquals(listOf(point(1)), recovered.run.points)
    }

    @Test fun checkpointInterruptionUsesAlreadySyncedMirror() {
        val dir = temporary.newFolder(); DiskRunStore(dir).beginDraft(base)
        val failing = DiskRunStore(dir, fault = { if (it == "before_replace:draft.json") throw IOException("power cut") })
        failing.loadDraftRecord()
        assertThrows(IOException::class.java) { failing.checkpoint(record(4321)) }
        assertEquals(4321L, DiskRunStore(dir).loadDraftRecord()!!.run.activeDurationMs)
    }

    @Test fun tornAppendKeepsEarlierPointsAndOriginalBytesBeforeRepair() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base)
        store.appendPoint(point(1), record(1000))
        File(dir, "draft-points.jsonl").appendText("{partial-point")
        val reopened = DiskRunStore(dir); assertEquals(listOf(point(1)), reopened.loadDraftRecord()!!.run.points)
        reopened.appendPoint(point(2, 1), record(2000))
        assertEquals(listOf(point(1), point(2, 1)), DiskRunStore(dir).loadDraftRecord()!!.run.points)
        val preserved = File(dir, "recovery").walkTopDown().filter { it.name == "draft-points.jsonl" }.toList()
        assertEquals(1, preserved.size); assertTrue(preserved.single().readText().endsWith("{partial-point"))
    }

    @Test fun completeLastRecordWithoutNewlineRemainsAfterResumeAndAnotherRestart() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base)
        store.appendPoint(point(1), record(1000)); store.appendPoint(point(2), record(2000))
        val journal = File(dir, "draft-points.jsonl"); journal.writeText(journal.readText().dropLast(1))
        val reopened = DiskRunStore(dir)
        assertEquals(listOf(point(1), point(2)), reopened.loadDraftRecord()!!.run.points)
        reopened.appendPoint(point(3, 1), record(3000))
        assertEquals(listOf(point(1), point(2), point(3, 1)), DiskRunStore(dir).loadDraftRecord()!!.run.points)
    }

    @Test fun checksumDamageSkipsOnlyTheDamagedRecordAndBreaksRouteContinuity() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base)
        for (i in 1..3) store.appendPoint(point(i), record(i * 1000L))
        val file = File(dir, "draft-points.jsonl"); val lines = file.readLines().toMutableList()
        val envelope = JSONObject(lines[2]); envelope.put("crc32", envelope.getLong("crc32") + 1); lines[2] = envelope.toString()
        file.writeText(lines.joinToString("\n", postfix = "\n"))
        val recovered = DiskRunStore(dir).loadDraftRecord()!!
        assertEquals(listOf(point(1).latitude, point(3).latitude), recovered.run.points.map { it.latitude })
        assertNotEquals(recovered.run.points[0].segment, recovered.run.points[1].segment)
        assertTrue(recovered.recoveredWithDamage)
    }

    @Test fun firstCompletedCopyCommitsEvenIfTheSecondWriteFails() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base)
        store.appendPoint(point(1), record(1000))
        val finished = record(1000, listOf(point(1))).run
        val failing = DiskRunStore(dir, fault = { if (it == "before_replace:run-test-run.json") throw IOException("power cut") })
        failing.complete(finished) // Must not report failure and offer Resume after a committed copy exists.
        assertEquals(finished, DiskRunStore(dir).get(base.id))
        assertNull(DiskRunStore(dir).loadDraftRecord())
    }

    @Test fun staleDraftCannotResurrectAfterCompletedHistoryIsDeleted() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base)
        store.appendPoint(point(1), record(1000))
        val failingCleanup = DiskRunStore(dir, fault = { if (it == "before_delete:draft-points.jsonl") throw IOException("cleanup failed") })
        failingCleanup.complete(record(1000, listOf(point(1))).run)
        val normal = DiskRunStore(dir); normal.delete(base.id)
        assertNull(DiskRunStore(dir).get(base.id)); assertNull(DiskRunStore(dir).loadDraftRecord())
    }

    @Test fun interruptedDiscardDoesNotResurrectRun() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base)
        store.appendPoint(point(1), record(1000))
        val failingCleanup = DiskRunStore(dir, fault = { if (it == "before_delete:draft-points.jsonl") throw IOException("power cut") })
        failingCleanup.discardDraft() // A committed discard must not offer Resume after cleanup failure.
        assertNull(DiskRunStore(dir).loadDraftRecord())
        DiskRunStore(dir).beginDraft(base.copy(id = "next-run"))
        assertEquals("next-run", DiskRunStore(dir).loadDraftRecord()!!.run.id)
    }

    @Test fun resolvedMarkerOutlivesDurableDeletionOfDraftData() {
        val dir = temporary.newFolder(); val syncedStates = mutableListOf<Set<String>>()
        val store = DiskRunStore(dir, syncDirectory = { syncedStates.add(it.list().orEmpty().toSet()) })
        store.beginDraft(base); store.appendPoint(point(1), record(1000)); syncedStates.clear()
        store.discardDraft()
        val protectedDeletion = syncedStates.indexOfFirst { names ->
            "draft-resolved.json" in names && "draft.json" !in names && "draft-seed.json" !in names && "draft-points.jsonl" !in names
        }
        assertTrue(protectedDeletion >= 0)
        assertTrue(syncedStates.drop(protectedDeletion + 1).any { "draft-resolved.json" !in it })
    }

    @Test fun corruptedCompletedFileFallsBackToIndependentCopy() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); val finished = record(1000, listOf(point(1))).run
        store.save(finished); File(dir, "run-test-run.json").writeText("broken")
        assertEquals(finished, DiskRunStore(dir).get(base.id)); assertEquals(listOf(finished), DiskRunStore(dir).list())
    }

    @Test fun ordinaryRestartNeverAddsTimeAndOnlySameBootCanAutoResume() {
        val dir = temporary.newFolder(); val store = DiskRunStore(dir); store.beginDraft(base); store.checkpoint(record(5000))
        val recovered = DiskRunStore(dir).loadDraftRecord()!!
        assertEquals(5000L, recovered.run.activeDurationMs)
        assertTrue(recovered.mayRestart(7, 900_000)); assertFalse(recovered.mayRestart(8, 900_000))
        assertFalse(recovered.mayRestart(7, 50)); assertFalse(recovered.copy(wantsRecording = false).mayRestart(7, 900_000))
        assertFalse(recovered.copy(bootCount = -1).mayRestart(-1, 900_000))
    }

    @Test fun legacyDraftRemainsReadableWithoutGuessingItsTimer() {
        val dir = temporary.newFolder()
        File(dir, "draft.json").writeText("""{"version":1,"id":"legacy","startedAtMs":1000000,"endedAtMs":1001000,"activeDurationMs":1000,"points":[]}""")
        File(dir, "draft-points.jsonl").writeText("""{"latitude":37.0,"longitude":-3.6,"timestampMs":1001000,"accuracyMeters":5,"altitudeMeters":null,"segment":0}""" + "\n")
        val recovered = DiskRunStore(dir).loadDraftRecord()!!
        assertEquals("legacy", recovered.run.id); assertEquals(1, recovered.run.points.size)
        assertEquals(1000L, recovered.run.activeDurationMs); assertFalse(recovered.wantsRecording)
    }

    @Test fun legacyAndUnknownActivityTypesPreserveTheEntireSavedRecording() {
        val expected = base.copy(endedAtMs = 8_200_000, activeDurationMs = 7_200_000, points = listOf(point(1), point(2)))
        for (storedType in listOf<String?>(null, "future-sport")) {
            val dir = temporary.newFolder()
            DiskRunStore(dir).save(expected)
            for (suffix in listOf("", ".backup")) {
                val file = File(dir, "run-test-run.json" + suffix)
                val value = JSONObject(JSONObject(file.readText()).getString("payload"))
                if (storedType == null) value.remove("activityType") else value.put("activityType", storedType)
                file.writeText(sealForTest(value))
            }
            assertEquals(expected, DiskRunStore(dir).get(base.id))
            assertEquals(ActivityType.RUN, DiskRunStore(dir).list().single().activityType)
        }
    }

    @Test fun selectedTypeSurvivesSeedJournalRecoveryAndFinalBackup() {
        for (activityType in ActivityType.entries) {
            val dir = temporary.newFolder()
            val selected = base.copy(activityType = activityType)
            val store = DiskRunStore(dir)
            store.beginDraft(selected)
            assertEquals(activityType, DiskRunStore(dir).loadDraftRecord()!!.run.activityType)
            store.appendPoint(point(1), DraftRecord(selected.copy(activeDurationMs = 1000, endedAtMs = 1_001_000), true, 7, 101_000))
            listOf("draft.json", "draft-checkpoint.json", "draft-seed.json").forEach { File(dir, it).writeText("corrupt") }
            val reopened = DiskRunStore(dir)
            val recovered = reopened.loadDraftRecord()!!
            assertEquals(activityType, recovered.run.activityType)
            assertEquals(listOf(point(1)), recovered.run.points)
            assertEquals(1000L, recovered.run.activeDurationMs)
            reopened.complete(recovered.run)
            File(dir, "run-test-run.json").writeText("corrupt")
            assertEquals(recovered.run, DiskRunStore(dir).get(selected.id))
            assertNull(DiskRunStore(dir).loadDraftRecord())
        }
    }

    @Test fun activityTypePersistsInTimerOnlyCheckpointsBeforeGpsArrives() {
        val dir = temporary.newFolder()
        val selected = base.copy(activityType = ActivityType.WALK)
        val store = DiskRunStore(dir)
        store.beginDraft(selected)
        store.checkpoint(DraftRecord(selected.copy(activeDurationMs = 5000, endedAtMs = 1_005_000), true, 7, 105_000))
        File(dir, "draft.json").writeText("corrupt")
        val recovered = DiskRunStore(dir).loadDraftRecord()!!
        assertEquals(ActivityType.WALK, recovered.run.activityType)
        assertEquals(5000L, recovered.run.activeDurationMs)
        assertTrue(recovered.mayRestart(7, 106_000))
    }

    private fun sealForTest(value: JSONObject): String {
        val payload = value.toString()
        return JSONObject().put("payload", payload).put("crc32", CRC32().apply { update(payload.toByteArray(Charsets.UTF_8)) }.value).toString()
    }

}
