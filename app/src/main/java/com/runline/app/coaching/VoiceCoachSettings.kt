package com.runline.app.coaching

import android.content.Context
import com.runline.app.core.CoachConfig
import com.runline.app.core.CoachGoal
import com.runline.app.core.SmartRunEffort

/** Independent optional preferences: a coaching error never changes a saved recording. */
object VoiceCoachSettings {
    private fun preferences(context: Context) = context.getSharedPreferences("voice_coach", Context.MODE_PRIVATE)
    fun load(context: Context): CoachConfig {
        val p = preferences(context)
        val config = CoachConfig(
            enabled = p.getBoolean("enabled", false),
            goal = runCatching { CoachGoal.valueOf(p.getString("goal", CoachGoal.NONE.name)!!) }.getOrDefault(CoachGoal.NONE),
            distanceKm = p.getString("distanceKm", "5.0")?.toDoubleOrNull() ?: 5.0,
            paceSecondsPerKm = p.getInt("paceSecondsPerKm", 360),
            targetMinutes = p.getString("targetMinutes", "30.0")?.toDoubleOrNull() ?: 30.0,
            kilometreAnnouncements = p.getBoolean("kilometreAnnouncements", true),
            paceFeedback = p.getBoolean("paceFeedback", true), encouragement = p.getBoolean("encouragement", true),
            terrainFeedback = p.getBoolean("terrainFeedback", true), lapFeedback = p.getBoolean("lapFeedback", true),
            paceMinSecondsPerKm = if (p.contains("paceMinSecondsPerKm")) p.getInt("paceMinSecondsPerKm", 0) else null,
            paceMaxSecondsPerKm = if (p.contains("paceMaxSecondsPerKm")) p.getInt("paceMaxSecondsPerKm", 0) else null,
            effortIntent = runCatching { SmartRunEffort.valueOf(p.getString("effortIntent", null).orEmpty()) }.getOrNull(),
        )
        return config.takeIf(CoachConfig::isValid) ?: CoachConfig()
    }
    fun save(context: Context, config: CoachConfig): Boolean {
        if (!config.isValid()) return false
        val editor = preferences(context).edit().putBoolean("enabled", config.enabled).putString("goal", config.goal.name)
            .putString("distanceKm", config.distanceKm.toString()).putInt("paceSecondsPerKm", config.paceSecondsPerKm)
            .putString("targetMinutes", config.targetMinutes.toString()).putBoolean("kilometreAnnouncements", config.kilometreAnnouncements)
            .putBoolean("paceFeedback", config.paceFeedback).putBoolean("encouragement", config.encouragement)
            .putBoolean("terrainFeedback", config.terrainFeedback).putBoolean("lapFeedback", config.lapFeedback)
        config.paceMinSecondsPerKm?.let { editor.putInt("paceMinSecondsPerKm", it) } ?: editor.remove("paceMinSecondsPerKm")
        config.paceMaxSecondsPerKm?.let { editor.putInt("paceMaxSecondsPerKm", it) } ?: editor.remove("paceMaxSecondsPerKm")
        config.effortIntent?.let { editor.putString("effortIntent", it.name) } ?: editor.remove("effortIntent")
        return editor.commit()
    }
}
