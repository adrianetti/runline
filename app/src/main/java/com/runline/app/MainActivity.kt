package com.runline.app

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.PowerManager
import android.view.MenuItem
import android.widget.ImageView
import android.content.res.Configuration
import android.graphics.Typeface
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.runline.app.sharing.StravaSharing
import com.runline.app.core.GpxExporter
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunSession
import com.runline.app.data.RunStore
import com.runline.app.tracking.TrackingService
import com.runline.app.tracking.RecordingStatus as TrackingStatus
import com.runline.app.ui.SatelliteMapView
import com.runline.app.ui.RunReplayView
import com.runline.app.ui.ActiveRunView
import androidx.appcompat.widget.PopupMenu
import com.runline.app.core.RunReplay
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabs: TabLayout
    private lateinit var body: LinearLayout
    private lateinit var store: RunStore
    private lateinit var stravaSharing: StravaSharing
    private var actionPendingUntil = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var screen = "record"
    private var currentRun: RunSession? = null
    private var renderedStatus = ""
    private var lastSavedId: String? = null
    private var pendingAction = TrackingService.ACTION_START
    private var pendingExport: RunSession? = null
    private var distanceLabel: TextView? = null
    private var durationLabel: TextView? = null
    private var paceLabel: TextView? = null
    private var gpsLabel: TextView? = null
    private var route: SatelliteMapView? = null
    private var replayView: RunReplayView? = null
    private var restoredReplayPosition = 0L
    private var restoredReplaySpeed = 1
    private var activeRunView: ActiveRunView? = null
    private var liveSpeedLabel: TextView? = null
    private var liveAltitudeLabel: TextView? = null
    private val ticker = object : Runnable {
        override fun run() { refreshRecording(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RunStore(this)
        val isNight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = !isNight; isAppearanceLightNavigationBars = !isNight }
        if (Build.VERSION.SDK_INT < 27) window.navigationBarColor = android.graphics.Color.BLACK
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(getColor(R.color.surface)) }
        setContentView(root)
        toolbar = MaterialToolbar(this).apply { title = "Runline"; setTitleTextColor(getColor(R.color.on_surface)) }
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(64)))
        tabs = TabLayout(this).apply {
            isInlineLabel = true
            addTab(newTab().setText("Record").setIcon(R.drawable.ic_run)); addTab(newTab().setText("Runs").setIcon(R.drawable.ic_history))
            tabIconTint = ColorStateList.valueOf(getColor(R.color.primary))
            setSelectedTabIndicatorColor(getColor(R.color.primary))
            setTabTextColors(getColor(R.color.on_surface_variant), getColor(R.color.primary))
        }
        root.addView(tabs, LinearLayout.LayoutParams(-1, dp(48)))
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { screen = if (tab.position == 0) "record" else "history"; render() }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        stravaSharing = StravaSharing(this, { id -> runCatching { store.get(id) }.getOrNull() }, ::notice)
        stravaSharing.restoreState(savedInstanceState)
        lastSavedId = TrackingService.state(this).savedRunId
        screen = savedInstanceState?.getString("screen") ?: "record"
        val selectedRunId = savedInstanceState?.getString("runId")
        if (selectedRunId != null) currentRun = runCatching { store.get(selectedRunId) }.getOrNull()
        pendingExport = savedInstanceState?.getString("exportId")?.let { runCatching { store.get(it) }.getOrNull() }
        restoredReplayPosition = savedInstanceState?.getLong("replayPosition") ?: 0L
        restoredReplaySpeed = savedInstanceState?.getInt("replaySpeed") ?: 1
        if (intent.getBooleanExtra(TrackingService.EXTRA_OPEN_RECORDING, false)) {
            intent.removeExtra(TrackingService.EXTRA_OPEN_RECORDING); screen = "record"
        }
        if (screen == "detail" && currentRun == null) screen = "history"
        if (screen == "history") tabs.getTabAt(1)?.select()
        render()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.getBooleanExtra(TrackingService.EXTRA_OPEN_RECORDING, false)) {
            intent.removeExtra(TrackingService.EXTRA_OPEN_RECORDING)
            screen = "record"; tabs.getTabAt(0)?.select(); render()
        }
    }
    override fun onResume() { super.onResume(); route?.onHostResume(); replayView?.onHostResume(); handler.removeCallbacks(ticker); handler.post(ticker); if (::stravaSharing.isInitialized) stravaSharing.onResume() }
    override fun onLowMemory() { super.onLowMemory(); route?.onLowMemory(); replayView?.map?.onLowMemory() }
    override fun onDestroy() { handler.removeCallbacks(ticker); route?.dispose(); replayView?.dispose(); if (::stravaSharing.isInitialized) stravaSharing.close(); super.onDestroy() }
    override fun onPause() { handler.removeCallbacks(ticker); route?.onHostPause(); replayView?.onHostPause(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", screen); outState.putString("runId", currentRun?.id)
        outState.putString("exportId", pendingExport?.id)
        outState.putLong("replayPosition", replayView?.positionMs ?: restoredReplayPosition)
        outState.putInt("replaySpeed", replayView?.playbackSpeed ?: restoredReplaySpeed)
        stravaSharing.saveState(outState); super.onSaveInstanceState(outState)
    }
    @Deprecated("Uses platform Back for this single-activity prototype")
    override fun onBackPressed() {
        if (screen == "detail") { screen = "history"; tabs.getTabAt(1)?.select(); render() }
        else if (screen == "history") { tabs.getTabAt(0)?.select() }
        else super.onBackPressed()
    }

    private fun render() {
        route?.dispose(); replayView?.dispose(); replayView = null
        body.removeAllViews(); distanceLabel = null; durationLabel = null; paceLabel = null; gpsLabel = null; route = null
        liveSpeedLabel = null; liveAltitudeLabel = null; activeRunView = null
        val state = TrackingService.state(this)
        val live = screen == "record" && state.run != null && state.status != TrackingStatus.RECOVERY_ERROR
        toolbar.visibility = if (live) View.GONE else View.VISIBLE
        toolbar.title = if (screen == "detail") "Run" else "Runline"
        tabs.visibility = if (screen == "detail" || live) View.GONE else View.VISIBLE
        toolbar.navigationIcon = if (screen == "detail") AppCompatResources.getDrawable(this, R.drawable.ic_back) else null
        toolbar.navigationIcon?.setTint(getColor(R.color.on_surface))
        toolbar.navigationContentDescription = "Back to runs"
        configureMenu()
        toolbar.setNavigationOnClickListener { screen = "history"; tabs.getTabAt(1)?.select(); render() }
        when (screen) { "history" -> renderHistory(); "detail" -> currentRun?.let { renderDetail(it) }; else -> renderRecord() }
    }
    private fun scrollContent(): LinearLayout {
        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        scroll.addView(column, ViewGroup.LayoutParams(-1, -2)); body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return column
    }
    private fun renderRecord() {
        val state = TrackingService.state(this)
        renderedStatus = state.status.name
        if (state.run != null && state.status != TrackingStatus.RECOVERY_ERROR) {
            activeRunView = ActiveRunView(this, state,
                onOpenRuns = { tabs.getTabAt(1)?.select() },
                onOptions = ::showRunOptions,
                onPause = { command(TrackingService.ACTION_PAUSE) },
                onResume = { requestRecording(TrackingService.ACTION_RESUME) },
                onFinish = { command(TrackingService.ACTION_FINISH) },
            ).also { view ->
                route = view.map
                body.addView(view, LinearLayout.LayoutParams(-1, -1))
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) view.map.onHostResume()
            }
            return
        }
        val content = scrollContent()
        val title = when (state.status) {
            TrackingStatus.RECORDING -> "Recording"
            TrackingStatus.PAUSED -> "Paused"
            TrackingStatus.INTERRUPTED -> "Run recovered"
            TrackingStatus.RECOVERY_ERROR -> "Draft needs recovery"
            else -> "Ready"
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(iconView(if (state.status == TrackingStatus.RECORDING) R.drawable.ic_location else R.drawable.ic_run), LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(12) })
        heading.addView(text(title, 24, bold = true))
        content.addView(heading, margin(bottom = 16))
        if (state.status == TrackingStatus.RECOVERY_ERROR) {
            content.addView(text("Keep the damaged draft and start fresh. Saved runs stay untouched.", 14, muted = true))
        } else {
            route = SatelliteMapView(this).also { map ->
                map.setFollowing(true)
                if (state.run?.points.isNullOrEmpty()) centerOnLastKnownLocation(map)
                content.addView(map, LinearLayout.LayoutParams(-1, dp(if (resources.configuration.screenHeightDp < 500) 120 else 240)).apply { bottomMargin = dp(16) })
                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) map.onHostResume()
            }
            val bigMetric = LinearLayout(this).apply { gravity = Gravity.BOTTOM }
            distanceLabel = text("0.00", 40, bold = true).apply { fontFeatureSettings = "tnum" }
            bigMetric.addView(distanceLabel)
            bigMetric.addView(text("km", 24, muted = true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8); bottomMargin = dp(9) })
            content.addView(bigMetric)
            val metrics = LinearLayout(this)
            val time = metric("Time", "00:00"); durationLabel = time.second
            val pace = metric("Pace /km", "—"); paceLabel = pace.second
            metrics.addView(time.first, LinearLayout.LayoutParams(0, -2, 1f))
            metrics.addView(pace.first, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(metrics, margin(top = 12, bottom = 12))
            if (state.run != null) {
                val extra = LinearLayout(this)
                val speed = metric("Speed · km/h", "—"); liveSpeedLabel = speed.second
                val altitude = metric("GPS altitude · m", "—"); liveAltitudeLabel = altitude.second
                extra.addView(speed.first, LinearLayout.LayoutParams(0, -2, 1f))
                extra.addView(altitude.first, LinearLayout.LayoutParams(0, -2, 1f))
                content.addView(extra, margin(bottom = 8))
            }
        }
        gpsLabel = text("Start outdoors", 14, muted = true)
        content.addView(gpsLabel, margin(top = 16))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(16)) }
        when (state.status) {
            TrackingStatus.RECORDING -> actions.addView(button("Pause", iconId = R.drawable.ic_pause) { command(TrackingService.ACTION_PAUSE) })
            TrackingStatus.PAUSED, TrackingStatus.INTERRUPTED -> {
                actions.addView(button("Resume", iconId = R.drawable.ic_play) { requestRecording(TrackingService.ACTION_RESUME) })
                actions.addView(button("Finish", outlined = true, iconId = R.drawable.ic_stop) { command(TrackingService.ACTION_FINISH) }, margin(top = 8))
            }
            TrackingStatus.RECOVERY_ERROR -> actions.addView(button("Keep draft & reset", iconId = R.drawable.ic_shield_check) {
                MaterialAlertDialogBuilder(this).setTitle("Set aside this draft?")
                    .setMessage("A copy stays on this phone for recovery. Your saved runs are unchanged.")
                    .setPositiveButton("Keep & reset") { _, _ -> command(TrackingService.ACTION_ARCHIVE_DRAFT) }.setNegativeButton("Cancel", null).show()
            })
            else -> actions.addView(button("Start run", iconId = R.drawable.ic_play) { requestRecording(TrackingService.ACTION_START) })
        }
        body.addView(actions)
        updateMetrics()
    }
    private fun refreshRecording() {
        val state = TrackingService.state(this)
        if (state.savedRunId != null && state.savedRunId != lastSavedId) {
            lastSavedId = state.savedRunId
            runCatching { store.get(state.savedRunId) }.getOrNull()?.let { openRun(it); return }
        }
        if (screen != "record") return
        if (state.status.name != renderedStatus) render() else updateMetrics()
    }
    private fun updateMetrics() {
        val state = TrackingService.state(this); val run = state.run
        activeRunView?.let { it.update(state); return }
        val distance = RunMetrics.distanceMeters(run?.points ?: emptyList())
        setValue(distanceLabel, String.format(Locale.getDefault(), "%.2f", distance / 1000))
        setValue(durationLabel, duration(run?.activeDurationMs ?: 0))
        setValue(paceLabel, pace(RunMetrics.paceSecondsPerKm(distance, run?.activeDurationMs ?: 0)))
        val points = run?.points ?: emptyList()
        route?.setRoute(points)
        route?.setMarker(points.lastOrNull())
        val latest = points.lastOrNull()
        val recent = latest != null && !state.gpsStale && state.status == TrackingStatus.RECORDING
        val speed = if (recent && run != null) {
            val tail = RunReplay(run.copy(points = points.takeLast(12)))
            tail.at(tail.durationMs)?.speedMetersPerSecond?.times(3.6)
        } else null
        setValue(liveSpeedLabel, number(speed, 1))
        setValue(liveAltitudeLabel, number(latest?.altitudeMeters, 0))
        val status = when {
            state.status == TrackingStatus.IDLE -> state.message ?: "Start outdoors"
            state.status == TrackingStatus.INTERRUPTED -> "Saved run recovered. Resume or finish."
            state.status == TrackingStatus.PAUSED -> state.message ?: "Time and GPS paused"
            state.message != null -> state.message
            state.accuracyMeters != null -> "GPS ±${state.accuracyMeters.roundToInt()} m · saving on phone"
            else -> "Finding GPS…"
        }
        setValue(gpsLabel, status)
    }
    private fun setValue(view: TextView?, value: CharSequence) { if (view?.text != value) view?.text = value }
    private fun renderHistory() {
        val content = scrollContent()
        val runs = try { store.list() } catch (e: Exception) { notice("Couldn't read run history. Your files have been kept."); emptyList() }
        content.addView(text("This week", 24, bold = true))
        val recent = runs.filter { it.startedAtMs >= System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L }
        val km = recent.sumOf { RunMetrics.distanceMeters(it.points) } / 1000
        content.addView(text("${String.format(Locale.getDefault(), "%.1f", km)} km · ${recent.size} runs · last 7 days", 16, muted = true), margin(top = 8, bottom = 28))
        if (runs.isEmpty()) {
            content.addView(text("No runs yet", 24, bold = true), margin(top = 28))
            content.addView(button("Start a run", iconId = R.drawable.ic_play) { tabs.getTabAt(0)?.select() })
        } else {
            runs.forEach { run ->
                val distance = RunMetrics.distanceMeters(run.points)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(20), dp(4), dp(20)); minimumHeight = dp(80)
                    isClickable = true; isFocusable = true
                    val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)); background = attrs.getDrawable(0); attrs.recycle()
                    setOnClickListener { openRun(run) }
                    contentDescription = "${date(run.startedAtMs)}, ${String.format(Locale.getDefault(), "%.2f", distance / 1000)} kilometres, ${duration(run.activeDurationMs)}. View run."
                }
                row.addView(text(date(run.startedAtMs), 14, muted = true))
                row.addView(text("${String.format(Locale.getDefault(), "%.2f", distance / 1000)} km", 26, bold = true), margin(top = 6))
                row.addView(text("${duration(run.activeDurationMs)} active · ${pace(RunMetrics.paceSecondsPerKm(distance, run.activeDurationMs))} /km", 14, muted = true), margin(top = 4))
                content.addView(row); content.addView(divider())
            }
        }
        content.addView(text("Saved on this phone", 14, muted = true), margin(top = 24))
    }
    private fun centerOnLastKnownLocation(map: SatelliteMapView) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val manager = getSystemService(LocationManager::class.java)
        val last = runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        if (last != null) map.setCenter(last.latitude, last.longitude)
    }
    private fun openRun(run: RunSession) { currentRun = run; restoredReplayPosition = 0L; restoredReplaySpeed = 1; screen = "detail"; render() }
    private fun renderDetail(run: RunSession) {
        val content = scrollContent()
        val replay = RunReplay(run)
        val distance = replay.summary.distanceMeters
        val compact = resources.configuration.screenHeightDp < 500
        content.addView(text(date(run.startedAtMs), 14, muted = true), margin(bottom = if (compact) 8 else 0))
        if (!compact) content.addView(text("${number(distance / 1000, 2)} km", 36, bold = true), margin(top = 8, bottom = 16))
        val metrics = LinearLayout(this)
        if (compact) metrics.addView(metric("Distance · km", number(distance / 1000, 2)).first, LinearLayout.LayoutParams(0, -2, 1f))
        val time = metric("Time", duration(run.activeDurationMs))
        val pace = metric("Average pace /km", pace(RunMetrics.paceSecondsPerKm(distance, run.activeDurationMs)))
        metrics.addView(time.first, LinearLayout.LayoutParams(0, -2, 1f)); metrics.addView(pace.first, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(metrics, margin(bottom = if (compact) 12 else 20))
        replayView = RunReplayView(this, replay).also { view ->
            view.restore(restoredReplayPosition, restoredReplaySpeed)
            content.addView(view)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) view.onHostResume()
        }
        if (replay.durationMs == 0L) content.addView(text("No continuous GPS route to share", 14, muted = true), margin(top = 16))
        content.addView(text("Run totals", 24, bold = true), margin(top = 24, bottom = 8))
        val summary = replay.summary
        addDetail(content, "Average speed", "${number(summary.averageSpeedMetersPerSecond?.times(3.6), 1)} km/h")
        addDetail(content, "Top GPS speed", "${number(summary.maxSpeedMetersPerSecond?.times(3.6), 1)} km/h")
        addDetail(content, "Ascent / descent", "${number(summary.elevationGainMeters, 0)} / ${number(summary.elevationLossMeters, 0)} m")
        addDetail(content, "Altitude low / high", "${number(summary.minAltitudeMeters, 0)} / ${number(summary.maxAltitudeMeters, 0)} m")
        addDetail(content, "Elapsed time", duration((run.endedAtMs - run.startedAtMs).coerceAtLeast(0)))
        addDetail(content, "GPS samples", run.points.size.toString())
        content.addView(text("Speed and elevation are GPS estimates.", 13, muted = true), margin(top = 12, bottom = 24))
        content.addView(text("Splits", 24, bold = true), margin(bottom = 8))
        val splits = RunMetrics.splits(run.copy(points = replay.segments.flatten()))
        if (splits.isEmpty()) content.addView(text("Not enough GPS data", 14, muted = true))
        else splits.forEach { split ->
            val label = if (split.distanceMeters >= 999.5) "Km ${split.index}" else "Last ${number(split.distanceMeters / 1000, 2)} km"
            addDetail(content, label, pace(RunMetrics.paceSecondsPerKm(split.distanceMeters, split.durationMs)) + " /km")
        }
    }
    private fun configureMenu() {
        toolbar.menu.clear()
        fun item(id: Int, title: String, iconId: Int, visible: Boolean = false) = toolbar.menu.add(0, id, id, title).apply {
            setIcon(iconId); icon?.setTint(getColor(R.color.on_surface)); setShowAsAction(if (visible) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER)
        }
        if (screen == "detail") {
            item(8, "Strava", R.drawable.ic_upload, true).apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                contentDescription = "Share run to Strava"
                isEnabled = (currentRun?.points?.size ?: 0) >= 2
                actionView = MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Strava"; isAllCaps = false; textSize = 14f
                    setIconResource(R.drawable.ic_upload); iconSize = dp(20); iconPadding = dp(6)
                    contentDescription = "Share run to Strava"
                    minHeight = dp(48); minimumHeight = dp(48)
                    setPadding(dp(8), 0, dp(8), 0)
                    layoutParams = ViewGroup.LayoutParams(-2, dp(48))
                    isEnabled = (currentRun?.points?.size ?: 0) >= 2
                    setOnClickListener { currentRun?.let { stravaSharing.start(it) } }
                }
            }
            item(1, "Export GPX", R.drawable.ic_download)
            item(2, "Share file", R.drawable.ic_share)
            item(3, "Run info", R.drawable.ic_info)
            item(4, "Delete run", R.drawable.ic_trash)
        } else {
            item(5, "Recording protection", R.drawable.ic_shield_check, true)
            val status = TrackingService.state(this).status
            if (status == TrackingStatus.PAUSED || status == TrackingStatus.INTERRUPTED) item(6, "Discard run", R.drawable.ic_trash)
            item(7, "About", R.drawable.ic_info)
        }
        toolbar.overflowIcon = AppCompatResources.getDrawable(this, R.drawable.ic_more)?.apply { setTint(getColor(R.color.on_surface)) }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> currentRun?.let { saveGpx(it) }
                2 -> currentRun?.let { shareGpx(it) }
                3 -> currentRun?.let { run -> MaterialAlertDialogBuilder(this).setTitle("Run info")
                    .setMessage("Elapsed ${duration((run.endedAtMs-run.startedAtMs).coerceAtLeast(0))} · ${run.points.size} GPS samples\n\nAverage pace uses active time. Replay and splits use GPS time, skipping pauses and gaps over 15 seconds. Replay speed is a five-second GPS estimate. Altitude is raw GPS altitude; ascent and descent ignore changes below 3 m and are not terrain-corrected. Missing measurements show —.")
                    .setPositiveButton("Done", null).show() }
                4 -> currentRun?.let { run -> MaterialAlertDialogBuilder(this).setTitle("Delete run?").setMessage("Export a copy first to keep it.")
                    .setNegativeButton("Keep", null).setPositiveButton("Delete") { _, _ -> try { store.delete(run.id); screen="history"; render() } catch (e: Exception) { notice("Couldn't delete. Try again.") } }.show() }
                8 -> currentRun?.let { stravaSharing.start(it) }
                5 -> showProtection()
                6 -> discardRun()
                7 -> MaterialAlertDialogBuilder(this).setTitle("Runline 0.4")
                    .setMessage("Your recordings stay on this phone. Maps request visible-area tiles from EOX and IGN; they do not upload the run file. Export before uninstalling.\n\nMap attribution is shown on the map. Icons: Tabler · MIT license")
                    .setPositiveButton("Done", null).setNeutralButton("Icon license") { _, _ ->
                        MaterialAlertDialogBuilder(this).setTitle("Tabler Icons · MIT").setMessage(assets.open("licenses/tabler-icons.txt").bufferedReader().use { it.readText() }).setPositiveButton("Done", null).show()
                    }.show()
            }; true
        }
    }
    private fun showRunOptions(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Recording protection")
            menu.add(0, 2, 1, "GPS direction")
            if (TrackingService.state(this@MainActivity).status != TrackingStatus.RECORDING) menu.add(0, 3, 2, "Discard run")
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showProtection()
                    2 -> MaterialAlertDialogBuilder(this@MainActivity).setTitle("GPS direction")
                        .setMessage("The arrow follows your movement, not the direction your phone is facing. Move outdoors to establish a heading. While stopped, it keeps the last known direction. Drag to explore; tap the location button to follow again.")
                        .setPositiveButton("Done", null).show()
                    3 -> discardRun()
                }; true
            }
        }.show()
    }
    private fun showProtection() {
        val unrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val notifications = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        MaterialAlertDialogBuilder(this).setTitle("Recording protection")
            .setMessage("Battery: ${if (unrestricted) "unrestricted" else "optimized"}\nNotifications: ${if (notifications) "on" else "off"}\n\nScreen off or app swiped away: recording continues while Android permits. After shutdown or force-stop: reopen to recover saved data.\n\nFor long runs, allow unrestricted battery use in Android settings.\n\nAllow Runline and silent notifications on the lock screen to see time, distance and controls. Expand the notification for actions. Samsung Now Bar support depends on your phone and One UI version.")
            .setPositiveButton("App settings") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            .setNeutralButton("Notifications") { _, _ -> startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
            .setNegativeButton("Done", null).show()
    }
    private fun addDetail(parent: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
        row.addView(text(label, 15, muted = true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(value, 16, bold = true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row); parent.addView(divider())
    }
    private fun requestRecording(action: String) {
        pendingAction = action
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("notificationAsked", false)) {
            getPreferences(0).edit().putBoolean("notificationAsked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101); return
        }
        val manager = getSystemService(LocationManager::class.java)
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            MaterialAlertDialogBuilder(this).setTitle("Turn on location").setMessage("Enable your phone's location setting, then return and start your run.")
                .setPositiveButton("Location settings") { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }.setNegativeButton("Cancel", null).show(); return
        }
        command(action)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) requestRecording(pendingAction)
            else MaterialAlertDialogBuilder(this).setTitle("Precise location is needed").setMessage("Approximate location cannot measure a run reliably. You can enable precise location in app settings.")
                .setPositiveButton("App settings") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }.setNegativeButton("Close", null).show()
        } else if (requestCode == 101) requestRecording(pendingAction)
    }
    private fun command(action: String) {
        if (android.os.SystemClock.elapsedRealtime() < actionPendingUntil) return
        actionPendingUntil = android.os.SystemClock.elapsedRealtime() + 700
        try { TrackingService.command(this, action); handler.postDelayed({ refreshRecording() }, 150) }
        catch (e: Exception) { notice("Couldn't start recording. Check location permission and try again.") }
    }
    private fun discardRun() {
        MaterialAlertDialogBuilder(this).setTitle("Discard this recording?").setMessage("This cannot be undone.")
            .setPositiveButton("Discard") { _, _ -> command(TrackingService.ACTION_DISCARD) }.setNegativeButton("Keep run", null).show()
    }
    private fun saveGpx(run: RunSession) {
        pendingExport = run
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/gpx+xml"; putExtra(Intent.EXTRA_TITLE, filename(run))
        }, 200)
    }
    @Deprecated("Platform document result is supported on the minimum SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (stravaSharing.handleActivityResult(requestCode, resultCode, data)) return
        if (requestCode == 200 && resultCode == RESULT_OK) {
            val run = pendingExport ?: return; val uri = data?.data ?: return
            try { (contentResolver.openOutputStream(uri) ?: error("No destination")).use { output -> output.bufferedWriter().use { it.write(GpxExporter.export(run)) } }; notice("GPX file saved.") }
            catch (e: Exception) { notice("Couldn't save the GPX file. Choose another location and try again.") }
        }
        if (requestCode == 200) pendingExport = null
    }
    private fun shareGpx(run: RunSession) {
        try {
            val dir = File(cacheDir, "exports").apply { mkdirs() }; val file = File(dir, filename(run)); file.writeText(GpxExporter.export(run))
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, "Run GPX", uri)
            }, "Share run file"))
        } catch (e: Exception) { notice("Couldn't share this run. Try Save GPX file instead.") }
    }
    private fun filename(run: RunSession) = "runline-${Instant.ofEpochMilli(run.startedAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))}.gpx"
    private fun notice(message: String) { Snackbar.make(root, message, Snackbar.LENGTH_LONG).show() }
    private fun text(value: String, size: Int, bold: Boolean = false, muted: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(getColor(if (muted) R.color.on_surface_variant else R.color.on_surface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false; setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun metric(label: String, value: String): Pair<LinearLayout, TextView> {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(text(label, 13, muted = true)); val amount = text(value, 24, bold = true).apply { fontFeatureSettings = "tnum" }
        column.addView(amount, margin(top = 7)); return Pair(column, amount)
    }
    private fun button(label: String, outlined: Boolean = false, iconId: Int = 0, action: () -> Unit) = MaterialButton(this, null,
        if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
        text = label; isAllCaps = false; minHeight = dp(64); minimumHeight = dp(64); cornerRadius = dp(16)
        if (iconId != 0) { setIconResource(iconId); iconSize = dp(24); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; iconPadding = dp(12) }
        setPadding(dp(16), dp(12), dp(16), dp(12)); setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    private fun iconView(id: Int) = ImageView(this).apply { setImageResource(id); imageTintList = ColorStateList.valueOf(getColor(R.color.primary)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    private fun divider() = View(this).apply { setBackgroundColor(getColor(R.color.surface_variant)); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) }
    private fun margin(top: Int = 0, bottom: Int = 0, left: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom); marginStart = dp(left) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", Locale.getDefault()))
    private fun duration(millis: Long): String { val seconds = (millis / 1000).coerceAtLeast(0); return if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60) else String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60) }
    private fun number(value: Double?, decimals: Int): String = if (value == null || !value.isFinite()) "—" else String.format(Locale.getDefault(), "%.${decimals}f", value)
    private fun pace(seconds: Double?): String { if (seconds == null || !seconds.isFinite() || seconds < 0) return "—"; val rounded = seconds.roundToInt(); return String.format(Locale.getDefault(), "%d:%02d", rounded / 60, rounded % 60) }
}
