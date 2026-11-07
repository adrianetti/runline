package com.runline.app.core

import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * A planned route as a replayable timeline, so it previews like a recording: the 3D camera,
 * playback speed and the timeline. Time is the steady-effort moving time from Tobler's hiking
 * function. A sample every 5 s of planned time keeps every gap far below the replay's 15 s cut.
 * Display only: it is never saved and never becomes a recording.
 */
object RoutePreview {
    const val SAMPLE_MS = 5_000L

    fun session(route: HikingRoute, activity: ActivityType = ActivityType.HIKE): RunSession {
        val pts = route.points
        val minutes = route.minutesAt
        val totalMs = (minutes.last() * 60_000).roundToLong().coerceAtLeast(SAMPLE_MS)
        val count = ceil(totalMs.toDouble() / SAMPLE_MS).toInt()
        val out = ArrayList<RunPoint>(count + 1)
        var k = 0
        for (i in 0..count) {
            val t = minOf(totalMs, i * SAMPLE_MS)
            val target = t / 60_000.0
            while (k + 1 < pts.lastIndex && minutes[k + 1] < target) k++
            val a = pts[k]; val b = pts[minOf(k + 1, pts.lastIndex)]
            val span = minutes[minOf(k + 1, pts.lastIndex)] - minutes[k]
            val f = if (span > 0) ((target - minutes[k]) / span).coerceIn(0.0, 1.0) else 0.0
            val altitude = if (a.elevationMeters != null && b.elevationMeters != null) a.elevationMeters + (b.elevationMeters - a.elevationMeters) * f
                else a.elevationMeters ?: b.elevationMeters
            out += RunPoint(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f, t, 5f, altitude)
        }
        return RunSession("preview-${route.id}", 0L, totalMs, totalMs, out, activity)
    }
}
