package com.runline.app.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.R
import com.runline.app.coaching.CoachSpeech
import com.runline.app.coaching.SpeechResult
import com.runline.app.coaching.VoiceCoachSettings
import com.runline.app.core.CoachConfig
import com.runline.app.core.CoachGoal
import kotlin.math.roundToInt

/** Optional settings; drafts survive rotation and never modify the recording itself. */
class VoiceCoachSheet(private val activity: AppCompatActivity, state: Bundle?, private val onSaved: () -> Unit) {
    private val config = VoiceCoachSettings.load(activity)
    private val sheet = AiSheet(activity, R.string.coach_ui_title)
    private val dialog = sheet.dialog
    private val speech = CoachSpeech(activity)
    private val enabled = MaterialSwitch(activity)
    private val kilometres = MaterialSwitch(activity)
    private val paceFeedback = MaterialSwitch(activity)
    private val encouragement = MaterialSwitch(activity)
    private val terrain = MaterialSwitch(activity)
    private val laps = MaterialSwitch(activity)
    private val status = text("", 14)
    private val distance = field(R.string.coach_ui_distance, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private val pace = field(R.string.coach_ui_pace, InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
    private val time = field(R.string.coach_ui_time, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    private var goal = runCatching { CoachGoal.valueOf(state?.getString("goal") ?: config.goal.name) }.getOrDefault(CoachGoal.NONE)
    private var closed = false
    val isShowing get() = dialog.isShowing

    init {
        val column = sheet.column
        enabled.text = activity.getString(R.string.coach_ui_enabled)
        enabled.isChecked = state?.getBoolean("enabled") ?: config.enabled
        enabled.minHeight = dp(56)
        column.addView(enabled, row())
        val goals = CoachGoal.entries
        val goalField = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = activity.getString(R.string.coach_ui_goal); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        val goalChoice = MaterialAutoCompleteTextView(goalField.context).apply {
            inputType = InputType.TYPE_NULL; minHeight = dp(56)
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, goals.map(::goalName)))
            setText(goalName(goal), false)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnItemClickListener { _, _, index, _ -> goal = goals[index]; updateFields() }
        }
        goalField.addView(goalChoice, LinearLayout.LayoutParams(-1, -2))
        column.addView(goalField, row(16, 12))
        distance.second.setText(state?.getString("distance") ?: decimal(config.distanceKm))
        pace.second.setText(state?.getString("pace") ?: "%d:%02d".format(java.util.Locale.ROOT, config.paceSecondsPerKm / 60, config.paceSecondsPerKm % 60))
        time.second.setText(state?.getString("time") ?: decimal(config.targetMinutes))
        time.first.helperText = activity.getString(R.string.coach_ui_active_time)
        listOf(distance, pace, time).forEach { column.addView(it.first, row(0, 12)) }
        kilometres.text = activity.getString(R.string.coach_ui_kilometres)
        kilometres.isChecked = state?.getBoolean("kilometres") ?: config.kilometreAnnouncements
        kilometres.minHeight = dp(56)
        column.addView(kilometres, row())
        column.addView(text(activity.getString(R.string.coach_ui_feedback), 18, true), row(20, 8))
        fun feedback(switch: MaterialSwitch, label: Int, key: String, fallback: Boolean) {
            switch.text = activity.getString(label); switch.minHeight = dp(56)
            switch.isChecked = state?.getBoolean(key) ?: fallback
            column.addView(switch, row())
        }
        feedback(paceFeedback, R.string.coach_ui_pace_feedback, "paceFeedback", config.paceFeedback)
        feedback(encouragement, R.string.coach_ui_encouragement, "encouragement", config.encouragement)
        feedback(terrain, R.string.coach_ui_terrain, "terrain", config.terrainFeedback)
        feedback(laps, R.string.coach_ui_laps, "laps", config.lapFeedback)
        val voiceActions = LinearLayout(activity).apply { isBaselineAligned = false }
        voiceActions.addView(button(activity.getString(R.string.coach_ui_test), R.drawable.ic_speaker).apply {
            setOnClickListener {
                status.setText(R.string.coach_ui_preparing)
                speech.speakPreview { result -> activity.runOnUiThread {
                    if (!closed) status.setText(when (result) {
                        SpeechResult.READY -> R.string.coach_ui_ready
                        SpeechResult.UNAVAILABLE -> R.string.coach_ui_unavailable
                        SpeechResult.AUDIO_BUSY -> R.string.coach_ui_audio_busy
                        SpeechResult.ERROR -> R.string.coach_ui_error
                    })
                } }
            }
        }, LinearLayout.LayoutParams(0, -1, 1f))
        voiceActions.addView(button(activity.getString(R.string.coach_ui_settings), R.drawable.ic_settings).apply {
            setOnClickListener {
                speech.stop()
                runCatching { activity.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                    .onFailure { status.setText(R.string.coach_ui_unavailable) }
            }
        }, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(8) })
        column.addView(voiceActions, row(16))
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        sheet.primaryAction(button(activity.getString(R.string.coach_ui_save), R.drawable.ic_check, filled = true).apply { setOnClickListener { save() } }, status)
        dialog.setOnDismissListener { closed = true; sheet.closed = true; speech.close() }
        updateFields()
    }

    fun show() { dialog.show(); speech.prepare() }
    fun onHostPause() = speech.stop()
    fun close() { speech.close(); closed = true; dialog.dismiss() }
    fun saveState() = Bundle().apply {
        putString("goal", goal.name); putBoolean("enabled", enabled.isChecked); putBoolean("kilometres", kilometres.isChecked)
        putBoolean("paceFeedback", paceFeedback.isChecked); putBoolean("encouragement", encouragement.isChecked)
        putBoolean("terrain", terrain.isChecked); putBoolean("laps", laps.isChecked)
        putString("distance", distance.second.text.toString()); putString("pace", pace.second.text.toString()); putString("time", time.second.text.toString())
    }

    private fun save() {
        listOf(distance, pace, time).forEach { it.first.error = null }
        val km = distance.second.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val minutes = time.second.text.toString().trim().replace(',', '.').toDoubleOrNull()
        val pieces = pace.second.text.toString().trim().split(':')
        val paceMinutes = pieces.getOrNull(0)?.toIntOrNull()
        val paceSeconds = pieces.getOrNull(1)?.toIntOrNull()
        val seconds = if (pieces.size == 2 && paceMinutes != null && paceMinutes in 0..60 && paceSeconds != null && paceSeconds in 0..59) paceMinutes * 60 + paceSeconds else null
        var valid = true
        if (goal in setOf(CoachGoal.DISTANCE, CoachGoal.DISTANCE_TIME) && (km == null || !km.isFinite() || km !in .1..500.0)) {
            distance.first.error = activity.getString(R.string.coach_ui_distance_error); valid = false
        }
        if (goal == CoachGoal.PACE && (seconds == null || seconds !in 60..3600)) {
            pace.first.error = activity.getString(R.string.coach_ui_pace_error); valid = false
        }
        if (goal == CoachGoal.DISTANCE_TIME && (minutes == null || !minutes.isFinite() || minutes !in 1.0..1440.0)) {
            time.first.error = activity.getString(R.string.coach_ui_time_error); valid = false
        }
        if (valid && goal == CoachGoal.DISTANCE_TIME && km != null && minutes != null && minutes * 60 / km !in 60.0..3600.0) {
            time.first.error = activity.getString(R.string.coach_ui_combination_error); valid = false
        }
        if (!valid && enabled.isChecked) return
        val chosen = (if (valid) CoachConfig(enabled.isChecked, goal, km ?: config.distanceKm, seconds ?: config.paceSecondsPerKm, minutes ?: config.targetMinutes, kilometres.isChecked)
            else config.copy(enabled = false, kilometreAnnouncements = kilometres.isChecked)).copy(
                paceFeedback = paceFeedback.isChecked, encouragement = encouragement.isChecked,
                terrainFeedback = terrain.isChecked, lapFeedback = laps.isChecked)
        if (!VoiceCoachSettings.save(activity, chosen)) { status.setText(R.string.coach_ui_invalid); return }
        onSaved(); close()
    }

    private fun updateFields() {
        distance.first.visibility = if (goal in setOf(CoachGoal.DISTANCE, CoachGoal.DISTANCE_TIME)) View.VISIBLE else View.GONE
        pace.first.visibility = if (goal == CoachGoal.PACE) View.VISIBLE else View.GONE
        time.first.visibility = if (goal == CoachGoal.DISTANCE_TIME) View.VISIBLE else View.GONE
    }
    private fun goalName(goal: CoachGoal) = activity.getString(when(goal) {
        CoachGoal.NONE -> R.string.coach_ui_no_goal; CoachGoal.DISTANCE -> R.string.coach_ui_goal_distance
        CoachGoal.PACE -> R.string.coach_ui_goal_pace; CoachGoal.DISTANCE_TIME -> R.string.coach_ui_goal_time
    })
    private fun field(label: Int, type: Int): Pair<TextInputLayout, TextInputEditText> {
        val wrapper = TextInputLayout(activity).apply { hint = activity.getString(label); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val edit = TextInputEditText(wrapper.context).apply { inputType = type; maxLines = 1; minHeight = dp(56); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        wrapper.addView(edit, LinearLayout.LayoutParams(-1, -2))
        return wrapper to edit
    }
    private fun text(value: String, size: Int, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(activity.getColor(if (bold) R.color.on_surface else R.color.on_surface_variant))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun button(label: String, icon: Int, filled: Boolean = false) = MaterialButton(activity, null,
        if (filled) com.google.android.material.R.attr.materialButtonStyle else com.google.android.material.R.attr.borderlessButtonStyle).apply {
        text = label; isAllCaps = false; textSize = 14f; minHeight = dp(48); minimumHeight = dp(48); minWidth = 0; minimumWidth = 0
        setIconResource(icon); iconSize = dp(22); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; iconPadding = dp(8); cornerRadius = dp(16)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    private fun row(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
    private fun decimal(value: Double) = String.format(activity.resources.configuration.locales[0], "%.1f", value)
}
