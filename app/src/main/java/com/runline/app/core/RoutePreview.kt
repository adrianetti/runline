package com.runline.app.core

import kotlin.math.ceil
import kotlin.math.roundToLong

object RoutePreview {
    const val SAMPLE_MS = 5_000L

    fun session(
        route: HikingRoute,
        activity: ActivityType = ActivityType.HIKE,
    ): RunSession {
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
            val a = pts[k]
            val b = pts[minOf(k + 1, pts.lastIndex)]
            val span = minutes[minOf(k + 1, pts.lastIndex)] - minutes[k]
            val f = if (span > 0) ((target - minutes[k]) / span).coerceIn(0.0, 1.0) else 0.0
            val altitude =
                if (a.elevationMeters != null && b.elevationMeters != null) {
                    a.elevationMeters + (b.elevationMeters - a.elevationMeters) * f
                } else {
                    a.elevationMeters ?: b.elevationMeters
                }
            out += RunPoint(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f, t, 5f, altitude)
        }
        return RunSession("preview-${route.id}", 0L, totalMs, totalMs, out, activity)
    }

    fun hasHeights(session: RunSession): Boolean = session.points.isNotEmpty() && session.points.all { it.altitudeMeters?.isFinite() == true }
}
