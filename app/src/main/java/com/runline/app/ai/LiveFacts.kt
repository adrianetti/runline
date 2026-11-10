package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Numbers the live coach may say, worked out here so a fast model never does arithmetic
 * mid-run. Derived only from the run summary and coaching context already in the request:
 * nothing new leaves the phone. Paces and times are "m:ss"; a negative change means faster.
 */
internal object LiveFacts {
    fun of(run: JSONObject, context: JSONObject): JSONObject = JSONObject().apply {
        val moving = run.number("moving_seconds")
        val distanceKm = run.number("distance_km")
        val average = run.number("average_pace_seconds_per_km")
        moving?.let { put("elapsed", clock(it)) }
        distanceKm?.let { put("distance_km", round2(it)) }
        average?.let { put("average_pace", clock(it)) }
        run.number("recent_pace_seconds_per_km")?.let { put("pace_last_2_minutes", clock(it)) }
        run.optJSONObject("recent_pace_comparison")?.let { c ->
            val now = c.number("recent_pace_seconds_per_km"); val before = c.number("previous_pace_seconds_per_km")
            if (now != null && before != null) put("last_2_minutes_vs_the_2_before", JSONObject()
                .put("trend", c.optString("trend")).put("change_seconds_per_km", (now - before).roundToInt()))
        }
        val splits = run.optJSONArray("splits").objects().filter { it.optBoolean("is_full_kilometre") }
            .mapNotNull { s -> s.number("pace_seconds_per_km")?.let { s.optInt("number") to it } }
        put("full_kilometres_completed", splits.size)
        if (splits.isNotEmpty()) {
            put("kilometre_splits", JSONArray(splits.map { clock(it.second) }))
            if (splits.size >= 2) {
                put("last_kilometre_vs_previous_seconds", (splits.last().second - splits[splits.size - 2].second).roundToInt())
                val fastest = splits.minBy { it.second }
                put("fastest_kilometre", JSONObject().put("number", fastest.first).put("pace", clock(fastest.second)))
                // Each kilometre quicker than the one before: a real negative split worth naming.
                if (splits.size >= 3) put("every_kilometre_faster_than_the_last", splits.zipWithNext().all { (a, b) -> b.second < a.second - 2 })
            }
        }
        val lapData = run.optJSONArray("estimated_repeated_laps").objects()
        val laps = lapData.mapNotNull { it.number("duration_seconds") }
        if (laps.isNotEmpty()) {
            // Durations of one loop of the same circuit: times, not paces per kilometre.
            put("repeated_lap_times", JSONArray(laps.map(::clock)))
            lapData.mapNotNull { it.number("distance_m") }.takeIf { it.isNotEmpty() }?.let { put("lap_length_km", round2(it.average() / 1000)) }
            if (laps.size >= 2) put("last_lap_vs_previous_seconds", (laps.last() - laps[laps.size - 2]).roundToInt())
        }
        goal(context.optJSONObject("active_goal"), moving, distanceKm, average, run.number("recent_pace_seconds_per_km"))?.let { put("goal", it) }
        history(run, context, distanceKm, average)?.let { put("history", it) }
    }

