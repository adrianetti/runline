package com.runline.app.data

import android.content.Context
import android.util.AtomicFile
import com.runline.app.core.SessionIntent
import java.io.File

class SessionIntentStore(context: Context) {
    private val folder = File(context.filesDir, "session-intents")
    private fun file(id: String): AtomicFile {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,150}")))
        return AtomicFile(File(folder, "$id.json"))
    }
    fun get(id: String): SessionIntent? = synchronized(LOCK) { runCatching {
        val target = file(id); require(target.baseFile.length() <= 4000)
        SessionIntent.parse(target.readFully().toString(Charsets.UTF_8))
    }.getOrNull() }
    fun save(id: String, intent: SessionIntent) = synchronized(LOCK) {
        check(folder.isDirectory || folder.mkdirs())
        writePrivateAtomic(file(id), intent.json().toString().toByteArray(Charsets.UTF_8))
    }
    fun delete(id: String) = synchronized(LOCK) { file(id).delete() }
    companion object { private val LOCK = Any() }
}
