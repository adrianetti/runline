package com.runline.app.ai

import java.time.LocalDate
import kotlin.math.abs
import org.json.JSONObject

/** Skip obvious duplicate/routine commentary before spending a request; uncertainty stays eligible. */
internal object LiveAdviceGate {
    fun shouldRequest(data: JSONObject): Boolean {
        val context = data.optJSONObject("coaching_context") ?: return true
        val spoken = context.optJSONArray("recent_spoken_feedback")
        if (spoken != null && (0 until spoken.length()).any { index ->
                val event = spoken.optJSONObject(index)
                event?.optString("source") in setOf("LOCAL", "AI") &&
                    event?.number("seconds_ago")?.let { it >= 0 && it < 85 } == true
            }) return false
        return !knownStableRun(data, context)
    }

    private fun knownStableRun(data: JSONObject, context: JSONObject): Boolean {
        val run = data.optJSONObject("run") ?: return false
        val goal = context.optJSONObject("active_goal") ?: return false
        val terrain = context.optJSONObject("recent_terrain") ?: return false
        if (goal.optString("intended_effort") !in setOf("", "EASY") ||
            goal.optString("type") !in setOf("DISTANCE", "TIME", "PACE")) return false
        if (terrain.optString("direction") != "FLAT" || terrain.number("grade_percent")?.let { abs(it) <= 1.5 } != true) return false
        if (run.optJSONObject("recent_pace_comparison")?.optString("trend") != "SIMILAR") return false
        val pace = run.number("recent_pace_seconds_per_km") ?: return false
        val pointTarget = goal.number("target_pace_seconds_per_km")
        val low = goal.number("pace_min_seconds_per_km") ?: pointTarget?.let { it - maxOf(10.0, it * .03) } ?: return false
        val high = goal.number("pace_max_seconds_per_km") ?: pointTarget?.let { it + maxOf(10.0, it * .03) } ?: return false
        if (low <= 0 || high < low || pace !in low..high) return false
        // Preserve room for advice around a goal's end or meaningful repeated-route change.
        val distance = run.number("distance_km") ?: return false
        if (goal.number("distance_goal_m")?.let { it - distance * 1000 <= 500 } == true) return false
        val elapsed = run.number("active_seconds") ?: return false
        if (goal.number("duration_goal_seconds")?.let { it - elapsed <= 120 } == true) return false
        val lap = run.optJSONObject("lap_comparison")
        if (lap != null && listOf("pace_change_percent", "quarter_pace_change_percent").any {
                lap.number(it)?.let { change -> abs(change) >= 8 } == true
            }) return false
        val profile = context.optJSONObject("runner_profile")
        if (profile != null && listOf("training_preferences", "self_reported_health_notes", "experience").any {
                profile.optString(it).isNotBlank()
            }) return false
        // Missing effort/history is not proof that a routine cue would be unhelpful.
        val today = runCatching { LocalDate.parse(context.getString("today")) }.getOrNull() ?: return false
        val history = context.optJSONArray("recent_runs") ?: return false
        var comparable = 0
        var longestKm = 0.0
        var longestSeconds = 0.0
        for (index in 0 until history.length()) {
            val prior = history.optJSONObject(index) ?: return false
            if (prior.optString("activity_type") != run.optString("activity_type")) continue
            val date = runCatching { LocalDate.parse(prior.getString("date")) }.getOrNull() ?: return false
            if (date > today || date < today.minusDays(13)) continue
            val effort = prior.number("self_reported_effort_1_to_10") ?: return false
            if (effort !in 1.0..4.0 || prior.optString("user_notes").isNotBlank()) return false
            val priorKm = prior.number("distance_km")?.takeIf { it > 0 } ?: return false
            val priorSeconds = prior.number("active_seconds")?.takeIf { it > 0 } ?: return false
            longestKm = maxOf(longestKm, priorKm)
            longestSeconds = maxOf(longestSeconds, priorSeconds)
            comparable++
        }
        if (comparable < 2 || distance >= longestKm * .9 || elapsed >= longestSeconds * .9) return false
        // Approaching previously recorded distance/time or weekly volume can be new context.
        // These are request-selection comparisons, not proof of ability or training limits.
        val completed = context.optJSONObject("completed_training") ?: return false
        val lastWeek = completed.number("last_7_days_km")?.takeIf { it >= 0 } ?: return false
        val usualWeek = completed.number("last_28_days_weekly_average_km")?.takeIf { it > 0 } ?: return false
        val longestMonth = completed.number("longest_run_last_30_days_km")?.takeIf { it > 0 } ?: return false
        return distance < longestMonth * .9 && lastWeek + distance < usualWeek
    }

    private fun JSONObject.number(name: String): Double? = (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }
}
