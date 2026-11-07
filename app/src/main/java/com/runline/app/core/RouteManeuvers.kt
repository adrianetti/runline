package com.runline.app.core

import kotlin.math.*

enum class TurnKind { BEAR_LEFT, BEAR_RIGHT, LEFT, RIGHT, SHARP_LEFT, SHARP_RIGHT, TURN_AROUND, ZIGZAG }

/** A point where the route changes direction. [angleDegrees] is signed: positive turns right. */
data class RouteManeuver(
    val alongMeters: Double,
    val kind: TurnKind,
    val angleDegrees: Double,
    /** For [TurnKind.ZIGZAG]: how far the switchbacks continue. */
    val lengthMeters: Double = 0.0,
)

/**
 * Turns derived from route geometry alone. The route is resampled every 5 m, and the
 * bearing of the 20 m before a 20 m window is compared with the 20 m after it. A change of
 * 35° or more is a turn. Turns within 30 m merge. Opposite turns within 30 m, or gentle ones
 * (under 60°) within 50 m, are a jog judged by their net change. Runs of switchbacks
 * become one zig-zag cue. A reversal is "turn around" only when the path really retraces
 * itself; a hairpin bend into a ravine is a sharp turn.
 *
 * With [junctions] (catalog routes list where they meet other paths), a turn away from
 * any junction is a bend in a single path and is not a manoeuvre.
 */
object RouteManeuvers {
    const val STEP = 5.0
    const val WINDOW = 20.0
    const val ARM = 20.0
    const val THRESHOLD = 35.0
    /** Same-direction turns closer than this are one turn. */
    const val MERGE = 30.0
    /** Opposite turns closer than this are a jog around something, judged by their net change. */
    const val JOG = 50.0
    const val END_MARGIN = 15.0
    const val JUNCTION_RADIUS = 20.0

    fun detect(route: HikingRoute, junctions: List<Pair<Double, Double>>? = null): List<RouteManeuver> =
        Detector(route.points).run(junctions)

    fun kind(angle: Double, retrace: Boolean = false): TurnKind {
        val a = abs(angle); val right = angle > 0
        return when {
            retrace -> TurnKind.TURN_AROUND
            a >= 120 -> if (right) TurnKind.SHARP_RIGHT else TurnKind.SHARP_LEFT
            a >= 60 -> if (right) TurnKind.RIGHT else TurnKind.LEFT
            else -> if (right) TurnKind.BEAR_RIGHT else TurnKind.BEAR_LEFT
        }
    }

    private class Turn(var along: Double, var angle: Double, val retrace: Boolean)

    private class Detector(points: List<RoutePoint>) {
        private val lat0 = points.first().latitude
        private val lon0 = points.first().longitude
        private val kx = 111_320.0 * cos(Math.toRadians(lat0))
        private val ky = 110_574.0
        private val xs = DoubleArray(points.size) { (points[it].longitude - lon0) * kx }
        private val ys = DoubleArray(points.size) { (points[it].latitude - lat0) * ky }
        private val along = DoubleArray(points.size) { points[it].distanceMeters }
        private val total = along.last()

        fun at(distance: Double): DoubleArray {
            val d = distance.coerceIn(0.0, total)
            var low = 0; var high = along.lastIndex
            while (low < high) { val mid = (low + high + 1) ushr 1; if (along[mid] <= d) low = mid else high = mid - 1 }
            if (low >= along.lastIndex) return doubleArrayOf(xs.last(), ys.last())
            val span = along[low + 1] - along[low]
            val t = if (span > 0) ((d - along[low]) / span).coerceIn(0.0, 1.0) else 0.0
            return doubleArrayOf(xs[low] + (xs[low + 1] - xs[low]) * t, ys[low] + (ys[low + 1] - ys[low]) * t)
        }
        private fun bearing(a: DoubleArray, b: DoubleArray) = Math.toDegrees(atan2(b[0] - a[0], b[1] - a[1]))
        private fun gap(a: DoubleArray, b: DoubleArray) = hypot(a[0] - b[0], a[1] - b[1])

        /** Bearing change from the arm ending at [from] to the arm starting at [to]; 0 if an arm is too short. */
        fun change(from: Double, to: Double): Double {
            val a = at(from - ARM); val b = at(from); val c = at(to); val d = at(to + ARM)
            if (gap(a, b) < 8 || gap(c, d) < 8) return 0.0
            return normalize(bearing(c, d) - bearing(a, b))
        }

