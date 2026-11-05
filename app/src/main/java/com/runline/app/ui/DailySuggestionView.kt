package com.runline.app.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.runline.app.R
import com.runline.app.ai.*
import com.runline.app.core.*

class RunPreparation : ViewModel() { var intent: SessionIntent? = null }

internal class DailySuggestionView(private val activity: AppCompatActivity, private val preparation: RunPreparation,
                                  private val onSelectionChanged: () -> Unit) : LinearLayout(activity) {
    private val changed: () -> Unit = { render() }
    private val words = LinearLayout(activity).apply { orientation = VERTICAL }
    private val primary = TextView(activity).apply { textSize = 26f; setTextColor(activity.getColor(R.color.on_surface)); fontFeatureSettings = "tnum" }
    private val secondary = TextView(activity).apply { textSize = 15f; setTextColor(activity.getColor(R.color.on_surface_variant)) }
    private val spinner = ProgressBar(activity)
    private val use = MaterialSwitch(activity).apply { setText(R.string.v16_use); minHeight = dp(48) }
    private val retry = MaterialButton(activity, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
        setIconResource(R.drawable.ic_refresh); iconPadding = 0; iconSize = dp(22); minWidth = 0; minimumWidth = 0
        setPadding(dp(12), 0, dp(12), 0); contentDescription = activity.getString(R.string.v16_retry_suggestion); tooltipText = contentDescription
        setOnClickListener { SmartRunAi.prepare(activity) }
    }
    private var updating = false
    init {
        orientation = if (resources.configuration.fontScale > 1.15f || resources.configuration.screenWidthDp < 360) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(16), 0, dp(16))
        words.addView(primary); words.addView(secondary, LayoutParams(-1, -2).apply { topMargin = dp(6) })
        addView(words, if (orientation == HORIZONTAL) LayoutParams(0, -2, 1f) else LayoutParams(-1, -2))
        addView(spinner, LayoutParams(dp(28), dp(28)).apply { marginStart = dp(12) })
        addView(use, LayoutParams(-2, dp(56)).apply { marginStart = dp(12) })
        addView(retry, LayoutParams(dp(48), dp(48)))
        use.setOnCheckedChangeListener { _, checked ->
            if (!updating) {
                val current = SmartRunAi.snapshot(activity)
                preparation.intent = if (checked && current?.suggestion != null && current.id != null)
                    SessionIntent(SmartRunGoal.toCoachConfig(current.suggestion, CoachConfig()), "smart", current.id)
                    else preparation.intent?.takeIf { it.source == "manual" }
                onSelectionChanged()
            }
        }
        SmartRunAi.listen(changed); render()
    }
    fun dispose() = SmartRunAi.unlisten(changed)
    fun render() {
        val config = AiSettings.load(activity)
        visibility = if (config.dailySuggestion && AiSettings.isReady(activity)) VISIBLE else GONE
        if (visibility == GONE) return
        val state = SmartRunAi.snapshot(activity); val result = state?.suggestion
        spinner.visibility = if (state?.running == true) VISIBLE else GONE
        retry.visibility = if (state?.running != true && result == null) VISIBLE else GONE
        use.visibility = if (result != null && result.goalType != SmartRunGoalType.REST) VISIBLE else GONE
        updating = true
        use.isChecked = state?.id != null && preparation.intent?.source == "smart" && preparation.intent?.suggestionId == state.id
        updating = false
        primary.textSize = if (result == null) 16f else 26f
        primary.text = when {
            result?.goalType == SmartRunGoalType.REST -> activity.getString(R.string.v16_rest)
            result != null -> result.targetDistanceKm?.let { activity.getString(R.string.v16_distance, number(it)) }
                ?: activity.getString(R.string.v16_minutes, number(result.durationMinutes ?: 0.0))
            state?.running == true -> activity.getString(R.string.smart_goal_thinking)
            state?.changed == true -> activity.getString(R.string.v16_suggestion_changed)
            state?.failure != null -> activity.getString(R.string.v16_suggestion_failed)
            else -> activity.getString(R.string.v16_daily)
        }
        secondary.visibility = if (result != null && result.goalType != SmartRunGoalType.REST) VISIBLE else GONE
        if (result != null) {
            val pace = result.paceMinSecondsPerKm?.let { lo ->
                val hi = result.paceMaxSecondsPerKm!!
                (if (lo == hi) pace(lo) else "${pace(lo)}–${pace(hi)}") + " min/km"
            } ?: if (result.goalType == SmartRunGoalType.DISTANCE_TIME) pace((result.durationMinutes!! * 60 / result.targetDistanceKm!!).toInt()) + " min/km" else null
            val effort = activity.getString(when (result.effort) { SmartRunEffort.EASY -> R.string.v16_easy; SmartRunEffort.RECOVERY -> R.string.v16_recovery; SmartRunEffort.STEADY -> R.string.v16_steady })
            secondary.text = listOfNotNull(pace, effort).joinToString(" · ")
        }
    }
    private fun number(value: Double) = java.text.NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply { maximumFractionDigits = 1 }.format(value)
    private fun pace(value: Int) = String.format(resources.configuration.locales[0], "%d:%02d", value / 60, value % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
