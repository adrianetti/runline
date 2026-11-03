package com.runline.app.ai

import android.content.Context
import com.runline.app.core.SmartRunSuggestion
import com.runline.app.data.RunStore
import com.runline.app.i18n.AppLanguage
import java.time.LocalDate
import java.util.concurrent.Executors

data class SmartRunAiState(val running: Boolean, val suggestion: SmartRunSuggestion? = null, val failure: AiFailure.Reason? = null)

/** Explicit preparation only. Reopening/rotating observes the same bounded in-memory result. */
object SmartRunAi {
    private data class Entry(val token: Long, val today: LocalDate, val config: AiConfig, val settingsRevision: Long,
                             val profile: RunnerProfileSnapshot, val goal: RunnerGoalSnapshot, val state: SmartRunAiState)
    private val worker = Executors.newSingleThreadExecutor()
    private val gate = SmartRunRequestGate()
    private val lock = Any()
    private var generation = 0L
    private var entry: Entry? = null

    /** False means a previous HTTP worker is still finishing; no second request is queued. */
    fun prepare(context: Context): Boolean = gate.start {
        try {
            val app = context.applicationContext
            val config = AiSettings.load(app)
            val profile = RunnerProfileStore(app).snapshot()
            val goal = RunnerGoalStore(app).snapshot()
            val request = synchronized(lock) {
                Entry(++generation, LocalDate.now(), config, AiSettings.revision(), profile, goal, SmartRunAiState(true)).also { entry = it }
            }
            worker.execute {
                try {
                    val result = runCatching {
                        if (!config.isReady(AiSettings.hasKey(app))) throw AiFailure(if (config.enabled) AiFailure.Reason.KEY else AiFailure.Reason.DISABLED)
                        ensureCurrent(app, request)
                        val key = AiSettings.readKey(app) ?: throw AiFailure(AiFailure.Reason.KEY)
                        val runs = RunStore(app)
                        val history = runs.list()
                        val input = SmartRunAiInput.build(request.today, history, runs::details, config.includeNotes, profile.profile, goal.goal)
                        val inputKey = SmartRunAiInput.key(input)
                        val language = if (AppLanguage.localizedContext(app).resources.configuration.locales[0].language == "es") "Spanish" else "English"
                        val suggestion = SmartRunAiInput.request(AnthropicProvider(), key, config, language, input) { current(app, request) }
                        // Re-read saved measurements/annotations before making the result available.
                        val latest = SmartRunAiInput.build(request.today, runs.list(), runs::details, config.includeNotes, profile.profile, goal.goal)
                        if (inputKey != SmartRunAiInput.key(latest)) throw AiFailure(AiFailure.Reason.RESPONSE)
                        ensureCurrent(app, request)
                        suggestion
                    }
                    synchronized(lock) {
                        if (entry?.token == request.token && generation == request.token) {
                            entry = request.copy(state = result.fold({ SmartRunAiState(false, suggestion = it) },
                                { SmartRunAiState(false, failure = (it as? AiFailure)?.reason ?: AiFailure.Reason.RESPONSE) }))
                        }
                    }
                } finally { gate.finished() }
            }
        } catch (failure: Throwable) { clear(); throw failure }
    }

    /** No HTTP, no generation, no saved-goal mutation. Activity completion/edit hooks invalidate history. */
    fun snapshot(context: Context): SmartRunAiState? {
        val value = synchronized(lock) { entry } ?: return null
        if (!current(context.applicationContext, value)) {
            synchronized(lock) { if (entry?.token == value.token) { generation++; entry = null } }
            return null
        }
        return synchronized(lock) { entry?.takeIf { it.token == value.token }?.state }
    }
    fun clear() = synchronized(lock) { generation++; entry = null }

    private fun current(context: Context, expected: Entry): Boolean =
        synchronized(lock) { entry?.token == expected.token && generation == expected.token } &&
            LocalDate.now() == expected.today && AiSettings.revision() == expected.settingsRevision &&
            AiSettings.load(context) == expected.config && RunnerProfileStore.revision() == expected.profile.revision &&
            RunnerProfileStore(context).load() == expected.profile.profile && RunnerGoalStore.revision() == expected.goal.revision &&
            RunnerGoalStore(context).load() == expected.goal.goal

    private fun ensureCurrent(context: Context, expected: Entry) {
        if (!current(context, expected)) throw AiFailure(AiFailure.Reason.DISABLED)
    }
}
