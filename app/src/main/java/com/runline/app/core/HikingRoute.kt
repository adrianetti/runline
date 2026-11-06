package com.runline.app.core

import kotlin.math.*

/** One vertex of a planned route; [distanceMeters] is the path distance from its start. */
data class RoutePoint(val latitude: Double, val longitude: Double, val elevationMeters: Double?, val distanceMeters: Double)

/** A named place along a route, from the GPX file. Display only. */
data class RouteWaypoint(val latitude: Double, val longitude: Double, val name: String, val description: String? = null,
                         val elevationMeters: Double? = null)

enum class RouteDifficulty { EASY, MODERATE, HARD, VERY_HARD }

data class RouteStats(
    val distanceMeters: Double,
    val ascentMeters: Double?,
    val descentMeters: Double?,
    val minElevationMeters: Double?,
    val maxElevationMeters: Double?,
    /** Moving time at a steady hiking effort; stops are not included. */
    val estimatedMinutes: Int,
    /** Distance plus one kilometre per 100 m of ascent, a common hiking effort measure. */
    val effortKilometres: Double,
    val difficulty: RouteDifficulty,
    val loop: Boolean,
)

/** A planned path to follow. Geometry only: it is never a recording and never changes one. */
class HikingRoute(val id: String, val name: String, val points: List<RoutePoint>, val waypoints: List<RouteWaypoint> = emptyList(),
                  val description: String? = null) {
    init { require(points.size >= 2 && id.isNotBlank()) }
    val distanceMeters: Double get() = points.last().distanceMeters
    val stats: RouteStats by lazy { RouteEstimates.stats(points) }
    /** Cumulative ascent at each point (3 m deadband), for "ascent left" while following. */
    val ascentAt: DoubleArray by lazy { RouteEstimates.cumulativeAscent(points) }
    /** Steady-effort minutes from the start to each point. */
    val minutesAt: DoubleArray by lazy { RouteEstimates.cumulativeMinutes(points) }

    fun runPoints(): List<RunPoint> = points.mapIndexed { index, p ->
        RunPoint(p.latitude, p.longitude, index * 1000L, 5f, p.elevationMeters)
    }

    companion object {
        const val MAX_POINTS = 100_000

        /** Builds a route from raw vertices, dropping repeated and invalid coordinates. */
        fun of(id: String, name: String, raw: List<Triple<Double, Double, Double?>>, waypoints: List<RouteWaypoint> = emptyList(),
               description: String? = null): HikingRoute {
            require(raw.size <= MAX_POINTS)
            val points = ArrayList<RoutePoint>(raw.size)
            for ((lat, lon, ele) in raw) {
                if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                val elevation = ele?.takeIf { it.isFinite() && it in -500.0..9000.0 }
                val last = points.lastOrNull()
                if (last != null && last.latitude == lat && last.longitude == lon) continue
                val distance = if (last == null) 0.0 else last.distanceMeters + RouteGeometry.meters(last.latitude, last.longitude, lat, lon)
                points += RoutePoint(lat, lon, elevation, distance)
            }
            return HikingRoute(id, name, points, waypoints, description)
        }
    }
}

