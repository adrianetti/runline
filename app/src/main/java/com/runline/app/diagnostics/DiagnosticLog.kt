package com.runline.app.diagnostics

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Bounded append-only JSONL. A torn final line is ignored; diagnostics never own a recording. */
class DiagnosticLog(private val folder: File) {
    init { folder.mkdirs() }
    private fun file(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,150}")))
        return File(folder, "$id.jsonl")
    }
    fun append(id: String, event: JSONObject) = synchronized(lock) {
        val bytes = (event.toString() + "\n").toByteArray(Charsets.UTF_8)
        require(bytes.size <= 8_192)
        val target = file(id)
        if (target.length() > 512_000) {
            val kept = read(id).takeLast(250).joinToString("\n", postfix = "\n") { it.toString() }
            val temp = File(folder, "$id.tmp")
            FileOutputStream(temp).use { it.write(kept.toByteArray()); it.fd.sync() }
            check(temp.renameTo(target));
        }
        // Separate a previously torn line from the new event.
        FileOutputStream(target, true).use { it.write('\n'.code); it.write(bytes); it.fd.sync() }
        folder.listFiles { f -> f.extension == "jsonl" && f.name != "usage.jsonl" }.orEmpty()
            .sortedByDescending { it.lastModified() }.drop(100).forEach { it.delete() }
    }
    fun read(id: String): List<JSONObject> = synchronized(lock) {
        val target = file(id)
        if (!target.isFile) emptyList() else target.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.take(4_000).mapNotNull { line -> if (line.length > 8_192) null else runCatching { JSONObject(line) }.getOrNull() }.toList()
        }
    }
    fun ids(): List<String> = synchronized(lock) {
        folder.listFiles { f -> f.extension == "jsonl" && f.name != "usage.jsonl" }.orEmpty()
            .sortedByDescending { it.lastModified() }.map { it.nameWithoutExtension }
    }
    fun delete(id: String) = synchronized(lock) { file(id).delete(); Unit }
    fun clear() = synchronized(lock) { folder.listFiles().orEmpty().filter { it.extension in setOf("jsonl", "tmp") }.forEach { it.delete() } }
    fun export(id: String, includeCoordinates: Boolean): String = read(id).joinToString("\n", postfix = "\n") {
        JSONObject(it.toString()).apply {
            if (!includeCoordinates) { remove("latitude"); remove("longitude") }
        }.toString()
    }
    companion object { private val lock = Any() }
}
