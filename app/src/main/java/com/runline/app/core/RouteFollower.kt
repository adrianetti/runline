package com.runline.app.core

import kotlin.math.*

enum class RouteState { APPROACHING, ON_ROUTE, OFF_ROUTE, FINISHED }

/** Where the path is, relative to the direction of travel. */
enum class RouteSide { AHEAD, RIGHT, BEHIND, LEFT }

data class RouteProgress(
    val state: RouteState,
    /** Distance along the route at the matched position. */
    val doneMeters: Double,
    val remainingMeters: Double,
    /** Distance from the fix to the route (to its start while approaching). */
    val distanceFromRouteMeters: Double,
    val ascentRemainingMeters: Double?,
    /** Steady-effort minutes left, scaled to the hiker's pace on this route after the first kilometre. */
    val minutesRemaining: Double,
    /** Only when off route or approaching: where the path is from here. */
    val side: RouteSide?,
    val bearingToRouteDegrees: Double?,
    val nextWaypoint: RouteWaypoint?,
    val nextWaypointMeters: Double?,
    /** True only on the update where [state] changed, so alerts fire once. */
    val changed: Boolean,
)

/**
 * Matches live fixes to a planned route. Each fix is projected onto the route and the
 * candidate closest to the *expected* progress wins, so overlapping out-and-back legs and
 * loops resolve correctly. Leaving the route needs 3 fixes over 10 s beyond a threshold
 * scaled to GPS accuracy; rejoining needs 2 fixes well inside it.
 */
class RouteFollower(val route: HikingRoute) {
    private val lat0 = route.points.first().latitude
    private val lon0 = route.points.first().longitude
    private val kx = 111_320.0 * cos(Math.toRadians(lat0))
    private val ky = 110_574.0
    private val xs = DoubleArray(route.points.size) { (route.points[it].longitude - lon0) * kx }
    private val ys = DoubleArray(route.points.size) { (route.points[it].latitude - lat0) * ky }
    private val total = route.distanceMeters
    private val waypointAlong: List<Pair<RouteWaypoint, Double>> = route.waypoints.filter { it.name.isNotBlank() }
        .map { w -> w to project((w.longitude - lon0) * kx, (w.latitude - lat0) * ky, null).along }.sortedBy { it.second }

    private var state = RouteState.APPROACHING
    private var along = 0.0
    private var joinedAlong: Double? = null
    private var offSince: Long? = null
    private var offCount = 0
    private var backCount = 0
    private var lastFix: Triple<Double, Double, Long>? = null
    /** Metres walked since joining; a wrong leg is corrected silently within the first 300 m. */
    private var sinceJoin = 0.0
    private var courseFrom: Pair<Double, Double>? = null
    /** Last direction of travel over at least 8 m; kept between fixes that move less. */
    private var course: Double? = null

    val currentState get() = state

    private data class Match(val along: Double, val distance: Double, val x: Double, val y: Double, val cost: Double)

    private fun project(px: Double, py: Double, expected: Double?): Match {
        var best: Match? = null
        for (k in 0 until xs.size - 1) {
            val ax = xs[k]; val ay = ys[k]; val dx = xs[k + 1] - ax; val dy = ys[k + 1] - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val qx = ax + t * dx; val qy = ay + t * dy
            val d = hypot(px - qx, py - qy)
            val a = route.points[k].distanceMeters + t * (route.points[k + 1].distanceMeters - route.points[k].distanceMeters)
            val cost = d + if (expected == null) 0.0 else 0.15 * abs(a - expected)
            if (best == null || cost < best.cost) best = Match(a, d, qx, qy, cost)
        }
        return best!!
    }

    private fun threshold(accuracy: Float) = (accuracy * 1.5).coerceIn(35.0, 80.0)

