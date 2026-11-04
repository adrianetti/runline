package com.runline.app.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.runline.app.core.RunSession
import com.runline.app.core.CoachConfig
import com.runline.app.coaching.VoiceCoachSettings
import com.runline.app.i18n.AppLanguage
import com.runline.app.data.RunStore
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.Executors

/** Monotonic-active-time schedule; failed requests count toward a hard per-run cost cap. */
class LiveAiSchedule(initialAttempts: Int = 0) {
    private var nextAt = 0L
    var attempts = initialAttempts.coerceIn(0, 24)
        private set
    fun due(activeMs: Long, intervalMinutes: Int): Boolean {
        val interval = intervalMinutes.coerceIn(3, 10) * 60_000L
        if (nextAt == 0L) nextAt = activeMs + interval
        if (activeMs < nextAt || attempts >= 24) return false
        nextAt = activeMs + interval; attempts++; return true
    }
}

data class AiCoachPrompt(val text: String, val isValid: () -> Boolean)

class AiRunCoach(context: Context, private val onPrompt: (AiCoachPrompt) -> Unit) {
    private val context = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var runId: String? = null
    private var schedule = LiveAiSchedule()
    private var generation = 0L
    private var active = false
    private var fresh = false
    private var busy = false
    private var closed = false
    private var spokenGeneration = 0L
    private var currentTerrain = com.runline.app.core.CoachTerrain.UNKNOWN
    @Synchronized fun onSpoken(text: String, source: String, elapsedRealtimeMs: Long) {
        val id = runId ?: return
        if (closed) return
        AiSpokenHistory.record(id, text, source, elapsedRealtimeMs)
        spokenGeneration++
    }
    @Synchronized fun update(run: RunSession, gpsFresh: Boolean, goalConfig: CoachConfig = VoiceCoachSettings.load(context), terrain: com.runline.app.core.CoachTerrain = com.runline.app.core.CoachTerrain.UNKNOWN) {
        if (closed) return
        if (run.id != runId) { generation++; spokenGeneration++; runId = run.id; schedule = LiveAiSchedule(context.getSharedPreferences("ai_live_attempts", Context.MODE_PRIVATE).getInt(run.id, 0)) }
        if (fresh && !gpsFresh) generation++
        active = true; fresh = gpsFresh; currentTerrain = terrain
        val settings = AiSettings.load(context)
        if (!settings.enabled || !settings.liveEnabled || !AiSettings.hasKey(context) || !VoiceCoachSettings.load(context).enabled) { generation++; return }
        if (!gpsFresh || busy || !schedule.due(run.activeDurationMs, settings.intervalMinutes)) return
        val token = generation; val settingsRevision = AiSettings.revision(); val requestedAt = android.os.SystemClock.elapsedRealtime(); busy = true
        val speechToken = spokenGeneration
        val attempt = schedule.attempts
        worker.execute {
            val result = runCatching {
                if (!context.getSharedPreferences("ai_live_attempts", Context.MODE_PRIVATE).edit().putInt(run.id, attempt).commit()) throw AiFailure(AiFailure.Reason.STORAGE)
                if (AiSettings.revision() != settingsRevision || AiSettings.load(context) != settings) throw AiFailure(AiFailure.Reason.DISABLED)
                val key = AiSettings.readKey(context) ?: throw AiFailure(AiFailure.Reason.KEY)
                val language = AppLanguage.localizedContext(context).resources.configuration.locales[0].language
                val goal = goalConfig
                val profile = RunnerProfileStore(context).snapshot()
                val runnerGoal = RunnerGoalStore(context).snapshot()
                val runs = RunStore(context)
                val input = JSONObject().put("run", AiRunSummary.of(run)).put("coaching_context", AiCoachingContext.build(
                    LocalDate.now(), activeGoal = goal, activeRun = run, history = runs.list(), details = runs::details,
                    includeNotes = settings.includeNotes, profile = profile.profile,
                    spoken = AiSpokenHistory.recent(run.id, requestedAt), nowElapsedMs = requestedAt, runnerGoal = runnerGoal.goal))
                if (AiSettings.revision() != settingsRevision || AiSettings.load(context) != settings) throw AiFailure(AiFailure.Reason.DISABLED)
                if (!profileCurrent(profile)) throw AiFailure(AiFailure.Reason.DISABLED)
                if (!goalCurrent(runnerGoal)) throw AiFailure(AiFailure.Reason.DISABLED)
                synchronized(this) {
                    if (closed || !active || !fresh || token != generation || speechToken != spokenGeneration || runId != run.id)
                        throw AiFailure(AiFailure.Reason.DISABLED)
                }
                Triple(LiveAiAdvice.request(com.runline.app.diagnostics.Diagnostics.provider(context, "live", run.id), key, settings.liveModel, if (language == "es") "Spanish" else "English", input), profile, runnerGoal)
            }.getOrNull()
            main.post {
                synchronized(this) {
                    busy = false
                    val advice = result?.first
                    val profile = result?.second
                    val runnerGoal = result?.third
                    val valid = { synchronized(this) {
                        val now = AiSettings.load(context)
                        !closed && active && fresh && currentTerrain == terrain && token == generation && speechToken == spokenGeneration && runId == run.id &&
                            now.enabled && now.liveEnabled && now == settings && AiSettings.revision() == settingsRevision &&
                            VoiceCoachSettings.load(context).enabled && profile != null && profileCurrent(profile) &&
                            runnerGoal != null && goalCurrent(runnerGoal) &&
                            android.os.SystemClock.elapsedRealtime() - requestedAt in 0..105_000
                    } }
                    if (advice != null && android.os.SystemClock.elapsedRealtime() - requestedAt <= 30_000 && valid()) {
                        onPrompt(AiCoachPrompt(advice, valid))
                    }
                }
            }
        }
    }
    @Synchronized fun pause() { active = false; fresh = false; generation++ }
    @Synchronized fun refresh() { generation++ }
    @Synchronized fun close() { closed = true; pause(); worker.shutdownNow() }
    private fun profileCurrent(snapshot: RunnerProfileSnapshot) = RunnerProfileStore.revision() == snapshot.revision &&
        RunnerProfileStore(context).load() == snapshot.profile
    private fun goalCurrent(snapshot: RunnerGoalSnapshot) = RunnerGoalStore.revision() == snapshot.revision &&
        RunnerGoalStore(context).load() == snapshot.goal
    companion object {
        fun cleanAdvice(raw: String): String? = LiveAiAdvice.clean(raw)
    }
}
