package com.runline.app.core

/**
 * The part of a replayed route already covered by its arrow, so the map can draw the path
 * behind the arrow as it moves. [route] is in replay order: segment, then time.
 */
object ReplayTrail {
    /** Index of the last fix at or before the arrow; -1 without an arrow. O(log n). */
    fun passed(route: List<RunPoint>, arrow: RunPoint?): Int {
        if (arrow == null) return -1
        var low = 0; var high = route.lastIndex; var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val point = route[middle]
            if (point.segment < arrow.segment || (point.segment == arrow.segment && point.timestampMs <= arrow.timestampMs)) {
                found = middle; low = middle + 1
            } else high = middle - 1
        }
        return found
    }

    /**
     * Index of the last fix at least [meters] of track behind [passed], for the map trail during
     * playback. It never reaches back past the start of the arrow's segment: there it is the
     * previous segment's last fix (or -1), so an earlier segment is always drawn complete.
     */
    fun behind(route: List<RunPoint>, passed: Int, meters: Double): Int {
        if (passed < 0 || passed > route.lastIndex) return passed
        val segment = route[passed].segment
        var index = passed; var covered = 0.0
        while (index > 0 && route[index - 1].segment == segment && covered < meters) {
            covered += RouteGeometry.meters(route[index - 1].latitude, route[index - 1].longitude, route[index].latitude, route[index].longitude)
            index--
        }
        return if (covered >= meters) index else index - 1
    }

    /** From the end of the drawn trail to the arrow itself, never across a segment break. */
    fun head(route: List<RunPoint>, trailEnd: Int, passed: Int, arrow: RunPoint?): List<RunPoint> {
        if (arrow == null || passed < 0 || passed > route.lastIndex) return emptyList()
        return route.subList(trailEnd.coerceIn(0, passed), passed + 1).filter { it.segment == arrow.segment } + arrow
    }
}
