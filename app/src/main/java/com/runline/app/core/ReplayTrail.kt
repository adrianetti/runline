package com.runline.app.core

object ReplayTrail {
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

    fun head(route: List<RunPoint>, trailEnd: Int, passed: Int, arrow: RunPoint?): List<RunPoint> {
        if (arrow == null || passed < 0 || passed > route.lastIndex) return emptyList()
        return route.subList(trailEnd.coerceIn(0, passed), passed + 1).filter { it.segment == arrow.segment } + arrow
    }
}
