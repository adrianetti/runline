package com.runline.app.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Empty app locales follow the phone; English resources are the fallback. */
object AppLanguage {
    private const val PREFERENCES = "app_language"
    private const val LANGUAGE = "language"
    private const val SYSTEM_DEFAULT_MIGRATED = "system_default_v6"
    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** Custom locale storage is supplied before Activity.onCreate on API 26–32. */
    fun beforeCreate(context: Context) {
        if (Build.VERSION.SDK_INT <= 32) {
            migrateDefault(context)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedTag(context)))
        }
    }

    /** v0.5 forced Spanish. This requested update returns existing installs to phone language once. */
    fun afterCreate(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && migrateDefault(context)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    private fun migrateDefault(context: Context): Boolean {
        val prefs = preferences(context)
        if (prefs.getBoolean(SYSTEM_DEFAULT_MIGRATED, false)) return false
        prefs.edit().putString(LANGUAGE, "").putBoolean(SYSTEM_DEFAULT_MIGRATED, true).apply()
        return true
    }

    fun selectedTag(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else preferences(context).getString(LANGUAGE, "") ?: ""

    fun select(context: Context, tag: String) {
        require(tag in setOf("", "en", "es"))
        preferences(context).edit().putString(LANGUAGE, tag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun localizedContext(context: Context): Context {
        val override = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else LocaleList.forLanguageTags(selectedTag(context))
        val locales = if (!override.isEmpty) override else if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else Resources.getSystem().configuration.locales
        val configuration = Configuration(context.resources.configuration).apply { setLocales(locales) }
        return context.createConfigurationContext(configuration)
    }
}
