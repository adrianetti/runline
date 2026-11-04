package com.runline.app.ui

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.R
import com.runline.app.ai.AiConfig
import com.runline.app.ai.AiFailure
import com.runline.app.ai.AiProviderCatalog
import com.runline.app.ai.AiSettings
import com.runline.app.ai.AnthropicProvider
import com.runline.app.ai.RunnerGoal
import com.runline.app.ai.RunnerGoalStore
import com.runline.app.tracking.TrackingService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Personal goal drafts survive rotation in memory, never in saved-state Bundles. */
class AiGoalDraft : ViewModel() { var text: String? = null }

class AiSettingsSheet(
    private val activity: AppCompatActivity,
    state: Bundle? = null,
    private val onChanged: () -> Unit = {},
) {
    private val sheet = AiSheet(activity, R.string.ai_v8_title)
    private val config = AiSettings.load(activity)
    private val provider = AiProviderCatalog.find(state?.getString("provider") ?: config.provider)
        ?: AiProviderCatalog.providers.first()
    private val enabled = toggle(R.string.ai_v8_enabled, state?.getBoolean("enabled") ?: config.enabled)
    private val live = toggle(R.string.ai_v8_live, state?.getBoolean("live") ?: config.liveEnabled)
    private val reviews = toggle(R.string.ai_v8_auto_review, state?.getBoolean("reviews") ?: config.automaticReviews)
    private val notes = toggle(R.string.ai_include_notes, state?.getBoolean("notes") ?: config.includeNotes)
    private val goalStore = RunnerGoalStore(activity)
    private val goalDraft = ViewModelProvider(activity)[AiGoalDraft::class.java]
    private val overallGoal: Pair<TextInputLayout, TextInputEditText>
    private val key: Pair<TextInputLayout, TextInputEditText>
    private val liveModel: ModelPicker
    private val reviewModel: ModelPicker
    private var intervalMinutes = state?.getString("interval")?.toIntOrNull()?.takeIf { it in 3..10 } ?: config.intervalMinutes
    private var privacyExpanded = state?.getBoolean("privacyExpanded") ?: false
    private val status = sheet.text("").apply {
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val saveStatus = sheet.text("").apply {
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val testButton: MaterialButton
    private var usage: DiagnosticsSheet? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val checkGeneration = AtomicInteger()
    val isShowing get() = sheet.isShowing

    init {
        sheet.column.addView(enabled, sheet.row(0, 4))
        sheet.column.addView(sheet.text(activity.getString(R.string.ai_v8_summary)), sheet.row(0, 4))
        val disclosure = sheet.text(activity.getString(R.string.smart_ai_disclosure)).apply {
            visibility = if (privacyExpanded) View.VISIBLE else View.GONE
        }
        val privacyButton = sheet.button(activity.getString(R.string.ai_v8_data_costs), R.drawable.ic_shield_check).apply {
            setOnClickListener {
                privacyExpanded = !privacyExpanded
                disclosure.visibility = if (privacyExpanded) View.VISIBLE else View.GONE
                setText(if (privacyExpanded) R.string.ai_v8_hide_details else R.string.ai_v8_data_costs)
            }
            if (privacyExpanded) setText(R.string.ai_v8_hide_details)
        }
        sheet.column.addView(privacyButton, sheet.row(0, 8))
        sheet.column.addView(disclosure, sheet.row(0, 12))

        overallGoal = sheet.field(R.string.smart_overall_goal, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        overallGoal.first.isSaveEnabled = false
        overallGoal.first.helperText = activity.getString(R.string.smart_overall_goal_hint)
        overallGoal.second.apply {
            isSaveEnabled = false; setSaveFromParentEnabled(false)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(RunnerGoal.TEXT_LIMIT))
            minLines = 2; maxLines = 3
            setText(goalDraft.text ?: goalStore.load().text)
            doAfterTextChanged { goalDraft.text = text.toString() }
        }

        dropdown(R.string.ai_v8_connection, AiProviderCatalog.providers.map { it.name },
            AiProviderCatalog.providers.indexOf(provider))
        key = sheet.field(R.string.ai_v8_key, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        key.first.isSaveEnabled = false
        key.second.apply {
            isSaveEnabled = false
            setSaveFromParentEnabled(false)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            filters = arrayOf(InputFilter.LengthFilter(512))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        key.first.helperText = activity.getString(if (AiSettings.hasKey(activity)) R.string.ai_key_saved else R.string.ai_key_hint)
        sheet.column.addView(sheet.button(activity.getString(R.string.ai_remove_key), R.drawable.ic_trash).apply {
            visibility = if (AiSettings.hasKey(activity)) View.VISIBLE else View.GONE
            setOnClickListener {
                runCatching { AiSettings.clearKey(activity) }.onSuccess {
                    invalidateCheck()
                    key.second.text?.clear()
                    enabled.isChecked = false; live.isChecked = false; reviews.isChecked = false
                    key.first.helperText = activity.getString(R.string.ai_key_hint)
                    visibility = View.GONE
                    showStatus(R.string.ai_key_removed)
                    TrackingService.refreshCoaching()
                    onChanged()
                }.onFailure { showStatus(R.string.ai_error_storage) }
            }
        }, sheet.row(0, 8))

        section(R.string.ai_v8_models)
        liveModel = ModelPicker(R.string.ai_v8_live_model, state?.getString("liveModel") ?: config.liveModel,
            state?.getString("liveCustom"), state?.getBoolean("liveCustomSelected"))
        reviewModel = ModelPicker(R.string.smart_ai_review_model, state?.getString("reviewModel") ?: config.reviewModel,
            state?.getString("reviewCustom"), state?.getBoolean("reviewCustomSelected"))
        testButton = sheet.button(activity.getString(R.string.ai_test), R.drawable.ic_check).apply {
            setOnClickListener { testConnection() }
        }
        sheet.column.addView(testButton, sheet.row(0, 4))
        sheet.column.addView(status, sheet.row(4, 12))

        sheet.column.addView(sheet.button(activity.getString(R.string.diag_v12_usage), R.drawable.ic_history).apply {
            setOnClickListener { usage?.close(); usage = DiagnosticsSheet(activity, usageOnly = true).also { it.show() } }
        }, sheet.row(0, 12))
        if (state?.getBoolean("usage") == true) sheet.column.post { usage = DiagnosticsSheet(activity, usageOnly = true).also { it.show() } }
        section(R.string.ai_v8_features)
        val allFeatures = toggle(R.string.ai_v9_all_features, live.isChecked && reviews.isChecked)
        var synchronizingFeatures = false
        allFeatures.setOnCheckedChangeListener { _, checked ->
            if (!synchronizingFeatures) {
                synchronizingFeatures = true
                live.isChecked = checked; reviews.isChecked = checked
                if (checked) enabled.isChecked = true
                synchronizingFeatures = false
            }
        }
        listOf(live, reviews).forEach { feature ->
            feature.setOnCheckedChangeListener { _, _ ->
                if (!synchronizingFeatures) {
                    synchronizingFeatures = true
                    allFeatures.isChecked = live.isChecked && reviews.isChecked
                    synchronizingFeatures = false
                }
            }
        }
        sheet.column.addView(allFeatures, sheet.row(0, 12))
        sheet.column.addView(live, sheet.row(0, 4))
        sheet.column.addView(sheet.text(activity.getString(R.string.ai_v8_live_hint), 14), sheet.row(0, 12))
        dropdown(R.string.ai_v8_interval, (3..10).map { activity.getString(R.string.ai_v8_minutes, it) }, intervalMinutes - 3) { index ->
            intervalMinutes = index + 3
        }
        listOf(reviews, notes).forEach { sheet.column.addView(it, sheet.row(0, 8)) }
        sheet.primaryAction(sheet.button(activity.getString(R.string.coach_ui_save), filled = true).apply {
            setOnClickListener { save() }
        }, saveStatus)
        key.second.doAfterTextChanged { key.first.error = null; invalidateCheck() }
        sheet.dialog.setOnDismissListener { dispose(); sheet.closed = true }
    }

    fun show() = sheet.show()
    fun close() { dispose(); sheet.close() }

    /** API keys are deliberately omitted, including unsaved input. */
    fun saveState() = Bundle().apply {
        putBoolean("usage", usage?.isShowing == true)
        putBoolean("enabled", enabled.isChecked); putBoolean("live", live.isChecked)
        putBoolean("reviews", reviews.isChecked); putBoolean("notes", notes.isChecked)
        putString("provider", provider.id)
        putString("liveModel", liveModel.value()); putString("reviewModel", reviewModel.value())
        putString("liveCustom", liveModel.customValue()); putString("reviewCustom", reviewModel.customValue())
        putBoolean("liveCustomSelected", liveModel.isCustom()); putBoolean("reviewCustomSelected", reviewModel.isCustom())
        putString("interval", intervalMinutes.toString()); putBoolean("privacyExpanded", privacyExpanded)
    }

    private fun save() {
        var desired = AiConfig(enabled.isChecked, live.isChecked, reviews.isChecked, false,
            liveModel.value(), reviewModel.value(), intervalMinutes, notes.isChecked, provider.id)
        if (!desired.valid() && !enabled.isChecked) desired = config.copy(enabled = false, automaticPlanUpdates = false)
        if (!desired.valid()) { validateModels(); return }
        val entered = key.second.text.toString().trim()
        if (!desired.enabled) {
            runCatching {
                // Disabling takes effect before touching the key store. Invalid drafts
                // cannot keep background AI enabled; valid model selections still save.
                AiSettings.save(activity, desired)
                goalStore.save(RunnerGoal(overallGoal.second.text.toString()))
                goalDraft.text = null
                TrackingService.refreshCoaching()
                onChanged()
                if (entered.isNotBlank() && AiSettings.validKey(entered)) AiSettings.saveKey(activity, entered)
            }.onSuccess { close() }
                .onFailure { saveStatus.setText(R.string.ai_error_storage); saveStatus.visibility = View.VISIBLE }
            return
        }
        if (entered.isNotBlank() && !AiSettings.validKey(entered)) {
            key.first.error = activity.getString(R.string.ai_error_key)
            key.second.requestFocus()
            return
        }
        if (desired.enabled && entered.isBlank() && !AiSettings.hasKey(activity)) {
            key.first.error = activity.getString(R.string.ai_v8_key_required)
            key.second.requestFocus()
            return
        }
        runCatching {
            if (entered.isNotBlank()) AiSettings.saveKey(activity, entered)
            goalStore.save(RunnerGoal(overallGoal.second.text.toString()))
            AiSettings.save(activity, desired)
        }.onSuccess {
            goalDraft.text = null
            TrackingService.refreshCoaching()
            onChanged()
            close()
        }.onFailure { saveStatus.setText(R.string.ai_error_storage); saveStatus.visibility = View.VISIBLE }
    }

    private fun testConnection() {
        if (!validateModels()) return
        val candidate = key.second.text.toString().trim().ifBlank { AiSettings.readKey(activity).orEmpty() }
        if (!AiSettings.validKey(candidate)) {
            key.first.error = activity.getString(R.string.ai_v8_key_required)
            key.second.requestFocus()
            return
        }
        val models = listOf(liveModel.value(), reviewModel.value()).distinct()
        val generation = checkGeneration.incrementAndGet()
        testButton.isEnabled = false
        showStatus(R.string.ai_testing)
        worker.execute {
            val result = runCatching {
                val connection = com.runline.app.diagnostics.Diagnostics.provider(activity, "connection")
                models.forEach { model ->
                    if (checkGeneration.get() != generation || Thread.currentThread().isInterrupted) throw AiFailure(AiFailure.Reason.DISABLED)
                    connection.checkModel(candidate, model)
                }
            }
            main.post {
                if (!sheet.closed) {
                    testButton.isEnabled = true
                    if (checkGeneration.get() == generation) showStatus(result.fold(
                        { activity.getString(R.string.ai_v8_test_ok) }, sheet::error))
                }
            }
        }
    }

    private fun validateModels(): Boolean {
        val validLive = liveModel.validate()
        val validReview = reviewModel.validate()
        return validLive && validReview
    }

    private fun invalidateCheck() { checkGeneration.incrementAndGet(); status.visibility = View.GONE; saveStatus.visibility = View.GONE }
    private fun dispose() {
        usage?.close(); usage = null
        checkGeneration.incrementAndGet(); key.second.text?.clear(); worker.shutdownNow()
        if (!activity.isChangingConfigurations) goalDraft.text = null
    }
    private fun showStatus(message: Int) = showStatus(activity.getString(message))
    private fun showStatus(message: String) { status.text = message; status.visibility = View.VISIBLE }
    private fun section(label: Int) { sheet.column.addView(sheet.text(activity.getString(label), 18, true), sheet.row(20, 12)) }
    private fun toggle(label: Int, checked: Boolean) = MaterialSwitch(activity).apply {
        setText(label); isChecked = checked; minHeight = sheet.dp(56); setPadding(0, sheet.dp(8), 0, sheet.dp(8))
    }

    private fun dropdown(label: Int, values: List<String>, selected: Int, changed: (Int) -> Unit = {}): MaterialAutoCompleteTextView {
        val wrapper = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = activity.getString(label)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        val choice = MaterialAutoCompleteTextView(wrapper.context).apply {
            inputType = InputType.TYPE_NULL
            minHeight = sheet.dp(56)
            setPadding(sheet.dp(16), sheet.dp(12), sheet.dp(16), sheet.dp(12))
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, values))
            setText(values[selected.coerceIn(values.indices)], false)
            setOnItemClickListener { _, _, index, _ -> changed(index) }
        }
        wrapper.addView(choice, LinearLayout.LayoutParams(-1, -2))
        sheet.column.addView(wrapper, sheet.row(4, 12))
        return choice
    }

    private inner class ModelPicker(label: Int, savedId: String, customDraft: String?, customSelected: Boolean?) {
        private var index = provider.presetIndex(savedId).let { if (it < 0 || customSelected == true) provider.models.size else it }
        private val choice = dropdown(label, provider.models.map { it.name } + activity.getString(R.string.ai_v8_custom_model), index)
        private val custom = sheet.field(R.string.ai_v8_custom_id, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        init {
            custom.second.filters = arrayOf(InputFilter.LengthFilter(107))
            custom.second.setText(customDraft ?: if (index == provider.models.size) savedId else "")
            custom.first.helperText = activity.getString(R.string.ai_v8_custom_hint)
            custom.first.visibility = if (index == provider.models.size) View.VISIBLE else View.GONE
            choice.setOnItemClickListener { _, _, selected, _ ->
                index = selected
                custom.first.visibility = if (index == provider.models.size) View.VISIBLE else View.GONE
                custom.first.error = null
                invalidateCheck()
            }
            custom.second.doAfterTextChanged { custom.first.error = null; invalidateCheck() }
        }
        fun isCustom(): Boolean = index == provider.models.size
        fun value(): String = provider.models.getOrNull(index)?.id ?: customValue()
        fun customValue(): String = custom.second.text.toString().trim()
        fun validate(): Boolean = AiConfig.validModel(value()).also { valid ->
            custom.first.error = if (valid) null else activity.getString(R.string.ai_v8_model_required)
            if (!valid) custom.second.requestFocus()
        }
    }
}