    /**
     * Joining: the earliest part of the route within [limit], then the closest point on that leg.
     * A loop starts at its start and an out-and-back on its way out, yet joining mid-route works
     * however far along it is.
     */
    private fun joinMatch(px: Double, py: Double, limit: Double): Match? {
        var first: Double? = null
        for (k in 0 until xs.size - 1) {
            val m = segment(k, px, py)
            if (m.distance <= limit && (first == null || m.along < first)) first = m.along
        }
        val earliest = first ?: return null
        var best: Match? = null
        for (k in 0 until xs.size - 1) {
            val m = segment(k, px, py)
            if (m.distance <= limit && m.along in earliest..(earliest + 2 * limit) && (best == null || m.distance < best.distance)) best = m
        }
        return best
    }
    /** The closest point within [limit] on a segment heading within 60° of [course]. */
    private fun alongCourse(px: Double, py: Double, limit: Double, course: Double): Match? {
        var best: Match? = null
        for (k in 0 until xs.size - 1) {
            val m = segment(k, px, py)
            if (m.distance > limit) continue
            val direction = (Math.toDegrees(atan2(xs[k + 1] - xs[k], ys[k + 1] - ys[k])) + 360) % 360
            if (abs(((direction - course + 540) % 360) - 180) > 60) continue
            if (best == null || m.distance < best.distance) best = m
        }
        return best
    }
    private fun segment(k: Int, px: Double, py: Double): Match {
        val ax = xs[k]; val ay = ys[k]; val dx = xs[k + 1] - ax; val dy = ys[k + 1] - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        val qx = ax + t * dx; val qy = ay + t * dy
        val d = hypot(px - qx, py - qy)
        return Match(route.points[k].distanceMeters + t * (route.points[k + 1].distanceMeters - route.points[k].distanceMeters), d, qx, qy, d)
    }

    fun update(latitude: Double, longitude: Double, accuracyMeters: Float, timeMs: Long, movingMinutes: Double? = null): RouteProgress {
        val px = (longitude - lon0) * kx; val py = (latitude - lat0) * ky
        val limit = threshold(accuracyMeters)
        val previousState = state
        val prior = lastFix
        val step = prior?.let { RouteGeometry.meters(it.first, it.second, latitude, longitude) } ?: 0.0
        lastFix = Triple(latitude, longitude, timeMs)
        val from = courseFrom
        if (from == null) courseFrom = latitude to longitude
        else if (RouteGeometry.meters(from.first, from.second, latitude, longitude) >= 8) {
            course = RouteGeometry.bearing(from.first, from.second, latitude, longitude); courseFrom = latitude to longitude
        }
        val course = course

        var match = when (state) {
            RouteState.APPROACHING -> project(px, py, 0.0)
            else -> project(px, py, along + step)
        }
        when (state) {
            RouteState.APPROACHING -> joinMatch(px, py, limit)?.let { state = RouteState.ON_ROUTE; along = it.along; joinedAlong = it.along; sinceJoin = 0.0; match = it }
            RouteState.ON_ROUTE -> {
                sinceJoin += step
                // Joined on a neighbouring leg (near a junction, beside a parallel path): within the first
                // 300 m, move to a leg within reach that runs the way you are walking.
                if (sinceJoin < 300 && course != null && (match.distance > limit || match.along < along - 5)) {
                    alongCourse(px, py, limit, course)?.takeIf { abs(it.along - match.along) > 2 * limit }?.let {
                        match = it; joinedAlong = it.along; offCount = 0; offSince = null
                    }
                }
                if (match.distance > limit) {
                    offCount++; if (offSince == null) offSince = timeMs
                    if (offCount >= 3 && timeMs - offSince!! >= 10_000) { state = RouteState.OFF_ROUTE; backCount = 0 }
                } else { offCount = 0; offSince = null; along = match.along }
            }
            RouteState.OFF_ROUTE -> {
                // Rejoining anywhere is fine (a shortcut or a detour back to the path).
                val anywhere = project(px, py, null)
                val rejoin = if (match.distance <= limit * 0.6) match else anywhere.takeIf { it.distance <= limit * 0.6 }
                if (rejoin != null) {
                    backCount++
                    if (backCount >= 2) { state = RouteState.ON_ROUTE; along = rejoin.along; offCount = 0; offSince = null }
                } else backCount = 0
            }
            RouteState.FINISHED -> Unit
        }
        if (state == RouteState.ON_ROUTE && along >= total - 30 && along - (joinedAlong ?: 0.0) >= total * 0.5) state = RouteState.FINISHED

        val shown = if (state == RouteState.APPROACHING) 0.0 else along
        val fromRoute = if (state == RouteState.APPROACHING)
            RouteGeometry.meters(latitude, longitude, route.points.first().latitude, route.points.first().longitude) else match.distance
        val target = if (state == RouteState.APPROACHING) route.points.first().let { (it.longitude - lon0) * kx to (it.latitude - lat0) * ky } else match.x to match.y
        val toRoute = if (fromRoute < 5) null else (Math.toDegrees(atan2(target.first - px, target.second - py)) + 360) % 360
        val side = if (state == RouteState.OFF_ROUTE || state == RouteState.APPROACHING) toRoute?.let { b -> course?.let { c -> sideOf(b, c) } } else null
        val ascent = if (route.stats.ascentMeters == null) null else (route.ascentAt.last() - interpolate(route.ascentAt, shown)).coerceAtLeast(0.0)
        val minutesLeft = (route.minutesAt.last() - interpolate(route.minutesAt, shown)).coerceAtLeast(0.0)
        val joined = joinedAlong
        val factor = if (joined != null && movingMinutes != null && shown - joined >= 1000) {
            val planned = interpolate(route.minutesAt, shown) - interpolate(route.minutesAt, joined)
            if (planned > 1) (movingMinutes / planned).coerceIn(0.6, 2.0) else 1.0
        } else 1.0
        val next = waypointAlong.firstOrNull { it.second > shown + 20 }
        return RouteProgress(state, shown, (total - shown).coerceAtLeast(0.0), fromRoute, ascent, minutesLeft * factor,
            side, toRoute, next?.first, next?.second?.minus(shown), state != previousState)
    }

