package com.runline.app.core

import kotlin.math.roundToInt

/** A named place on the route with a short note to read out as you reach it. */
data class RoutePlace(val name: String, val note: String, val alongMeters: Double)

sealed class RouteCue {
    /** [meters] is rounded for speech; [now] is the late cue. [then] follows within 60 m. */
    data class Turn(val maneuver: RouteManeuver, val meters: Int, val now: Boolean, val then: RouteManeuver?) : RouteCue()
    data class Climb(val climb: RouteClimb, val meters: Int) : RouteCue()
    data class Place(val place: RoutePlace) : RouteCue()
}

/** What the island shows: the next manoeuvre and the climb you are in or approaching. */
data class RouteGuidanceView(
    val maneuver: RouteManeuver?,
    val maneuverMeters: Double?,
    val climb: RouteClimb?,
    /** Distance to the start of [climb]; zero or less inside it. */
    val climbMeters: Double?,
)

/**
 * Turns progress on a followed route into spoken cues: each turn about 80 m ahead and again
 * at 20 m, each climb or descent once about 200 m before it, and each place note once as you
 * reach it, after any turn just ahead. A cue is never repeated, and nothing already behind you is spoken, including
 * after rejoining the route somewhere else. Only one cue per update; turns come first.
 */
class RouteGuidance(
    val maneuvers: List<RouteManeuver>,
    val climbs: List<RouteClimb>,
    val places: List<RoutePlace>,
) {
    private val far = BooleanArray(maneuvers.size)
    private val near = BooleanArray(maneuvers.size)
    private val climbSpoken = BooleanArray(climbs.size)
    private val placeSpoken = BooleanArray(places.size)
    @Volatile private var done = 0.0
    @Volatile private var onRoute = false

    fun update(progress: RouteProgress): RouteCue? {
        done = progress.doneMeters
        onRoute = progress.state == RouteState.ON_ROUTE
        if (!onRoute) return null
        val d = progress.doneMeters
        // Everything behind you is settled for good, so a rejoin never speaks old turns.
        maneuvers.forEachIndexed { i, m -> if (m.alongMeters < d - PASSED) { far[i] = true; near[i] = true } }
        climbs.forEachIndexed { i, c -> if (c.startMeters - d < CLIMB_MIN) climbSpoken[i] = true }
        places.forEachIndexed { i, p -> if (d > p.alongMeters + PLACE_AFTER) placeSpoken[i] = true }
        var turnSoon = false

        val next = maneuvers.indexOfFirst { it.alongMeters >= d - PASSED }
        if (next >= 0) {
            val m = maneuvers[next]; val ahead = m.alongMeters - d
            if (ahead <= NEAR && !near[next]) {
                near[next] = true; far[next] = true
                val then = maneuvers.getOrNull(next + 1)?.takeIf { it.alongMeters - m.alongMeters <= THEN }
                if (then != null) far[next + 1] = true
                return RouteCue.Turn(m, ahead.coerceAtLeast(0.0).roundToInt(), true, then)
            }
            if (ahead < FAR_MIN) far[next] = true
            if (ahead <= FAR && !far[next]) {
                far[next] = true
                return RouteCue.Turn(m, roundTo(ahead, 10), false, null)
            }
            // A long note would hold back the "now" cue; read it once the turn is behind you.
            turnSoon = ahead <= PLACE_CLEAR && !near[next]
        }
        val place = if (turnSoon) null else places.indices.firstOrNull { !placeSpoken[it] && d >= places[it].alongMeters - PLACE_BEFORE }
        if (place != null) { placeSpoken[place] = true; return RouteCue.Place(places[place]) }
        val climb = climbs.indices.firstOrNull { !climbSpoken[it] && climbs[it].startMeters - d <= CLIMB_AHEAD }
        if (climb != null) { climbSpoken[climb] = true; return RouteCue.Climb(climbs[climb], roundTo(climbs[climb].startMeters - d, 50)) }
        return null
    }

    /** True while a queued cue still describes what is ahead; a passed turn is never spoken late. */
    fun stillAhead(cue: RouteCue): Boolean = onRoute && when (cue) {
        is RouteCue.Turn -> cue.maneuver.alongMeters - done >= if (cue.now) -8.0 else NEAR
        is RouteCue.Climb -> cue.climb.startMeters - done >= 0
        is RouteCue.Place -> done <= cue.place.alongMeters + PLACE_STALE
    }

    fun view(doneMeters: Double): RouteGuidanceView {
        val m = maneuvers.firstOrNull { it.alongMeters >= doneMeters - PASSED }
        val c = RouteClimbs.next(climbs, doneMeters)
        return RouteGuidanceView(m, m?.let { (it.alongMeters - doneMeters).coerceAtLeast(0.0) }, c, c?.let { it.startMeters - doneMeters })
    }

    companion object {
        const val FAR = 85.0
        const val FAR_MIN = 40.0
        const val NEAR = 22.0
        const val THEN = 60.0
        const val PASSED = 5.0
        const val CLIMB_AHEAD = 220.0
        const val CLIMB_MIN = 50.0
        const val PLACE_BEFORE = 60.0
        const val PLACE_AFTER = 150.0
        const val PLACE_STALE = 250.0
        /** No place note starts while a turn's "now" cue is still to come within this distance. */
        const val PLACE_CLEAR = 150.0

        fun roundTo(meters: Double, step: Int) = ((meters / step).roundToInt() * step).coerceAtLeast(step)

        /** Places from catalog notes: each waypoint named in [notes] is projected onto the route. */
        fun places(route: HikingRoute, notes: Map<String, String>): List<RoutePlace> {
            if (notes.isEmpty()) return emptyList()
            return route.waypoints.filter { it.name in notes }.mapNotNull { w ->
                val nearest = route.points.minByOrNull { RouteGeometry.meters(it.latitude, it.longitude, w.latitude, w.longitude) } ?: return@mapNotNull null
                if (RouteGeometry.meters(nearest.latitude, nearest.longitude, w.latitude, w.longitude) > 100) null
                else RoutePlace(w.name, notes.getValue(w.name), nearest.distanceMeters)
            }.sortedBy { it.alongMeters }
        }
    }
}
