package com.runline.app.core

import kotlin.math.exp

/** Display orientation only: never predicts a position or modifies a GPS sample. */
class FollowCamera {
    data class Pose(val bearing: Double, val tilt: Double, val markerRotation: Double?)
    private var bearing = 0.0
    private var tilt = 0.0
    private var lastFrameMs: Long? = null

    fun reset() { bearing = 0.0; tilt = 0.0; lastFrameMs = null }

    /** Wall time makes steering independent of replay speed and display frame rate. */
    fun update(heading: Double?, nowMs: Long, cut: Boolean = false, reduceMotion: Boolean = false): Pose {
        val course = heading?.takeIf(Double::isFinite)?.let(::normalize)
        val priorTime = lastFrameMs
        val interrupted = cut || priorTime == null || nowMs < priorTime || nowMs - priorTime > 2_000L
        val targetBearing = if (reduceMotion) 0.0 else course ?: if (interrupted) 0.0 else bearing
        val targetTilt = when { reduceMotion -> 0.0; course != null -> FOLLOW_TILT; interrupted -> 0.0; else -> tilt }
        if (interrupted || reduceMotion) {
            bearing = targetBearing
            tilt = targetTilt
        } else {
            val elapsed = (nowMs - priorTime).coerceAtLeast(0L)
            val amount = 1.0 - exp(-elapsed / 240.0)
            bearing = normalize(bearing + shortestAngle(bearing, targetBearing) * amount)
            tilt += (targetTilt - tilt) * amount
        }
        lastFrameMs = nowMs
        return Pose(bearing, tilt, course?.let { shortestAngle(bearing, it) })
    }

    companion object {
        const val FOLLOW_TILT = 46.0
        fun normalize(value: Double) = ((value % 360.0) + 360.0) % 360.0
        fun shortestAngle(from: Double, to: Double) = ((normalize(to) - normalize(from) + 540.0) % 360.0) - 180.0
        fun relativeBearing(heading: Double?, cameraBearing: Double): Double? =
            heading?.takeIf(Double::isFinite)?.let { shortestAngle(cameraBearing.takeIf(Double::isFinite) ?: 0.0, it) }
    }
}
