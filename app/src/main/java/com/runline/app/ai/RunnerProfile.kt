package com.runline.app.ai

import org.json.JSONObject

/** Optional self-report. Never inferred from GPS, pace, age, or appearance. */
enum class RunningExperience { UNSPECIFIED, STARTING, RETURNING, REGULAR, EXPERIENCED }

data class RunnerProfile(
    val experience: RunningExperience = RunningExperience.UNSPECIFIED,
    val preferences: String = "",
    val healthNotes: String = "",
    val includeInAi: Boolean = false,
) {
    fun bounded() = copy(preferences = preferences.trim().take(TEXT_LIMIT), healthNotes = healthNotes.trim().take(TEXT_LIMIT))
    fun forAi(): JSONObject? {
        if (!includeInAi) return null
        val value = bounded()
        return JSONObject().put("source", "optional user self-report, not a clinical assessment").apply {
            if (value.experience != RunningExperience.UNSPECIFIED) put("experience", value.experience.name)
            if (value.preferences.isNotEmpty()) put("training_preferences", value.preferences)
            if (value.healthNotes.isNotEmpty()) put("self_reported_health_notes", value.healthNotes)
        }
    }
    fun json(): JSONObject = bounded().let { JSONObject().put("version", 1).put("experience", it.experience.name)
        .put("preferences", it.preferences).put("health_notes", it.healthNotes).put("include_in_ai", it.includeInAi) }
    companion object {
        const val TEXT_LIMIT = 600
        fun parse(json: JSONObject): RunnerProfile {
            require(json.getInt("version") == 1)
            return RunnerProfile(runCatching { RunningExperience.valueOf(json.optString("experience")) }.getOrDefault(RunningExperience.UNSPECIFIED),
                json.optString("preferences", ""), json.optString("health_notes", ""), json.optBoolean("include_in_ai", false)).bounded()
        }
    }
}
