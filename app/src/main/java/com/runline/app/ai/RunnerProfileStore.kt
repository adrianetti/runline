package com.runline.app.ai

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

data class RunnerProfileSnapshot(val profile: RunnerProfile, val revision: Long)

/** App-private, outside backup and every recording/export path. No network access. */
class RunnerProfileStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "runner-profile.json"))
    fun load(): RunnerProfile = snapshot().profile
    fun snapshot(): RunnerProfileSnapshot = synchronized(LOCK) {
        val value = runCatching {
            val bytes = file.readFully(); require(bytes.size <= 12_000)
            RunnerProfile.parse(JSONObject(bytes.toString(Charsets.UTF_8)))
        }.getOrDefault(RunnerProfile())
        RunnerProfileSnapshot(value, revision())
    }
    fun save(profile: RunnerProfile) = synchronized(LOCK) {
        val out = file.startWrite()
        try {
            out.write(profile.json().toString().toByteArray(Charsets.UTF_8)); file.finishWrite(out)
            changes.incrementAndGet()
            AiSpokenHistory.clearAll(); SmartRunAi.clear()
        } catch (error: Exception) { file.failWrite(out); throw error }
    }
    fun clear() = synchronized(LOCK) { file.delete(); changes.incrementAndGet(); AiSpokenHistory.clearAll(); SmartRunAi.clear() }
    companion object {
        private val LOCK = Any()
        private val changes = AtomicLong()
        fun revision(): Long = changes.get()
    }
}
