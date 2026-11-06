package com.runline.app.ai

import org.json.JSONObject

/**
 * Self-reported, or AI-assessed from recorded activities only when the user turns on
 * [RunnerProfile.aiManaged]. Never inferred from age or appearance.
 */
enum class RunningExperience { UNSPECIFIED, STARTING, RETURNING, REGULAR, EXPERIENCED }

data class RunnerProfile(
    val experience: RunningExperience = RunningExperience.UNSPECIFIED,
    val preferences: String = "",
    val healthNotes: String = "",
    val includeInAi: Boolean = false,
    /** The AI keeps experience and a short training summary current after new runs. */
    val aiManaged: Boolean = false,
) {
    fun bounded() = copy(preferences = preferences.trim().take(TEXT_LIMIT), healthNotes = healthNotes.trim().take(TEXT_LIMIT))
    /** Self-report needs its own consent; the AI's earlier assessment comes from activity data only. */
    fun forAi(insights: RunnerInsights? = null): JSONObject? {
        val assessed = insights?.takeIf { aiManaged }
        if (!includeInAi && assessed == null) return null
        val value = bounded()
        return JSONObject().apply {
            if (includeInAi) {
                put("source", "optional user self-report, not a clinical assessment")
                if (value.experience != RunningExperience.UNSPECIFIED && assessed == null) put("experience", value.experience.name)
                if (value.preferences.isNotEmpty()) put("training_preferences", value.preferences)
                if (value.healthNotes.isNotEmpty()) put("self_reported_health_notes", value.healthNotes)
            }
            assessed?.let { put("ai_maintained_profile", it.forAi()) }
        }
    }
    fun json(): JSONObject = bounded().let { JSONObject().put("version", 1).put("experience", it.experience.name)
        .put("preferences", it.preferences).put("health_notes", it.healthNotes).put("include_in_ai", it.includeInAi)
        .put("ai_managed", it.aiManaged) }
    companion object {
        const val TEXT_LIMIT = 600
        fun parse(json: JSONObject): RunnerProfile {
            require(json.getInt("version") == 1)
            return RunnerProfile(runCatching { RunningExperience.valueOf(json.optString("experience")) }.getOrDefault(RunningExperience.UNSPECIFIED),
                json.optString("preferences", ""), json.optString("health_notes", ""), json.optBoolean("include_in_ai", false),
                json.optBoolean("ai_managed", false)).bounded()
        }
    }
}
