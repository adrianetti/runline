package com.runline.app.core

import kotlin.math.roundToInt

enum class Compass { NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;
    companion object {
        fun of(bearingDegrees: Double): Compass = entries[(((bearingDegrees % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
    }
}

data class ApproachCue(val meters: Int, val side: RouteSide?, val compass: Compass?)

class RouteApproach {
    private var started = false
    private var band = Int.MAX_VALUE
    private var lastAt = 0L
    private var lastSide: RouteSide? = null

    fun update(progress: RouteProgress, nowMs: Long): ApproachCue? {
        if (progress.state != RouteState.APPROACHING) { reset(); return null }
        val meters = progress.distanceFromRouteMeters
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
        band = if (started) maxOf(band, nextBand) else nextBand
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

        fun round(meters: Double): Int = when {
            meters < 100 -> (meters / 10).roundToInt() * 10
            meters < 1_000 -> (meters / 50).roundToInt() * 50
            else -> (meters / 100).roundToInt() * 100
        }
    }
}
