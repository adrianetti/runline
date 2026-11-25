package com.runline.app.atlas

import android.app.Instrumentation
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import com.runline.app.i18n.AppLanguage

internal object AtlasLocale {
    private const val LOCALE_MANAGER_API = 33

    fun select(
        instrumentation: Instrumentation,
        tag: String,
    ) {
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= LOCALE_MANAGER_API) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
            instrumentation.waitForIdleSync()
        } else {
            instrumentation.runOnMainSync { AppLanguage.select(context, tag) }
        }
    }
}
