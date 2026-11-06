package com.runline.app.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import com.runline.app.R
import com.runline.app.ai.AiRunCoach
import com.runline.app.ai.AiActivityCompletion
import com.runline.app.coaching.CoachSpeech
import com.runline.app.coaching.VoiceCoachSettings
import com.runline.app.core.CoachConfig
import com.runline.app.core.CoachEngine
import com.runline.app.core.ActivityType
import com.runline.app.i18n.AppLanguage
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import com.runline.app.data.DraftRecord
import com.runline.app.data.RunStore
import java.util.UUID
import kotlin.math.roundToLong

enum class RecordingStatus { IDLE, RECORDING, PAUSED, INTERRUPTED, RECOVERY_ERROR }

data class TrackingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val run: RunSession? = null,
    val accuracyMeters: Float? = null,
    val message: String? = null,
    val savedRunId: String? = null,
    val lastSavedAtMs: Long? = null,
    val gpsStale: Boolean = false,
    val messageRes: Int? = null,
    /** Live standstill start (GPS wall clock). Derived only; recording continues unchanged. */
    val stoppedSinceMs: Long? = null,
    /** A followed route and the hiker's progress on it. Derived only. */
    val route: LiveRoute? = null,
) {
    /** Resolve again when read so a language change also updates a paused/recovered state. */
    fun localized(context: Context): TrackingState = messageRes?.let {
        copy(message = AppLanguage.localizedContext(context).getString(it))
    } ?: this
}

/** A planned route being followed; [points] keeps one identity so the map draws it once. */
data class LiveRoute(val id: String, val name: String, val points: List<com.runline.app.core.RunPoint>,
                     val progress: com.runline.app.core.RouteProgress?, val sunsetMs: Long?)

/** User-started foreground recorder. UI closure never stops it; force-stop and reboot never auto-start it. */
class TrackingService : Service(), LocationListener {
    private lateinit var store: RunStore
    private lateinit var locations: LocationManager
    private val thread = HandlerThread("RunlineRecorder")
    private lateinit var worker: Handler
    private val main = Handler(Looper.getMainLooper())
    // Accessed only on the main thread, where Android delivers start commands.
    private var latestStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var current: RunSession? = null
    private var recovered: DraftRecord? = null
    private var mode = RecordingStatus.IDLE
    private var activeSince = 0L
    private var lastLocationElapsed = 0L
    private var segment = 0
    private var tickCount = 0
    private var accuracy: Float? = null
    private var messageRes: Int? = null
    private var lastSavedAt: Long? = null
    private var bootCount = -1
    private var coachConfig = CoachConfig()
    private var coachEngine: CoachEngine? = null
    private var coachSpeech: CoachSpeech? = null
    private var aiCoach: AiRunCoach? = null
    private var stillness = com.runline.app.core.StillnessTracker()
    private var autoPause = true
    private var notifiedStopped = false
    private var routeFollower: com.runline.app.core.RouteFollower? = null
    private var routeId: String? = null
    private var routeName = ""
    private var routePoints: List<com.runline.app.core.RunPoint> = emptyList()
    private var routeProgress: com.runline.app.core.RouteProgress? = null
    private var routeSunset: Long? = null
    private var routeState: com.runline.app.core.RouteState? = null
    private var lastRouteAlert = 0L
    @Volatile private var foreground = false
    @Volatile private var published = TrackingState()

