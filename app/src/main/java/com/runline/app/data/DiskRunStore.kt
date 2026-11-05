package com.runline.app.data

import com.runline.app.core.ActivityType
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.CRC32

/** Everything needed for recovery; the timer is a checkpoint, never inferred from time spent dead. */
data class DraftRecord(
    val run: RunSession,
    val wantsRecording: Boolean = false,
    val bootCount: Int = -1,
    val checkpointElapsedMs: Long = 0,
    val revision: Long = 0,
    val recoveredWithDamage: Boolean = false,
) {
    fun mayRestart(currentBootCount: Int, nowElapsedMs: Long): Boolean =
        wantsRecording && bootCount >= 0 && bootCount == currentBootCount &&
            checkpointElapsedMs > 0 && nowElapsedMs >= checkpointElapsedMs
}

/**
 * A deliberately small write-ahead store. A point is acknowledged only after fd.sync().
 * Session identity exists in two independent places; each checkpoint has two CRC-protected
 * copies. Journal entries include timer checkpoints, so losing both mutable headers does
 * not strand the recorded route. Original damaged bytes are retained before tail repair.
 *
 * The directory sync function is supplied by Android; tests run against real temporary files.
 */
class DiskRunStore(
    private val directory: File,
    private val syncDirectory: (File) -> Unit = {},
    private val fault: (String) -> Unit = {},
) {
    private val header = File(directory, "draft.json")
    private val mirror = File(directory, "draft-checkpoint.json")
    private val seed = File(directory, "draft-seed.json")
    private val journal = File(directory, "draft-points.jsonl")
    private val resolution = File(directory, "draft-resolved.json")
    private var revision = 0L
    init { if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create run storage.") }

    fun list(): List<RunSession> = directory.listFiles().orEmpty()
        .filter { it.name.startsWith("run-") && (it.name.endsWith(".json") || it.name.endsWith(".json.backup") || it.name.endsWith(".json.bak")) }
        .map { it.name.removePrefix("run-").removeSuffix(".backup").removeSuffix(".bak").removeSuffix(".json") }.distinct()
        .mapNotNull { id -> runCatching { get(id) }.getOrNull() }.sortedByDescending { it.startedAtMs }

    fun get(id: String): RunSession? {
        val main = runFile(id)
        val candidates = listOf(main, File(main.path + ".backup"), File(main.path + ".bak"))
        if (candidates.none { it.exists() }) return null
        return candidates.firstNotNullOfOrNull { file ->
            runCatching { decodeRun(unseal(file.readText())).also { require(it.id == id) } }.getOrNull()
        } ?: throw IOException("This run could not be read. Its files have been kept.")
    }

    fun save(run: RunSession) {
        val text = seal(encodeRun(run))
        // Two independent durable copies, each self-checking. No cloud or external backup.
        atomicWrite(File(runFile(run.id).path + ".backup"), text)
        atomicWrite(runFile(run.id), text)
    }

    fun complete(run: RunSession) {
        try { save(run) } catch (error: Exception) {
            // The first completed copy is a commit. Never report "retry/resume" after it
            // exists: otherwise later points could be discarded against that older copy.
            val exactCopyExists = listOf(runFile(run.id), File(runFile(run.id).path + ".backup")).any {
                runCatching { decodeRun(unseal(it.readText())) == run }.getOrDefault(false)
            }
            if (!exactCopyExists) throw error
            runCatching { resolveDraft(run.id, "completed") }
            return // Keep the journal as an extra copy when the second write failed.
        }
        runCatching { resolveDraft(run.id, "completed"); clearDraft() }
    }

    fun delete(id: String) {
        // Clear a matching stale draft BEFORE removing the completed copies that prove
        // it was saved. If cleanup fails, deletion fails visibly instead of resurrecting it.
        val related = listOf(header, mirror, seed, File(header.path + ".bak")).any {
            runCatching { decodeRecord(unseal(it.readText())).run.id == id }.getOrDefault(false)
        } || runCatching {
            val first = journal.bufferedReader().use { it.readLine() }
            unseal(first).optJSONObject("record")?.optString("id") == id
        }.getOrDefault(false)
        if (related) { resolveDraft(id, "deleted"); clearDraft() }
        val main = runFile(id)
        listOf(main, File(main.path + ".backup"), File(main.path + ".bak"), File(main.path + ".new"), File(main.path + ".backup.new")).forEach(::checkedDelete)
        syncDirectory(directory)
    }

    fun loadDraft(): RunSession? = loadDraftRecord()?.run

    fun loadDraftRecord(): DraftRecord? {
        if (runCatching { unseal(resolution.readText()).getString("state") in setOf("completed", "discarded", "deleted", "archived") }.getOrDefault(false)) {
            clearDraft()
            return null
        }
        val candidates = listOf(header, mirror, seed, File(header.path + ".bak"))
        val headers = candidates.mapNotNull { file -> runCatching { decodeRecord(unseal(file.readText())) }.getOrNull() }
        val lines = if (journal.exists()) journal.readLines() else emptyList()
        val journalSeed = lines.firstOrNull()?.let { line -> runCatching {
            val entry = unseal(line)
            if (entry.optString("kind") == "seed") decodeRecord(entry.getJSONObject("record")) else null
        }.getOrNull() }
        val metadata = headers + listOfNotNull(journalSeed)
        if (metadata.isEmpty()) {
            if (activeFiles().any { it.exists() && it.length() > 0 }) throw IOException("The unfinished run has no readable metadata.")
            return null
        }
        val chosen = metadata.maxWithOrNull(compareBy<DraftRecord> { it.revision }.thenBy { it.run.activeDurationMs })!!
        val id = chosen.run.id
        // Saving the completed file committed this session even when draft cleanup was interrupted.
        if (runCatching { get(id) }.getOrNull() != null) {
            clearDraft()
            return null
        }
        var latest = chosen
        val hasTornTail = journal.exists() && journal.length() > 0 && RandomAccessFile(journal, "r").use { file -> file.seek(file.length() - 1); file.read() != 10 }
        var damaged = hasTornTail || (header.exists() && runCatching { unseal(header.readText()) }.isFailure)
        var segmentOffset = 0
        var lastSegment = -1
        val points = mutableListOf<RunPoint>()
        for (line in lines) {
            try {
                val entry = unseal(line)
                when (entry.optString("kind")) {
                    "seed" -> Unit
                    "point" -> {
                        require(entry.getString("runId") == id)
                        val p = decodePoint(entry.getJSONObject("point"))
                        val corrected = p.copy(segment = maxOf(p.segment + segmentOffset, lastSegment))
                        points.add(corrected); lastSegment = corrected.segment
                        val checkpoint = decodeRecord(entry.getJSONObject("record"))
                        require(checkpoint.run.id == id)
                        if (checkpoint.revision > latest.revision) latest = checkpoint
                    }
                    else -> { // Read the original prototype's plain JSON point journal.
                        val p = decodePoint(entry)
                        val corrected = p.copy(segment = maxOf(p.segment + segmentOffset, lastSegment))
                        points.add(corrected); lastSegment = corrected.segment
                    }
                }
            } catch (_: Exception) {
                damaged = true
                // Never draw or measure a line across a missing/corrupt record.
                segmentOffset = maxOf(segmentOffset + 1, lastSegment + 1)
            }
        }
        revision = maxOf(revision, latest.revision)
        return latest.copy(run = latest.run.copy(points = points), recoveredWithDamage = damaged)
    }

    fun beginDraft(run: RunSession) {
        check(loadDraftRecord() == null) { "Finish the existing run first." }
        check(activeFiles().none { it.exists() && it.length() > 0 }) { "Previous recovery files still need attention." }
        revision = 0
        val record = DraftRecord(run.copy(points = emptyList()), revision = ++revision)
        // The immutable seed reaches disk before any route data can be recorded.
        atomicWrite(seed, seal(encodeRecord(record)))
        FileOutputStream(journal, false).use { stream ->
            stream.write((seal(JSONObject().put("kind", "seed").put("record", encodeRecord(record))) + "\n").toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        syncDirectory(directory)
        checkpoint(record)
    }

    fun checkpoint(record: DraftRecord) {
        val updated = record.copy(run = record.run.copy(points = emptyList()), revision = ++revision)
        if (!seed.exists()) atomicWrite(seed, seal(encodeRecord(updated))) // Legacy draft upgrade.
        val text = seal(encodeRecord(updated))
        atomicWrite(mirror, text)
        atomicWrite(header, text)
    }

    fun checkpoint(run: RunSession) = checkpoint(DraftRecord(run))

    fun appendPoint(point: RunPoint, record: DraftRecord) {
        repairTailIfNeeded()
        val updated = record.copy(run = record.run.copy(points = emptyList()), revision = ++revision)
        val entry = JSONObject().put("kind", "point").put("runId", record.run.id)
            .put("point", encodePoint(point)).put("record", encodeRecord(updated))
        FileOutputStream(journal, true).use { output ->
            output.write((seal(entry) + "\n").toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        fault("point_synced")
    }

    fun appendPoint(point: RunPoint) {
        val current = loadDraftRecord() ?: throw IOException("No unfinished run.")
        appendPoint(point, current)
    }

    fun discardDraft() {
        val id = loadDraftRecord()?.run?.id ?: return
        try { resolveDraft(id, "discarded") } catch (error: Exception) {
            val committed = runCatching {
                val marker = unseal(resolution.readText())
                marker.getString("id") == id && marker.getString("state") == "discarded"
            }.getOrDefault(false)
            if (!committed) throw error
        }
        // Discard is committed. Never leave Resume available merely because cleanup failed.
        runCatching { clearDraft() }
    }

    private fun resolveDraft(id: String, state: String) = atomicWrite(resolution, seal(JSONObject().put("id", id).put("state", state)))

    fun clearDraft() {
        // A failure leaves readable seed/checkpoint copies for another attempt.
        checkedDelete(journal)
        listOf(header, mirror, File(header.path + ".bak"), File(header.path + ".new"), File(mirror.path + ".new"), File(seed.path + ".new"), seed).forEach(::checkedDelete)
        // Make removal of the old data durable before removing its resolution proof.
        syncDirectory(directory)
        checkedDelete(File(resolution.path + ".new")); checkedDelete(resolution)
        syncDirectory(directory)
    }

    fun archiveDraft() {
        archiveFiles(activeFiles().filter { it.exists() })
        resolveDraft("recovery", "archived")
        clearDraft()
    }

    private fun repairTailIfNeeded() {
        if (!journal.exists() || journal.length() == 0L) return
        RandomAccessFile(journal, "rw").use { file ->
            file.seek(file.length() - 1)
            if (file.read() == 10) return
            archiveFiles(listOf(journal)) // Retain original bytes before either kind of repair.
            val tail = journal.readText().substringAfterLast('\n')
            val complete = runCatching {
                val value = unseal(tail)
                when (value.optString("kind")) {
                    "seed" -> decodeRecord(value.getJSONObject("record"))
                    "point" -> { decodePoint(value.getJSONObject("point")); decodeRecord(value.getJSONObject("record")) }
                    else -> decodePoint(value)
                }
            }.isSuccess
            if (complete) {
                // A valid checked record that lost only its terminator is recoverable.
                file.seek(file.length()); file.write(10); file.fd.sync(); return
            }
            var end = file.length()
            while (end > 0) { file.seek(--end); if (file.read() == 10) { end++; break } }
            file.setLength(end); file.fd.sync()
        }
    }

    private fun archiveFiles(sources: List<File>) {
        if (sources.isEmpty()) return
        val archive = File(directory, "recovery/draft-${System.currentTimeMillis()}-${UUID.randomUUID()}")
        if (!archive.mkdirs()) throw IOException("Could not create recovery storage.")
        sources.forEach { source ->
            if (!source.isFile) throw IOException("Invalid recovery file.")
            FileOutputStream(File(archive, source.name)).use { output ->
                val count = source.inputStream().use { it.copyTo(output) }
                if (count != source.length()) throw IOException("Incomplete recovery copy.")
                output.fd.sync()
            }
        }
        syncDirectory(archive); syncDirectory(archive.parentFile!!); syncDirectory(directory)
    }

    private fun activeFiles() = listOf(header, mirror, seed, journal, File(header.path + ".bak"),
        File(header.path + ".new"), File(mirror.path + ".new"), File(seed.path + ".new"), resolution, File(resolution.path + ".new"))

    private fun atomicWrite(file: File, content: String) {
        val temporary = File(file.path + ".new")
        FileOutputStream(temporary).use { stream -> stream.write(content.toByteArray(Charsets.UTF_8)); stream.fd.sync() }
        fault("before_replace:${file.name}")
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(directory)
    }

    private fun checkedDelete(file: File) {
        fault("before_delete:${file.name}")
        if (file.exists() && !file.delete()) throw IOException("Could not clear ${file.name}.")
    }

    private fun runFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid run identifier." }
        return File(directory, "run-$id.json")
    }

    private fun seal(value: JSONObject): String {
        val payload = value.toString()
        val crc = CRC32().apply { update(payload.toByteArray(Charsets.UTF_8)) }.value
        return JSONObject().put("payload", payload).put("crc32", crc).toString()
    }

    private fun unseal(text: String): JSONObject {
        val objectValue = JSONObject(text)
        if (!objectValue.has("payload")) return objectValue // Version-one compatibility.
        val payload = objectValue.getString("payload")
        val crc = CRC32().apply { update(payload.toByteArray(Charsets.UTF_8)) }.value
        if (crc != objectValue.getLong("crc32")) throw IOException("Recording checksum mismatch.")
        return JSONObject(payload)
    }

    private fun encodeRecord(record: DraftRecord) = encodeRun(record.run).apply {
        put("wantsRecording", record.wantsRecording); put("bootCount", record.bootCount)
        put("checkpointElapsedMs", record.checkpointElapsedMs); put("revision", record.revision)
    }
    private fun decodeRecord(value: JSONObject) = DraftRecord(decodeRun(value), value.optBoolean("wantsRecording", false),
        value.optInt("bootCount", -1), value.optLong("checkpointElapsedMs", 0), value.optLong("revision", 0))
    private fun encodeRun(run: RunSession) = JSONObject().apply {
        put("version", 2); put("id", run.id); put("startedAtMs", run.startedAtMs); put("endedAtMs", run.endedAtMs)
        put("activeDurationMs", run.activeDurationMs); put("activityType", run.activityType.storageValue); put("points", JSONArray().apply { run.points.forEach { put(encodePoint(it)) } })
    }
    private fun decodeRun(value: JSONObject): RunSession = RunSession(value.getString("id"), value.getLong("startedAtMs"),
        value.getLong("endedAtMs"), value.getLong("activeDurationMs"), value.getJSONArray("points").let { a -> (0 until a.length()).map { decodePoint(a.getJSONObject(it)) } },
        ActivityType.fromStorage(value.optString("activityType", null)))
        .also { require(it.id.matches(Regex("[A-Za-z0-9_-]+")) && it.activeDurationMs >= 0 && it.startedAtMs >= 0) }
    private fun encodePoint(p: RunPoint) = JSONObject().apply {
        put("latitude", p.latitude); put("longitude", p.longitude); put("timestampMs", p.timestampMs)
        put("accuracyMeters", p.accuracyMeters.toDouble()); put("altitudeMeters", p.altitudeMeters ?: JSONObject.NULL); put("segment", p.segment)
        put("verticalAccuracyMeters", p.verticalAccuracyMeters?.toDouble() ?: JSONObject.NULL)
    }
    private fun decodePoint(o: JSONObject): RunPoint = RunPoint(o.getDouble("latitude"), o.getDouble("longitude"),
        o.getLong("timestampMs"), o.getDouble("accuracyMeters").toFloat(), if (o.isNull("altitudeMeters")) null else o.getDouble("altitudeMeters"), o.getInt("segment"),
        o.optDouble("verticalAccuracyMeters", Double.NaN).takeIf { it.isFinite() && it >= 0 }?.toFloat())
        .also { require(it.latitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude.isFinite() && it.longitude in -180.0..180.0 && it.timestampMs >= 0 && it.accuracyMeters.isFinite() && it.accuracyMeters >= 0 && it.segment >= 0) }
}
