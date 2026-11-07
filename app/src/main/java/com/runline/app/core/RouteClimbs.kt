package com.runline.app.core

import kotlin.math.*

/** A sustained climb (positive [heightMeters]) or descent (negative) along a route. */
data class RouteClimb(
    val startMeters: Double,
    val endMeters: Double,
    val heightMeters: Double,
) {
    val lengthMeters get() = endMeters - startMeters
    val uphill get() = heightMeters > 0
    /** Average grade in percent, always positive. */
    val gradePercent get() = if (lengthMeters > 0) abs(heightMeters) / lengthMeters * 100 else 0.0
}

/**
 * Climbs and descents from a route profile. Heights are resampled every 25 m, and a 10 m
 * hysteresis keeps small bumps from splitting a climb. A flat stretch of 400 m or more (under
 * 2 m per 100 m) splits a section, so a long valley floor never becomes one endless descent. A
 * section counts when it changes at least 30 m at an average of 1.5% or more: 30 m within 2 km.
 */
object RouteClimbs {
    const val STEP = 25.0
    const val HYSTERESIS = 10.0
    const val MIN_HEIGHT = 30.0
    const val MIN_GRADE = 0.015
    const val FLAT_SPLIT = 400.0

    fun detect(route: HikingRoute): List<RouteClimb> {
        val heights = profile(route, 0.0, route.distanceMeters, STEP) ?: return emptyList()
        val out = ArrayList<RouteClimb>()
        // Alternating extremes: a turning point is confirmed once the height moves 10 m back from it.
        var low = 0; var high = 0; var anchorIndex = 0; var extremeIndex = 0; var rising: Boolean? = null
        fun close(from: Int, to: Int) {
            val up = heights[to] > heights[from]
            for ((a, b) in splitAtFlats(from, to, heights)) {
                val height = heights[b] - heights[a]; val length = (b - a) * STEP
                if ((height > 0) == up && abs(height) >= MIN_HEIGHT && length > 0 && abs(height) / length >= MIN_GRADE)
                    out += RouteClimb(a * STEP, min(b * STEP, route.distanceMeters), height)
            }
        }
        for (i in 1 until heights.size) {
            val h = heights[i]
            when (rising) {
                null -> {
                    if (h - heights[low] >= HYSTERESIS) { rising = true; anchorIndex = low; extremeIndex = i }
                    else if (heights[high] - h >= HYSTERESIS) { rising = false; anchorIndex = high; extremeIndex = i }
                    if (h < heights[low]) low = i
                    if (h > heights[high]) high = i
                }
                true -> if (h >= heights[extremeIndex]) extremeIndex = i else if (heights[extremeIndex] - h >= HYSTERESIS) {
                    close(anchorIndex, extremeIndex); anchorIndex = extremeIndex; extremeIndex = i; rising = false
                }
                false -> if (h <= heights[extremeIndex]) extremeIndex = i else if (h - heights[extremeIndex] >= HYSTERESIS) {
                    close(anchorIndex, extremeIndex); anchorIndex = extremeIndex; extremeIndex = i; rising = true
                }
            }
        }
        if (rising != null) close(anchorIndex, extremeIndex)
        return trim(out, heights)
    }

    /** Pieces of [from]..[to] between flat stretches of at least [FLAT_SPLIT] metres. */
    private fun splitAtFlats(from: Int, to: Int, heights: DoubleArray): List<Pair<Int, Int>> {
        val window = 4 // 100 m
        val minRun = (FLAT_SPLIT / STEP).roundToInt()
        val pieces = ArrayList<Pair<Int, Int>>()
        var start = from; var i = from
        while (i + window <= to) {
            if (abs(heights[i + window] - heights[i]) >= 2.0) { i++; continue }
            var j = i
            while (j + window <= to && abs(heights[j + window] - heights[j]) < 2.0) j++
            val end = j - 1 + window
            if (end - i >= minRun) { if (i > start) pieces += start to i; start = end }
            i = j + 1
        }
        if (to > start) pieces += start to to
        return pieces
    }

    /** Starts each section where the height actually begins to change, not on the flat before it. */
    private fun trim(sections: List<RouteClimb>, heights: DoubleArray): List<RouteClimb> = sections.map { c ->
        var from = (c.startMeters / STEP).roundToInt(); val to = (c.endMeters / STEP).roundToInt().coerceAtMost(heights.lastIndex)
        val up = c.uphill
        while (from + 1 < to && (if (up) heights[from + 1] - heights[from] else heights[from] - heights[from + 1]) < 0.3) from++
        var end = to
        while (end - 1 > from && (if (up) heights[end] - heights[end - 1] else heights[end - 1] - heights[end]) < 0.3) end--
        RouteClimb(from * STEP, end * STEP, heights[end] - heights[from])
    }

    /** The climb you are in, or the next one starting within [lookAhead] metres. */
    fun next(climbs: List<RouteClimb>, doneMeters: Double, lookAhead: Double = 2000.0): RouteClimb? =
        climbs.firstOrNull { it.endMeters > doneMeters + 50 && it.startMeters <= doneMeters + lookAhead }

    /**
     * Heights every [step] metres from [from] to [to] along the route, interpolated between
     * vertices. Null when less than half the route has heights.
     */
    fun profile(route: HikingRoute, from: Double, to: Double, step: Double): DoubleArray? {
        val pts = route.points
        if (pts.count { it.elevationMeters != null } < max(2, pts.size / 2)) return null
        val known = pts.filter { it.elevationMeters != null }
        val start = from.coerceIn(0.0, route.distanceMeters); val end = to.coerceIn(start, route.distanceMeters)
        val n = max(1, ceil((end - start) / step).toInt())
        var cursor = 0
        return DoubleArray(n + 1) { k ->
            val d = min(end, start + k * step)
            while (cursor + 1 < known.size && known[cursor + 1].distanceMeters < d) cursor++
            val a = known[cursor]; val b = known.getOrNull(cursor + 1)
            if (b == null || d <= a.distanceMeters) a.elevationMeters!!
            else {
                val span = b.distanceMeters - a.distanceMeters
                val t = if (span > 0) ((d - a.distanceMeters) / span).coerceIn(0.0, 1.0) else 0.0
                a.elevationMeters!! + (b.elevationMeters!! - a.elevationMeters) * t
            }
        }
    }
}
