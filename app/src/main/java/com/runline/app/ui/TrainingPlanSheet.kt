package com.runline.app.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.R
import com.runline.app.ai.*
import com.runline.app.core.ActivityType
import com.runline.app.core.RunMetrics
import com.runline.app.data.RunStore
import com.runline.app.training.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TrainingPlanSheet(private val activity: AppCompatActivity, state: Bundle? = null) {
    private val sheet = AiSheet(activity, R.string.ai_plan_title)
    private val store = TrainingPlanStore(activity)
    private var plan = store.load()
    private val status = sheet.text("")
    private val sessions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val goalFields = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val distance = field(R.string.ai_goal_distance, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private val date = field(R.string.ai_goal_date, InputType.TYPE_CLASS_TEXT)
    private val days = field(R.string.ai_goal_days, InputType.TYPE_CLASS_NUMBER)
    private val weekly = field(R.string.ai_goal_weekly, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private var settings: AiSettingsSheet? = null
    private var expanded = state?.getBoolean("expanded") ?: false
    val isShowing get() = sheet.isShowing
    init {
        sheet.column.addView(sheet.text(activity.getString(R.string.ai_plan_hint)), sheet.row(0, 16))
        sheet.column.addView(goalFields, sheet.row())
        val current = plan?.goal
        distance.second.setText(state?.getString("distance") ?: current?.distanceKm?.toString() ?: "21.1")
        date.second.setText(state?.getString("date") ?: current?.raceDate?.toString().orEmpty())
        days.second.setText(state?.getString("days") ?: current?.daysPerWeek?.toString() ?: "3")
        val baseline = runCatching { RunStore(activity).list().filter { it.activityType == ActivityType.RUN && it.startedAtMs >= System.currentTimeMillis() - 28 * 86400000L }
            .sumOf { RunMetrics.distanceMeters(it.points) } / 4000.0 }.getOrDefault(0.0)
        weekly.second.setText(state?.getString("weekly") ?: current?.weeklyKm?.toString() ?: if (baseline >= 5) String.format(Locale.ROOT, "%.1f", baseline) else "")
        date.first.helperText = "YYYY-MM-DD"
        date.first.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM
        date.first.setEndIconDrawable(R.drawable.ic_calendar)
        date.first.setEndIconContentDescription(R.string.ai_choose_date)
        date.first.setEndIconOnClickListener {
            val initial = runCatching { LocalDate.parse(date.second.text.toString()) }.getOrDefault(LocalDate.now().plusWeeks(16))
            DatePickerDialog(activity, { _, y, m, d -> date.second.setText(LocalDate.of(y, m + 1, d).toString()) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }
        if (plan != null) goalFields.visibility = View.GONE
        sheet.column.addView(sheet.button(activity.getString(if (plan == null) R.string.ai_plan_create else R.string.ai_plan_edit), R.drawable.ic_route, plan == null).apply {
            setOnClickListener {
                if (goalFields.visibility != View.VISIBLE) { goalFields.visibility = View.VISIBLE; setText(R.string.ai_plan_create); return@setOnClickListener }
                val goal = runCatching { TrainingGoal(distance.second.text.toString().replace(',', '.').toDouble(), LocalDate.parse(date.second.text.toString().trim()),
                    days.second.text.toString().toInt(), weekly.second.text.toString().replace(',', '.').toDouble()) }.getOrNull()
                if (goal == null || !TrainingPlanEngine.validateGoal(goal, LocalDate.now())) { status.setText(R.string.ai_goal_invalid); return@setOnClickListener }
                fun create() {
                    runCatching {
                        val fresh = TrainingPlanEngine.create(goal, LocalDate.now(), spanish())
                        store.save(fresh); plan = fresh; goalFields.visibility = View.GONE; setText(R.string.ai_plan_edit); render()
                        status.setText(R.string.ai_plan_created)
                    }.onFailure { status.setText(R.string.ai_error_storage) }
                }
                if (plan == null) create() else MaterialAlertDialogBuilder(activity).setTitle(R.string.ai_plan_replace)
                    .setMessage(R.string.ai_plan_replace_hint).setNegativeButton(R.string.main_cancel, null)
                    .setPositiveButton(R.string.coach_ui_save) { _, _ -> create() }.show()
            }
        }, sheet.row(8))
        sheet.column.addView(sheet.button(activity.getString(R.string.ai_plan_personalize), R.drawable.ic_speaker).apply { setOnClickListener {
            val currentPlan = plan ?: run { status.setText(R.string.ai_plan_create_first); return@setOnClickListener }
            if (!AiSettings.load(activity).enabled || !AiSettings.hasKey(activity)) { status.setText(R.string.ai_need_setup); return@setOnClickListener }
            isEnabled = false; status.setText(R.string.ai_working)
            AiWork.personalizePlan(activity, currentPlan) { result -> if (!sheet.closed) {
                isEnabled = true
                result.onSuccess { plan = it; render(); status.setText(R.string.ai_plan_updated) }.onFailure { status.text = sheet.error(it) }
            } }
        } }, sheet.row())
        sheet.column.addView(sheet.button(activity.getString(R.string.ai_title), R.drawable.ic_settings).apply { setOnClickListener { settings?.close(); settings = AiSettingsSheet(activity).also { it.show() } } }, sheet.row())
        sheet.column.addView(status, sheet.row(8, 12))
        sheet.column.addView(sessions, sheet.row())
        render()
        // No network request without the separate automatic-plan option. Missed sessions reconcile on app open.
    }
    private fun render() {
        sessions.removeAllViews()
        val current = plan ?: return
        sessions.addView(sheet.text(activity.getString(R.string.ai_plan_goal_summary, current.goal.distanceKm.toString(), current.goal.raceDate.toString()), 20, true), sheet.row(12, 12))
        sessions.addView(sheet.text(current.summary), sheet.row(0, 16))
        sessions.addView(sheet.text(activity.getString(if (current.source == "anthropic") R.string.ai_plan_source_ai else R.string.ai_plan_source_local)), sheet.row(0, 16))
        val relevant = current.sessions.filter { !it.date.isBefore(LocalDate.now().minusDays(7)) }
        val visible = if (expanded) relevant else relevant.take(21)
        val format = DateTimeFormatter.ofPattern("EEE d MMM", activity.resources.configuration.locales[0])
        visible.forEach { session ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            row.addView(sheet.text("${session.date.format(format)} · ${String.format(activity.resources.configuration.locales[0], "%.1f", session.distanceKm)} km · ${kind(session.kind)}", 16, true), sheet.row())
            row.addView(sheet.text(session.note), sheet.row(4, 4))
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
    }
    private fun kind(kind: String) = activity.getString(when (kind) { "long" -> R.string.ai_kind_long; "recovery" -> R.string.ai_kind_recovery; "steady" -> R.string.ai_kind_steady; else -> R.string.ai_kind_easy })
    private fun stateName(value: SessionState) = activity.getString(when (value) { SessionState.PLANNED -> R.string.ai_session_planned; SessionState.DONE -> R.string.ai_session_done; SessionState.SKIPPED -> R.string.ai_session_skipped })
    private fun spanish() = activity.resources.configuration.locales[0].language == "es"
    private fun field(label: Int, type: Int): Pair<com.google.android.material.textfield.TextInputLayout, com.google.android.material.textfield.TextInputEditText> = sheet.field(label, type).also {
        sheet.column.removeView(it.first); goalFields.addView(it.first, sheet.row(0, 12))
    }
    fun show() = sheet.show()
    fun close() { settings?.close(); sheet.close() }
    fun saveState() = Bundle().apply { putString("distance", distance.second.text.toString()); putString("date", date.second.text.toString()); putString("days", days.second.text.toString()); putString("weekly", weekly.second.text.toString()); putBoolean("expanded", expanded) }
}
