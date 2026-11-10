package com.runline.app.core

import kotlin.math.roundToInt

/** Compass points for when there is no direction of travel yet (standing still at the trailhead car park). */
enum class Compass { NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;
    companion object {
        fun of(bearingDegrees: Double): Compass = entries[(((bearingDegrees % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
    }
}

/** What to say on the way to the route: rounded distance to the start, and which way it is. */
data class ApproachCue(val meters: Int, val side: RouteSide?, val compass: Compass?)

/**
 * Spoken help getting to the route, as the crow flies: no street routing, which the phone's maps app
 * does better (see the Directions button). Says where the start is when following begins, as it gets
 * closer (1 km, 500, 200, 100, 50 m), when it falls behind you, and every few minutes otherwise.
 */
class RouteApproach {
    private var started = false
    private var band = Int.MAX_VALUE
    private var lastAt = 0L
    private var lastSide: RouteSide? = null

    fun update(progress: RouteProgress, nowMs: Long): ApproachCue? {
        if (progress.state != RouteState.APPROACHING) { reset(); return null }
        val meters = progress.distanceFromRouteMeters
        // Close enough that "On the route" follows within a few steps.
        if (meters < JOINING) return null
        val nextBand = BANDS.indexOfFirst { meters > it }.let { if (it < 0) BANDS.size else it }
        val side = progress.side
        val speak = when {
            !started -> true
            nextBand > band -> true
            side == RouteSide.BEHIND && lastSide != RouteSide.BEHIND && nowMs - lastAt >= TURNED_AWAY_MS -> true
            nowMs - lastAt >= REMINDER_MS -> true
            else -> false
        }
        // Bands only move closer: drifting back out does not repeat an announcement.
        band = if (started) maxOf(band, nextBand) else nextBand
        // A turn away from the start stays pending until it has been said.
        if (!speak) { if (side != null && side != RouteSide.BEHIND) lastSide = side; return null }
        started = true; lastAt = nowMs; lastSide = side
        return ApproachCue(round(meters), side, if (side == null) progress.bearingToRouteDegrees?.let(Compass::of) else null)
    }

    fun reset() { started = false; band = Int.MAX_VALUE; lastAt = 0L; lastSide = null }

    companion object {
        private val BANDS = listOf(1_000.0, 500.0, 200.0, 100.0, 50.0)
        private const val JOINING = 30.0
        private const val TURNED_AWAY_MS = 45_000L
        private const val REMINDER_MS = 180_000L

        /** 10 m steps under 100 m, 50 m under 1 km, then 100 m. */
        fun round(meters: Double): Int = when {
            meters < 100 -> (meters / 10).roundToInt() * 10
            meters < 1_000 -> (meters / 50).roundToInt() * 50
            else -> (meters / 100).roundToInt() * 100
        }
    }
}
