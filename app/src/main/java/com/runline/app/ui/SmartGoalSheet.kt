package com.runline.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.runline.app.R
import com.runline.app.ai.AiFailure
import com.runline.app.ai.AiSettings
import com.runline.app.ai.RunnerGoalStore
import com.runline.app.ai.SmartRunAi
import com.runline.app.ai.SmartRunAiState
import com.runline.app.coaching.VoiceCoachSettings
import com.runline.app.core.CoachConfig
import com.runline.app.core.SmartRunEffort
import com.runline.app.core.SmartRunGoal
import com.runline.app.core.SmartRunGoalType
import com.runline.app.core.SmartRunSuggestion

/** Preparation is an explicit paid action; opening or recreating this sheet only observes it. */
class SmartGoalSheet(
    private val activity: AppCompatActivity,
    @Suppress("UNUSED_PARAMETER") state: Bundle? = null,
    private val onUseGoal: (CoachConfig) -> Unit,
    private val onEditGoal: (CoachConfig) -> Unit,
    private val onConfigureAi: () -> Unit = {},
) {
    private val sheet = AiSheet(activity, R.string.smart_goal_title)
    private val handler = Handler(Looper.getMainLooper())
    private val resultColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val analyze = sheet.button(activity.getString(R.string.smart_goal_analyze), R.drawable.ic_speaker)
    private val configure = sheet.button(activity.getString(R.string.smart_goal_setup), R.drawable.ic_settings)
    private val setupHint = sheet.text(activity.getString(R.string.smart_goal_setup_hint))
    private val costHint = sheet.text(activity.getString(R.string.smart_goal_request_cost), 14)
    private val progress = CircularProgressIndicator(activity).apply {
        isIndeterminate = true
        indicatorSize = sheet.dp(24); trackThickness = sheet.dp(3)
        setIndicatorColor(activity.getColor(R.color.primary))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val progressRow = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(progress, LinearLayout.LayoutParams(sheet.dp(32), sheet.dp(32)))
        addView(sheet.text(activity.getString(R.string.smart_goal_thinking)).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = sheet.dp(12) })
    }
    private val status = sheet.text("").apply {
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val useGoal = sheet.button(activity.getString(R.string.smart_goal_use), filled = true)
    private var shown: SmartRunSuggestion? = null
    private var lastState: SmartRunAiState? = null
    private var lastReady = false
    private var firstRender = true
    private val poll = Runnable { observe() }
    private val lifecycleObserver = LifecycleEventObserver { _, event -> when (event) {
        Lifecycle.Event.ON_START -> observe()
        Lifecycle.Event.ON_STOP -> handler.removeCallbacks(poll)
        else -> Unit
    } }
    val isShowing get() = sheet.isShowing

    init {
        val overall = RunnerGoalStore(activity).load().text
        if (overall.isNotBlank()) sheet.column.addView(sheet.text(overall, 18, true), sheet.row(0, 12))
        sheet.column.addView(sheet.text(activity.getString(R.string.smart_goal_context)), sheet.row(0, 12))
        sheet.column.addView(setupHint, sheet.row(0, 8))
        sheet.column.addView(configure, sheet.row(0, 12))
        sheet.column.addView(analyze, sheet.row(0, 4))
        sheet.column.addView(costHint, sheet.row(0, 16))
        sheet.column.addView(progressRow, sheet.row(0, 16))
        sheet.column.addView(status, sheet.row(0, 16))
        sheet.column.addView(resultColumn, sheet.row())
        sheet.primaryAction(useGoal)
        configure.setOnClickListener { close(); onConfigureAi() }
        analyze.setOnClickListener {
            if (!AiSettings.isReady(activity)) { observe(force = true); return@setOnClickListener }
            status.visibility = View.GONE
            runCatching { SmartRunAi.prepare(activity) }.onSuccess { accepted ->
                observe(force = true)
                if (!accepted) showStatus(activity.getString(R.string.smart_goal_previous))
            }.onFailure { showStatus(sheet.error(it)) }
        }
        useGoal.setOnClickListener { applySuggestion(edit = false) }
        sheet.dialog.setOnDismissListener { sheet.closed = true; stopObserving() }
        activity.lifecycle.addObserver(lifecycleObserver)
        render(SmartRunAi.snapshot(activity), AiSettings.isReady(activity))
    }

    fun show() { sheet.show(); observe() }
    fun close() { stopObserving(); sheet.close() }
    /** The singleton owns in-flight work; no private goal, generated response or key enters a Bundle. */
    fun saveState() = Bundle()

    private fun observe(force: Boolean = false) {
        handler.removeCallbacks(poll)
        if (sheet.closed || !sheet.isShowing || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val state = SmartRunAi.snapshot(activity)
        val ready = AiSettings.isReady(activity)
        if (force || firstRender || state != lastState || ready != lastReady) {
            val invalidated = !firstRender && lastState?.suggestion != null && state == null
            render(state, ready)
            if (invalidated) showStatus(activity.getString(R.string.smart_goal_changed))
        }
        handler.postDelayed(poll, 750)
    }

    private fun render(state: SmartRunAiState?, ready: Boolean) {
        firstRender = false; lastState = state; lastReady = ready
        val running = state?.running == true
        progressRow.visibility = if (running) View.VISIBLE else View.GONE
        progress.visibility = if (running) View.VISIBLE else View.GONE
        configure.visibility = if (ready) View.GONE else View.VISIBLE
        setupHint.visibility = if (ready) View.GONE else View.VISIBLE
        analyze.visibility = if (ready) View.VISIBLE else View.GONE
        costHint.visibility = if (ready) View.VISIBLE else View.GONE
        analyze.isEnabled = ready && !running
        analyze.setText(if (state?.suggestion != null) R.string.smart_goal_analyze_again else R.string.smart_goal_analyze)
        status.visibility = View.GONE
        state?.failure?.let { showStatus(sheet.error(AiFailure(it))) }
        resultColumn.removeAllViews()
        shown = state?.suggestion?.takeIf { ready && !running }
        val suggestion = shown
        val executable = suggestion != null && suggestion.goalType != SmartRunGoalType.REST
        (useGoal.parent as? View)?.visibility = if (executable) View.VISIBLE else View.GONE
        useGoal.isEnabled = executable
        if (suggestion == null) return
        resultColumn.addView(sheet.text(activity.getString(if (suggestion.goalType == SmartRunGoalType.REST)
            R.string.smart_goal_rest else R.string.smart_goal_ready), 20, true), sheet.row(4, 12))
        if (suggestion.goalType != SmartRunGoalType.REST) {
            suggestion.targetDistanceKm?.let { target ->
                val min = suggestion.minDistanceKm
                val max = suggestion.maxDistanceKm
                val range = if (min != null && max != null && number(min) != number(max))
                    activity.getString(R.string.smart_distance_range_value, number(min), number(max)) else activity.getString(R.string.smart_distance_value, number(target))
                resultColumn.addView(sheet.text(range, 24, true).apply { fontFeatureSettings = "tnum" }, sheet.row(0, 4))
                if (min != null && max != null && number(min) != number(max))
                    resultColumn.addView(sheet.text(activity.getString(R.string.smart_distance_target_value, number(target)), 14), sheet.row(0, 8))
            }
            val minPace = suggestion.paceMinSecondsPerKm
            val maxPace = suggestion.paceMaxSecondsPerKm
            if (minPace != null && maxPace != null) resultColumn.addView(sheet.text(
                activity.getString(R.string.smart_pace_range_value, pace(minPace), pace(maxPace)), 20, true), sheet.row(0, 8))
            suggestion.durationMinutes?.let { resultColumn.addView(sheet.text(activity.getString(R.string.smart_duration_value, number(it)), 20, true), sheet.row(0, 8)) }
            resultColumn.addView(sheet.text(activity.getString(when (suggestion.effort) {
                SmartRunEffort.EASY -> R.string.smart_goal_easy
                SmartRunEffort.RECOVERY -> R.string.smart_goal_recovery
                SmartRunEffort.STEADY -> R.string.smart_goal_steady
            })), sheet.row(0, 12))
        }
        resultColumn.addView(sheet.text(suggestion.rationale), sheet.row(0, 16))
        if (executable) resultColumn.addView(sheet.button(activity.getString(R.string.smart_goal_adjust), R.drawable.ic_edit).apply {
            setOnClickListener { applySuggestion(edit = true) }
        }, sheet.row())
    }

    private fun applySuggestion(edit: Boolean) {
        val suggestion = shown ?: return
        val current = SmartRunAi.snapshot(activity)
        if (current?.running == true || current?.suggestion != suggestion || !AiSettings.isReady(activity)) {
            observe(force = true); showStatus(activity.getString(R.string.smart_goal_changed)); return
        }
        if (suggestion.goalType == SmartRunGoalType.REST) return
        val config = runCatching { SmartRunGoal.toCoachConfig(suggestion, VoiceCoachSettings.load(activity)).copy(enabled = true) }
            .getOrElse { showStatus(sheet.error(it)); return }
        if (!config.isValid()) { showStatus(activity.getString(R.string.coach_ui_invalid)); return }
        close()
        if (edit) onEditGoal(config) else onUseGoal(config)
    }

    private fun showStatus(message: String) { status.text = message; status.visibility = View.VISIBLE }
    private fun number(value: Double) = String.format(activity.resources.configuration.locales[0], "%.1f", value)
    private fun pace(value: Int) = "%d:%02d".format(java.util.Locale.ROOT, value / 60, value % 60)
    private fun stopObserving() {
        handler.removeCallbacks(poll)
        activity.lifecycle.removeObserver(lifecycleObserver)
    }
}