        fun run(junctions: List<Pair<Double, Double>>?): List<RouteManeuver> {
            if (total < 2 * END_MARGIN + 2 * ARM) return emptyList()
            val n = floor(total / STEP).toInt()
            val delta = DoubleArray(n + 1) { k ->
                val s = k * STEP
                if (s < END_MARGIN || s > total - END_MARGIN) 0.0 else change(s - WINDOW / 2, s + WINDOW / 2)
            }
            // Neighbouring samples over the threshold are one turn; a reversal may flip sign at ±180°.
            val turns = ArrayList<Turn>()
            var k = 0
            while (k <= n) {
                if (abs(delta[k]) < THRESHOLD) { k++; continue }
                val start = k; val sign = sign(delta[k]); var peak = abs(delta[k])
                while (k + 1 <= n && abs(delta[k + 1]) >= THRESHOLD &&
                    (sign(delta[k + 1]) == sign || abs(delta[k + 1]) >= 150 || peak >= 150)) { k++; peak = max(peak, abs(delta[k])) }
                val top = (start..k).filter { abs(delta[it]) >= peak - 2 }
                val s = top[top.size / 2] * STEP
                turns += if (peak >= 150) hairpin(s) else Turn(s, change(start * STEP - WINDOW / 2, k * STEP + WINDOW / 2), false)
                k++
            }
            val merged = ArrayList<Turn>()
            for (t in turns) {
                val p = merged.lastOrNull()
                val gap = if (p == null) Double.MAX_VALUE else t.along - p.along
                if (p != null && !p.retrace && !t.retrace && sign(p.angle) == sign(t.angle) && gap < MERGE) {
                    p.along = (p.along + t.along) / 2; p.angle = change(p.along - gap / 2 - WINDOW / 2, p.along + gap / 2 + WINDOW / 2); continue
                }
                // A wiggle around a tree or rock, not two real turns such as a street offset.
                val wiggle = gap < MERGE || (gap < JOG && p != null && abs(p.angle) < 60 && abs(t.angle) < 60)
                if (p != null && !p.retrace && !t.retrace && sign(p.angle) != sign(t.angle) && wiggle) {
                    val net = change(p.along - WINDOW / 2, t.along + WINDOW / 2)
                    if (abs(net) < THRESHOLD) { merged.removeAt(merged.lastIndex); continue }
                    if (abs(t.angle) > abs(p.angle)) merged[merged.lastIndex] = t
                    continue
                }
                merged += t
            }
            var kept = merged.filter { it.retrace || abs(it.angle) >= THRESHOLD }
            if (junctions != null) {
                val near = junctions.map { (lat, lon) -> doubleArrayOf((lon - lon0) * kx, (lat - lat0) * ky) }
                kept = kept.filter { t -> t.retrace || at(t.along).let { p -> near.any { gap(it, p) <= JUNCTION_RADIUS } } }
            }
            return zigzags(kept)
        }

        /** A ±180° change: a true reversal retraces the way it came; a hairpin bend does not. */
        private fun hairpin(s: Double): Turn {
            val before = at(s - 30); val after = at(s + 30); val inbound = at(s - 30 - ARM)
            val cross = (before[0] - inbound[0]) * (after[1] - before[1]) - (before[1] - inbound[1]) * (after[0] - before[0])
            val overlapping = (10..40 step 5).count { gap(at(s - it), at(s + it)) < 10 }
            return if (overlapping >= 6) Turn(s, 180.0, true) else Turn(s, if (cross > 0) -150.0 else 150.0, false)
        }

        private fun zigzags(turns: List<Turn>): List<RouteManeuver> {
            val out = ArrayList<RouteManeuver>()
            var i = 0
            fun switchback(t: Turn) = !t.retrace && abs(t.angle) >= 100
            while (i < turns.size) {
                var j = i
                while (j + 1 < turns.size && switchback(turns[j]) && switchback(turns[j + 1]) &&
                    sign(turns[j].angle) != sign(turns[j + 1].angle) && turns[j + 1].along - turns[j].along < 80) j++
                if (j - i >= 2) {
                    out += RouteManeuver(turns[i].along, TurnKind.ZIGZAG, turns[i].angle, turns[j].along - turns[i].along)
                    i = j + 1
                } else {
                    val t = turns[i]
                    out += RouteManeuver(t.along, kind(t.angle, t.retrace), t.angle)
                    i++
                }
            }
            return out
        }
    }

    private fun normalize(degrees: Double) = ((degrees + 540) % 360) - 180
}