    private fun goal(goal: JSONObject?, moving: Double?, distanceKm: Double?, average: Double?, recent: Double?): JSONObject? {
        if (goal == null || moving == null || distanceKm == null) return null
        val result = JSONObject().put("type", goal.optString("type"))
        goal.optString("intended_effort").takeIf { it.isNotBlank() && it != "null" }?.let { result.put("intended_effort", it) }
        goal.number("distance_goal_m")?.let { meters ->
            val remaining = (meters / 1000 - distanceKm).coerceAtLeast(0.0)
            result.put("goal_distance_km", round2(meters / 1000)).put("remaining_km", round2(remaining))
                .put("progress_percent", (distanceKm * 1000 / meters * 100).roundToInt().coerceIn(0, 100))
            if (average != null && distanceKm >= .2) result.put("projected_finish_time_at_average_pace", clock(average * meters / 1000))
            goal.number("distance_deadline_minutes")?.let { result.put("deadline", clock(it * 60)) }
        }
        goal.number("duration_goal_seconds")?.let { seconds ->
            result.put("goal_time", clock(seconds)).put("remaining_time", clock((seconds - moving).coerceAtLeast(0.0)))
                .put("progress_percent", (moving / seconds * 100).roundToInt().coerceIn(0, 100))
            if (moving >= 60 && distanceKm > 0) result.put("projected_distance_km_at_average_pace", round2(distanceKm / moving * seconds))
        }
        val low = goal.number("pace_min_seconds_per_km") ?: goal.number("target_pace_seconds_per_km")
        val high = goal.number("pace_max_seconds_per_km") ?: goal.number("target_pace_seconds_per_km")
        if (low != null && high != null) {
            result.put("target_pace", if (low == high) clock(low) else clock(low) + " to " + clock(high))
            if (recent != null) result.put("pace_last_2_minutes_vs_target", when {
                recent < low - 8 -> "FASTER_THAN_TARGET"; recent > high + 8 -> "SLOWER_THAN_TARGET"; else -> "ON_TARGET"
            })
        }
        return result
    }

    private fun history(run: JSONObject, context: JSONObject, distanceKm: Double?, average: Double?): JSONObject? {
        val today = runCatching { LocalDate.parse(context.optString("today")) }.getOrNull() ?: return null
        val same = context.optJSONArray("recent_runs").objects().filter { it.optString("activity_type") == run.optString("activity_type") }
            .mapNotNull { r -> runCatching { LocalDate.parse(r.optString("date")) }.getOrNull()?.takeIf { it <= today }?.let { it to r } }
            .filter { (it.second.number("distance_km") ?: 0.0) >= .3 }
        if (same.isEmpty()) return null
        val result = JSONObject()
        val (lastDate, last) = same.first()
        val lastPace = last.number("average_pace_seconds_per_km")
        result.put("previous_run", JSONObject().put("days_ago", ChronoUnit.DAYS.between(lastDate, today))
            .put("distance_km", round2(last.number("distance_km") ?: 0.0)).apply {
                lastPace?.let { put("average_pace", clock(it)) }
                last.number("self_reported_effort_1_to_10")?.let { put("effort_1_to_10", it.roundToInt()) }
            })
        if (average != null && lastPace != null && (distanceKm ?: 0.0) >= .5)
            result.put("average_pace_vs_previous_run_seconds_per_km", (average - lastPace).roundToInt())
        val paces = same.mapNotNull { it.second.number("average_pace_seconds_per_km") }.sorted()
        if (paces.size >= 3) result.put("usual_pace_recent_runs", clock(paces[paces.size / 2]))
        val month = same.filter { it.first >= today.minusDays(29) }
        month.mapNotNull { it.second.number("distance_km") }.maxOrNull()?.let { longest ->
            result.put("longest_run_last_30_days_km", round2(longest))
            if (distanceKm != null && distanceKm > longest) result.put("this_is_now_the_longest_run_in_30_days", true)
        }
        val week = same.filter { it.first >= today.minusDays(6) }
        result.put("runs_last_7_days_before_this", week.size)
        val weekKm = week.sumOf { it.second.number("distance_km") ?: 0.0 }
        result.put("km_last_7_days_including_this_run", round2(weekKm + (distanceKm ?: 0.0)))
        return result
    }

    /** 461.1 -> "7:41"; an hour or more -> "1:02:05". */
    fun clock(seconds: Double): String {
        val total = seconds.roundToInt().coerceAtLeast(0)
        val h = total / 3600; val m = total % 3600 / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
    private fun round2(value: Double) = (value * 100).roundToInt() / 100.0
    private fun JSONObject.number(name: String): Double? = (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
