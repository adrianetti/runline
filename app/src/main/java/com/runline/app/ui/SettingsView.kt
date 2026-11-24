package com.runline.app.ui

import android.content.Context
import android.widget.LinearLayout
import com.runline.app.R
import com.runline.app.i18n.AppLanguage
import com.runline.app.tracking.RecordingSettings

internal class SettingsActions(
    val profile: () -> Unit,
    val ai: () -> Unit,
    val music: () -> Unit,
    val terrain: () -> Unit,
    val language: () -> Unit,
    val protection: () -> Unit,
    val diagnostics: () -> Unit,
    val about: () -> Unit,
)

internal class SettingsView(
    context: Context,
    actions: SettingsActions,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        addView(UiText.title(context, context.getString(R.string.v8_settings)))
        addView(UiRows.hairline(context), LayoutParams(LayoutParams.MATCH_PARENT, UiText.dp(context, Space.HAIRLINE)).apply { topMargin = UiText.dp(context, Space.L) })
        item(R.drawable.ic_user, R.string.profile_v9_title, actions.profile)
        item(R.drawable.ic_ai, R.string.v8_ai, actions.ai)
        item(R.drawable.ic_music, R.string.music_title, actions.music)
        item(R.drawable.ic_climb, R.string.terrain_offline_title, actions.terrain)
        item(R.drawable.ic_language, R.string.main_language, actions.language, language())
        addView(
            UiRows.toggle(context, R.drawable.ic_auto_pause, context.getString(R.string.v17_auto_pause), RecordingSettings.autoPause(context)) {
                RecordingSettings.setAutoPause(context, it)
            },
        )
        addView(UiRows.hairline(context))
        item(R.drawable.ic_shield_check, R.string.main_protection, actions.protection)
        item(R.drawable.ic_diagnostics, R.string.diag_v12_title, actions.diagnostics)
        item(R.drawable.ic_info, R.string.main_about, actions.about)
    }

    private fun item(
        icon: Int,
        label: Int,
        action: () -> Unit,
        value: String = "",
    ) {
        addView(UiRows.navigation(context, icon, context.getString(label), value, action))
        addView(UiRows.hairline(context))
    }

    private fun language() =
        when (AppLanguage.selectedTag(context).substringBefore(',')) {
            "es" -> "Español"
            "en" -> "English"
            else -> context.getString(R.string.main_phone_language)
        }
}
