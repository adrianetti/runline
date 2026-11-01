package com.runline.app.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Activity locales use AppCompat; services need an explicit localized context before API 33. */
object AppLanguage {
    private const val PREFERENCES = "app_language"
    private const val LANGUAGE = "language"
    private const val INITIALIZED = "framework_initialized"
    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** Custom locale storage must be supplied before Activity.onCreate on API 26–32. */
    fun beforeCreate(context: Context) {
        if (Build.VERSION.SDK_INT <= 32) {
            val tag = preferences(context).getString(LANGUAGE, "es") ?: "es"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    /** This personal build initially selects Spanish; later OS/app choices are respected. */
    fun afterCreate(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && !preferences(context).getBoolean(INITIALIZED, false)) {
            val existing = context.getSystemService(LocaleManager::class.java).applicationLocales
            val tag = preferences(context).getString(LANGUAGE, "es") ?: "es"
            preferences(context).edit().putBoolean(INITIALIZED, true).apply()
            if (existing.isEmpty) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    fun select(context: Context, tag: String) {
        require(tag in setOf("en", "es"))
        preferences(context).edit().putString(LANGUAGE, tag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun localizedContext(context: Context): Context {
        val locales = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else LocaleList.forLanguageTags(preferences(context).getString(LANGUAGE, "es") ?: "es")
        if (locales.isEmpty) return context
        val configuration = Configuration(context.resources.configuration).apply { setLocales(locales) }
        return context.createConfigurationContext(configuration)
    }
}
