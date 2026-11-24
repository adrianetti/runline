package com.runline.app.ai

import com.runline.app.core.ActivityDetails
import com.runline.app.core.LapAnalysis
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunPoint
import com.runline.app.core.RunReplay
import com.runline.app.core.RunSession
import com.runline.app.core.StopInterval
import com.runline.app.core.withTrustedElevation
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

object AiRunSummary {
    private const val MIN_RECENT_MOVING_MS = 30_000L
    private const val COMPARED_MS = 240_000L

    fun of(
        sourceRun: RunSession,
        details: ActivityDetails? = null,
        includeNotes: Boolean = false,
    ): JSONObject {
        val run = sourceRun.withTrustedElevation()
        val replay = RunReplay(run)
        val analysis = replay.summary
        val distance = RunMetrics.distanceMeters(run.points)
        val stops =
            com.runline.app.core.StopDetection
                .detect(sourceRun.points, sourceRun.activityType)
        val moving =
            com.runline.app.core.StopDetection
                .movingDurationMs(sourceRun, stops)
        val splits = JSONArray()
        RunMetrics.splits(run, stops).take(80).forEach { split ->
            splits.put(
                JSONObject()
                    .put("number", split.index)
                    .put("distance_m", split.distanceMeters)
                    .put("duration_seconds", split.durationMs / 1000.0)
                    .put("pace_seconds_per_km", RunMetrics.paceSecondsPerKm(split.distanceMeters, split.durationMs) ?: JSONObject.NULL)
                    .put("is_full_kilometre", split.distanceMeters >= 999.999),
            )
        }
        val recent = run.points.takeLastWhile { run.points.last().timestampMs - it.timestampMs <= 120_000 }
        val recentDistance = RunMetrics.distanceMeters(recent)
        val recentStopped = stoppedWithin(stops, recent)
        val recentDuration =
            (RunReplay(run.copy(points = recent)).summary.observedDurationMs - recentStopped)
                .takeIf { recentStopped == 0L || it >= MIN_RECENT_MOVING_MS } ?: 0L
        val lapData = JSONArray()
        val laps = LapAnalysis.analyze(run.points)
        laps.laps.takeLast(12).forEach { lap ->
            lapData.put(
                JSONObject()
                    .put("number", lap.number)
                    .put("distance_m", lap.distanceMeters)
                    .put("duration_seconds", lap.durationMs / 1000.0)
                    .put("ascent_m", lap.ascentMeters ?: JSONObject.NULL)
                    .put("descent_m", lap.descentMeters ?: JSONObject.NULL)
                    .put(
                        "sectors",
                        JSONArray().also { a ->
                            lap.sectors.forEach { s ->
                                a.put(
                                    JSONObject()
                                        .put("number", s.number)
                                        .put("distance_m", s.distanceMeters)
                                        .put("duration_seconds", s.durationMs / 1000.0)
                                        .put("elevation_change_m", s.elevationChangeMeters ?: JSONObject.NULL),
                                )
                            }
                        },
                    ),
            )
        }
        val result =
            JSONObject()
                .put("activity_type", run.activityType.storageValue)
                .put(
                    "date",
                    Instant
                        .ofEpochMilli(run.startedAtMs)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString(),
                ).put("active_seconds", run.activeDurationMs / 1000.0)
                .put("distance_km", distance / 1000.0)
                .put("moving_seconds", moving / 1000.0)
                .put(
                    "stops",
                    JSONObject()
                        .put("count", stops.size)
                        .put(
                            "total_seconds",
                            com.runline.app.core.StopDetection
                                .totalMs(stops) / 1000.0,
                        ).put("longest_seconds", (stops.maxOfOrNull { it.durationMs } ?: 0L) / 1000.0),
                ).put(AiRunEvents.KEY, AiRunEvents.of(sourceRun))
                .put("average_pace_seconds_per_km", RunMetrics.paceSecondsPerKm(distance, moving) ?: JSONObject.NULL)
                .put("elapsed_average_pace_seconds_per_km", RunMetrics.paceSecondsPerKm(distance, run.activeDurationMs) ?: JSONObject.NULL)
                .put("recent_pace_seconds_per_km", RunMetrics.paceSecondsPerKm(recentDistance, recentDuration) ?: JSONObject.NULL)
                .put("recent_pace_comparison", unbrokenComparison(replay.segments.lastOrNull(), stops) ?: JSONObject.NULL)
                .put("ascent_m", analysis.elevationGainMeters ?: JSONObject.NULL)
                .put("descent_m", analysis.elevationLossMeters ?: JSONObject.NULL)
                .put("gps_observed_seconds", analysis.observedDurationMs / 1000.0)
                .put("splits", splits)
                .put("estimated_repeated_laps", lapData)
                .put("elevation_source", run.elevationSource.name)
                .put("elevation_models", JSONArray(run.points.mapNotNull { it.terrainModel?.takeIf { model -> model != com.runline.app.core.TerrainModel.STRUCTURE }?.name }.distinct()))
                .put("structure_gap_points", run.points.count { it.terrainModel == com.runline.app.core.TerrainModel.STRUCTURE })
                .put("elevation_coverage", if (run.points.isEmpty()) 0.0 else run.points.count { it.altitudeMeters != null }.toDouble() / run.points.size)
                .put("measurement_limits", "GPS estimates; missing or paused intervals are excluded from observed time. Detected standstills (traffic lights, shoelaces) are excluded from moving_seconds, splits and average_pace; elapsed pace includes them. Compare pace_seconds_per_km, never raw split durations: the final split may cover less than one kilometre. Lower pace seconds means faster. Elevation is available only from an independent terrain map; incomplete coverage undercounts totals. Never infer hills from missing or uncorroborated elevation. No heart rate, cadence, pain or terrain surface data.")
        laps.latestComparison?.let { c -> result.put("lap_comparison", JSONObject().put("pace_change_percent", c.paceChangePercent).also { comparison -> c.slowerSector?.let { sector -> comparison.put("slower_quarter", sector.number).put("quarter_pace_change_percent", sector.paceChangePercent).put("quarter_elevation_change_m", sector.elevationChangeMeters ?: JSONObject.NULL) } }) }
        details?.let {
            result.put("self_reported_effort_1_to_10", it.effort ?: JSONObject.NULL).put("routine", it.routine.storageValue)
            if (includeNotes) result.put("user_title", it.title).put("user_notes", it.description.take(2000))
        }
        return result
    }

    private fun stoppedWithin(
        stops: List<StopInterval>,
        window: List<RunPoint>,
    ): Long {
        val from = window.firstOrNull()?.timestampMs ?: return 0L
        val to = window.last().timestampMs
        return stops.sumOf { (minOf(it.endMs, to) - maxOf(it.startMs, from)).coerceAtLeast(0L) }
    }

    private fun unbrokenComparison(
        segment: List<RunPoint>?,
        stops: List<StopInterval>,
    ): JSONObject? {
        val end = segment?.lastOrNull()?.timestampMs ?: return null
        return if (stops.any { it.endMs > end - COMPARED_MS && it.startMs < end }) null else AiPaceWindows.comparison(segment)
    }

    fun history(
        runs: List<RunSession>,
        details: (String) -> ActivityDetails = { ActivityDetails() },
        includeNotes: Boolean = false,
    ) = JSONArray().also { a -> runs.sortedByDescending { it.startedAtMs }.take(16).forEach { a.put(of(it, details(it.id), includeNotes)) } }
}