object RouteGeometry {
    private const val EARTH = 6371008.8
    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = Math.toRadians(lat1); val b = Math.toRadians(lat2)
        val h = sin((b - a) / 2).pow(2) + cos(a) * cos(b) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
        return 2 * EARTH * asin(sqrt(min(1.0, h)))
    }
    /** Initial bearing in degrees, 0 = north, clockwise. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = Math.toRadians(lat1); val b = Math.toRadians(lat2); val d = Math.toRadians(lon2 - lon1)
        val y = sin(d) * cos(b); val x = cos(a) * sin(b) - sin(a) * cos(b) * cos(d)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }
}

object RouteEstimates {
    private const val STEP_METERS = 50.0
    private const val DEADBAND_METERS = 3.0

    /** Tobler's hiking function, km/h, for a grade (rise over run). */
    fun toblerKmh(grade: Double) = 6 * exp(-3.5 * abs(grade + 0.05))

    fun difficulty(effortKilometres: Double) = when {
        effortKilometres < 10 -> RouteDifficulty.EASY
        effortKilometres < 20 -> RouteDifficulty.MODERATE
        effortKilometres < 30 -> RouteDifficulty.HARD
        else -> RouteDifficulty.VERY_HARD
    }

    fun stats(points: List<RoutePoint>): RouteStats {
        val distance = points.last().distanceMeters
        val heights = points.mapNotNull { it.elevationMeters }
        val known = heights.size >= points.size / 2 && heights.size >= 2
        var gain = 0.0; var loss = 0.0
        if (known) {
            var anchor: Double? = null
            for (h in points.mapNotNull { it.elevationMeters }) {
                val a = anchor
                if (a == null) anchor = h
                else if (abs(h - a) >= DEADBAND_METERS) { if (h > a) gain += h - a else loss += a - h; anchor = h }
            }
        }
        val minutes = cumulativeMinutes(points).last()
        val effort = distance / 1000 + (if (known) gain / 100 else 0.0)
        val first = points.first(); val last = points.last()
        return RouteStats(distance, gain.takeIf { known }, loss.takeIf { known }, heights.minOrNull()?.takeIf { known },
            heights.maxOrNull()?.takeIf { known }, minutes.roundToInt(), effort, difficulty(effort),
            RouteGeometry.meters(first.latitude, first.longitude, last.latitude, last.longitude) <= 400 && distance > 1000)
    }

    fun cumulativeAscent(points: List<RoutePoint>): DoubleArray {
        val out = DoubleArray(points.size); var anchor: Double? = null; var gain = 0.0
        points.forEachIndexed { i, p ->
            val h = p.elevationMeters
            if (h != null) {
                val a = anchor
                if (a == null) anchor = h else if (abs(h - a) >= DEADBAND_METERS) { if (h > a) gain += h - a; anchor = h }
            }
            out[i] = gain
        }
        return out
    }

    /**
     * Minutes from the start to each point. Grades come from heights resampled every 50 m,
     * so GPS or model noise between close vertices cannot inflate the estimate.
     */
    fun cumulativeMinutes(points: List<RoutePoint>): DoubleArray {
        val out = DoubleArray(points.size)
        val total = points.last().distanceMeters
        val samples = max(1, ceil(total / STEP_METERS).toInt())
        val stepMinutes = DoubleArray(samples + 1)
        var cursor = 0
        fun heightAt(d: Double): Double? {
            while (cursor + 1 < points.size && points[cursor + 1].distanceMeters < d) cursor++
            val a = points[cursor]; val b = points.getOrNull(cursor + 1) ?: return a.elevationMeters
            val ha = a.elevationMeters ?: return b.elevationMeters; val hb = b.elevationMeters ?: return ha
            val span = b.distanceMeters - a.distanceMeters
            return if (span <= 0) ha else ha + (hb - ha) * ((d - a.distanceMeters) / span).coerceIn(0.0, 1.0)
        }
        var previous = heightAt(0.0)
        for (k in 1..samples) {
            val d = min(total, k * STEP_METERS); val length = d - (k - 1) * STEP_METERS
            val h = heightAt(d)
            val grade = if (previous != null && h != null && length > 0) ((h - previous) / length).coerceIn(-0.6, 0.6) else 0.0
            stepMinutes[k] = stepMinutes[k - 1] + length / 1000 / toblerKmh(grade) * 60
            previous = h
        }
        points.forEachIndexed { i, p ->
            val position = p.distanceMeters / STEP_METERS
            val k = floor(position).toInt().coerceIn(0, samples - 1)
            out[i] = stepMinutes[k] + (stepMinutes[k + 1] - stepMinutes[k]) * (position - k).coerceIn(0.0, 1.0)
        }
        return out
    }
}
