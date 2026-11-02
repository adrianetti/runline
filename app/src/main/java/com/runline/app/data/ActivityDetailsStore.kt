package com.runline.app.data

import com.runline.app.core.ActivityDetails
import com.runline.app.core.ActivityType
import com.runline.app.core.RoutineType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32

/** Two revisioned, checksummed sidecars. Editing cannot replace a recorded route or draft. */
class ActivityDetailsStore(
    private val directory: File,
    private val syncDirectory: (File) -> Unit = {},
    private val fault: (String) -> Unit = {},
) {
    private data class Record(val revision: Long, val details: ActivityDetails)
    init { check(directory.isDirectory || directory.mkdirs()) { "Could not open activity details." } }

    @Synchronized fun get(id: String): ActivityDetails = read(id)?.details ?: ActivityDetails()

    @Synchronized fun save(id: String, details: ActivityDetails) {
        val value = details.validated()
        val existing = read(id)
        require(value.photoIds.all { photoFile(id, it).isFile }) { "An activity photo is missing." }
        val next = Record((existing?.revision ?: 0) + 1, value)
        val payload = encode(id, next)
        try {
            atomicWrite(File(sidecar(id).path + ".backup"), payload)
            atomicWrite(sidecar(id), payload)
        } catch (error: Exception) {
            // One durable copy is enough to commit; read() chooses its newer revision.
            if (runCatching { read(id) == next }.getOrDefault(false)) return
            throw error
        }
        // Keep removed images until both new copies are committed. If interrupted, an old
        // fallback document still has its images; unreferenced files are harmless.
        existing?.details?.photoIds?.filterNot { it in value.photoIds }?.forEach {
            runCatching { photoFile(id, it).delete() }
        }
    }

    @Synchronized fun delete(id: String) {
        val main = sidecar(id)
        listOf(main, File(main.path + ".backup"), File(main.path + ".new"), File(main.path + ".backup.new")).forEach {
            if (it.exists() && !it.delete()) throw IOException("Could not delete activity details.")
        }
        val folder = photoDirectory(id)
        if (folder.exists() && !folder.deleteRecursively()) throw IOException("Could not delete activity photos.")
        syncDirectory(directory)
    }

    fun photoDirectory(id: String): File { validateId(id); return File(directory, "photos-$id") }
    fun photoFile(id: String, photoId: String): File {
        require(ActivityDetails.PHOTO_ID.matches(photoId)) { "Invalid photo identifier." }
        return File(photoDirectory(id), photoId)
    }

    private fun read(id: String): Record? {
        val main = sidecar(id)
        val copies = listOf(main, File(main.path + ".backup"))
        if (copies.none(File::exists)) return null
        return copies.mapNotNull { file -> runCatching {
            require(file.length() in 1..65_536); decode(id, file.readText())
        }.getOrNull() }.maxByOrNull { it.revision }
            ?: throw IOException("Activity details could not be read. Their files were kept.")
    }
    private fun sidecar(id: String): File { validateId(id); return File(directory, "details-$id.json") }
    private fun validateId(id: String) { require(id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid activity identifier." } }
    private fun atomicWrite(file: File, value: String) {
        val temporary = File(file.path + ".new")
        FileOutputStream(temporary).use { it.write(value.toByteArray(Charsets.UTF_8)); it.fd.sync() }
        fault("before_replace:${file.name}")
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(directory)
    }
    private fun encode(id: String, record: Record): String {
        val details = record.details
        val payload = JSONObject().put("version", 1).put("id", id).put("revision", record.revision)
            .put("title", details.title).put("description", details.description)
            .put("effort", details.effort ?: JSONObject.NULL)
            .put("activityType", details.activityType?.storageValue ?: JSONObject.NULL)
            .put("routine", details.routine.storageValue).put("photoIds", JSONArray(details.photoIds)).toString()
        return JSONObject().put("payload", payload).put("crc32", checksum(payload)).toString()
    }
    private fun decode(id: String, text: String): Record {
        val envelope = JSONObject(text); val payload = envelope.getString("payload")
        require(checksum(payload) == envelope.getLong("crc32"))
        val json = JSONObject(payload)
        require(json.getInt("version") == 1 && json.getString("id") == id && json.getLong("revision") > 0)
        val photos = json.getJSONArray("photoIds")
        val type = if (json.isNull("activityType")) null else {
            ActivityType.entries.firstOrNull { it.storageValue == json.getString("activityType") }
                ?: throw IOException("Unknown activity type.")
        }
        val details = ActivityDetails(json.getString("title"), json.getString("description"),
            if (json.isNull("effort")) null else json.getInt("effort"), type,
            RoutineType.fromStorage(json.getString("routine")), List(photos.length()) { photos.getString(it) }).validated()
        return Record(json.getLong("revision"), details)
    }
    private fun checksum(text: String) = CRC32().apply { update(text.toByteArray(Charsets.UTF_8)) }.value
}
