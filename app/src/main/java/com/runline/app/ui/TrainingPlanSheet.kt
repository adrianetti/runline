package com.runline.app.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.R
import com.runline.app.ai.AiSettings
import com.runline.app.ai.AiWork
import com.runline.app.core.ActivityType
import com.runline.app.core.RunMetrics
import com.runline.app.data.RunStore
import com.runline.app.training.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.Executors

class TrainingPlanSheet(private val activity: AppCompatActivity, state: Bundle? = null) {
    private val sheet = AiSheet(activity, R.string.ai_plan_title)
    private val store = TrainingPlanStore(activity)
    private var plan = store.load()
    private val today = LocalDate.now()
    private val locale = activity.resources.configuration.locales[0]
    private val goalFields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val status = sheet.text("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private val sessions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val currentGoal = plan?.goal
    private var goalType = runCatching { TrainingGoalType.valueOf(state?.getString("goalType").orEmpty()) }.getOrDefault(currentGoal?.goalType ?: TrainingGoalType.HALF_MARATHON)
    private var timing = runCatching { TrainingTiming.valueOf(state?.getString("timing").orEmpty()) }.getOrDefault(currentGoal?.timing?.takeUnless { it == TrainingTiming.ROLLING } ?: TrainingTiming.EXACT_DAY)
    private var exactDay = runCatching { LocalDate.parse(state?.getString("exactDay")) }.getOrDefault(currentGoal?.raceDate?.takeIf { currentGoal?.timing == TrainingTiming.EXACT_DAY } ?: today.plusWeeks(16))
    private var targetMonth = runCatching { YearMonth.parse(state?.getString("targetMonth")) }.getOrDefault(currentGoal?.raceDate?.let(YearMonth::from) ?: YearMonth.from(exactDay))
    private var daysPerWeek = state?.getInt("days", currentGoal?.daysPerWeek ?: 2) ?: currentGoal?.daysPerWeek ?: 2
    private var expanded = state?.getBoolean("expanded") ?: false
    private var showPlanNotes = state?.getBoolean("showPlanNotes") ?: false
    private var editing = state?.getBoolean("editing") ?: (plan == null)
    private var adjusting = state?.getBoolean("adjusting") ?: false
    private var baseline: TrainingBaseline? = null
    private val preserveBase = currentGoal != null || state?.getBoolean("baselineReady") == true
    private var daysEdited = state?.getBoolean("daysEdited") ?: (currentGoal != null)
    private var baseEdited = state?.getBoolean("baseEdited") ?: (currentGoal != null)
    private val worker = Executors.newSingleThreadExecutor()
    private val goalChoice = dropdown(R.string.plan_v8_goal)
    private val distance = field(R.string.ai_goal_distance)
    private val timingChoice = dropdown(R.string.plan_v8_when)
    private val dateButton = sheet.button("", R.drawable.ic_calendar)
    private val rollingHint = sheet.text(activity.getString(R.string.plan_v8_rolling_hint))
    private val daysChoice = dropdown(R.string.plan_v8_days)
    private val baselineText = sheet.text(activity.getString(R.string.plan_v8_history_loading))
    private val baseText = sheet.text("")
    private val weekly = field(R.string.plan_v8_starting_distance)
    private val adjustButton = sheet.button(activity.getString(R.string.plan_v8_adjust), R.drawable.ic_edit)
    private val createButton = sheet.button(activity.getString(if (editing) R.string.ai_plan_create else R.string.ai_plan_edit), R.drawable.ic_route, plan == null)
    private val personalizeButton = sheet.button(activity.getString(R.string.ai_plan_personalize), R.drawable.ic_speaker)
    private val handler = Handler(Looper.getMainLooper())
    private var waitingForPlan = state?.getBoolean("pendingPlan") ?: false
    private var previousPlanTime = state?.getLong("previousPlanTime", -1L) ?: -1L
    private val poll = Runnable { observePlan() }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> observePlan()
            Lifecycle.Event.ON_STOP -> handler.removeCallbacks(poll)
            else -> Unit
        }
    }
    val isShowing get() = sheet.isShowing

    init {
        sheet.dialog.setOnDismissListener { sheet.closed = true; worker.shutdownNow(); stopObserving() }
        distance.second.setText(state?.getString("distance") ?: currentGoal?.distanceKm?.takeIf { it > 0 }?.toString() ?: "")
        weekly.second.setText(state?.getString("weekly") ?: currentGoal?.weeklyKm?.toString() ?: "5.0")
        setupChoice(goalChoice.second, TrainingGoalType.entries.map { goalLabel(it) }, TrainingGoalType.entries.indexOf(goalType)) { index ->
            goalType = TrainingGoalType.entries[index]; updateForm()
        }
        setupChoice(timingChoice.second, listOf(activity.getString(R.string.plan_v8_exact_day), activity.getString(R.string.plan_v8_target_month)), if (timing == TrainingTiming.TARGET_MONTH) 1 else 0) { index ->
            val selected = if (index == 1) TrainingTiming.TARGET_MONTH else TrainingTiming.EXACT_DAY
            if (selected != timing) {
                if (selected == TrainingTiming.TARGET_MONTH) targetMonth = YearMonth.from(exactDay)
                else exactDay = targetMonth.atDay(minOf(exactDay.dayOfMonth, targetMonth.lengthOfMonth()))
            }
            timing = selected; updateForm()
        }
        setupChoice(daysChoice.second, (2..5).map { activity.resources.getQuantityString(R.plurals.plan_v8_days_count, it, it) }, daysPerWeek - 2) { index ->
            daysPerWeek = index + 2; daysEdited = true
        }
        // Progressive fields stay in the same form order when the goal changes.
        listOf(goalChoice.first, distance.first, timingChoice.first, dateButton, rollingHint, daysChoice.first, baselineText, baseText, adjustButton, weekly.first).forEach { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            goalFields.addView(view, sheet.row(0, if (view == baselineText) 6 else 12))
        }
        dateButton.setOnClickListener { if (timing == TrainingTiming.TARGET_MONTH) chooseMonth() else chooseDay() }
        adjustButton.setOnClickListener { adjusting = !adjusting; baseEdited = true; updateForm() }
        weekly.second.doAfterTextChanged { updateBaseText() }
        sheet.column.addView(goalFields, sheet.row())
        sheet.column.addView(createButton, sheet.row(8))
        createButton.setOnClickListener {
            if (!editing) { editing = true; updateForm(); return@setOnClickListener }
            val goal = goalFromFields() ?: return@setOnClickListener
            fun create() {
                runCatching {
                    val fresh = TrainingPlanEngine.create(goal, LocalDate.now(), spanish())
                    store.save(fresh); plan = fresh; editing = false; expanded = false; updateForm(); render()
                    status.setText(R.string.plan_v8_saved)
                }.onFailure { status.setText(R.string.ai_error_storage) }
            }
            if (plan == null) create() else MaterialAlertDialogBuilder(activity).setTitle(R.string.ai_plan_replace)
                .setMessage(R.string.ai_plan_replace_hint).setNegativeButton(R.string.main_cancel, null)
                .setPositiveButton(R.string.coach_ui_save) { _, _ -> create() }.show()
        }
        sheet.column.addView(personalizeButton, sheet.row())
        personalizeButton.setOnClickListener {
            val current = plan ?: return@setOnClickListener
            if (!AiSettings.isReady(activity)) { personalizeButton.visibility = View.GONE; return@setOnClickListener }
            if (AiWork.isPlanRunning()) { observePlan(); return@setOnClickListener }
            previousPlanTime = current.updatedAtMs
            waitingForPlan = true
            personalizeButton.isEnabled = false; status.setText(R.string.ai_working)
            AiWork.personalizePlan(activity, current) { result -> if (!sheet.closed) {
                waitingForPlan = false
                handler.removeCallbacks(poll)
                reloadPlan()
                updateForm()
                result.onSuccess { status.setText(R.string.ai_plan_updated) }.onFailure { status.text = sheet.error(it) }
                if (AiWork.isPlanRunning()) observePlan()
            } }
            observePlan()
        }
        sheet.column.addView(status, sheet.row(8, 12))
        sheet.column.addView(sessions, sheet.row())
        updateForm(); render()
        activity.lifecycle.addObserver(lifecycleObserver)
        // History parsing stays off the UI thread. Incomplete history is not labelled measured fitness.
        worker.execute {
            val result = runCatching {
                val history = RunStore(activity.applicationContext).list().filter { it.activityType == ActivityType.RUN }.map {
                    TrainingHistoryRun(Instant.ofEpochMilli(it.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate(), RunMetrics.distanceMeters(it.points) / 1000.0)
                }
                TrainingBaselineEstimator.infer(history, today)
            }
            activity.runOnUiThread {
                if (!sheet.closed) {
                    baseline = result.getOrElse { TrainingBaselineEstimator.infer(emptyList(), today) }
                    if (!preserveBase && !baseEdited) weekly.second.setText(baseline!!.weeklyKm.toString())
                    if (!preserveBase && !daysEdited) { daysPerWeek = baseline!!.daysPerWeek; daysChoice.second.setText(daysLabel(daysPerWeek), false) }
                    baselineText.text = if (result.isFailure) activity.getString(R.string.plan_v8_history_unavailable) else historySummary(baseline!!)
                    updateForm()
                }
            }
            worker.shutdown()
        }
    }

    private fun updateForm() {
        goalFields.visibility = if (editing) View.VISIBLE else View.GONE
        sessions.visibility = if (editing) View.GONE else View.VISIBLE
        distance.first.visibility = if (goalType == TrainingGoalType.CUSTOM) View.VISIBLE else View.GONE
        val rolling = goalType == TrainingGoalType.PERSONAL_PROGRESS
        timingChoice.first.visibility = if (rolling) View.GONE else View.VISIBLE
        dateButton.visibility = if (rolling) View.GONE else View.VISIBLE
        rollingHint.visibility = if (rolling) View.VISIBLE else View.GONE
        dateButton.text = if (timing == TrainingTiming.TARGET_MONTH) targetMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)) else exactDay.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        weekly.first.visibility = if (adjusting) View.VISIBLE else View.GONE
        adjustButton.setText(if (adjusting) R.string.plan_v8_hide_adjustment else R.string.plan_v8_adjust)
        updateBaseText()
        createButton.setText(if (editing) R.string.ai_plan_create else R.string.ai_plan_edit)
        createButton.isEnabled = !editing || baseline != null
        personalizeButton.visibility = if (plan != null && !editing && AiSettings.isReady(activity)) View.VISIBLE else View.GONE
        personalizeButton.isEnabled = !AiWork.isPlanRunning()
    }

    private fun updateBaseText() {
        val base = weekly.second.text.toString().replace(',', '.').toDoubleOrNull()
        baseText.text = if (base != null && base.isFinite()) activity.getString(if (baseline?.established == true || baseEdited) R.string.plan_v8_base else R.string.plan_v8_gentle_base, number(base)) else ""
    }

    private fun goalFromFields(): TrainingGoal? {
        distance.first.error = null; weekly.first.error = null; status.text = ""
        val km = when (goalType) {
            TrainingGoalType.FIVE_K -> 5.0
            TrainingGoalType.TEN_K -> 10.0
            TrainingGoalType.HALF_MARATHON -> 21.1
            TrainingGoalType.PERSONAL_PROGRESS -> 0.0
            TrainingGoalType.CUSTOM -> distance.second.text.toString().replace(',', '.').toDoubleOrNull()
        }
        if (km == null || !km.isFinite() || (goalType != TrainingGoalType.PERSONAL_PROGRESS && km !in 1.0..50.0)) {
            distance.first.error = activity.getString(R.string.plan_v8_distance_error); distance.second.requestFocus(); return null
        }
        val base = weekly.second.text.toString().replace(',', '.').toDoubleOrNull()
        if (base == null || !base.isFinite() || base !in 5.0..100.0) {
            adjusting = true; updateForm(); weekly.first.error = activity.getString(R.string.plan_v8_base_error); weekly.second.requestFocus(); return null
        }
        val selectedTiming = if (goalType == TrainingGoalType.PERSONAL_PROGRESS) TrainingTiming.ROLLING else timing
        val end = when (selectedTiming) { TrainingTiming.ROLLING -> LocalDate.now().plusWeeks(8); TrainingTiming.TARGET_MONTH -> targetMonth.atEndOfMonth(); else -> exactDay }
        val goal = TrainingGoal(km, end, daysPerWeek, base, goalType, selectedTiming)
        if (!TrainingPlanEngine.validateGoal(goal, LocalDate.now())) { status.setText(R.string.plan_v8_date_error); return null }
        return goal
    }

    private fun chooseDay() {
        val now = LocalDate.now(); val min = now.plusDays(28); val max = now.plusDays(364)
        val initial = exactDay.coerceIn(min, max)
        DatePickerDialog(activity, { _, year, month, day -> exactDay = LocalDate.of(year, month + 1, day); targetMonth = YearMonth.from(exactDay); updateForm() }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            datePicker.minDate = min.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            datePicker.maxDate = max.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    }

    private fun chooseMonth() {
        val now = LocalDate.now()
        val months = (0L..12L).map { YearMonth.from(now).plusMonths(it) }.filter { it.atEndOfMonth() in now.plusDays(28)..now.plusDays(364) }
        val picker = NumberPicker(activity).apply {
            minValue = 0; maxValue = months.lastIndex; wrapSelectorWheel = false
            displayedValues = months.map { it.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)) }.toTypedArray()
            value = months.indexOf(targetMonth).takeIf { it >= 0 } ?: 0
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            contentDescription = activity.getString(R.string.plan_v8_target_month)
        }
        MaterialAlertDialogBuilder(activity).setTitle(R.string.plan_v8_choose_month).setView(picker)
            .setNegativeButton(R.string.main_cancel, null).setPositiveButton(R.string.coach_ui_save) { _, _ ->
                targetMonth = months[picker.value]
                exactDay = targetMonth.atDay(minOf(exactDay.dayOfMonth, targetMonth.lengthOfMonth()))
                updateForm()
            }.show()
    }

    private fun render() {
        sessions.removeAllViews()
        val current = plan ?: return
        val goal = current.goal
        val title = when {
            goal.goalType == TrainingGoalType.PERSONAL_PROGRESS -> activity.getString(R.string.plan_v8_progress_summary)
            goal.timing == TrainingTiming.TARGET_MONTH -> "${goalLabel(goal.goalType, goal.distanceKm)} · ${goal.targetMonth!!.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))}"
            else -> "${goalLabel(goal.goalType, goal.distanceKm)} · ${goal.raceDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))}"
        }
        sessions.addView(sheet.text(title, 20, true), sheet.row(12, 12))
        sessions.addView(sheet.text(activity.getString(if (current.source == "anthropic") R.string.plan_v8_ai_source else R.string.plan_v8_local_source)), sheet.row(0, 12))
        if (current.source == "anthropic") {
            sessions.addView(sheet.button(activity.getString(if (showPlanNotes) R.string.plan_v8_hide_notes else R.string.plan_v8_show_notes), R.drawable.ic_info).apply {
                setOnClickListener { showPlanNotes = !showPlanNotes; render() }
            }, sheet.row(0, 12))
            if (showPlanNotes) sessions.addView(sheet.text(current.summary), sheet.row(0, 16))
        }
        val relevant = current.sessions.filter { !it.date.isBefore(LocalDate.now().minusDays(7)) }
        val visible = if (expanded) relevant else relevant.take(21)
        val format = DateTimeFormatter.ofPattern("EEE d MMM", locale)
        visible.forEach { session ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            row.addView(sheet.text("${session.date.format(format)} · ${number(session.distanceKm)} km · ${kind(session.kind)}", 16, true), sheet.row())
            if (current.source == "anthropic" && showPlanNotes && session.note.isNotBlank()) row.addView(sheet.text(session.note), sheet.row(4, 4))
            row.addView(sheet.button(stateName(session.state), if (session.state == SessionState.DONE) R.drawable.ic_check else R.drawable.ic_clock).apply { setOnClickListener {
                val states = SessionState.entries
                MaterialAlertDialogBuilder(activity).setTitle(session.date.format(format)).setItems(states.map(::stateName).toTypedArray()) { _, which ->
                    runCatching { store.update { saved -> saved.copy(sessions = saved.sessions.map { if (it.date == session.date) it.copy(state = states[which], activityId = null) else it }, updatedAtMs = System.currentTimeMillis()) } }
                        .onSuccess { plan = it; render() }.onFailure { status.setText(R.string.ai_error_storage) }
                }.show()
            } }, sheet.row())
            sessions.addView(row, sheet.row(0, 20))
        }
        if (!expanded && relevant.size > visible.size) sessions.addView(sheet.button(activity.getString(R.string.ai_plan_show_all), R.drawable.ic_history).apply { setOnClickListener { expanded = true; render() } }, sheet.row())
        personalizeButton.visibility = if (!editing && AiSettings.isReady(activity)) View.VISIBLE else View.GONE
    }

    private fun historySummary(value: TrainingBaseline): String = if (value.observedRuns == 0) activity.getString(R.string.plan_v8_no_history) else activity.getString(
        if (value.windowDays == 7) R.string.plan_v8_history_week else R.string.plan_v8_history_month, number(value.observedWeeklyKm),
        activity.resources.getQuantityString(R.plurals.plan_v8_runs_count, value.observedRuns, value.observedRuns))
    private fun number(value: Double) = String.format(locale, "%.1f", value)
    private fun daysLabel(value: Int) = activity.resources.getQuantityString(R.plurals.plan_v8_days_count, value, value)
    private fun goalLabel(type: TrainingGoalType, km: Double? = null): String = activity.getString(when (type) {
        TrainingGoalType.FIVE_K -> R.string.plan_v8_5k; TrainingGoalType.TEN_K -> R.string.plan_v8_10k
        TrainingGoalType.HALF_MARATHON -> R.string.plan_v8_half; TrainingGoalType.PERSONAL_PROGRESS -> R.string.plan_v8_progress
        TrainingGoalType.CUSTOM -> R.string.plan_v8_custom
    }).let { if (type == TrainingGoalType.CUSTOM && km != null) activity.getString(R.string.plan_v8_custom_summary, number(km)) else it }
    private fun kind(kind: String) = activity.getString(when (kind) { "long" -> R.string.ai_kind_long; "recovery" -> R.string.ai_kind_recovery; "steady" -> R.string.ai_kind_steady; else -> R.string.ai_kind_easy })
    private fun stateName(value: SessionState) = activity.getString(when (value) { SessionState.PLANNED -> R.string.ai_session_planned; SessionState.DONE -> R.string.ai_session_done; SessionState.SKIPPED -> R.string.ai_session_skipped })
    private fun spanish() = locale.language == "es"
    private fun field(label: Int): Pair<TextInputLayout, TextInputEditText> = sheet.field(label, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).also { sheet.column.removeView(it.first) }
    private fun dropdown(label: Int): Pair<TextInputLayout, MaterialAutoCompleteTextView> {
        val wrapper = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply { hint = activity.getString(label); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val input = MaterialAutoCompleteTextView(wrapper.context).apply { inputType = InputType.TYPE_NULL; minHeight = sheet.dp(56); setPadding(sheet.dp(16), sheet.dp(12), sheet.dp(16), sheet.dp(12)) }
        wrapper.addView(input); return wrapper to input
    }
    private fun setupChoice(view: MaterialAutoCompleteTextView, labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
        view.setSimpleItems(labels.toTypedArray()); view.setText(labels[selected.coerceIn(labels.indices)], false)
        view.setOnItemClickListener { _, _, index, _ -> onSelected(index) }
    }
    fun show() { sheet.show(); observePlan() }
    fun close() { worker.shutdownNow(); stopObserving(); sheet.close() }
    fun saveState() = Bundle().apply {
        putString("goalType", goalType.name); putString("timing", timing.name); putString("exactDay", exactDay.toString()); putString("targetMonth", targetMonth.toString())
        putString("distance", distance.second.text.toString()); putInt("days", daysPerWeek); putString("weekly", weekly.second.text.toString())
        putBoolean("expanded", expanded); putBoolean("editing", editing); putBoolean("adjusting", adjusting); putBoolean("baselineReady", baseline != null)
        putBoolean("daysEdited", daysEdited); putBoolean("baseEdited", baseEdited); putBoolean("showPlanNotes", showPlanNotes)
        putBoolean("pendingPlan", waitingForPlan); putLong("previousPlanTime", previousPlanTime)
    }

    private fun observePlan() {
        handler.removeCallbacks(poll)
        if (sheet.closed || !isShowing || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (AiWork.isPlanRunning()) {
            if (AiWork.isPlanRequestRunning()) {
                if (!waitingForPlan) previousPlanTime = plan?.updatedAtMs ?: -1L
                waitingForPlan = true
                status.setText(R.string.ai_working)
            }
            personalizeButton.isEnabled = false
            handler.postDelayed(poll, 750)
            return
        }
        reloadPlan()
        updateForm()
        if (waitingForPlan) {
            waitingForPlan = false
            status.setText(if (plan != null && plan!!.updatedAtMs != previousPlanTime) R.string.ai_plan_updated else R.string.ai_error_response)
        }
    }

    /** A late result updates the saved schedule, never the user's form draft. */
    private fun reloadPlan() {
        val saved = store.load()
        if (saved != plan) { plan = saved; render() }
    }

    private fun stopObserving() {
        handler.removeCallbacks(poll)
        activity.lifecycle.removeObserver(lifecycleObserver)
    }
}
