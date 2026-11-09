package com.runline.app.core

/** Offline suggestions only: no reverse geocoding, guessed towns or inferred trail surfaces. */
object RouteImportNaming {
    const val MAX_NAME = 120
    enum class Shape { ROUTE, LOOP, OUT_AND_BACK }

    fun clean(value: String): String = value
        .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
        .replace(Regex("\\s+"), " ").trim().take(MAX_NAME).trim()

    fun suppliedName(gpxName: String?, fileName: String?): String? {
        clean(gpxName.orEmpty()).takeIf { it.isNotEmpty() }?.let { return it }
        val file = fileName.orEmpty().substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("(?i)\\.gpx$"), "").replace('_', ' ')
        val name = clean(file)
        // Provider IDs, timestamps and generic export names are less useful than route geometry.
        if (name.none { it.isLetter() } || Regex("(?i)[0-9a-f-]{20,}").matches(name) ||
            Regex("(?i)(gpx|track|route|activity|recording|export|download|untitled|ruta|actividad)([\\s._()-]*[0-9]+[\\s._()-]*)*").matches(name)) return null
        return name.takeIf { it.isNotBlank() }
    }

    fun shape(route: HikingRoute): Shape {
        if (route.distanceMeters < 200) return Shape.ROUTE
        val first = route.points.first(); val last = route.points.last()
        val closure = (route.distanceMeters * .02).coerceIn(25.0, 100.0)
        if (distance(first, last) > closure) return Shape.ROUTE
        // Compare equal distances from opposite ends, independent of vertex spacing.
        // A shared start/end alone does not make a path an out-and-back.
        val pairs = (1..24).map { step ->
            val d = route.distanceMeters * step / 50.0
            distance(at(route, d), at(route, route.distanceMeters - d))
        }
        return if (pairs.count { it <= 25 } >= 23 && pairs.max() <= 60 &&
            distance(first, at(route, route.distanceMeters / 2)) >= 100) Shape.OUT_AND_BACK else Shape.LOOP
    }

    /** Only waypoint labels actually near the start and end/turnaround are useful here. */
    fun places(route: HikingRoute, shape: Shape): String? {
        fun near(point: RoutePoint) = route.waypoints.filter { clean(it.name).isNotEmpty() }
            .minByOrNull { RouteGeometry.meters(point.latitude, point.longitude, it.latitude, it.longitude) }
            ?.takeIf { RouteGeometry.meters(point.latitude, point.longitude, it.latitude, it.longitude) <= 100 }
            ?.name?.let(::clean)
        val start = near(route.points.first())
        val end = near(if (shape == Shape.ROUTE) route.points.last() else at(route, route.distanceMeters / 2))
        return listOfNotNull(start, end).distinct().joinToString(" – ").takeIf { it.isNotBlank() }
    }

    private fun distance(a: RoutePoint, b: RoutePoint) = RouteGeometry.meters(a.latitude, a.longitude, b.latitude, b.longitude)
    private fun at(route: HikingRoute, meters: Double): RoutePoint {
        val points = route.points
        var low = 1; var high = points.lastIndex
        while (low < high) { val middle = (low + high) / 2; if (points[middle].distanceMeters < meters) low = middle + 1 else high = middle }
        val a = points[low - 1]; val b = points[low]
        val fraction = ((meters - a.distanceMeters) / (b.distanceMeters - a.distanceMeters)).coerceIn(0.0, 1.0)
        // Interpolate across the antimeridian on the short arc.
        val delta = ((b.longitude - a.longitude + 540) % 360) - 180
        val lon = ((a.longitude + delta * fraction + 540) % 360) - 180
        return RoutePoint(a.latitude + (b.latitude - a.latitude) * fraction, lon, null, meters)
    }
}
