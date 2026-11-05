package com.runline.app.ai

import com.runline.app.core.SmartRunSuggestion
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalDate

/** Durable request reservation: process death cannot make a paid attempt disappear. */
data class DailySuggestion(val day: String, val zone: String, val attemptedAt: Long, val inputKey: String,
                           val token: String, val suggestion: SmartRunSuggestion? = null,
                           val attemptedDays: List<String> = listOf(day)) {
    fun canAutomaticallyRequest(today: LocalDate, now: Long, timeZone: String): Boolean =
        today.toString() !in attemptedDays && today.toString() > day && now >= attemptedAt &&
            (zone == timeZone || now - attemptedAt >= 20 * 60 * 60_000L)
    fun json() = JSONObject().put("version", 1).put("day", day).put("zone", zone).put("attempted_at", attemptedAt)
        .put("input_key", inputKey).put("token", token).put("days", JSONArray(attemptedDays.takeLast(14)))
        .put("result", suggestion?.let(::suggestionJson) ?: JSONObject.NULL)
    companion object {
        fun parse(text: String): DailySuggestion {
            require(text.length <= 16000)
            val j = JSONObject(text); require(j.getInt("version") == 1); LocalDate.parse(j.getString("day"))
            val days = j.getJSONArray("days"); require(days.length() in 1..14)
            return DailySuggestion(j.getString("day"), j.getString("zone"), j.getLong("attempted_at"), j.getString("input_key"),
                j.getString("token"), j.optJSONObject("result")?.let { SmartRunAiInput.parse(it.toString()) },
                (0 until days.length()).map { days.getString(it).also(LocalDate::parse) })
        }
        fun suggestionJson(s: SmartRunSuggestion) = JSONObject().put("goal_type", s.goalType.name).put("effort", s.effort.name)
            .put("target_distance_km", s.targetDistanceKm ?: JSONObject.NULL).put("min_distance_km", s.minDistanceKm ?: JSONObject.NULL)
            .put("max_distance_km", s.maxDistanceKm ?: JSONObject.NULL).put("pace_min_seconds_per_km", s.paceMinSecondsPerKm ?: JSONObject.NULL)
            .put("pace_max_seconds_per_km", s.paceMaxSecondsPerKm ?: JSONObject.NULL).put("duration_minutes", s.durationMinutes ?: JSONObject.NULL)
            .put("rationale", s.rationale)
    }
}
