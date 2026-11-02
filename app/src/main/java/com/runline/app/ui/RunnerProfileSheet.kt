package com.runline.app.ui

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.R
import com.runline.app.ai.*
import com.runline.app.tracking.TrackingService

/** A rotation-only memory draft; sensitive text never enters saved instance state. */
class RunnerProfileDraft : ViewModel() { var value: RunnerProfile? = null }

class RunnerProfileSheet(private val activity: AppCompatActivity, @Suppress("UNUSED_PARAMETER") state: Bundle? = null) {
    private val sheet = AiSheet(activity, R.string.profile_v9_title)
    private val store = RunnerProfileStore(activity)
    private val draft = ViewModelProvider(activity)[RunnerProfileDraft::class.java]
    private val initial = draft.value ?: store.load()
    private var experience = initial.experience
    private val preference = sheet.field(R.string.profile_v9_preferences, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    private val health = sheet.field(R.string.profile_v9_health, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    private val include = MaterialSwitch(activity).apply {
        setText(R.string.profile_v9_include); isChecked = initial.includeInAi; minHeight = sheet.dp(56)
        isSaveEnabled = false; setSaveFromParentEnabled(false)
    }
    private val status = sheet.text("").apply { visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    val isShowing get() = sheet.isShowing

    init {
        sheet.column.removeView(preference.first); sheet.column.removeView(health.first)
        sheet.column.addView(sheet.text(activity.getString(R.string.profile_v9_optional)), sheet.row(0, 16))
        val wrapper = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = activity.getString(R.string.profile_v9_experience); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            isSaveEnabled = false
        }
        val choice = MaterialAutoCompleteTextView(wrapper.context).apply {
            inputType = InputType.TYPE_NULL; minHeight = sheet.dp(56)
            isSaveEnabled = false; setSaveFromParentEnabled(false); importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            setSimpleItems(RunningExperience.entries.map { activity.getString(experienceLabel(it)) }.toTypedArray())
            setText(activity.getString(experienceLabel(experience)), false)
            setOnItemClickListener { _, _, index, _ -> experience = RunningExperience.entries[index]; saveDraft() }
        }
        wrapper.addView(choice); sheet.column.addView(wrapper, sheet.row(0, 16))
        listOf(preference to initial.preferences, health to initial.healthNotes).forEach { (field, value) ->
            field.first.isSaveEnabled = false
            field.second.apply {
                isSaveEnabled = false; setSaveFromParentEnabled(false)
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                filters = arrayOf(InputFilter.LengthFilter(RunnerProfile.TEXT_LIMIT))
                minLines = 2; maxLines = 4; setText(value)
                doAfterTextChanged { saveDraft() }
            }
            sheet.column.addView(field.first, sheet.row(0, 16))
        }
        sheet.column.addView(include, sheet.row(0, 4))
        sheet.column.addView(sheet.text(activity.getString(R.string.profile_v9_disclosure), 14), sheet.row(0, 12))
        include.setOnCheckedChangeListener { _, _ -> saveDraft() }
        sheet.column.addView(sheet.button(activity.getString(R.string.profile_v9_clear), R.drawable.ic_trash).apply {
            setOnClickListener {
                runCatching { store.clear(); TrackingService.refreshCoaching() }.onSuccess {
                    draft.value = null; close()
                }.onFailure { showError() }
            }
        }, sheet.row())
        sheet.primaryAction(sheet.button(activity.getString(R.string.coach_ui_save), filled = true).apply {
            setOnClickListener {
                runCatching { store.save(current()); TrackingService.refreshCoaching() }.onSuccess {
                    draft.value = null; close()
                }.onFailure { showError() }
            }
        }, status)
        sheet.dialog.setOnDismissListener { sheet.closed = true; clearDraftUnlessRotating() }
    }
    fun show() = sheet.show()
    fun close() { clearDraftUnlessRotating(); sheet.close() }
    fun saveState() = Bundle()
    private fun current() = RunnerProfile(experience, preference.second.text.toString(), health.second.text.toString(), include.isChecked).bounded()
    private fun saveDraft() { draft.value = current() }
    private fun clearDraftUnlessRotating() { if (!activity.isChangingConfigurations) draft.value = null }
    private fun showError() { status.setText(R.string.ai_error_storage); status.visibility = View.VISIBLE }
    private fun experienceLabel(value: RunningExperience) = when (value) {
        RunningExperience.UNSPECIFIED -> R.string.profile_v9_unspecified
        RunningExperience.STARTING -> R.string.profile_v9_starting
        RunningExperience.RETURNING -> R.string.profile_v9_returning
        RunningExperience.REGULAR -> R.string.profile_v9_regular
        RunningExperience.EXPERIENCED -> R.string.profile_v9_experienced
    }
}
