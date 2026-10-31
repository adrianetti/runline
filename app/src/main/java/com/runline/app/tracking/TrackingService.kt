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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import com.runline.app.R
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import com.runline.app.data.DraftRecord
import com.runline.app.data.RunStore
import java.util.Locale
import java.util.UUID

enum class RecordingStatus { IDLE, RECORDING, PAUSED, INTERRUPTED, RECOVERY_ERROR }

data class TrackingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val run: RunSession? = null,
    val accuracyMeters: Float? = null,
    val message: String? = null,
    val savedRunId: String? = null,
    val lastSavedAtMs: Long? = null,
    val gpsStale: Boolean = false,
)

/** User-started foreground recorder. UI closure never stops it; force-stop and reboot never auto-start it. */
class TrackingService : Service(), LocationListener {
    private lateinit var store: RunStore
    private lateinit var locations: LocationManager
    private val thread = HandlerThread("RunlineRecorder")
    private lateinit var worker: Handler
    private var wakeLock: PowerManager.WakeLock? = null
    private var current: RunSession? = null
    private var recovered: DraftRecord? = null
    private var mode = RecordingStatus.IDLE
    private var activeSince = 0L
    private var lastLocationElapsed = 0L
    private var segment = 0
    private var tickCount = 0
    private var accuracy: Float? = null
    private var message: String? = null
    private var lastSavedAt: Long? = null
    private var bootCount = -1
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
            if (isGpsStale()) { accuracy = null; message = "GPS lost · time continues" }
            if (tickCount % 300 == 0) wakeLock?.acquire(WAKE_LOCK_TIMEOUT_MS)
            publish()
            if (tickCount % 10 == 0) updateNotification()
            worker.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = RunStore(this)
        locations = getSystemService(LocationManager::class.java)
        bootCount = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
        thread.start(); worker = Handler(thread.looper)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Run recording", NotificationManager.IMPORTANCE_LOW))
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
                    message = if (recovered?.recoveredWithDamage == true) "Recovered available points · check your route" else "Run recovered · resume or save"
                }
            } catch (_: Exception) { mode = RecordingStatus.RECOVERY_ERROR; message = "Draft needs recovery · files kept" }
            publish()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null || action == ACTION_START || action == ACTION_RESUME) {
            try {
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                else startForeground(NOTIFICATION, notification())
                foreground = true
            } catch (_: RuntimeException) {
                worker.post { fail("Open the app to resume recording"); stopSelf() }
                return START_NOT_STICKY
            }
        }
        worker.post {
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
                ACTION_START -> startRun()
                ACTION_RESUME -> resumeRun()
                ACTION_PAUSE -> pauseRun()
                ACTION_FINISH -> finishRun()
                ACTION_DISCARD -> discardRun()
                ACTION_ARCHIVE_DRAFT -> archiveDraft()
            }
        }
        return if (action == null || action == ACTION_START || action == ACTION_RESUME) START_STICKY else START_NOT_STICKY
    }

    private fun startRun() {
        if (mode == RecordingStatus.RECOVERY_ERROR) { fail("Set aside the damaged draft first"); return }
        if (current != null) { publish(); updateNotification(); return }
        val now = System.currentTimeMillis()
        val run = RunSession(UUID.randomUUID().toString(), now, now, 0L, emptyList())
        if (!safely { store.beginDraft(run) }) return
        current = run; lastSavedAt = now; segment = 0
        resumeRun()
    }

    private fun resumeRun() {
        if (mode == RecordingStatus.RECORDING) return
        if (current == null) { stopSelf(); return }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fail("Allow precise location to resume"); return
        }
        if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) { fail("Turn on location, then resume"); return }
        try {
            // Neither the timer nor the route connects across any process outage or pause.
            segment = (current!!.points.maxOfOrNull { it.segment } ?: -1) + 1
            activeSince = SystemClock.elapsedRealtime(); lastLocationElapsed = 0L; tickCount = 0
            mode = RecordingStatus.RECORDING; accuracy = null; message = "Finding GPS"
            persistCheckpoint()
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, thread.looper)
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Runline:recording").apply {
                    setReferenceCounted(false); acquire(WAKE_LOCK_TIMEOUT_MS)
                }
            worker.removeCallbacks(ticker); worker.post(ticker)
            publish(); updateNotification()
        } catch (_: Exception) { fail("Recording paused · check location and storage") }
    }

    private fun pauseRun() {
        if (current == null) { stopSelf(); return }
        freezeTimer(); mode = RecordingStatus.PAUSED
        releaseRecordingResources(); message = "Paused"
        safely { persistCheckpoint() }
        publish(); updateNotification()
    }

    private fun finishRun() {
        if (current == null) { stopSelf(); return }
        pauseRun()
        val finished = current!!.copy(endedAtMs = System.currentTimeMillis())
        if (!safely { store.complete(finished) }) return
        current = null; mode = RecordingStatus.IDLE
        published = TrackingState(savedRunId = finished.id, lastSavedAtMs = System.currentTimeMillis()); cached = published
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }

    private fun discardRun() {
        if (mode == RecordingStatus.RECOVERY_ERROR) { fail("Set aside the damaged draft to keep its recovery files"); return }
        freezeTimer(); mode = RecordingStatus.PAUSED; releaseRecordingResources()
        if (!safely { store.discardDraft() }) return
        current = null; mode = RecordingStatus.IDLE; published = TrackingState(); cached = published
        stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }

    private fun archiveDraft() {
        if (mode != RecordingStatus.RECOVERY_ERROR || current != null) { message = "Finish your current run first"; publish(); return }
        try { store.archiveDraft(); mode = RecordingStatus.IDLE; message = "Draft kept in recovery storage" }
        catch (_: Exception) { message = "Recovery files kept · free space and retry" }
        publish(); stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        if (mode != RecordingStatus.RECORDING) return
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy !in 0f..30f || ageMs !in 0..15_000 ||
            !location.latitude.isFinite() || location.latitude !in -90.0..90.0 || !location.longitude.isFinite() || location.longitude !in -180.0..180.0) {
            accuracy = null; message = "Weak GPS · time continues"; publish(); return
        }
        val elapsed = location.elapsedRealtimeNanos / 1_000_000
        if (elapsed < activeSince || location.time < 0) return // Never import a cached fix from before Start/Resume.
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
            if (location.hasAltitude() && location.altitude.isFinite()) location.altitude else null, segment)
        val checkpoint = draftRecord()
        if (!safely { store.appendPoint(point, checkpoint) }) return
        current = current!!.copy(points = current!!.points + point)
        lastLocationElapsed = elapsed; lastSavedAt = System.currentTimeMillis()
        accuracy = location.accuracy; message = null
        publish()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Removing the activity from Recents does not stop this foreground service.
        worker.post { if (current != null) safely { persistCheckpoint() } }
        super.onTaskRemoved(rootIntent)
    }
    override fun onProviderDisabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) fail("Location off · turn it on and resume") }
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
    private fun publish() { published = TrackingState(mode, snapshotRun(), accuracy, message, lastSavedAtMs = lastSavedAt, gpsStale = isGpsStale()); cached = published }
    private fun safely(block: () -> Unit): Boolean = try { block(); true } catch (_: Exception) {
        fail("Recording paused · free storage and save again"); false
    }
    private fun fail(reason: String) {
        freezeTimer(); mode = if (mode == RecordingStatus.RECOVERY_ERROR) mode else if (current == null) RecordingStatus.IDLE else RecordingStatus.PAUSED
        releaseRecordingResources(); message = reason
        // Best effort: this is additional protection, never the only durable copy.
        if (current != null) runCatching { persistCheckpoint() }
        publish(); updateNotification()
        if (current == null) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf() }
    }
    private fun releaseRecordingResources() {
        worker.removeCallbacks(ticker); runCatching { locations.removeUpdates(this) }
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, packageManager.getLaunchIntentForPackage(packageName)!!, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val state = published; val distance = state.run?.let { RunMetrics.distanceMeters(it.points) / 1000 } ?: 0.0
        val builder = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_run)
            .setContentTitle(if (state.status == RecordingStatus.RECORDING) "Runline · Recording" else "Runline · Paused")
            .setContentText(String.format(Locale.getDefault(), "%.2f km · Open run", distance)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE).setCategory(Notification.CATEGORY_SERVICE)
        if (state.status == RecordingStatus.RECORDING) {
            val pause = PendingIntent.getService(this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "Pause", pause).build())
        } else builder.addAction(Notification.Action.Builder(null, "Open run", open).build())
        return builder.build()
    }
    private fun updateNotification() { if (foreground) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification()) }
    override fun onDestroy() {
        runCatching { unregisterReceiver(shutdown) }; stopForeground(STOP_FOREGROUND_REMOVE); foreground = false
        worker.post {
            freezeTimer(); releaseRecordingResources()
            if (current != null) {
                mode = RecordingStatus.INTERRUPTED; runCatching { persistCheckpoint() }
                message = "Run recovered · resume or save"; publish()
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
        private const val CHANNEL = "run_recording"
        private const val NOTIFICATION = 1001
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        @Volatile private var instance: TrackingService? = null
        @Volatile private var cached: TrackingState? = null
        fun state(context: Context): TrackingState {
            instance?.let { return it.published }; cached?.let { return it }
            val recovered = try {
                RunStore(context).loadDraftRecord()?.let {
                    TrackingState(RecordingStatus.INTERRUPTED, it.run,
                        message = if (it.recoveredWithDamage) "Recovered available points · check your route" else "Run recovered · resume or save",
                        lastSavedAtMs = it.run.endedAtMs)
                } ?: TrackingState()
            } catch (_: Exception) { TrackingState(RecordingStatus.RECOVERY_ERROR, message = "Draft needs recovery · files kept") }
            cached = recovered; return recovered
        }
        /** START/RESUME are explicit actions from the visible Activity; sticky restart is system-managed. */
        fun command(context: Context, action: String) {
            val intent = Intent(context, TrackingService::class.java).setAction(action)
            if (action == ACTION_START || action == ACTION_RESUME) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
