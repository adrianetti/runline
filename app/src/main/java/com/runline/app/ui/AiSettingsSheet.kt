package com.runline.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.appcompat.app.AppCompatActivity
import com.runline.app.R
import com.runline.app.ai.*
import com.runline.app.tracking.TrackingService
import java.util.concurrent.Executors

class AiSettingsSheet(private val activity: AppCompatActivity, state: Bundle? = null) {
    private val sheet = AiSheet(activity, R.string.ai_title)
    private val config = AiSettings.load(activity)
    private val enabled = toggle(R.string.ai_enabled, state?.getBoolean("enabled") ?: config.enabled)
    private val live = toggle(R.string.ai_live, state?.getBoolean("live") ?: config.liveEnabled)
    private val reviews = toggle(R.string.ai_auto_review, state?.getBoolean("reviews") ?: config.automaticReviews)
    private val adapt = toggle(R.string.ai_auto_plan, state?.getBoolean("adapt") ?: config.automaticPlanUpdates)
    private val notes = toggle(R.string.ai_include_notes, state?.getBoolean("notes") ?: config.includeNotes)
    private val key: Pair<com.google.android.material.textfield.TextInputLayout, com.google.android.material.textfield.TextInputEditText>
    private val liveModel: Pair<com.google.android.material.textfield.TextInputLayout, com.google.android.material.textfield.TextInputEditText>
    private val reviewModel: Pair<com.google.android.material.textfield.TextInputLayout, com.google.android.material.textfield.TextInputEditText>
    private val interval: Pair<com.google.android.material.textfield.TextInputLayout, com.google.android.material.textfield.TextInputEditText>
    private val status = sheet.text("")
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    val isShowing get() = sheet.isShowing
    init {
        sheet.column.addView(sheet.text(activity.getString(R.string.ai_disclosure)), sheet.row(0, 16))
        listOf(enabled, live, reviews, adapt, notes).forEach { sheet.column.addView(it, sheet.row(0, 4)) }
        key = sheet.field(R.string.ai_key, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        key.second.isSaveEnabled = false; key.second.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        key.first.helperText = activity.getString(if (AiSettings.hasKey(activity)) R.string.ai_key_saved else R.string.ai_key_hint)
        liveModel = sheet.field(R.string.ai_live_model, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        liveModel.second.setText(state?.getString("liveModel") ?: config.liveModel)
        reviewModel = sheet.field(R.string.ai_review_model, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        reviewModel.second.setText(state?.getString("reviewModel") ?: config.reviewModel)
        listOf(liveModel, reviewModel).forEach { field ->
            field.first.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM
            field.first.setEndIconDrawable(R.drawable.ic_more)
            field.first.setEndIconContentDescription(R.string.ai_choose_model)
            field.first.setEndIconOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.ai_choose_model)
                    .setItems(arrayOf("Haiku 5.5", "Sonnet 5.5")) { _, which -> field.second.setText(if (which == 0) "claude-haiku-5-5" else "claude-sonnet-5-5") }.show()
            }
        }
        sheet.column.addView(sheet.text(activity.getString(R.string.ai_model_hint)), sheet.row(0, 12))
        interval = sheet.field(R.string.ai_interval, InputType.TYPE_CLASS_NUMBER)
        interval.second.setText(state?.getString("interval") ?: config.intervalMinutes.toString())
        sheet.column.addView(sheet.button(activity.getString(R.string.ai_test), R.drawable.ic_check).apply { setOnClickListener {
            val candidate = key.second.text.toString().trim().ifBlank { AiSettings.readKey(activity).orEmpty() }
            val model = liveModel.second.text.toString().trim()
            if (candidate.isBlank() || !AiConfig.validModel(model)) { status.setText(R.string.ai_error_key); return@setOnClickListener }
            isEnabled = false; status.setText(R.string.ai_testing)
            worker.execute {
                val result = runCatching { AnthropicProvider().checkModel(candidate, model) }
                main.post { if (!sheet.closed) { isEnabled = true; status.text = result.fold({ activity.getString(R.string.ai_test_ok) }, sheet::error) } }
            }
        } }, sheet.row())
        sheet.column.addView(sheet.button(activity.getString(R.string.ai_remove_key), R.drawable.ic_trash).apply { setOnClickListener {
            runCatching { AiSettings.clearKey(activity) }.onSuccess {
                key.second.text?.clear(); enabled.isChecked = false; live.isChecked = false; reviews.isChecked = false; adapt.isChecked = false
                key.first.helperText = activity.getString(R.string.ai_key_hint); status.setText(R.string.ai_key_removed); TrackingService.refreshCoaching()
            }.onFailure { status.setText(R.string.ai_error_storage) }
        } }, sheet.row())
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        sheet.column.addView(status, sheet.row(8, 12))
        sheet.column.addView(sheet.button(activity.getString(R.string.coach_ui_save), filled = true).apply { setOnClickListener { save() } }, sheet.row())
        sheet.dialog.setOnDismissListener { key.second.text?.clear(); sheet.closed = true; worker.shutdownNow() }
    }
    fun show() = sheet.show()
    fun close() { key.second.text?.clear(); worker.shutdownNow(); sheet.close() }
    fun saveState() = Bundle().apply {
        putBoolean("enabled", enabled.isChecked); putBoolean("live", live.isChecked); putBoolean("reviews", reviews.isChecked); putBoolean("adapt", adapt.isChecked); putBoolean("notes", notes.isChecked)
        putString("liveModel", liveModel.second.text.toString()); putString("reviewModel", reviewModel.second.text.toString()); putString("interval", interval.second.text.toString())
    }
    private fun save() {
        var desired = AiConfig(enabled.isChecked, live.isChecked, reviews.isChecked, adapt.isChecked,
            liveModel.second.text.toString().trim(), reviewModel.second.text.toString().trim(), interval.second.text.toString().toIntOrNull() ?: 0, notes.isChecked)
        if (!desired.valid() && !enabled.isChecked) desired = config.copy(enabled = false)
        if (!desired.valid()) { status.setText(R.string.ai_fields_invalid); return }
        val entered = key.second.text.toString().trim()
        if (desired.enabled && entered.isBlank() && !AiSettings.hasKey(activity)) { key.first.error = activity.getString(R.string.ai_error_key); return }
        runCatching { if (entered.isNotBlank()) AiSettings.saveKey(activity, entered); AiSettings.save(activity, desired) }
            .onSuccess { TrackingService.refreshCoaching(); close() }.onFailure { status.setText(R.string.ai_error_storage) }
    }
    private fun toggle(label: Int, checked: Boolean) = MaterialSwitch(activity).apply { setText(label); isChecked = checked; minHeight = sheet.dp(56) }
}
