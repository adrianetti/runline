package com.runline.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.PowerManager
import android.view.MenuItem
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
import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.core.ActivityType
import com.runline.app.i18n.AppLanguage
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.runline.app.sharing.ActivityExporter
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunSession
import com.runline.app.data.RunStore
import com.runline.app.tracking.TrackingService
import com.runline.app.tracking.RecordingStatus as TrackingStatus
import com.runline.app.ui.SatelliteMapView
import com.runline.app.ui.RunReplayView
import com.runline.app.ui.ActiveRunView
import com.runline.app.ui.VoiceCoachSheet
import com.runline.app.ui.HistoryInsightsView
import com.runline.app.ui.ActivityDetailsEditor
import com.runline.app.ui.RunnerProfileSheet
import com.runline.app.ui.AppSettingsSheet
import com.runline.app.ai.AiSettings
import com.runline.app.ui.AiSettingsSheet
import com.runline.app.ui.SmartGoalSheet
import com.runline.app.core.CoachConfig
import com.runline.app.ui.RunReviewSheet
import com.runline.app.ai.AiActivityCompletion
import com.runline.app.coaching.VoiceCoachSettings
import androidx.appcompat.widget.PopupMenu
import com.runline.app.core.RunReplay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabs: TabLayout
    private lateinit var body: LinearLayout
    private lateinit var store: RunStore
    private lateinit var exporter: ActivityExporter
    private var actionPendingUntil = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var screen = "record"
    private var currentRun: RunSession? = null
    private var renderedStatus = ""
    private var lastSavedId: String? = null
    private var pendingAction = TrackingService.ACTION_START
    private var route: SatelliteMapView? = null
    private var replayView: RunReplayView? = null
    private var restoredReplayPosition = 0L
    private var restoredReplaySpeed = 0
    private var activeRunView: ActiveRunView? = null
    private var selectedActivity = ActivityType.RUN
    private var coachSheet: VoiceCoachSheet? = null
    private var historyType: ActivityType? = null
    private var detailsEditor: ActivityDetailsEditor? = null
    private var aiSettings: AiSettingsSheet? = null
    private var appSettings: AppSettingsSheet? = null
    private var runnerProfile: RunnerProfileSheet? = null
    private var smartGoal: SmartGoalSheet? = null
    private var runReview: RunReviewSheet? = null
    private val ticker = object : Runnable {
        override fun run() { refreshRecording(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLanguage.beforeCreate(this)
        super.onCreate(savedInstanceState)
        AppLanguage.afterCreate(this)
        selectedActivity = ActivityType.fromStorage(savedInstanceState?.getString("activityType") ?: getPreferences(0).getString("activityType", null))
        historyType = savedInstanceState?.getString("historyType")?.let(ActivityType::fromStorage)
        pendingAction = savedInstanceState?.getString("pendingAction") ?: TrackingService.ACTION_START
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
            addTab(newTab().setText(R.string.main_record).setIcon(R.drawable.ic_run)); addTab(newTab().setText(R.string.main_activities).setIcon(R.drawable.ic_history))
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
        exporter = ActivityExporter(this, { id -> runCatching { store.get(id) }.getOrNull() }, ::notice)
        exporter.restoreState(savedInstanceState)
        lastSavedId = getSharedPreferences("activity_completion", MODE_PRIVATE).getString("seen_id", null)
        screen = savedInstanceState?.getString("screen") ?: "record"
        val selectedRunId = savedInstanceState?.getString("runId")
        if (selectedRunId != null) currentRun = runCatching { store.get(selectedRunId) }.getOrNull()
        restoredReplayPosition = savedInstanceState?.getLong("replayPosition") ?: 0L
        restoredReplaySpeed = savedInstanceState?.getInt("replaySpeed") ?: 0
        if (intent.getBooleanExtra(TrackingService.EXTRA_OPEN_RECORDING, false)) {
            intent.removeExtra(TrackingService.EXTRA_OPEN_RECORDING); screen = "record"
        }
        if (screen == "detail" && currentRun == null) screen = "history"
        if (screen == "history") tabs.getTabAt(1)?.select()
        render()
        if (savedInstanceState?.getBoolean("appSettings") == true) showSettings()
        savedInstanceState?.getBundle("coachSheet")?.let { showCoach(it) }
        savedInstanceState?.getBundle("aiSettings")?.let { aiSettings = AiSettingsSheet(this, it) { AiActivityCompletion.onAppOpen(this) }.also { sheet -> sheet.show() } }
        savedInstanceState?.getBundle("runnerProfile")?.let { runnerProfile = RunnerProfileSheet(this, it).also { sheet -> sheet.show() } }
        savedInstanceState?.getBundle("smartGoal")?.let { showSmartGoal(it) }
        savedInstanceState?.getBundle("runReview")?.takeIf { AiSettings.isReady(this) }?.let { draft -> currentRun?.let { run -> runReview = RunReviewSheet(this, run, draft).also { it.show() } } }
        AiActivityCompletion.onAppOpen(this)
        savedInstanceState?.getBundle("detailsEditor")?.let { draft ->
            draft.getString("runId")?.let { id -> runCatching { store.get(id) }.getOrNull()?.let { showActivityEditor(it, draft) } }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent.getBooleanExtra(TrackingService.EXTRA_OPEN_RECORDING, false)) {
            intent.removeExtra(TrackingService.EXTRA_OPEN_RECORDING)
            screen = "record"; tabs.getTabAt(0)?.select(); render()
        }
    }
    override fun onResume() { super.onResume(); TrackingService.refreshLanguage(); route?.onHostResume(); replayView?.onHostResume(); handler.removeCallbacks(ticker); handler.post(ticker); if (::exporter.isInitialized) exporter.onResume() }
    override fun onLowMemory() { super.onLowMemory(); route?.onLowMemory(); replayView?.map?.onLowMemory() }
    override fun onDestroy() { runnerProfile?.close(); appSettings?.close(); detailsEditor?.close(); aiSettings?.close(); smartGoal?.close(); runReview?.close(); coachSheet?.close(); handler.removeCallbacks(ticker); route?.dispose(); replayView?.dispose(); if (::exporter.isInitialized) exporter.close(); super.onDestroy() }
    override fun onPause() { coachSheet?.onHostPause(); handler.removeCallbacks(ticker); route?.onHostPause(); replayView?.onHostPause(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("activityType", selectedActivity.storageValue)
        outState.putString("historyType", historyType?.storageValue)
        outState.putString("pendingAction", pendingAction)
        outState.putString("screen", screen); outState.putString("runId", currentRun?.id)
        outState.putLong("replayPosition", replayView?.positionMs ?: restoredReplayPosition)
        outState.putInt("replaySpeed", replayView?.playbackSpeed ?: restoredReplaySpeed)
        exporter.saveState(outState); super.onSaveInstanceState(outState)
        outState.putBoolean("appSettings", appSettings?.isShowing == true)
        coachSheet?.takeIf { it.isShowing }?.let { outState.putBundle("coachSheet", it.saveState()) }
        detailsEditor?.takeIf { it.isShowing }?.let { outState.putBundle("detailsEditor", it.saveState()) }
        aiSettings?.takeIf { it.isShowing }?.let { outState.putBundle("aiSettings", it.saveState()) }
        runnerProfile?.takeIf { it.isShowing }?.let { outState.putBundle("runnerProfile", it.saveState()) }
        smartGoal?.takeIf { it.isShowing }?.let { outState.putBundle("smartGoal", it.saveState()) }
        runReview?.takeIf { it.isShowing }?.let { outState.putBundle("runReview", it.saveState()) }
    }
    @Deprecated("Uses platform Back for this single-activity prototype")
    override fun onBackPressed() {
        if (screen == "detail") { screen = "history"; tabs.getTabAt(1)?.select(); render() }
        else if (screen == "history") { tabs.getTabAt(0)?.select() }
        else super.onBackPressed()
    }

    private fun render() {
        route?.dispose(); replayView?.dispose(); replayView = null
        body.removeAllViews(); route = null; activeRunView = null
        val state = TrackingService.state(this)
        val live = screen == "record" && state.run != null && state.status != TrackingStatus.RECOVERY_ERROR
        toolbar.visibility = if (live || screen == "detail") View.GONE else View.VISIBLE
        toolbar.title = "Runline"
        tabs.visibility = if (screen == "detail" || live) View.GONE else View.VISIBLE
        toolbar.navigationIcon = if (screen == "detail") AppCompatResources.getDrawable(this, R.drawable.ic_back) else null
        toolbar.navigationIcon?.setTint(getColor(R.color.on_surface))
        toolbar.navigationContentDescription = getString(R.string.main_back_activities)
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
                onOpenRuns = { tabs.getTabAt(1)?.select() }, onOptions = ::showRunOptions,
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
        if (state.status == TrackingStatus.RECOVERY_ERROR) {
            content.addView(text(getString(R.string.main_recovery_title), 24, bold = true))
            content.addView(text(getString(R.string.main_recovery_body), 14, muted = true), margin(top = 16))
            content.addView(button(getString(R.string.main_keep_reset), iconId = R.drawable.ic_shield_check) {
                MaterialAlertDialogBuilder(this).setTitle(R.string.main_archive_title).setMessage(R.string.main_archive_body)
                    .setPositiveButton(R.string.main_keep_reset) { _, _ -> command(TrackingService.ACTION_ARCHIVE_DRAFT) }
                    .setNegativeButton(R.string.main_cancel, null).show()
            }, margin(top = 24))
            return
        }
        content.addView(text(getString(R.string.main_ready), 30, bold = true), margin(bottom = 24))
        val types = ActivityType.entries
        val field = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = getString(R.string.main_activity_type)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        val choice = MaterialAutoCompleteTextView(field.context).apply {
            inputType = android.text.InputType.TYPE_NULL
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, types.map(::activityName)))
            setText(activityName(selectedActivity), false)
            minHeight = dp(64); setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnItemClickListener { _, _, index, _ ->
                selectedActivity = types[index]
                getPreferences(0).edit().putString("activityType", selectedActivity.storageValue).apply()
                render()
            }
        }
        field.addView(choice, LinearLayout.LayoutParams(-1, -2))
        content.addView(field, margin(bottom = 16))
        content.addView(button(getString(if (VoiceCoachSettings.load(this).enabled) R.string.coach_ui_on else R.string.coach_ui_off), outlined = true, iconId = R.drawable.ic_speaker) { showCoach() }, margin(top = 24))
        VoiceCoachSettings.load(this).takeIf { it.enabled && it.goal != com.runline.app.core.CoachGoal.NONE }?.let {
            content.addView(text(VoiceCoachSheet.goalSummary(this, it), 14, muted = true), margin(top = 8))
        }
        if (selectedActivity == ActivityType.RUN) content.addView(button(getString(R.string.smart_goal_title), outlined = true, iconId = R.drawable.ic_speaker) { showSmartGoal() }, margin(top = 12))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(16)) }
        actions.addView(button(getString(R.string.main_start), iconId = R.drawable.ic_play) { requestRecording(TrackingService.ACTION_START) })
        body.addView(actions)
    }
    private fun activityName(type: ActivityType) = getString(when(type) {
        ActivityType.RUN -> R.string.main_type_run
        ActivityType.WALK -> R.string.main_type_walk
        ActivityType.HIKE -> R.string.main_type_hike
    })
    private fun refreshRecording() {
        val state = TrackingService.state(this)
        val completion = getSharedPreferences("activity_completion", MODE_PRIVATE)
        val finishedId = state.savedRunId ?: completion.getString("pending_id", null)
        if (finishedId != null && finishedId != lastSavedId) {
            runCatching { store.get(finishedId) }.getOrNull()?.let {
                lastSavedId = finishedId
                completion.edit().putString("seen_id", finishedId).apply()
                openRun(it)
                showActivityEditor(it)
                AiActivityCompletion.onSaved(this, it)
                return
            }
        }
        if (screen != "record") return
        if (state.status.name != renderedStatus) render() else updateMetrics()
    }
    private fun updateMetrics() { activeRunView?.update(TrackingService.state(this)) }
    private fun renderHistory() {
        val content = scrollContent()
        val runs = try { store.list() } catch (e: Exception) { notice(getString(R.string.main_history_failed)); emptyList() }
        if (runs.isNotEmpty()) {
            content.addView(HistoryInsightsView(this, runs, historyType) { historyType = it })
            content.addView(text(getString(R.string.main_activities), 24, bold = true), margin(top = 24, bottom = 12))
            val stacked = resources.configuration.screenWidthDp < 360 || resources.configuration.fontScale > 1.15f
            val transfers = LinearLayout(this).apply { orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
            listOf(true, false).forEachIndexed { index, share ->
                val action = button(getString(if (share) R.string.v7_share_short else R.string.v7_export_short), outlined = true,
                    iconId = if (share) R.drawable.ic_share else R.drawable.ic_download) { showHistoryTransfer(runs, share) }.apply {
                    contentDescription = getString(if (share) R.string.main_share_all else R.string.main_export_all)
                    maxLines = 1
                }
                transfers.addView(action, if (stacked) LinearLayout.LayoutParams(-1, dp(64)).apply { if (index > 0) topMargin = dp(12) }
                    else LinearLayout.LayoutParams(0, dp(64), 1f).apply { if (index > 0) marginStart = dp(12) })
            }
            content.addView(transfers, margin(bottom = 12))
        }
        if (runs.isEmpty()) {
            content.addView(text(getString(R.string.main_no_activities), 24, bold = true), margin(top = 28))
            content.addView(button(getString(R.string.main_start), iconId = R.drawable.ic_play) { tabs.getTabAt(0)?.select() })
        } else {
            runs.forEach { run ->
                val distance = RunMetrics.distanceMeters(run.points)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(20), dp(4), dp(20)); minimumHeight = dp(80)
                    isClickable = true; isFocusable = true
                    val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)); background = attrs.getDrawable(0); attrs.recycle()
                    setOnClickListener { openRun(run) }
                    contentDescription = getString(R.string.main_history_row, activityName(run.activityType), date(run.startedAtMs), number(distance / 1000, 2), duration(run.activeDurationMs))
                }
                val details = runCatching { store.details(run.id) }.getOrNull()
                details?.title?.takeIf { it.isNotBlank() }?.let { title ->
                    row.addView(text(title, 20, bold = true).apply { maxLines = 2 }, margin(bottom = 6))
                    row.contentDescription = "$title. ${row.contentDescription}"
                }
                row.addView(text(getString(R.string.main_activity_date, activityName(run.activityType), date(run.startedAtMs)), 14, muted = true))
                row.addView(text(getString(R.string.main_distance_km, number(distance / 1000, 2)), 26, bold = true), margin(top = 6))
                row.addView(text(getString(R.string.main_history_stats, duration(run.activeDurationMs), pace(RunMetrics.paceSecondsPerKm(distance, run.activeDurationMs))), 14, muted = true), margin(top = 4))
                content.addView(row); content.addView(divider())
            }
        }
    }
    private fun openRun(run: RunSession) { currentRun = run; restoredReplayPosition = 0L; restoredReplaySpeed = 0; screen = "detail"; render() }
    private fun renderDetail(run: RunSession) {
        replayView = RunReplayView(this, RunReplay(run),
            onBack = { screen = "history"; tabs.getTabAt(1)?.select(); render() },
            onExport = { showActivityTransfer(run) }, onMore = ::showDetailOptions,
            onEdit = { showActivityEditor(run) }, onReview = { showRunReview(run) },
        ).also { view ->
            view.restore(restoredReplayPosition, restoredReplaySpeed)
            body.addView(view, LinearLayout.LayoutParams(-1, -1))
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) view.onHostResume()
        }
    }
    private fun configureMenu() {
        toolbar.menu.clear()
        toolbar.menu.add(0, 15, 0, R.string.v8_settings).apply {
            setIcon(R.drawable.ic_settings); icon?.setTint(getColor(R.color.on_surface))
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        toolbar.setOnMenuItemClickListener { showSettings(); true }
    }
    private fun showSettings() {
        appSettings?.close()
        appSettings = AppSettingsSheet(this, ::showRunnerProfile, ::showAiSettings, ::showLanguage, ::showProtection, ::showAbout).also { it.show() }
    }
    private fun showDetailOptions(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 13, 0, R.string.v7_edit_activity)
            if (AiSettings.isReady(this@MainActivity)) menu.add(0, 14, 1, R.string.v7_run_review)
            menu.add(0, 4, 2, R.string.main_delete_activity)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    13 -> currentRun?.let { showActivityEditor(it) }
                    14 -> currentRun?.let { showRunReview(it) }
                    4 -> currentRun?.let { run -> MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.main_delete_title).setMessage(R.string.main_delete_body)
                        .setNegativeButton(R.string.main_keep, null).setPositiveButton(R.string.main_delete) { _, _ ->
                            try { store.delete(run.id); AiActivityCompletion.onDeleted(this@MainActivity, run.id); screen = "history"; tabs.getTabAt(1)?.select(); render() }
                            catch (_: Exception) { notice(getString(R.string.main_delete_failed)) }
                        }.show() }
                }; true
            }
        }.show()
    }
    private fun showRunOptions(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 10, 0, R.string.coach_ui_title).setIcon(R.drawable.ic_speaker)
            setForceShowIcon(true)
            menu.add(0, 1, 1, R.string.main_protection).setIcon(R.drawable.ic_shield_check)
            if (TrackingService.state(this@MainActivity).status != TrackingStatus.RECORDING) menu.add(0, 3, 3, R.string.main_discard_activity)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    10 -> showCoach()
                    1 -> showProtection()
                    3 -> discardRun()
                }; true
            }
        }.show()
    }
    private fun showLanguage() {
        val tags = arrayOf("", "en", "es")
        val current = AppLanguage.selectedTag(this).substringBefore(',')
        MaterialAlertDialogBuilder(this).setTitle(R.string.main_language)
            .setSingleChoiceItems(arrayOf(getString(R.string.main_phone_language), "English", "Español"), tags.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss(); AppLanguage.select(this, tags[which])
            }.setNegativeButton(R.string.main_cancel, null).show()
    }
    private fun showAbout() {
        MaterialAlertDialogBuilder(this).setTitle("Runline 0.10.1")
            .setMessage(R.string.main_about_body).setPositiveButton(R.string.main_done, null)
            .setNeutralButton(R.string.main_icon_license) { _, _ ->
                MaterialAlertDialogBuilder(this).setTitle("Tabler Icons · MIT")
                    .setMessage(assets.open("licenses/tabler-icons.txt").bufferedReader().use { it.readText() })
                    .setPositiveButton(R.string.main_done, null).show()
            }.show()
    }
    private fun showProtection() {
        val unrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val notifications = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        MaterialAlertDialogBuilder(this).setTitle(R.string.main_protection)
            .setMessage(getString(R.string.main_protection_body,
                getString(if (unrestricted) R.string.main_unrestricted else R.string.main_optimized),
                getString(if (notifications) R.string.main_on else R.string.main_off)))
            .setPositiveButton(R.string.main_app_settings) { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            .setNeutralButton(R.string.main_notifications) { _, _ -> startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
            .setNegativeButton(R.string.main_done, null).show()
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
            MaterialAlertDialogBuilder(this).setTitle(getString(R.string.main_location_title)).setMessage(getString(R.string.main_location_body))
                .setPositiveButton(getString(R.string.main_location_settings)) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }.setNegativeButton(getString(R.string.main_cancel), null).show(); return
        }
        command(action)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) requestRecording(pendingAction)
            else MaterialAlertDialogBuilder(this).setTitle(getString(R.string.main_precise_title)).setMessage(getString(R.string.main_precise_body))
                .setPositiveButton(getString(R.string.main_app_settings)) { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }.setNegativeButton(getString(R.string.main_close), null).show()
        } else if (requestCode == 101) requestRecording(pendingAction)
    }
    private fun command(action: String) {
        if (android.os.SystemClock.elapsedRealtime() < actionPendingUntil) return
        actionPendingUntil = android.os.SystemClock.elapsedRealtime() + 700
        try { TrackingService.command(this, action, selectedActivity); handler.postDelayed({ refreshRecording() }, 150) }
        catch (e: Exception) { notice(getString(R.string.main_start_failed)) }
    }
    private fun discardRun() {
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.main_discard_title)).setMessage(getString(R.string.main_discard_body))
            .setPositiveButton(getString(R.string.main_discard)) { _, _ -> command(TrackingService.ACTION_DISCARD) }.setNegativeButton(getString(R.string.main_keep), null).show()
    }
    private fun showActivityTransfer(run: RunSession) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.main_activity_file)
            .setItems(arrayOf(getString(R.string.main_download_gpx), getString(R.string.main_share_file), getString(R.string.main_save_to))) { _, which ->
                when (which) { 0 -> exporter.start(run); 1 -> exporter.share(run); 2 -> exporter.saveAs(run) }
            }.show()
    }
    private fun showHistoryTransfer(runs: List<RunSession>, share: Boolean) {
        MaterialAlertDialogBuilder(this).setTitle(if (share) R.string.main_share_all else R.string.main_export_all)
            .setItems(arrayOf(getString(R.string.main_summary_csv), getString(R.string.main_archive_zip))) { _, format ->
                if (share) { if (format == 0) exporter.shareSummary(runs) else exporter.shareArchive(runs) }
                else MaterialAlertDialogBuilder(this).setTitle(R.string.main_export_all)
                    .setItems(arrayOf(getString(R.string.main_downloads), getString(R.string.main_save_to))) { _, destination ->
                        if (format == 0) { if (destination == 0) exporter.startSummary(runs) else exporter.saveSummaryAs(runs) }
                        else { if (destination == 0) exporter.startArchive(runs) else exporter.saveArchiveAs(runs) }
                    }.show()
            }.show()
    }
    private fun showRunnerProfile() { runnerProfile?.close(); runnerProfile = RunnerProfileSheet(this).also { it.show() } }
    private fun showAiSettings() { aiSettings?.close(); aiSettings = AiSettingsSheet(this) { AiActivityCompletion.onAppOpen(this) }.also { it.show() } }
    private fun showSmartGoal(state: Bundle? = null) {
        // Smart suggestions are preparation, never a replacement target during capture.
        if (selectedActivity != ActivityType.RUN || TrackingService.state(this).run != null) return
        coachSheet?.close(); smartGoal?.close()
        smartGoal = SmartGoalSheet(this, state,
            onUseGoal = { config ->
                if (TrackingService.state(this).run == null && VoiceCoachSettings.save(this, config)) {
                    TrackingService.refreshCoaching()
                    if (screen == "record") render()
                }
            },
            onEditGoal = { config -> if (TrackingService.state(this).run == null) showCoach(initialConfig = config) },
            onConfigureAi = ::showAiSettings,
        ).also { it.show() }
    }
    private fun showRunReview(run: RunSession) {
        if (!AiSettings.isReady(this)) return
        runReview?.close(); runReview = RunReviewSheet(this, run).also { it.show() }
    }
    private fun showActivityEditor(run: RunSession, state: Bundle? = null) {
        detailsEditor?.close()
        detailsEditor = ActivityDetailsEditor(this, run, state) {
            currentRun = store.get(run.id) ?: run
            restoredReplayPosition = replayView?.positionMs ?: restoredReplayPosition
            restoredReplaySpeed = replayView?.playbackSpeed ?: restoredReplaySpeed
            render()
        }.also { it.show() }
    }
    private fun showCoach(state: Bundle? = null, initialConfig: CoachConfig? = null) {
        coachSheet?.close()
        coachSheet = VoiceCoachSheet(this, state, initialConfig = initialConfig, onSmartGoal = { showSmartGoal() }, allowSmartGoal = selectedActivity == ActivityType.RUN && TrackingService.state(this).run == null) {
            TrackingService.refreshCoaching()
            if (screen == "record" && activeRunView == null) render()
        }.also { it.show() }
    }
    @Deprecated("Platform document result is supported on the minimum SDK")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (detailsEditor?.handleActivityResult(requestCode, resultCode, data) == true) return
        exporter.handleActivityResult(requestCode, resultCode, data)
    }
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
    private fun divider() = View(this).apply { setBackgroundColor(getColor(R.color.surface_variant)); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) }
    private fun margin(top: Int = 0, bottom: Int = 0, left: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom); marginStart = dp(left) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", resources.configuration.locales[0]))
    private fun duration(millis: Long): String { val seconds = (millis / 1000).coerceAtLeast(0); return if (seconds >= 3600) String.format(resources.configuration.locales[0], "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60) else String.format(resources.configuration.locales[0], "%02d:%02d", seconds / 60, seconds % 60) }
    private fun number(value: Double?, decimals: Int): String = if (value == null || !value.isFinite()) "—" else String.format(resources.configuration.locales[0], "%.${decimals}f", value)
    private fun pace(seconds: Double?): String { if (seconds == null || !seconds.isFinite() || seconds < 0) return "—"; val rounded = seconds.roundToInt(); return String.format(resources.configuration.locales[0], "%d:%02d", rounded / 60, rounded % 60) }
}
