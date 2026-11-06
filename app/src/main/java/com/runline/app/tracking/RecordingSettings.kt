package com.runline.app.tracking

import android.content.Context
import androidx.core.content.edit

/** Recorder preferences that change derived numbers only, never the recorded fixes. */
object RecordingSettings {
    private const val FILE = "recording"
    fun autoPause(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("auto_pause", true)
    fun setAutoPause(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean("auto_pause", enabled) }
        TrackingService.refreshCoaching()
    }
}
