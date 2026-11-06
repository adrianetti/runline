package com.runline.app.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.AtomicFile
import com.runline.app.core.SmartRunSuggestion
import com.runline.app.data.RunStore
import com.runline.app.i18n.AppLanguage
import com.runline.app.tracking.TrackingService
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One durable daily reservation, with explicit retries. UI observation never sends HTTP. */
object SmartRunAi {
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean()
    private val lock = Any()
    private var generation = 0L
    @Volatile private var state: SmartRunAiState? = null
    private var day: LocalDate? = null
    private var configRevision = -1L
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    fun listen(callback: () -> Unit) { listeners += callback }
    fun unlisten(callback: () -> Unit) { listeners -= callback }
    private fun notifyChanged() { main.post { listeners.forEach { it() } } }
    fun clear() { synchronized(lock) { generation++; state = null; day = null }; notifyChanged() }
    fun snapshot(context: Context): SmartRunAiState? {
        if (!AiSettings.isReady(context)) return null
        synchronized(lock) {
            if (day != LocalDate.now() || configRevision != AiSettings.revision()) return null
            return state
        }
    }
    fun prepare(context: Context): Boolean = refresh(context, explicit = true)
    fun refresh(context: Context, explicit: Boolean = false): Boolean {
        val app = context.applicationContext; val config = AiSettings.load(app)
        if (!config.isReady(AiSettings.hasKey(app)) || (!explicit && !config.dailySuggestion) || TrackingService.state(app).run != null) return false
        if (!explicit && snapshot(app) != null) return false
        if (!busy.compareAndSet(false, true)) return false
        val token: Long
        synchronized(lock) { token = ++generation; day = LocalDate.now(); configRevision = AiSettings.revision(); state = SmartRunAiState(true) }
        notifyChanged()
        worker.execute {
          try {
            val today = LocalDate.now(); val now = System.currentTimeMillis(); val zone = ZoneId.systemDefault().id
            val epoch = AiSettings.cacheEpoch(app); val profile = RunnerProfileStore(app).snapshot(); val goal = RunnerGoalStore(app).snapshot()
            fun current() = synchronized(lock) { generation == token } && today == LocalDate.now() &&
                AiSettings.load(app) == config && AiSettings.cacheEpoch(app) == epoch && AiSettings.hasKey(app) &&
                RunnerProfileStore(app).load() == profile.profile && RunnerGoalStore(app).load() == goal.goal && TrackingService.state(app).run == null
            val result = runCatching {
                val file = AtomicFile(File(app.noBackupFilesDir, "daily-suggestion.json"))
                val exists = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
                val previous = if (exists) {
                    try { require(file.baseFile.length() <= 16000); DailySuggestion.parse(file.readFully().toString(Charsets.UTF_8)) }
                    catch (e: Exception) { if (!explicit) throw AiFailure(AiFailure.Reason.STORAGE) else null }
                } else null
                val runs = RunStore(app)
                fun input() = SmartRunAiInput.build(today, runs.list(), runs::details, config.includeNotes, profile.profile, goal.goal, RunnerInsightsStore(app).load())
                val payload = input()
                val inputKey = SmartRunAiInput.key(payload) + ":" + epoch
                var key: String? = null
                DailySuggestionWork.execute(today, now, zone, inputKey, explicit, previous,
                    persist = { write(file, it) },
                    beforeRequest = {
                        val network = app.getSystemService(ConnectivityManager::class.java)
                        if (network.getNetworkCapabilities(network.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true)
                            throw AiFailure(AiFailure.Reason.NETWORK)
                        key = AiSettings.readKey(app) ?: throw AiFailure(AiFailure.Reason.KEY)
                    }, request = {
                        val lastEnded = runs.list().filter { it.endedAtMs <= now && (runs.details(it.id).activityType ?: it.activityType) == com.runline.app.core.ActivityType.RUN }.maxOfOrNull { it.endedAtMs }
                        payload.put("context_time_ms", now).put("hours_since_last_completed_run", lastEnded?.let { (now - it) / 3_600_000.0 } ?: org.json.JSONObject.NULL)
                        val language = if (AppLanguage.localizedContext(app).resources.configuration.locales[0].language == "es") "Spanish" else "English"
                        SmartRunAiInput.request(com.runline.app.diagnostics.Diagnostics.provider(app, "smart"), requireNotNull(key), config, language, payload, ::current)
                    }, isCurrent = { current() && SmartRunAiInput.key(input()) + ":" + epoch == inputKey })
            }
            synchronized(lock) {
                if (generation == token) state = result.getOrElse { SmartRunAiState(false, failure = (it as? AiFailure)?.reason ?: AiFailure.Reason.RESPONSE) }
            }
          } catch (_: Exception) {
            synchronized(lock) { if (generation == token) state = SmartRunAiState(false, failure = AiFailure.Reason.STORAGE) }
          } finally { busy.set(false); notifyChanged() }
        }
        return true
    }
    private fun write(file: AtomicFile, record: DailySuggestion) {
        try { com.runline.app.data.writePrivateAtomic(file, record.json().toString().toByteArray(Charsets.UTF_8)) }
        catch (_: Exception) { throw AiFailure(AiFailure.Reason.STORAGE) }
    }
}