    private fun interpolate(values: DoubleArray, distance: Double): Double {
        val pts = route.points
        var low = 0; var high = pts.lastIndex
        while (low < high) { val mid = (low + high + 1) ushr 1; if (pts[mid].distanceMeters <= distance) low = mid else high = mid - 1 }
        if (low >= pts.lastIndex) return values.last()
        val a = pts[low].distanceMeters; val b = pts[low + 1].distanceMeters
        val t = if (b > a) ((distance - a) / (b - a)).coerceIn(0.0, 1.0) else 0.0
        return values[low] + (values[low + 1] - values[low]) * t
    }

    companion object {
        fun sideOf(bearingToRoute: Double, course: Double): RouteSide {
            val relative = ((bearingToRoute - course + 540) % 360) - 180
            return when {
                abs(relative) <= 45 -> RouteSide.AHEAD
                abs(relative) >= 135 -> RouteSide.BEHIND
                relative > 0 -> RouteSide.RIGHT
                else -> RouteSide.LEFT
            }
        }
    }
}

/** NOAA-style sunrise equation; accurate to a minute or two at hiking latitudes. */
object SunTimes {
    /** Epoch milliseconds of sunset on [epochDay] (UTC date) at the place, or null in polar day or night. */
    fun sunset(epochDay: Long, latitude: Double, longitude: Double): Long? = event(epochDay, latitude, longitude, +1)
    fun sunrise(epochDay: Long, latitude: Double, longitude: Double): Long? = event(epochDay, latitude, longitude, -1)

    private fun event(epochDay: Long, latitude: Double, longitude: Double, sign: Int): Long? {
        // Julian date at 00:00 UTC of the day; at noon the ceiling below would pick the next day.
        val julian0 = epochDay + 2440587.5
        val n = ceil(julian0 - 2451545.0 + 0.0008)
        val meanSolarNoon = n - longitude / 360
        val m = (357.5291 + 0.98560028 * meanSolarNoon).mod(360.0)
        val mr = Math.toRadians(m)
        val center = 1.9148 * sin(mr) + 0.0200 * sin(2 * mr) + 0.0003 * sin(3 * mr)
        val lambda = Math.toRadians((m + center + 180 + 102.9372).mod(360.0))
        val transit = 2451545.0 + meanSolarNoon + 0.0053 * sin(mr) - 0.0069 * sin(2 * lambda)
        val declination = asin(sin(lambda) * sin(Math.toRadians(23.4397)))
        val phi = Math.toRadians(latitude)
        val cosHour = (sin(Math.toRadians(-0.833)) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))
        if (cosHour < -1 || cosHour > 1) return null
        val julian = transit + sign * Math.toDegrees(acos(cosHour)) / 360
        return ((julian - 2440587.5) * 86_400_000).roundToLong()
    }
}
