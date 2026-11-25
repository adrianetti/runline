package com.runline.app.ui

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
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

class RunnerProfileDraft : ViewModel() {
    var value: RunnerProfile? = null
}

class RunnerProfileSheet(
    private val activity: AppCompatActivity,
    @Suppress("UNUSED_PARAMETER") state: Bundle? = null,
) {
    private val sheet = AiSheet(activity, R.string.profile_v9_title)
    private val store = RunnerProfileStore(activity)
    private val draft = ViewModelProvider(activity)[RunnerProfileDraft::class.java]
    private val initial = draft.value ?: store.load()
    private var experience = initial.experience
    private val preference = sheet.field(R.string.profile_v9_preferences, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    private val health = sheet.field(R.string.profile_v9_health, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    private val include =
        MaterialSwitch(activity).apply {
            setText(R.string.profile_v9_include)
            isChecked = initial.includeInAi
            minHeight = sheet.dp(56)
            isSaveEnabled = false
            setSaveFromParentEnabled(false)
        }
    private val status =
        sheet.text("").apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
    private val aiReady = AiSettings.isReady(activity)
    private val managed =
        MaterialSwitch(activity).apply {
            setText(R.string.v17_profile_ai_managed)
            isChecked = initial.aiManaged && aiReady
            isEnabled = aiReady || initial.aiManaged
            minHeight = sheet.dp(56)
            isSaveEnabled = false
            setSaveFromParentEnabled(false)
        }
    private val snapshotFields = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
    private val insightsCard = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
    private var experienceField: View? = null
    val isShowing get() = sheet.isShowing

    init {
        sheet.column.removeView(preference.first)
        sheet.column.removeView(health.first)
        sheet.column.addView(sheet.text(activity.getString(R.string.v17_profile_from_activities), 16, bold = true), sheet.row(0, 4))
        sheet.column.addView(snapshotFields, sheet.row(0, 16))
        loadSnapshot()
        sheet.column.addView(managed, sheet.row(0, 0))
        sheet.column.addView(sheet.text(activity.getString(if (aiReady) R.string.v17_profile_ai_hint else R.string.v17_profile_ai_needs_ai), 13), sheet.row(0, 8))
        sheet.column.addView(insightsCard, sheet.row(0, 16))
        renderInsights(RunnerInsightsStore(activity).load())
        managed.setOnCheckedChangeListener { _, _ ->
            saveDraft()
            updateExperienceVisibility()
        }
        sheet.column.addView(sheet.text(activity.getString(R.string.profile_v9_optional)), sheet.row(0, 16))
        val wrapper =
            TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
                hint = activity.getString(R.string.profile_v9_experience)
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                isSaveEnabled = false
            }
        val choice =
            MaterialAutoCompleteTextView(wrapper.context).apply {
                inputType = InputType.TYPE_NULL
                minHeight = sheet.dp(56)
                isSaveEnabled = false
                setSaveFromParentEnabled(false)
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                setSimpleItems(RunningExperience.entries.map { activity.getString(experienceLabel(it)) }.toTypedArray())
                setText(activity.getString(experienceLabel(experience)), false)
                setOnItemClickListener { _, _, index, _ ->
                    experience = RunningExperience.entries[index]
                    saveDraft()
                }
            }
        wrapper.addView(choice)
        sheet.column.addView(wrapper, sheet.row(0, 16))
        experienceField = wrapper
        updateExperienceVisibility()
        listOf(preference to initial.preferences, health to initial.healthNotes).forEach { (field, value) ->
            field.first.isSaveEnabled = false
            field.second.apply {
                isSaveEnabled = false
                setSaveFromParentEnabled(false)
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                filters = arrayOf(InputFilter.LengthFilter(RunnerProfile.TEXT_LIMIT))
                minLines = 2
                maxLines = 4
                setText(value)
                doAfterTextChanged { saveDraft() }
            }
            sheet.column.addView(field.first, sheet.row(0, 16))
        }
        sheet.column.addView(include, sheet.row(0, 4))
        sheet.column.addView(sheet.text(activity.getString(R.string.profile_v9_disclosure), 14), sheet.row(0, 12))
        include.setOnCheckedChangeListener { _, _ -> saveDraft() }
        sheet.column.addView(
            sheet.button(activity.getString(R.string.profile_v9_clear), R.drawable.ic_trash).apply {
                setOnClickListener {
                    runCatching {
                        store.clear()
                        RunnerInsightsStore(activity).clear()
                        TrackingService.refreshCoaching()
                    }.onSuccess {
                        AiActivityCompletion.onAppOpen(activity.applicationContext)
                        draft.value = null
                        close()
                    }.onFailure { showError() }
                }
            },
            sheet.row(),
        )
        sheet.primaryAction(
            sheet.button(activity.getString(R.string.coach_ui_save), filled = true).apply {
                setOnClickListener {
                    runCatching {
                        store.save(current())
                        TrackingService.refreshCoaching()
                    }.onSuccess {
                        AiActivityCompletion.onAppOpen(activity.applicationContext)
                        draft.value = null
                        close()
                    }.onFailure { showError() }
                }
            },
            status,
        )
        sheet.dialog.setOnDismissListener {
            sheet.closed = true
            clearDraftUnlessRotating()
        }
    }

    fun show() = sheet.show()

    fun close() {
        clearDraftUnlessRotating()
        sheet.close()
    }

    fun saveState() = Bundle()

    private fun current() =
        RunnerProfile(
            experience,
            preference.second.text.toString(),
            health.second.text.toString(),
            include.isChecked,
            managed.isChecked,
        ).bounded()

    private fun updateExperienceVisibility() {
        experienceField?.visibility = if (managed.isChecked) View.GONE else View.VISIBLE
    }

    private fun loadSnapshot() {
        val app = activity.applicationContext
        Thread {
            val snapshot =
                runCatching {
                    val store =
                        com.runline.app.data
                            .RunStore(app)
                    TrainingSnapshot.of(store.list(), store::details, System.currentTimeMillis())
                }.getOrNull()
            activity.runOnUiThread { if (!sheet.closed) showSnapshot(snapshot) }
        }.start()
    }

    private fun showSnapshot(snapshot: TrainingSnapshot?) {
        snapshotFields.removeAllViews()
        if (snapshot == null || snapshot.totalRuns == 0) {
            snapshotFields.addView(sheet.text(activity.getString(R.string.v17_profile_snapshot_empty), TextScale.LABEL))
            return
        }
        snapshotFields(snapshot).chunked(SNAPSHOT_COLUMNS).forEachIndexed { index, cells ->
            val line = android.widget.LinearLayout(activity)
            cells.forEach { line.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
            repeat(SNAPSHOT_COLUMNS - cells.size) { line.addView(View(activity), android.widget.LinearLayout.LayoutParams(0, 0, 1f)) }
            snapshotFields.addView(line, sheet.row(if (index == 0) 0 else Space.L, 0))
        }
    }

    private fun snapshotFields(snapshot: TrainingSnapshot): List<DataFieldView> {
        val locale = activity.resources.configuration.locales[0]
        val km = activity.getString(R.string.island_km)
        val runs = activity.resources.getQuantityString(R.plurals.v26_profile_runs, snapshot.runs28Days)

        fun field(
            label: Int,
            value: String,
            unit: String,
        ) = DataFieldView(activity, activity.getString(label), value, unit)

        fun decimal(value: Double) = String.format(locale, ONE_DECIMAL, value)
        val fields =
            mutableListOf(
                field(R.string.v26_profile_recent, snapshot.runs28Days.toString(), runs),
                field(R.string.v26_profile_weekly, decimal(snapshot.kilometresPerWeek), km),
            )
        snapshot.longestKilometres90Days?.let { fields += field(R.string.v26_profile_longest, decimal(it), km) }
        snapshot.typicalPaceSeconds?.let { seconds ->
            val total = kotlin.math.round(seconds).toInt()
            val pace = String.format(locale, "%d:%02d", total / SECONDS_PER_MINUTE, total % SECONDS_PER_MINUTE)
            fields += field(R.string.v26_profile_pace, pace, activity.getString(R.string.v26_unit_per_km))
        }
        val outOfTen = activity.getString(R.string.v26_unit_of_ten)
        snapshot.averageEffort?.let { fields += field(R.string.v26_profile_effort, decimal(it), outOfTen) }
        return fields
    }

    private fun renderInsights(value: RunnerInsights?) {
        insightsCard.removeAllViews()
        if (value != null) {
            insightsCard.addView(sheet.text(activity.getString(experienceLabel(value.experience)), 18, bold = true), sheet.row(0, 4))
            insightsCard.addView(sheet.text(value.summary, 15), sheet.row(0, 4))
            insightsCard.addView(sheet.text(value.focus, 15), sheet.row(0, 4))
            val date =
                java.time.Instant
                    .ofEpochMilli(value.updatedAtMs)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(
                        java.time.format.DateTimeFormatter
                            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                            .withLocale(activity.resources.configuration.locales[0]),
                    )
            insightsCard.addView(sheet.text(activity.getString(R.string.v17_profile_ai_updated, date), 13), sheet.row(0, 8))
        }
        if (!aiReady) return
        val update = sheet.button(activity.getString(R.string.v17_profile_ai_update_now), R.drawable.ic_refresh)
        update.setOnClickListener {
            runCatching { store.save(current()) }.onFailure {
                showError()
                return@setOnClickListener
            }
            if (!managed.isChecked) return@setOnClickListener
            update.isEnabled = false
            update.text = activity.getString(R.string.v17_profile_ai_updating)
            AiWork.updateProfile(activity, force = true) { result ->
                if (sheet.closed) return@updateProfile
                result.onSuccess { renderInsights(it) }.onFailure {
                    update.isEnabled = true
                    update.text = activity.getString(R.string.v17_profile_ai_update_now)
                    status.setText(R.string.v17_profile_ai_failed)
                    status.visibility = View.VISIBLE
                }
            }
        }
        insightsCard.addView(update, sheet.row(0, 0))
    }

    private fun saveDraft() {
        draft.value = current()
    }

    private fun clearDraftUnlessRotating() {
        if (!activity.isChangingConfigurations) draft.value = null
    }

    private fun showError() {
        status.setText(R.string.ai_error_storage)
        status.visibility = View.VISIBLE
    }

    private fun experienceLabel(value: RunningExperience) =
        when (value) {
            RunningExperience.UNSPECIFIED -> R.string.profile_v9_unspecified
            RunningExperience.STARTING -> R.string.profile_v9_starting
            RunningExperience.RETURNING -> R.string.profile_v9_returning
            RunningExperience.REGULAR -> R.string.profile_v9_regular
            RunningExperience.EXPERIENCED -> R.string.profile_v9_experienced
        }

    private companion object {
        const val SNAPSHOT_COLUMNS = 3
        const val ONE_DECIMAL = "%.1f"
        const val SECONDS_PER_MINUTE = 60
    }
}
