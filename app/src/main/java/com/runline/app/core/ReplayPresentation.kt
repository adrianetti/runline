package com.runline.app.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Display choices only. No extra fixes, time or distance are added to a recording. */
object ReplayPresentation {
    val speeds = listOf(1, 5, 10, 30, 60, 120, 240, 480)

    fun defaultSpeed(durationMs: Long): Int = speeds.minBy {
        abs(durationMs.coerceAtLeast(0).toDouble() / it - 30_000.0)
    }

    /** A short observed window softens turns; it never looks across a GPS cut. */
    fun bearing(replay: RunReplay, positionMs: Long): Double? {
        val current = replay.at(positionMs) ?: return null
        val before = replay.at((positionMs - 2_000).coerceAtLeast(0))
            ?.takeIf { it.segmentIndex == current.segmentIndex } ?: current
        val after = replay.at((positionMs + 2_000).coerceAtMost(replay.durationMs))
            ?.takeIf { it.segmentIndex == current.segmentIndex } ?: current
        val a = before.point
        val b = after.point
        if (a.accuracyMeters > 30 || b.accuracyMeters > 30 ||
            RunMetrics.distanceMeters(listOf(a, b)) < 2.0) return null
        val start = Math.toRadians(a.latitude)
        val end = Math.toRadians(b.latitude)
        val longitude = Math.toRadians(b.longitude - a.longitude)
        return (Math.toDegrees(atan2(sin(longitude) * cos(end),
            cos(start) * sin(end) - sin(start) * cos(end) * cos(longitude))) + 360) % 360
    }
}
