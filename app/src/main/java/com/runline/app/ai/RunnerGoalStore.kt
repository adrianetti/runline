package com.runline.app.ai

import android.content.Context
import android.util.AtomicFile
import com.runline.app.training.TrainingPlanStore
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong

data class RunnerGoalSnapshot(val goal: RunnerGoal, val revision: Long)

/** Private, atomic, excluded from exports/backup. Empty is an explicit saved choice. */
class RunnerGoalStore(context: Context) {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, "runner-goal.json"))
    fun load(): RunnerGoal = snapshot().goal
    fun snapshot(): RunnerGoalSnapshot = synchronized(LOCK) {
        val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
        val goal = if (exists) runCatching {
            val bytes = file.readFully(); require(bytes.size <= 8_000)
            RunnerGoal.parse(JSONObject(bytes.toString(Charsets.UTF_8)))
        }.getOrDefault(RunnerGoal()) else {
            RunnerGoal.fromLegacy(TrainingPlanStore(app).load()).also { runCatching { write(it) } }
        }
        RunnerGoalSnapshot(goal, revision())
    }
    fun save(goal: RunnerGoal) {
        synchronized(LOCK) { write(goal.bounded()); changes.incrementAndGet() }
        AiSpokenHistory.clearAll()
        SmartRunAi.clear()
    }
    fun clear() = save(RunnerGoal())
    private fun write(goal: RunnerGoal) {
        val out = file.startWrite()
        try { out.write(goal.json().toString().toByteArray(Charsets.UTF_8)); file.finishWrite(out) }
        catch (error: Exception) { file.failWrite(out); throw error }
    }
    companion object {
        private val LOCK = Any()
        private val changes = AtomicLong()
        fun revision(): Long = changes.get()
    }
}
