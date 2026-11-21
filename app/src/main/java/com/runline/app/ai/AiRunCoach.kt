package com.runline.app.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.runline.app.core.RunSession
import com.runline.app.core.CoachConfig
import com.runline.app.core.CoachCue
import com.runline.app.core.CoachTerrain
import com.runline.app.coaching.VoiceCoachSettings
import com.runline.app.i18n.AppLanguage
import com.runline.app.data.RunStore
import com.runline.app.terrain.TerrainRepository
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.Executors

data class AiCoachPrompt(val text: String, val isValid: () -> Boolean)

class AiRunCoach(context: Context, private val onEvent: (event: String, detail: String, latencyMs: Long?) -> Unit = { _, _, _ -> },
                 private val onPrompt: (AiCoachPrompt) -> Unit) {
    private val context = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var runId: String? = null
    private var plan = LiveCoachPlan()
    private var generation = 0L
    private var active = false
    private var fresh = false
    private var busy = false
    private var closed = false
    private var activeMs = 0L
    private var currentTerrain = CoachTerrain.UNKNOWN
    private val usedPoints = mutableListOf<String>()

    val leading: Boolean @Synchronized get() = !closed && active && plan.healthy && enabled()

    private fun enabled(): Boolean {
        val settings = AiSettings.load(context)
        return settings.enabled && settings.liveEnabled && AiSettings.hasKey(context) && VoiceCoachSettings.load(context).enabled
    }

    @Synchronized fun onSpoken(text: String, source: String, elapsedRealtimeMs: Long) {
        val id = runId ?: return
        if (closed) return
        AiSpokenHistory.record(id, text, source, elapsedRealtimeMs)
        plan.spoken(activeMs, ai = source == "AI")
    }

    private fun observe(run: RunSession, gpsFresh: Boolean, terrain: CoachTerrain) {
        if (run.id != runId) {
            generation++; runId = run.id; usedPoints.clear()
            plan = LiveCoachPlan(context.getSharedPreferences("ai_live_attempts", Context.MODE_PRIVATE).getInt(run.id, 0))
        }
        if (fresh && !gpsFresh) generation++
        active = true; fresh = gpsFresh; currentTerrain = terrain; activeMs = run.activeDurationMs
    }

    @Synchronized fun update(run: RunSession, gpsFresh: Boolean, goalConfig: CoachConfig = VoiceCoachSettings.load(context), terrain: CoachTerrain = CoachTerrain.UNKNOWN) {
        if (closed) return
        observe(run, gpsFresh, terrain)
        val settings = AiSettings.load(context)
        if (!enabled()) { generation++; return }
        if (!gpsFresh || busy) return
        val call = plan.next(run.activeDurationMs, settings.intervalMinutes) ?: return
        launch(run, goalConfig, terrain, settings, call, null) { line, valid, label, latency ->
            if (line == null) return@launch
            if (latency <= ARRIVE_WITHIN_MS && valid()) { onEvent("ai_ready", "$label ${line.pointId}", latency); onPrompt(AiCoachPrompt(line.text, valid)) }
            else onEvent("ai_discarded", "$label ${if (latency > ARRIVE_WITHIN_MS) "late" else "context_changed"}", latency)
        }
    }

    @Synchronized fun voice(cue: CoachCue, run: RunSession, gpsFresh: Boolean, goalConfig: CoachConfig, terrain: CoachTerrain, onResult: (String?) -> Unit) {
        val moment = LiveMoment.of(cue); val milestone = LiveMilestone.of(cue)
        if (closed || moment == null || milestone == null) { main.post { onResult(null) }; return }
        observe(run, gpsFresh, terrain)
        val settings = AiSettings.load(context)
        val call = if (enabled() && gpsFresh && !busy) plan.milestone(run.activeDurationMs, moment) else null
        if (call == null) { main.post { onResult(null) }; return }
        var answered = false
        val giveUp = Runnable { if (!answered) { answered = true; onEvent("ai_discarded", "MOMENT:$moment late", VOICE_WITHIN_MS); onResult(null) } }
        main.postDelayed(giveUp, VOICE_WITHIN_MS)
        launch(run, goalConfig, terrain, settings, call, milestone) { line, valid, label, latency ->
            if (answered) return@launch
            answered = true; main.removeCallbacks(giveUp)
            if (line != null && valid()) { onEvent("ai_ready", "$label ${line.pointId}", latency); onResult(line.text) } else onResult(null)
        }
    }

    private fun launch(run: RunSession, goalConfig: CoachConfig, terrain: CoachTerrain, settings: AiConfig, call: LiveCall, milestone: JSONObject?,
                       deliver: (line: LiveLine?, valid: () -> Boolean, label: String, latencyMs: Long) -> Unit) {
        val token = generation; val settingsRevision = AiSettings.revision(); val requestedAt = SystemClock.elapsedRealtime(); busy = true
        val terrainRevision = TerrainRepository.get(context).snapshot.revision
        val attempt = plan.attempts
        val used = usedPoints.toList()
        val requestActiveMs = run.activeDurationMs
        val label = call.trigger.name + (call.moment?.let { ":" + it.name } ?: if (call.trigger == LiveTrigger.CADENCE) ":" + call.focus.name else "")
        onEvent("ai_requested", label, null)
        worker.execute {
            var failure: String? = null
            val result = runCatching {
                if (!context.getSharedPreferences("ai_live_attempts", Context.MODE_PRIVATE).edit().putInt(run.id, attempt).commit()) throw AiFailure(AiFailure.Reason.STORAGE)
                if (AiSettings.revision() != settingsRevision || AiSettings.load(context) != settings) throw AiFailure(AiFailure.Reason.DISABLED)
                val key = AiSettings.readKey(context) ?: throw AiFailure(AiFailure.Reason.KEY)
                val language = AppLanguage.localizedContext(context).resources.configuration.locales[0].language
                val profile = RunnerProfileStore(context).snapshot()
                val runnerGoal = RunnerGoalStore(context).snapshot()
                val runs = RunStore(context)
                val terrainMap = TerrainRepository.get(context).snapshot
                val verifiedRun = terrainMap.project(run)
                val input = JSONObject().put("run", AiRunSummary.of(verifiedRun)).put("coaching_context", AiCoachingContext.build(
                    LocalDate.now(), activeGoal = goalConfig, activeRun = verifiedRun, history = runs.list().map(terrainMap::project), details = runs::details,
                    includeNotes = false, spoken = AiSpokenHistory.recent(run.id, requestedAt), nowElapsedMs = requestedAt, runnerGoal = runnerGoal.goal))
                if (AiSettings.revision() != settingsRevision || AiSettings.load(context) != settings) throw AiFailure(AiFailure.Reason.DISABLED)
                if (!profileCurrent(profile) || !goalCurrent(runnerGoal)) throw AiFailure(AiFailure.Reason.DISABLED)
                synchronized(this) {
                    if (closed || !active || !fresh || token != generation || runId != run.id) throw AiFailure(AiFailure.Reason.DISABLED)
                }
                Triple(LiveAiAdvice.compose(com.runline.app.diagnostics.Diagnostics.provider(context, "live", run.id), key, settings.liveModel,
                    if (language == "es") "Spanish" else "English", input, call, used, milestone), profile, runnerGoal)
            }.onFailure { failure = (it as? AiFailure)?.reason?.name ?: it.javaClass.simpleName }.getOrNull()
            main.post {
                synchronized(this) {
                    busy = false
                    val latency = SystemClock.elapsedRealtime() - requestedAt
                    if (result == null) {
                        if (failure != AiFailure.Reason.DISABLED.name) plan.failed(activeMs.coerceAtLeast(requestActiveMs))
                        onEvent("ai_failed", "$label ${failure.orEmpty()}".trim(), latency)
                        deliver(null, { false }, label, latency); return@synchronized
                    }
                    val (line, profile, runnerGoal) = result
                    plan.answered(activeMs.coerceAtLeast(requestActiveMs), line != null)
                    if (line == null) { onEvent("ai_silent", label, latency); deliver(null, { false }, label, latency); return@synchronized }
                    if (runId == run.id) usedPoints += line.pointIds
                    val aboutGround = LiveAiAdvice.mentionsTerrain(line.text)
                    val valid = { synchronized(this) {
                        val now = AiSettings.load(context)
                        !closed && active && fresh && token == generation && runId == run.id &&
                            (!aboutGround || (TerrainRepository.get(context).snapshot.revision == terrainRevision && currentTerrain == terrain)) &&
                            now.enabled && now.liveEnabled && now == settings && AiSettings.revision() == settingsRevision &&
                            VoiceCoachSettings.load(context).enabled && profileCurrent(profile) && goalCurrent(runnerGoal) &&
                            SystemClock.elapsedRealtime() - requestedAt in 0..SPEAK_WITHIN_MS
                    } }
                    deliver(line, valid, label, latency)
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
        const val ARRIVE_WITHIN_MS = 15_000L
        const val SPEAK_WITHIN_MS = 50_000L
        const val VOICE_WITHIN_MS = 3_500L
        fun cleanAdvice(raw: String): String? = LiveAiAdvice.clean(raw)
    }
}
