package com.runline.app.sharing

import android.content.Context
import com.runline.app.R
import com.runline.app.core.ActivityDetails
import com.runline.app.core.ActivityExportFileName
import com.runline.app.core.ActivityType
import com.runline.app.core.RunSession
import com.runline.app.i18n.AppLanguage
import java.time.ZoneId

object ActivityExportName {
    fun fileName(context: Context, run: RunSession, details: ActivityDetails = ActivityDetails()): String {
        val localized = AppLanguage.localizedContext(context)
        val name = localized.getString(when (details.activityType ?: run.activityType) {
            ActivityType.RUN -> R.string.export_activity_run
            ActivityType.WALK -> R.string.export_activity_walk
            ActivityType.HIKE -> R.string.export_activity_hike
        })
        return ActivityExportFileName.format(name, run, localized.resources.configuration.locales[0], ZoneId.systemDefault(), details.title)
    }
}
