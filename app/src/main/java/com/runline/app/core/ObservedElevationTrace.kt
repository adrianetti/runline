package com.runline.app.core

/** Heights use exactly the replay's observed clock, even when a height is unusable. */
class ObservedElevationTrace private constructor(segments: List<List<RunPoint>>, latestIsObserved: Boolean) {
    data class Sample(val positionMs: Long, val heightMeters: Double)
    private data class Fix(val positionMs: Long, val point: RunPoint, val drawable: Boolean)
    val sections: List<List<Sample>>
    val durationMs: Long
    private val bySegment: List<List<Fix>>
    val latestCursor: Sample?
    val latestSegmentIndex: Int get() = bySegment.lastIndex

    init {
        val paths = mutableListOf<List<Sample>>()
        var clock = 0L
        bySegment = segments.map { points ->
            val start = points.first().timestampMs
            val fixes = points.map { Fix(clock + it.timestampMs - start, it, false) }.toMutableList()
            var path = mutableListOf<Int>()
            fun finish() {
                if (path.size >= 2) {
                    paths += path.map { index ->
                        fixes[index] = fixes[index].copy(drawable = true)
                        Sample(fixes[index].positionMs, fixes[index].point.altitudeMeters!!)
                    }
                }
                path = mutableListOf()
            }
            fixes.forEachIndexed { index, fix ->
                if (index > 0 && !canJoinAltitude(fixes[index - 1].point, fix.point)) finish()
                if (validHeight(fix.point)) path += index else finish()
            }
            finish()
            clock += points.last().timestampMs - start
            fixes
        }
        sections = paths
        durationMs = clock
        latestCursor = if (latestIsObserved) at(clock, bySegment.lastIndex) else null
    }

    /** Never bridges unknown heights, poor fixes, jumps, or an isolated endpoint. */
    fun at(positionMs: Long, segmentIndex: Int): Sample? {
        val fixes = bySegment.getOrNull(segmentIndex) ?: return null
        if (fixes.isEmpty() || positionMs < fixes.first().positionMs || positionMs > fixes.last().positionMs) return null
        var low = 0; var high = fixes.lastIndex
        while (low < high) {
            val middle = (low + high + 1) ushr 1
            if (fixes[middle].positionMs <= positionMs) low = middle else high = middle - 1
        }
        val a = fixes[low]
        if (!a.drawable || !validHeight(a.point)) return null
        if (positionMs == a.positionMs) return Sample(positionMs, a.point.altitudeMeters!!)
        val b = fixes.getOrNull(low + 1) ?: return null
        if (!b.drawable || !validHeight(b.point) || !canJoinAltitude(a.point, b.point)) return null
        val fraction = (positionMs - a.positionMs).toDouble() / (b.positionMs - a.positionMs)
        return Sample(positionMs, a.point.altitudeMeters!! + (b.point.altitudeMeters!! - a.point.altitudeMeters) * fraction)
    }

    companion object {
        fun fromReplay(replay: RunReplay) = ObservedElevationTrace(replay.segments, true)

        fun fromPoints(points: List<RunPoint>): ObservedElevationTrace {
            // The same accepted order/gap rule as RunReplay; height quality does not
            // remove time. Trace breaks and cursor visibility are separate concerns.
            val segments = buildList {
                continuousSegments(points).forEach { source ->
                    var segment = mutableListOf<RunPoint>()
                    source.forEach { point ->
                        if (segment.isNotEmpty() && point.timestampMs - segment.last().timestampMs > RunReplay.MAX_GPS_GAP_MS) {
                            add(segment); segment = mutableListOf()
                        }
                        segment += point
                    }
                    if (segment.isNotEmpty()) add(segment)
                }
            }
            return ObservedElevationTrace(segments, segments.lastOrNull()?.lastOrNull() == points.lastOrNull())
        }

        private fun validHeight(point: RunPoint) = point.hasUsableAltitude() &&
            point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..30f
    }
}
