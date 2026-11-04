package com.runline.app.diagnostics

import android.content.Context
import com.runline.app.ai.*
import com.runline.app.core.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

object Diagnostics {
    private val worker = Executors.newSingleThreadExecutor()
    fun log(context: Context) = DiagnosticLog(File(context.filesDir, "diagnostics"))
    fun enabled(context: Context) = context.getSharedPreferences("diagnostics", 0).getBoolean("coach", true)
    fun setEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences("diagnostics", 0).edit().putBoolean("coach", enabled).apply() }
    fun speech(context: Context, run: RunSession, event: String, text: String, source: String, cue: String, situation: CoachSituation, fresh: Boolean) {
        if (!enabled(context)) return
        val now = System.currentTimeMillis()
        val fix = run.points.lastOrNull()
        val value = JSONObject().put("version", 1).put("event", event).put("at_ms", now).put("source", source)
            .put("text", text.take(500)).put("cue", cue.take(120)).put("active_ms", run.activeDurationMs)
            .put("distance_m", situation.distanceMeters).put("pace_seconds_per_km", situation.paceSeconds ?: JSONObject.NULL)
            .put("terrain", situation.terrain.name).put("grade_percent", situation.gradePercent ?: JSONObject.NULL)
            .put("gps_fresh", fresh).put("fix_at_ms", fix?.timestampMs ?: JSONObject.NULL)
            .put("accuracy_m", fix?.accuracyMeters?.takeIf { it.isFinite() } ?: JSONObject.NULL)
        if (fix != null && fix.hasValidCoordinates()) value.put("latitude", fix.latitude).put("longitude", fix.longitude)
        val app = context.applicationContext
        worker.execute { runCatching { log(app).append(run.id, value) } }
    }
    fun provider(context: Context, operation: String, runId: String? = null): AnthropicProvider {
        val app = context.applicationContext
        return AnthropicProvider(onMetric = { m ->
            val value = JSONObject().put("version", 1).put("event", "api").put("at_ms", m.atMs)
                .put("operation", operation).put("run_id", runId ?: JSONObject.NULL).put("model", m.model)
                .put("http_method", m.method).put("http_status", m.status ?: JSONObject.NULL).put("latency_ms", m.durationMs)
                .put("input_tokens", m.inputTokens ?: JSONObject.NULL).put("output_tokens", m.outputTokens ?: JSONObject.NULL)
                .put("cache_read_tokens", m.cacheReadTokens ?: JSONObject.NULL).put("cache_write_tokens", m.cacheWriteTokens ?: JSONObject.NULL)
                .put("estimated_usd", m.estimatedUsd ?: JSONObject.NULL).put("failure", m.failure ?: JSONObject.NULL)
            // Provider calls already run outside the recorder/main thread.
            runCatching { log(app).append("usage", value) }
        })
    }
    fun delete(context: Context, id: String) { val app = context.applicationContext; worker.execute { runCatching { log(app).delete(id) } } }
    fun clear(context: Context, done: () -> Unit = {}) { val app = context.applicationContext; worker.execute { runCatching { log(app).clear() }; android.os.Handler(android.os.Looper.getMainLooper()).post(done) } }
}
