package com.runline.app.ui

import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import com.runline.app.R
import com.runline.app.i18n.AppLanguage

/** Global preferences have one home; recording menus contain session actions only. */
class AppSettingsSheet(
    activity: AppCompatActivity,
    onAi: () -> Unit,
    onLanguage: () -> Unit,
    onProtection: () -> Unit,
    onAbout: () -> Unit,
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
        item(activity.getString(R.string.v8_ai), R.drawable.ic_ai, onAi)
        val language = when (AppLanguage.selectedTag(activity).substringBefore(',')) {
            "es" -> "Español"
            "en" -> "English"
            else -> activity.getString(R.string.main_phone_language)
        }
        item(activity.getString(R.string.v8_language_value, language), R.drawable.ic_language, onLanguage)
        item(activity.getString(R.string.main_protection), R.drawable.ic_shield_check, onProtection)
        item(activity.getString(R.string.main_about), R.drawable.ic_info, onAbout)
    }
    fun show() = sheet.show()
    fun close() = sheet.close()
}
