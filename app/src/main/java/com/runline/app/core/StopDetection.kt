package com.runline.app.core

/** A standstill inside one recording segment, such as a traffic light or a shoelace. */
data class StopInterval(val startMs: Long, val endMs: Long) {
    val durationMs get() = (endMs - startMs).coerceAtLeast(0)
}

/**
 * Finds standstills from the accepted GPS fixes. Derived only: fixes, timer history and
 * exports are never edited, so moving time can always be recomputed or ignored.
 *
 * The recorder drops fixes that move less than 2.5 m within 10 s, so standing still shows
 * as sparse steps with almost no movement. A step slower than [stillSpeed] is a stop; the
 * time needed to cover its small distance at a normal moving speed stays moving time.
 * Jitter steps inside one stop are joined when the position stays within GPS accuracy.
 */
object StopDetection {
    const val MIN_STOP_MS = 5_000L
    private const val MIN_STEP_MS = 2_000L
    private const val MAX_STEP_MS = 15_000L
    private const val MERGE_GAP_MS = 12_000L

    fun detect(points: List<RunPoint>, type: ActivityType = ActivityType.RUN): List<StopInterval> {
        val stillSpeed = if (type == ActivityType.RUN) .8 else .35
        val leaveSpeed = if (type == ActivityType.RUN) 2.5 else 1.2
        val found = mutableListOf<Triple<Long, Long, RunPoint>>()
        for (segment in continuousSegments(points)) {
            var open: Triple<Long, Long, RunPoint>? = null
            for (index in 1 until segment.size) {
                val a = segment[index - 1]; val b = segment[index]
                val step = b.timestampMs - a.timestampMs
                if (step !in MIN_STEP_MS..MAX_STEP_MS || !usable(a) || !usable(b)) continue
                val meters = RunMetrics.distanceMeters(listOf(a, b))
                if (meters * 1_000 / step >= stillSpeed) continue
                val end = maxOf(a.timestampMs, b.timestampMs - (meters / leaveSpeed * 1_000).toLong())
                val current = open
                open = if (current != null && a.timestampMs - current.second <= MERGE_GAP_MS &&
                    RunMetrics.distanceMeters(listOf(current.third, b)) <= b.accuracyMeters.toDouble().coerceIn(8.0, 15.0))
                    Triple(current.first, maxOf(current.second, end), current.third)
                else {
                    current?.let(found::add)
                    Triple(a.timestampMs, end, a)
                }
            }
            open?.let(found::add)
        }
        return found.filter { it.second - it.first >= MIN_STOP_MS }.map { StopInterval(it.first, it.second) }
    }

    /** Total of overlapping intervals counted once. */
    fun totalMs(stops: List<StopInterval>): Long {
        var total = 0L; var end = Long.MIN_VALUE
        stops.sortedBy { it.startMs }.forEach { stop ->
            if (stop.endMs <= end) return@forEach
            total += stop.endMs - maxOf(stop.startMs, end)
            end = stop.endMs
        }
        return total
    }

    /** Active timer time minus standstills. Never negative, never more than the active time. */
    fun movingDurationMs(run: RunSession, stops: List<StopInterval> = detect(run.points, run.activityType), openStopSinceMs: Long? = null,
                         nowMs: Long = run.endedAtMs): Long {
        val open = openStopSinceMs?.takeIf { nowMs - it >= MIN_STOP_MS }?.let { StopInterval(it, nowMs) }
        return (run.activeDurationMs - totalMs(stops + listOfNotNull(open))).coerceIn(0, run.activeDurationMs.coerceAtLeast(0))
    }

    private fun usable(point: RunPoint) = point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..30f
}

/**
 * Live standstill signal from every valid GPS fix, including the ones the recorder does not
 * store because they barely moved. Doppler speed answers quickly; position is the fallback.
 */
class StillnessTracker(private val type: ActivityType = ActivityType.RUN) {
    private var anchor: RunPoint? = null
    private var stillSinceMs: Long? = null
    var stoppedSinceMs: Long? = null
        private set

    fun reset() { anchor = null; stillSinceMs = null; stoppedSinceMs = null }

    fun update(latitude: Double, longitude: Double, timeMs: Long, accuracyMeters: Float, speedMetersPerSecond: Float?): Long? {
        val stillSpeed = if (type == ActivityType.RUN) .6 else .3
        val fix = RunPoint(latitude, longitude, timeMs, accuracyMeters)
        val previous = anchor
        val meters = previous?.let { RunMetrics.distanceMeters(listOf(it, fix)) }
        val seconds = previous?.let { (timeMs - it.timestampMs) / 1_000.0 }
        val radius = accuracyMeters.toDouble().coerceIn(5.0, 10.0)
        // Within GPS accuracy of the anchor and not steadily drifting away from it.
        val near = meters != null && seconds != null && meters <= radius && (seconds < 3.0 || meters / seconds < stillSpeed * 1.5)
        val slow = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0f }?.let { it < stillSpeed }
        val still = when {
            previous == null -> slow ?: false
            // Leaving GPS accuracy is movement, even when a provider reports zero speed.
            meters != null && meters > radius -> false
            else -> slow ?: near
        }
        if (!still) { anchor = fix; stillSinceMs = null; stoppedSinceMs = null; return null }
        if (!near) anchor = fix
        val since = stillSinceMs ?: timeMs.also { stillSinceMs = it }
        if (timeMs - since >= StopDetection.MIN_STOP_MS) stoppedSinceMs = since
        return stoppedSinceMs
    }
}
