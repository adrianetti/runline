package com.runline.app.ai

import com.runline.app.core.RunSession
import com.runline.app.core.ActivityDetails
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunReplay
import com.runline.app.core.LapAnalysis
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Coordinate/photo-free summaries. Titles and notes require a separate explicit option. */
object AiRunSummary {
    fun of(run: RunSession, details: ActivityDetails? = null, includeNotes: Boolean = false): JSONObject {
        val analysis = RunReplay(run).summary
        val distance = RunMetrics.distanceMeters(run.points)
        val splits = JSONArray()
        RunMetrics.splits(run).take(80).forEach { split -> splits.put(JSONObject().put("number", split.index)
            .put("distance_m", split.distanceMeters).put("duration_seconds", split.durationMs / 1000.0)) }
        val recent = run.points.takeLastWhile { run.points.last().timestampMs - it.timestampMs <= 120_000 }
        val recentDistance = RunMetrics.distanceMeters(recent)
        val recentDuration = RunReplay(run.copy(points = recent)).summary.observedDurationMs
        val lapData = JSONArray()
        val laps = LapAnalysis.analyze(run.points)
        laps.laps.takeLast(12).forEach { lap -> lapData.put(JSONObject().put("number", lap.number)
            .put("distance_m", lap.distanceMeters).put("duration_seconds", lap.durationMs / 1000.0)
            .put("ascent_m", lap.ascentMeters ?: JSONObject.NULL).put("descent_m", lap.descentMeters ?: JSONObject.NULL)
            .put("sectors", JSONArray().also { a -> lap.sectors.forEach { s -> a.put(JSONObject().put("number", s.number).put("distance_m", s.distanceMeters).put("duration_seconds", s.durationMs / 1000.0).put("elevation_change_m", s.elevationChangeMeters ?: JSONObject.NULL)) } })) }
        val result = JSONObject().put("activity_type", run.activityType.storageValue)
            .put("date", Instant.ofEpochMilli(run.startedAtMs).atZone(ZoneId.systemDefault()).toLocalDate().toString())
            .put("active_seconds", run.activeDurationMs / 1000.0).put("distance_km", distance / 1000.0)
            .put("average_pace_seconds_per_km", RunMetrics.paceSecondsPerKm(distance, run.activeDurationMs) ?: JSONObject.NULL)
            .put("recent_pace_seconds_per_km", RunMetrics.paceSecondsPerKm(recentDistance, recentDuration) ?: JSONObject.NULL)
            .put("ascent_m", analysis.elevationGainMeters ?: JSONObject.NULL).put("descent_m", analysis.elevationLossMeters ?: JSONObject.NULL)
            .put("gps_observed_seconds", analysis.observedDurationMs / 1000.0).put("splits", splits).put("estimated_repeated_laps", lapData)
            .put("measurement_limits", "GPS estimates; missing or paused intervals are excluded from observed time. Elevation is uncorrected GPS. No heart rate, cadence, pain or terrain surface data.")
        laps.latestComparison?.let { c -> result.put("lap_comparison", JSONObject().put("pace_change_percent", c.paceChangePercent).also { comparison -> c.slowerSector?.let { sector -> comparison.put("slower_quarter", sector.number).put("quarter_pace_change_percent", sector.paceChangePercent).put("quarter_elevation_change_m", sector.elevationChangeMeters ?: JSONObject.NULL) } }) }
        details?.let {
            result.put("self_reported_effort_1_to_10", it.effort ?: JSONObject.NULL).put("routine", it.routine.storageValue)
            if (includeNotes) result.put("user_title", it.title).put("user_notes", it.description.take(2000))
        }
        return result
    }
    fun history(runs: List<RunSession>, details: (String) -> ActivityDetails = { ActivityDetails() }, includeNotes: Boolean = false) = JSONArray().also { a -> runs.sortedByDescending { it.startedAtMs }.take(16).forEach { a.put(of(it, details(it.id), includeNotes)) } }
}