    private val shutdown = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pending = goAsync()
            worker.post {
                try {
                    freezeTimer(); mode = if (current != null) RecordingStatus.INTERRUPTED else mode
                    releaseRecordingResources()
                    current?.let { runCatching { persistCheckpoint() } }
                    publish()
                } finally { pending.finish() }
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (mode != RecordingStatus.RECORDING) return
            tickCount++
            // Durability does not depend on onPause/onDestroy/shutdown callbacks being delivered.
            if (!safely { persistCheckpoint() }) return
            if (isGpsStale()) { accuracy = null; messageRes = R.string.tracking_gps_lost }
            if (tickCount % 300 == 0) wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
            updateCoaching()
            publish()
            // A standstill (or moving again) should show on the lock screen without waiting.
            val stoppedNow = published.stoppedSinceMs != null
            if (tickCount % 5 == 0 || stoppedNow != notifiedStopped) { notifiedStopped = stoppedNow; updateNotification() }
            worker.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = RunStore(this)
        locations = getSystemService(LocationManager::class.java)
        bootCount = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
        thread.start(); worker = Handler(thread.looper)
        refreshNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(shutdown, IntentFilter(Intent.ACTION_SHUTDOWN), RECEIVER_NOT_EXPORTED)
        else registerReceiver(shutdown, IntentFilter(Intent.ACTION_SHUTDOWN))
        instance = this
        worker.post {
            try {
                recovered = store.loadDraftRecord(); current = recovered?.run
                current?.let {
                    mode = RecordingStatus.INTERRUPTED
                    lastSavedAt = it.endedAtMs
                    segment = (it.points.maxOfOrNull { point -> point.segment } ?: -1) + 1
                    messageRes = if (recovered?.recoveredWithDamage == true) R.string.tracking_recovered_damage else R.string.tracking_recovered_hint
                }
            } catch (_: Exception) { mode = RecordingStatus.RECOVERY_ERROR; messageRes = R.string.tracking_recovery_error }
            publish()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Services survive configuration changes. A paused recorder has no ticker,
        // so refresh its notification when the OS changes the app language too.
        refreshLanguage()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val action = intent?.action
        val requestedType = ActivityType.fromStorage(intent?.getStringExtra(EXTRA_ACTIVITY_TYPE))
        val fromNotification = intent?.hasExtra(EXTRA_NOTIFICATION_RUN_ID) == true
        val expectedRunId = intent?.getStringExtra(EXTRA_NOTIFICATION_RUN_ID)
        val wasForeground = foreground
        var foregroundFailed = false
        if (!foreground && (action == null || action == ACTION_START || action == ACTION_RESUME)) {
            try {
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                else startForeground(NOTIFICATION, notification())
                foreground = true
            } catch (_: RuntimeException) {
                foregroundFailed = true
            }
        }
        worker.post {
            // onCreate's recovery runs first on this same worker. Never trust the snapshot
            // shown when a PendingIntent was built: it may belong to a finished/older run.
            if (fromNotification && !RunNotificationActions.isAllowed(action, expectedRunId, current?.id, mode)) {
                if (!wasForeground && mode != RecordingStatus.RECORDING) {
                    // A newer Start/Resume may already have reused our foreground state.
                    // Serialize cleanup with onStartCommand so an old action cannot remove
                    // that protection, including between the ID check and stopForeground.
                    main.post {
                        if (latestStartId == startId && published.status != RecordingStatus.RECORDING) {
                            stopForeground(STOP_FOREGROUND_REMOVE); foreground = false
                            stopSelfResult(startId)
                        }
                    }
                } else updateNotification()
                return@post
            }
            if (foregroundFailed) {
                fail(R.string.tracking_open_to_resume); stopSelfResult(startId)
                return@post
            }
            when (action) {
                null -> {
                    // Only the OS's sticky-service recreation enters here. A normal app launch
                    // merely displays recovery; same-boot check rules out reboot/shutdown gaps.
                    if (recovered?.mayRestart(bootCount, SystemClock.elapsedRealtime()) == true) {
                        resumeRun()
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
                    }
                }
                ACTION_START -> startRun(requestedType, intent?.getStringExtra(EXTRA_SESSION_INTENT))
                ACTION_RESUME -> resumeRun()
                ACTION_PAUSE -> pauseRun()
                ACTION_FINISH -> finishRun()
                ACTION_DISCARD -> discardRun()
                ACTION_ARCHIVE_DRAFT -> archiveDraft()
            }
        }
        // A rejected, old notification action must not remove sticky recovery from an
        // active recorder. Paused drafts still cannot auto-resume (mayRestart checks that).
        return START_STICKY
    }

    private fun startRun(activityType: ActivityType, intentJson: String?) {
        if (mode == RecordingStatus.RECOVERY_ERROR) { fail(R.string.tracking_set_aside_first); return }
        if (current != null) { publish(); updateNotification(); return }
        val now = System.currentTimeMillis()
        val run = RunSession(UUID.randomUUID().toString(), now, now, 0L, emptyList(), activityType)
        if (!safely {
            val target = intentJson?.let(com.runline.app.core.SessionIntent::parse) ?: com.runline.app.core.SessionIntent.free()
            com.runline.app.data.SessionIntentStore(this).save(run.id, target)
            store.beginDraft(run)
        }) return
        current = run; lastSavedAt = now; segment = 0
        stillness = com.runline.app.core.StillnessTracker(activityType)
        resumeRun()
    }

    private fun resumeRun() {
        if (mode == RecordingStatus.RECORDING) return
        if (current == null) { stopSelf(); return }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fail(R.string.tracking_allow_location); return
        }
        if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) { fail(R.string.tracking_enable_location); return }
        try {
            // Neither the timer nor the route connects across any process outage or pause.
            segment = (current!!.points.maxOfOrNull { it.segment } ?: -1) + 1
            activeSince = SystemClock.elapsedRealtime(); lastLocationElapsed = 0L; tickCount = 0
            stillness = com.runline.app.core.StillnessTracker(current!!.activityType)
            mode = RecordingStatus.RECORDING; accuracy = null; messageRes = R.string.tracking_finding_gps
            persistCheckpoint()
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, thread.looper)
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Runline:recording").apply {
                    setReferenceCounted(false); acquire(WAKE_LOCK_TIMEOUT_MS)
                }
            reloadCoaching()
            loadRoute()
            worker.removeCallbacks(ticker); worker.post(ticker)
            publish(); updateNotification()
        } catch (_: Exception) { fail(R.string.tracking_check_location_storage) }
    }

    private fun pauseRun() {
        if (current == null) { stopSelf(); return }
        freezeTimer(); mode = RecordingStatus.PAUSED
        releaseRecordingResources(); messageRes = R.string.tracking_paused
        safely { persistCheckpoint() }
        publish(); updateNotification()
    }

    private fun finishRun() {
        if (current == null) { stopSelf(); return }
        pauseRun()
        val finished = current!!.copy(endedAtMs = System.currentTimeMillis())
        if (!safely { store.complete(finished) }) return
        // The route is durable before optional editing or online analysis is scheduled.
        runCatching { getSharedPreferences("activity_completion", MODE_PRIVATE).edit().putString("pending_id", finished.id).apply() }
        runCatching { AiActivityCompletion.onSaved(this, finished) }
        current = null; mode = RecordingStatus.IDLE; clearRoute()
        published = TrackingState(savedRunId = finished.id, lastSavedAtMs = System.currentTimeMillis()); cached = published
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }

    private fun discardRun() {
        if (mode == RecordingStatus.RECOVERY_ERROR) { fail(R.string.tracking_preserve_damaged); return }
        freezeTimer(); mode = RecordingStatus.PAUSED; releaseRecordingResources()
        if (!safely { store.discardDraft() }) return
        current = null; mode = RecordingStatus.IDLE; clearRoute(); published = TrackingState(); cached = published
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }

    private fun archiveDraft() {
        if (mode != RecordingStatus.RECOVERY_ERROR || current != null) { messageRes = R.string.tracking_finish_first; publish(); return }
        try { store.archiveDraft(); mode = RecordingStatus.IDLE; messageRes = R.string.tracking_archived }
        catch (_: Exception) { messageRes = R.string.tracking_free_space_retry }
        publish(); stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        if (mode != RecordingStatus.RECORDING) return
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy !in 0f..30f || ageMs !in 0..15_000 ||
            !location.latitude.isFinite() || location.latitude !in -90.0..90.0 || !location.longitude.isFinite() || location.longitude !in -180.0..180.0) {
            accuracy = null; messageRes = R.string.tracking_weak_gps; publish(); return
        }
        val elapsed = location.elapsedRealtimeNanos / 1_000_000
        if (elapsed < activeSince || location.time < 0) return // Never import a cached fix from before Start/Resume.
        // Every valid fix informs the standstill signal, including those too close to store.
        stillness.update(location.latitude, location.longitude, location.time, location.accuracy,
            if (location.hasSpeed() && location.speed.isFinite()) location.speed else null)
        updateRoute(location.latitude, location.longitude, location.accuracy, location.time)
        val previous = current!!.points.lastOrNull()
        if (lastLocationElapsed > 0 && elapsed <= lastLocationElapsed) return
        val seconds = (elapsed - lastLocationElapsed) / 1000.0
        if (previous != null && lastLocationElapsed > 0 && seconds <= 15) {
            val distance = FloatArray(1)
            Location.distanceBetween(previous.latitude, previous.longitude, location.latitude, location.longitude, distance)
            if (distance[0] > 15 * seconds + previous.accuracyMeters + location.accuracy) return
            if (distance[0] < 2.5f && seconds < 10) return
        }
        if (lastLocationElapsed > 0 && seconds > 15) segment++
        // UTC labels must keep increasing within a segment even if the wall clock is corrected.
        if (previous != null && previous.segment == segment && location.time <= previous.timestampMs) segment++
        val point = RunPoint(location.latitude, location.longitude, location.time, location.accuracy,
            if (location.hasAltitude() && location.altitude.isFinite()) location.altitude else null, segment,
            if (location.hasVerticalAccuracy() && location.verticalAccuracyMeters.isFinite() && location.verticalAccuracyMeters >= 0)
                location.verticalAccuracyMeters else null)
        val checkpoint = draftRecord()
        if (!safely { store.appendPoint(point, checkpoint) }) return
        current = current!!.copy(points = current!!.points + point)
        lastLocationElapsed = elapsed; lastSavedAt = System.currentTimeMillis()
        accuracy = location.accuracy; messageRes = null
        com.runline.app.terrain.TerrainRepository.get(this).request(listOf(point))
        publish()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing the activity from Recents does not stop this foreground service.
        worker.post { if (current != null) safely { persistCheckpoint() } }
        super.onTaskRemoved(rootIntent)
    }
    override fun onProviderDisabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) fail(R.string.tracking_location_off) }
    override fun onProviderEnabled(provider: String) = Unit
    @Deprecated("Required on Android 8–10")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun snapshotRun(): RunSession? = current?.let {
        it.copy(endedAtMs = System.currentTimeMillis(), activeDurationMs = it.activeDurationMs +
            if (mode == RecordingStatus.RECORDING) (SystemClock.elapsedRealtime() - activeSince).coerceAtLeast(0) else 0)
    }
    private fun draftRecord() = DraftRecord(snapshotRun() ?: error("No run"), mode == RecordingStatus.RECORDING,
        bootCount, SystemClock.elapsedRealtime())
    private fun persistCheckpoint() { store.checkpoint(draftRecord()); lastSavedAt = System.currentTimeMillis() }
    private fun freezeTimer() { if (mode == RecordingStatus.RECORDING) { current = snapshotRun(); activeSince = SystemClock.elapsedRealtime() } }
    private fun isGpsStale() = mode == RecordingStatus.RECORDING &&
        SystemClock.elapsedRealtime() - (lastLocationElapsed.takeIf { it > 0 } ?: activeSince) > 15_000
    private fun stoppedSince(): Long? = stillness.stoppedSinceMs?.takeIf { mode == RecordingStatus.RECORDING && !isGpsStale() }
    private fun publish() { published = TrackingState(mode, snapshotRun(), accuracy, lastSavedAtMs = lastSavedAt, gpsStale = isGpsStale(), messageRes = messageRes,
        stoppedSinceMs = stoppedSince(), route = routeId?.let { LiveRoute(it, routeName, routePoints, routeProgress, routeSunset) }).localized(this); cached = published }

    /** The session's planned route, rebuilt from the recorded fixes after a restart. Optional: never in the storage path. */
    private fun loadRoute() {
        runCatching {
            val session = current ?: return
            val id = com.runline.app.data.SessionIntentStore(this).get(session.id)?.routeId
            if (id == null) { clearRoute(); return }
            if (id == routeId && routeFollower != null) return
            val routes = com.runline.app.data.RouteStore(this)
            val route = routes.load(id) ?: run { clearRoute(); return }
            val follower = com.runline.app.core.RouteFollower(route)
            var last: com.runline.app.core.RouteProgress? = null
            session.points.forEach { last = follower.update(it.latitude, it.longitude, it.accuracyMeters, it.timestampMs) }
            val language = AppLanguage.localizedContext(this).resources.configuration.locales[0].language
            routeFollower = follower; routeId = id; routeProgress = last; routeState = last?.state
            routeName = routes.entry(id, language)?.name ?: route.name
            routePoints = route.runPoints()
            routeSunset = com.runline.app.core.SunTimes.sunset(java.time.LocalDate.now().toEpochDay(), route.points.first().latitude, route.points.first().longitude)
        }
    }
    private fun clearRoute() { routeFollower = null; routeId = null; routeProgress = null; routeState = null; routePoints = emptyList() }

    private fun updateRoute(latitude: Double, longitude: Double, accuracyMeters: Float, timeMs: Long) {
        val follower = routeFollower ?: return
        runCatching {
            val progress = follower.update(latitude, longitude, accuracyMeters, timeMs, snapshotRun()?.activeDurationMs?.div(60_000.0))
            val previous = routeState
            routeProgress = progress; routeState = progress.state
            val now = SystemClock.elapsedRealtime()
            val repeat = progress.state == com.runline.app.core.RouteState.OFF_ROUTE && now - lastRouteAlert >= 120_000
            if (progress.changed || repeat) { lastRouteAlert = now; routeAlert(progress, previous); publish(); updateNotification() }
        }
    }

    private fun routeAlert(progress: com.runline.app.core.RouteProgress, previous: com.runline.app.core.RouteState?) {
        val meters = progress.distanceFromRouteMeters.roundToLong()
        val (key, pattern, text) = when (progress.state) {
            com.runline.app.core.RouteState.OFF_ROUTE -> Triple("off", longArrayOf(0, 400, 200, 400),
                progress.side?.let { tr(R.string.v19_route_off_side, meters, tr(sideText(it))) } ?: tr(R.string.v19_route_off, meters))
            com.runline.app.core.RouteState.ON_ROUTE -> if (previous == com.runline.app.core.RouteState.OFF_ROUTE)
                Triple("back", longArrayOf(0, 150), tr(R.string.v19_route_back))
                else Triple("joined", longArrayOf(0, 150), tr(R.string.v19_route_joined, progress.remainingMeters / 1000))
            com.runline.app.core.RouteState.FINISHED -> Triple("done", longArrayOf(0, 300, 150, 300), tr(R.string.v19_route_finished))
            else -> return
        }
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(android.os.VibratorManager::class.java).defaultVibrator
                else @Suppress("DEPRECATION") getSystemService(android.os.Vibrator::class.java)
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        }
        coachSpeech?.speakRoute(key, text) { published.status == RecordingStatus.RECORDING }
    }
    private fun sideText(side: com.runline.app.core.RouteSide) = when (side) {
        com.runline.app.core.RouteSide.LEFT -> R.string.v19_side_left; com.runline.app.core.RouteSide.RIGHT -> R.string.v19_side_right
        com.runline.app.core.RouteSide.AHEAD -> R.string.v19_side_ahead; com.runline.app.core.RouteSide.BEHIND -> R.string.v19_side_behind
    }
    private fun safely(block: () -> Unit): Boolean = try { block(); true } catch (_: Exception) {
        fail(R.string.tracking_storage_paused); false
    }
    private fun fail(reason: Int) {
        freezeTimer(); mode = if (mode == RecordingStatus.RECOVERY_ERROR) mode else if (current == null) RecordingStatus.IDLE else RecordingStatus.PAUSED
        releaseRecordingResources(); messageRes = reason
        // Best effort: this is additional protection, never the only durable copy.
        if (current != null) runCatching { persistCheckpoint() }
        publish(); updateNotification()
        if (current == null) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf() }
    }
    private fun releaseRecordingResources() {
        coachEngine?.pause(); coachSpeech?.stop(); aiCoach?.pause()
        worker.removeCallbacks(ticker); runCatching { locations.removeUpdates(this) }
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }
    /** Optional audio must never be part of the recorder's storage/error path. */
    private fun reloadCoaching() {
        runCatching {
            val preferences = VoiceCoachSettings.load(this)
            val next = current?.id?.let { com.runline.app.data.SessionIntentStore(this).get(it)?.applyTo(preferences) } ?: preferences
            if (aiCoach == null) aiCoach = AiRunCoach(this) { advice ->
                // AI callbacks arrive on main; published is the recorder's volatile snapshot.
                if (published.status == RecordingStatus.RECORDING && VoiceCoachSettings.load(this).enabled) {
                    coachSpeech?.speakAdvice(advice.text) {
                        published.status == RecordingStatus.RECORDING && !published.gpsStale &&
                            published.accuracyMeters != null && VoiceCoachSettings.load(this).enabled && advice.isValid()
                    }
                }
            }
            aiCoach?.refresh()
            coachSpeech?.invalidatePending()
            if (next != coachConfig || coachEngine == null || autoPause != RecordingSettings.autoPause(this)) {
                autoPause = RecordingSettings.autoPause(this)
                coachConfig = next
                coachEngine = CoachEngine(next, snapshotRun(),
                    terrainPoint = { point -> com.runline.app.terrain.TerrainRepository.get(this).snapshot.point(point) },
                    terrainRevision = { com.runline.app.terrain.TerrainRepository.get(this).snapshot.revision },
                    autoPause = RecordingSettings.autoPause(this))
                coachSpeech?.reset()
            }
            if (next.enabled && mode == RecordingStatus.RECORDING) {
                if (coachSpeech == null) coachSpeech = CoachSpeech(this, onDiagnostic = { event, text, source, cue, situation ->
                    published.run?.let { observed -> com.runline.app.diagnostics.Diagnostics.speech(
                        this, observed, event, text, source, cue, situation, !published.gpsStale) }
                }) { spoken ->
                    aiCoach?.onSpoken(spoken.text, spoken.source.name, spoken.elapsedRealtimeMs)
                }
                coachSpeech?.prepare()
            } else {
                coachSpeech?.close(); coachSpeech = null; aiCoach?.pause()
            }
        }
    }
    private fun updateCoaching() {
        runCatching {
            val run = snapshotRun() ?: return
            val fresh = accuracy != null && lastLocationElapsed > 0 && !isGpsStale()
            val stopped = stoppedSince()
            val cue = coachEngine?.update(run, mode == RecordingStatus.RECORDING, fresh, stopped)
            coachEngine?.situation?.let { coachSpeech?.updateSituation(it) }
            cue?.let { coachSpeech?.speak(it) }
            // A standstill is not a moment for advice; the AI coach waits for movement too.
            if (coachConfig.enabled && mode == RecordingStatus.RECORDING && stopped == null) aiCoach?.update(run, fresh, coachConfig, coachEngine?.situation?.terrain ?: com.runline.app.core.CoachTerrain.UNKNOWN)
        }
    }
    private fun notification(): Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)!!
            .putExtra(EXTRA_OPEN_RECORDING, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val state = published.localized(this)
        val run = state.run
        val recording = state.status == RecordingStatus.RECORDING
        val distance = run?.let { RunMetrics.distanceMeters(it.points) } ?: 0.0
        val stopped = recording && state.stoppedSinceMs != null
        // With auto-pause, time and pace exclude standstills, matching the recording screen.
        val duration = run?.let { if (RecordingSettings.autoPause(this)) com.runline.app.core.StopDetection.movingDurationMs(it, openStopSinceMs = state.stoppedSinceMs) else it.activeDurationMs } ?: 0L
        val formatLocale = AppLanguage.localizedContext(this).resources.configuration.locales[0]
        val paceSeconds = RunMetrics.paceSecondsPerKm(distance, duration)?.roundToLong()
        val pace = paceSeconds?.let { String.format(formatLocale, "%d:%02d", it / 60, it % 60) } ?: "—"
        val metrics = tr(R.string.tracking_metrics, distance / 1000, pace)
        val elapsedSeconds = duration.coerceAtLeast(0) / 1000
        val elapsed = if (elapsedSeconds >= 3600)
            String.format(formatLocale, "%d:%02d:%02d", elapsedSeconds / 3600, elapsedSeconds / 60 % 60, elapsedSeconds % 60)
        else String.format(formatLocale, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
        val routeProgress = state.route?.progress
        val offRoute = recording && routeProgress?.state == com.runline.app.core.RouteState.OFF_ROUTE
        val title = when {
            offRoute -> tr(R.string.v19_notification_off_route, routeProgress!!.distanceFromRouteMeters.roundToLong())
            stopped && RecordingSettings.autoPause(this) -> tr(R.string.v17_stopped_auto_paused)
            recording -> tr(when (run?.activityType) {
                ActivityType.WALK -> R.string.tracking_walking
                ActivityType.HIKE -> R.string.tracking_hiking
                else -> R.string.tracking_running
            })
            run != null && state.status == RecordingStatus.INTERRUPTED -> tr(R.string.tracking_recovered)
            run != null -> tr(R.string.tracking_notification_paused, elapsed)
            else -> tr(R.string.tracking_opening)
        }
        val detail = state.message.takeUnless { state.messageRes == R.string.tracking_paused }
        val routeLine = routeProgress?.takeIf { it.state != com.runline.app.core.RouteState.APPROACHING }?.let {
            if (it.state == com.runline.app.core.RouteState.FINISHED) tr(R.string.v19_route_complete_short) else tr(R.string.v19_route_left_short, it.remainingMeters / 1000)
        }
        val expanded = listOfNotNull(metrics, routeLine, if (!recording) tr(R.string.tracking_active_time, elapsed) else null, detail).joinToString("\n")
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_run)
            .setContentTitle(title).setContentText(if (run == null) tr(R.string.tracking_saved_on_phone) else metrics)
            .setContentIntent(open).setStyle(Notification.BigTextStyle().bigText(expanded))
            .setWhen(System.currentTimeMillis() - duration).setShowWhen(recording && !stopped).setUsesChronometer(recording && !stopped)
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (Build.VERSION.SDK_INT >= 31) builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (run != null) {
            if (recording) {
                builder.addAction(notificationAction(R.drawable.ic_pause, tr(R.string.tracking_action_pause), ACTION_PAUSE, run.id))
            } else if (state.status == RecordingStatus.PAUSED || state.status == RecordingStatus.INTERRUPTED) {
                builder.addAction(notificationAction(R.drawable.ic_play, tr(R.string.tracking_action_resume), ACTION_RESUME, run.id))
                // Two deliberate steps: Pause, then Finish. A stale Finish is rejected if
                // recording has already resumed, even when it still belongs to this run.
                builder.addAction(notificationAction(R.drawable.ic_stop, tr(R.string.tracking_action_finish), ACTION_FINISH, run.id))
            }
        }
        return builder.build()
    }
    private fun refreshNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, tr(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = tr(R.string.tracking_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
    }

    private fun tr(id: Int, vararg args: Any): String = AppLanguage.localizedContext(this).getString(id, *args)

    private fun notificationAction(icon: Int, label: String, action: String, runId: String): Notification.Action {
        val intent = Intent(this, TrackingService::class.java).setAction(action)
            .setData(Uri.Builder().scheme("runline").authority("recording").appendPath(runId).appendPath(action).build())
            .putExtra(EXTRA_NOTIFICATION_RUN_ID, runId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // A user tapping a notification is an explicit exemption to both background FGS
        // start restrictions and Android 14's while-in-use location checks.
        val pending = if (action == ACTION_RESUME) PendingIntent.getForegroundService(this, 0, intent, flags)
            else PendingIntent.getService(this, 0, intent, flags)
        return Notification.Action.Builder(Icon.createWithResource(this, icon), label, pending).build()
    }
    private fun updateNotification() { if (foreground) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification()) }
    override fun onDestroy() {
        runCatching { unregisterReceiver(shutdown) }; stopForeground(STOP_FOREGROUND_REMOVE); foreground = false
        worker.post {
            freezeTimer(); releaseRecordingResources()
            coachSpeech?.close(); coachSpeech = null
            aiCoach?.close(); aiCoach = null
            if (current != null) {
                mode = RecordingStatus.INTERRUPTED; runCatching { persistCheckpoint() }
                messageRes = R.string.tracking_recovered_hint; publish()
            }
            if (instance === this) instance = null
            thread.quitSafely()
        }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.runline.app.START"
        const val ACTION_PAUSE = "com.runline.app.PAUSE"
        const val ACTION_RESUME = "com.runline.app.RESUME"
        const val ACTION_FINISH = "com.runline.app.FINISH"
        const val ACTION_DISCARD = "com.runline.app.DISCARD"
        const val ACTION_ARCHIVE_DRAFT = "com.runline.app.ARCHIVE_DRAFT"
        const val EXTRA_SESSION_INTENT = "com.runline.app.SESSION_INTENT"
        const val EXTRA_ACTIVITY_TYPE = "com.runline.app.ACTIVITY_TYPE"
        const val EXTRA_OPEN_RECORDING = "com.runline.app.OPEN_RECORDING"
        private const val EXTRA_NOTIFICATION_RUN_ID = "com.runline.app.NOTIFICATION_RUN_ID"
        private const val CHANNEL = "run_recording"
        private const val NOTIFICATION = 1001
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        @Volatile private var instance: TrackingService? = null
        @Volatile private var cached: TrackingState? = null
        fun state(context: Context): TrackingState {
            instance?.let { return it.published.localized(context) }; cached?.let { return it.localized(context) }
            val recovered = try {
                RunStore(context).loadDraftRecord()?.let {
                    TrackingState(RecordingStatus.INTERRUPTED, it.run,
                        messageRes = if (it.recoveredWithDamage) R.string.tracking_recovered_damage else R.string.tracking_recovered_hint,
                        lastSavedAtMs = it.run.endedAtMs)
                } ?: TrackingState()
            } catch (_: Exception) { TrackingState(RecordingStatus.RECOVERY_ERROR, messageRes = R.string.tracking_recovery_error) }
            cached = recovered; return recovered.localized(context)
        }
        /** A language change must also update an existing paused notification. */
        fun refreshLanguage() {
            instance?.let { recorder -> recorder.worker.post {
                recorder.refreshNotificationChannel()
                recorder.published = recorder.published.localized(recorder)
                cached = recorder.published
                recorder.updateNotification()
            } }
        }
        /** Apply optional voice preferences without starting or altering a recording. */
        fun refreshCoaching() {
            instance?.let { recorder -> recorder.worker.post { recorder.reloadCoaching() } }
        }
        /** Visible Activity controls; notifications use their own run-scoped PendingIntents. */
        fun command(context: Context, action: String, activityType: ActivityType = ActivityType.RUN, sessionIntent: com.runline.app.core.SessionIntent? = null) {
            val intent = Intent(context, TrackingService::class.java).setAction(action)
            if (action == ACTION_START) { intent.putExtra(EXTRA_ACTIVITY_TYPE, activityType.storageValue); sessionIntent?.let { intent.putExtra(EXTRA_SESSION_INTENT, it.json().toString()) } }
            if (action == ACTION_START || action == ACTION_RESUME) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
