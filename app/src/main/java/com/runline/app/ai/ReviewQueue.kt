package com.runline.app.ai

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Persistent one-attempt automatic queue. An interrupted HTTP attempt is never silently repeated. */
data class ReviewJob(val runId: String, val dueAt: Long, val state: String = "pending", val error: String? = null)
class ReviewQueue(private val folder: File) {
    init { folder.mkdirs() }
    private fun file(id: String): File { require(id.matches(Regex("[a-zA-Z0-9_-]{1,150}"))); return File(folder, "$id.json") }
    fun get(id: String): ReviewJob? = synchronized(lock) { runCatching { JSONObject(file(id).readText()).let {
        ReviewJob(id, it.getLong("due"), it.getString("state"), it.optString("error").takeIf(String::isNotEmpty))
    } }.getOrNull() }
    fun all(): List<ReviewJob> = synchronized(lock) { folder.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { get(it.nameWithoutExtension) } }
    fun enqueue(id: String, now: Long): Boolean = synchronized(lock) {
        if (get(id) != null) false else { save(ReviewJob(id, now + 90_000)); true }
    }
    fun claim(id: String, now: Long): Boolean = synchronized(lock) {
        val job = get(id)
        if (job?.state != "pending" || job.dueAt > now) false else { save(job.copy(state = "running")); true }
    }
    fun finish(id: String, error: String? = null) = synchronized(lock) {
        get(id)?.let { save(it.copy(state = if (error == null) "done" else "failed", error = error)) }
    }
    fun delete(id: String) = synchronized(lock) { file(id).delete(); Unit }
    private fun save(job: ReviewJob) {
        val target = file(job.runId); val temp = File(folder, "${job.runId}.tmp")
        val value = JSONObject().put("due", job.dueAt).put("state", job.state).put("error", job.error ?: "").toString()
        FileOutputStream(temp).use { it.write(value.toByteArray()); it.fd.sync() }
        check(temp.renameTo(target))
    }
    companion object { private val lock = Any() }
}
