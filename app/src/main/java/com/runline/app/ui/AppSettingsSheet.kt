package com.runline.app.ui

import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import com.runline.app.R
import com.runline.app.i18n.AppLanguage

/** Global preferences have one home; recording menus contain session actions only. */
class AppSettingsSheet(
    activity: AppCompatActivity,
    onProfile: () -> Unit,
    onAi: () -> Unit,
    onLanguage: () -> Unit,
    onProtection: () -> Unit,
    onAbout: () -> Unit,
    onDiagnostics: () -> Unit,
    onTerrain: () -> Unit,
) {
    private val sheet = AiSheet(activity, R.string.v8_settings)
    val isShowing get() = sheet.isShowing
    init {
        fun item(label: String, icon: Int, action: () -> Unit) {
            sheet.column.addView(sheet.button(label, icon).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener { close(); action() }
            }, sheet.row(0, 8))
        }
        item(activity.getString(R.string.profile_v9_title), R.drawable.ic_user, onProfile)
        item(activity.getString(R.string.v8_ai), R.drawable.ic_ai, onAi)
        val language = when (AppLanguage.selectedTag(activity).substringBefore(',')) {
            "es" -> "Español"
            "en" -> "English"
            else -> activity.getString(R.string.main_phone_language)
        }
        item(activity.getString(R.string.v8_language_value, language), R.drawable.ic_language, onLanguage)
        item(activity.getString(R.string.terrain_maps_title), R.drawable.ic_slope_up, onTerrain)
        // Derived only: stops never remove fixes, so this can be changed after a run too.
        // Same button style as the other rows keeps icon and label on one start edge.
        val autoPause = com.google.android.material.materialswitch.MaterialSwitch(activity).apply {
            isChecked = com.runline.app.tracking.RecordingSettings.autoPause(activity)
            contentDescription = activity.getString(R.string.v17_auto_pause)
            setOnCheckedChangeListener { _, checked -> com.runline.app.tracking.RecordingSettings.setAutoPause(activity, checked) }
        }
        sheet.column.addView(android.widget.LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(sheet.button(activity.getString(R.string.v17_auto_pause), R.drawable.ic_pause).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setOnClickListener { autoPause.toggle() }
            }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            addView(autoPause, android.widget.LinearLayout.LayoutParams(-2, -2).apply { marginStart = sheet.dp(8) })
        }, sheet.row(0, 8))
        item(activity.getString(R.string.main_protection), R.drawable.ic_shield_check, onProtection)
        item(activity.getString(R.string.diag_v12_title), R.drawable.ic_history, onDiagnostics)
        item(activity.getString(R.string.main_about), R.drawable.ic_info, onAbout)
    }
    fun show() = sheet.show()
    fun close() = sheet.close()
}
