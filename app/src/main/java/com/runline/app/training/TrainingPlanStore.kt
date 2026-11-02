package com.runline.app.training

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

class TrainingPlanStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "training-plan.json"))
    fun load(): TrainingPlan? = synchronized(LOCK) { runCatching { TrainingPlanEngine.parse(JSONObject(file.readFully().toString(Charsets.UTF_8))) }.getOrNull() }
    fun save(plan: TrainingPlan) = synchronized(LOCK) {
        val bytes = TrainingPlanEngine.json(plan).toString().toByteArray(Charsets.UTF_8)
        val out = file.startWrite()
        try { out.write(bytes); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun update(transform: (TrainingPlan) -> TrainingPlan): TrainingPlan? = synchronized(LOCK) { load()?.let { transform(it).also(::save) } }
    fun clear() = synchronized(LOCK) { file.delete() }
    companion object { private val LOCK = Any() }
}
