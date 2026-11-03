package com.runline.app.ai

import com.runline.app.core.RunMetrics
import com.runline.app.core.RunPoint
import kotlin.math.abs
import org.json.JSONObject

/** Compare like-for-like observed windows, never raw durations of differently sized splits. */
internal object AiPaceWindows {
    fun comparison(segment: List<RunPoint>): JSONObject? {
        val last = segment.lastOrNull() ?: return null
        val recent = segment.takeLastWhile { last.timestampMs - it.timestampMs <= 120_000 }
        val boundary = recent.firstOrNull() ?: return null
        val previous = segment.filter { it.timestampMs <= boundary.timestampMs && boundary.timestampMs - it.timestampMs <= 120_000 }
        fun pace(points: List<RunPoint>): Double? {
            if (points.size < 6) return null
            val duration = points.last().timestampMs - points.first().timestampMs
            val distance = RunMetrics.distanceMeters(points)
            if (duration !in 90_000..120_000 || distance < 100.0) return null
            return RunMetrics.paceSecondsPerKm(distance, duration)?.takeIf { it in 120.0..1800.0 }
        }
        val before = pace(previous) ?: return null
        val now = pace(recent) ?: return null
        val delta = now - before
        val threshold = maxOf(12.0, before * .05)
        val trend = if (abs(delta) < threshold) "SIMILAR" else if (delta < 0) "FASTER" else "SLOWER"
        return JSONObject().put("trend", trend).put("recent_pace_seconds_per_km", now)
            .put("previous_pace_seconds_per_km", before).put("pace_change_percent", delta / before * 100)
            .put("recent_observed_seconds", (recent.last().timestampMs - recent.first().timestampMs) / 1000.0)
            .put("previous_observed_seconds", (previous.last().timestampMs - previous.first().timestampMs) / 1000.0)
            .put("comparison", "Two adjacent, non-overlapping observed windows in the current continuous GPS segment. Lower seconds per kilometre means faster. SIMILAR means no material change; it is not fatigue or effort evidence.")
    }
}
