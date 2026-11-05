package com.runline.app.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.runline.app.core.RunSession
import com.runline.app.data.RunStore
import com.runline.app.i18n.AppLanguage
import com.runline.app.coaching.VoiceCoachSettings
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.Executors

data class RunAiReview(val text: String, val model: String, val createdAtMs: Long, val card: ReviewCard? = null)
class AiReviewStore(context: Context) {
    private val folder = File(context.filesDir, "ai-reviews").apply { mkdirs() }
    private fun file(id: String): AtomicFile { require(id.matches(Regex("[a-zA-Z0-9_-]{1,150}"))); return AtomicFile(File(folder, "$id.json")) }
    fun get(id: String): RunAiReview? = runCatching { JSONObject(file(id).readFully().toString(Charsets.UTF_8)).let {
        RunAiReview(it.getString("text"), it.getString("model"), it.getLong("createdAt"), it.optJSONObject("card")?.let { card -> runCatching { ReviewCard.parse(card.toString()) }.getOrNull() }) } }.getOrNull()
    fun delete(id: String) = file(id).delete()
    fun save(id: String, review: RunAiReview) {
        val target = file(id); val out = target.startWrite()
        try { out.write(JSONObject().put("text", review.text).put("model", review.model).put("createdAt", review.createdAtMs).put("card", review.card?.json() ?: JSONObject.NULL).toString().toByteArray()); target.finishWrite(out) }
        catch (e: Exception) { target.failWrite(out); throw e }
    }
}

/** Bounded independent workers. Neither failures nor API latency enter recording persistence. */
object AiWork {
    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val running = mutableSetOf<String>()
    private val deletedRuns = mutableSetOf<String>()
    /** Observation only: reopening a sheet must never enqueue another paid request. */
    fun isReviewRunning(runId: String): Boolean = synchronized(running) { "review:$runId" in running }
    fun deleted(context: Context, id: String) = synchronized(deletedRuns) { deletedRuns += id; AiReviewStore(context).delete(id) }
    private fun language(context: Context) = if (AppLanguage.localizedContext(context).resources.configuration.locales[0].language == "es") "Spanish" else "English"
    private fun credentials(context: Context): Pair<AiConfig, String> {
        val config = AiSettings.load(context)
        if (!config.enabled) throw AiFailure(AiFailure.Reason.DISABLED)
        return config to (AiSettings.readKey(context) ?: throw AiFailure(AiFailure.Reason.KEY))
    }
    fun review(context: Context, run: RunSession, automatic: Boolean = false, callback: (Result<RunAiReview>) -> Unit = {}) {
        val app = context.applicationContext
        submit("review:${run.id}", callback) {
            val revision = AiSettings.revision()
            val (config, key) = credentials(app)
            val profile = RunnerProfileStore(app).snapshot()
            if (automatic && !config.automaticReviews) throw AiFailure(AiFailure.Reason.DISABLED)
            val runnerGoal = RunnerGoalStore(app).snapshot()
            val runStore = RunStore(app)
            val elapsed = android.os.SystemClock.elapsedRealtime()
            val runDetails = runStore.details(run.id)
            val terrainMap = com.runline.app.terrain.TerrainRepository.get(app).snapshot
            val verifiedRun = terrainMap.project(run)
            val history = runStore.list().map(terrainMap::project).filter { it.startedAtMs <= run.endedAtMs }.distinctBy { it.id }
            val asOf = java.time.Instant.ofEpochMilli(run.endedAtMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val input = JSONObject().put("activity", AiRunSummary.of(verifiedRun, runDetails, config.includeNotes))
                .put("coaching_context", AiCoachingContext.build(asOf, activeGoal = VoiceCoachSettings.load(app),
                    activeRun = verifiedRun, history = history, details = runStore::details, includeNotes = config.includeNotes,
                    profile = profile.profile, spoken = AiSpokenHistory.recent(run.id, elapsed), nowElapsedMs = elapsed, runnerGoal = runnerGoal.goal))
            ensureCurrent(app, config, revision, profile, runnerGoal)
            val card = ReviewAi.request(com.runline.app.diagnostics.Diagnostics.provider(app, "review", run.id),
                key, config.reviewModel, language(app), input)
            if (runStore.details(run.id) != runDetails) throw AiFailure(AiFailure.Reason.RESPONSE)
            ensureCurrent(app, config, revision, profile, runnerGoal)
            synchronized(deletedRuns) {
                if (run.id in deletedRuns || RunStore(app).get(run.id) == null) throw AiFailure(AiFailure.Reason.RESPONSE)
                RunAiReview(card.text(), config.reviewModel, System.currentTimeMillis(), card).also { AiReviewStore(app).save(run.id, it) }
            }
        }
    }
    private fun ensureCurrent(context: Context, config: AiConfig, revision: Long, profile: RunnerProfileSnapshot, goal: RunnerGoalSnapshot) {
        if (AiSettings.revision() != revision || AiSettings.load(context) != config || !config.enabled || !AiSettings.hasKey(context) ||
            RunnerProfileStore.revision() != profile.revision || RunnerProfileStore(context).load() != profile.profile ||
            RunnerGoalStore.revision() != goal.revision || RunnerGoalStore(context).load() != goal.goal)
            throw AiFailure(AiFailure.Reason.DISABLED)
    }
    private fun <T> submit(id: String, callback: (Result<T>) -> Unit, operation: () -> T) {
        synchronized(running) { if (!running.add(id)) { main.post { callback(Result.failure(AiFailure(AiFailure.Reason.RATE_LIMIT))) }; return } }
        worker.execute {
            val result = runCatching(operation)
            synchronized(running) { running.remove(id) }
            main.post { callback(result) }
        }
    }
}

object AiActivityCompletion {
    fun onAppOpen(context: Context) {
        RunnerGoalStore(context.applicationContext).load(); SmartRunAi.snapshot(context.applicationContext)
        AutomaticReviews.reconcile(context.applicationContext)
    }
    fun onDeleted(context: Context, id: String) {
        AiWork.deleted(context.applicationContext, id)
        AutomaticReviews.delete(context, id)
        com.runline.app.diagnostics.Diagnostics.delete(context, id)
        SmartRunAi.clear()
    }
    fun onDetailsSaved(context: Context, runId: String) {
        // The caller invokes this only after the annotation write succeeds; no UI must remain alive.
        if (runId.isNotBlank()) SmartRunAi.clear()
    }
    fun onSaved(context: Context, run: RunSession) {
        val app = context.applicationContext
        AutomaticReviews.enqueue(app, run)
        SmartRunAi.clear()
    }
}
