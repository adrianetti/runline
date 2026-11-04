package com.runline.app.core

/** Recent GPS pace for the display only. Never changes stored samples or coaching. */
object LivePace {
    fun secondsPerKm(points: List<RunPoint>, nowMs: Long): Double? {
        val last = points.lastOrNull() ?: return null
        if (nowMs - last.timestampMs !in 0L..5_000L) return null
        var distance = 0.0
        var elapsed = 0L
        var pairs = 0
        for (index in points.lastIndex downTo 1) {
            val a = points[index - 1]; val b = points[index]
            if (!MarkerMotion.canInterpolate(a, b)) break
            val span = b.timestampMs - a.timestampMs
            val length = RunMetrics.distanceMeters(listOf(a, b))
            if (length * 1000.0 / span > 15.0) break
            val used = minOf(span, 10_000L - elapsed)
            distance += length * used / span
            elapsed += used; pairs++
            if (elapsed >= 10_000L) break
        }
        if (elapsed < 5_000L || pairs < 2 || distance * 1000.0 / elapsed < .5) return null
        return RunMetrics.paceSecondsPerKm(distance, elapsed)
    }
}
