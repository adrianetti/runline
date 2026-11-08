package com.runline.app.core

import kotlin.math.*

enum class TerrainSource { GPS, TERRAIN_MAP, UNKNOWN }

/** A kilometre-aligned area, never a route or timestamp sent to the provider. */
data class TerrainTileKey(val eastKm: Int, val northKm: Int) {
    val name get() = "mdt05-v1-$eastKm-$northKm"
    val west get() = eastKm * 1000.0 - 50
    val south get() = northKm * 1000.0 - 50
    fun url() = "https://servicios.idee.es/wcs-inspire/mdt?service=WCS&version=2.0.1&request=GetCoverage&coverageId=Elevacion25830_5&format=application%2Fasc&subset=x(${west.toInt()},${(west + 1100).toInt()})&subset=y(${south.toInt()},${(south + 1100).toInt()})"
    companion object {
        fun at(point: RunPoint): TerrainTileKey? = SpainTerrainProjection.project(point.latitude, point.longitude)?.let {
            TerrainTileKey(floor(it.first / 1000).toInt(), floor(it.second / 1000).toInt())
        }
    }
}

/** ETRS89 / UTM 30N (GRS80). Initial coverage: mainland Spain and Balearic Islands. */
object SpainTerrainProjection {
    fun project(latitude: Double, longitude: Double): Pair<Double, Double>? {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in 35.7..43.9 || longitude !in -9.6..4.5) return null
        val phi = Math.toRadians(latitude); val lambda = Math.toRadians(longitude + 3)
        val a = 6378137.0; val e2 = 0.006694380022900787; val ep2 = e2 / (1 - e2); val k = .9996
        val n = a / sqrt(1 - e2 * sin(phi).pow(2)); val t = tan(phi).pow(2); val c = ep2 * cos(phi).pow(2); val aa = cos(phi) * lambda
        val m = a * ((1 - e2 / 4 - 3 * e2.pow(2) / 64 - 5 * e2.pow(3) / 256) * phi -
            (3 * e2 / 8 + 3 * e2.pow(2) / 32 + 45 * e2.pow(3) / 1024) * sin(2 * phi) +
            (15 * e2.pow(2) / 256 + 45 * e2.pow(3) / 1024) * sin(4 * phi) - 35 * e2.pow(3) / 3072 * sin(6 * phi))
        return (500000 + k * n * (aa + (1 - t + c) * aa.pow(3) / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ep2) * aa.pow(5) / 120)) to
            (k * (m + n * tan(phi) * (aa * aa / 2 + (5 - t + 9 * c + 4 * c * c) * aa.pow(4) / 24 +
                (61 - 58 * t + t * t + 600 * c - 330 * ep2) * aa.pow(6) / 720)))
    }
}

/** Strict, bounded Arc/Info ASCII reader; supports IGN's multipart response without an XML parser. */
class TerrainGrid private constructor(val columns: Int, val rows: Int, val west: Double, val south: Double,
                                      val cell: Double, private val heights: FloatArray) {
    fun at(east: Double, north: Double): Double? {
        val x = (east - west) / cell - .5; val y = rows - (north - south) / cell - .5
        if (!x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x >= columns - 1 || y >= rows - 1) return null
        val ix = floor(x).toInt(); val iy = floor(y).toInt(); val fx = x - ix; val fy = y - iy
        val a = heights[iy * columns + ix]; val b = heights[iy * columns + ix + 1]
        val c = heights[(iy + 1) * columns + ix]; val d = heights[(iy + 1) * columns + ix + 1]
        if (listOf(a, b, c, d).any { !it.isFinite() }) return null
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    }
    fun matches(key: TerrainTileKey) = columns == 220 && rows == 220 && abs(west - key.west) < .01 && abs(south - key.south) < .01 && cell == 5.0
    companion object {
        const val MAX_BYTES = 1_500_000
        fun parse(text: String): TerrainGrid {
            require(text.length <= MAX_BYTES)
            val lines = text.lineSequence().iterator()
            var first: String? = null
            while (lines.hasNext()) { val line = lines.next().trim(); if (line.startsWith("ncols", true)) { first = line; break } }
            fun header(line: String, name: String): Double {
                val parts = line.trim().split(Regex("\\s+")); require(parts.size == 2 && parts[0].equals(name, true))
                return parts[1].toDouble().also { require(it.isFinite()) }
            }
            val colsValue = header(requireNotNull(first), "ncols"); val rowsValue = header(lines.next(), "nrows")
            require(colsValue % 1 == 0.0 && rowsValue % 1 == 0.0 && colsValue in 2.0..512.0 && rowsValue in 2.0..512.0)
            val cols = colsValue.toInt(); val rows = rowsValue.toInt(); require(cols * rows <= 262144)
            val west = header(lines.next(), "xllcorner"); val south = header(lines.next(), "yllcorner")
            val cell = header(lines.next(), "cellsize"); require(cell == 5.0)
            var line = lines.next().trim(); var noData: Double? = null
            if (line.startsWith("NODATA_value", true)) { noData = header(line, "NODATA_value"); line = lines.next().trim() }
            val heights = FloatArray(cols * rows); var index = 0
            repeat(rows) { row ->
                if (row > 0) line = lines.next().trim()
                val tokens = line.split(Regex("\\s+")); require(tokens.size == cols)
                tokens.forEach { token ->
                    val value = token.toDouble(); require(value.isFinite())
                    heights[index++] = if (value == noData || value !in -500.0..9000.0) Float.NaN else value.toFloat()
                }
            }
            return TerrainGrid(cols, rows, west, south, cell, heights)
        }
    }
}

/**
 * The ground a fix may stand on: its centre and eight probes at the GPS uncertainty radius.
 * Heights spread over more than 3 m there are ambiguous at a bridge, wall or embankment,
 * but not on an even hillside, where a path cut across the slope always has that spread.
 */
object TerrainFootprint {
    /** Probes may stray this far from the best plane through them, or a tenth of the radius. */
    const val PLANE_METERS = 1.0
    /** No steeper than this: open hillside, not a cliff or a wall. */
    const val HILLSIDE_GRADE = .7
    /** How far up and down the slope the ground is checked to keep falling. */
    const val REACH_METERS = 20.0
    /** Over that reach the ground keeps at least this share of the local slope. */
    const val SUSTAINED = .5

    /** Metres (east, north): the centre first, then four sides and four diagonals. */
    fun offsets(radius: Double): List<Pair<Double, Double>> = listOf(0.0 to 0.0, radius to 0.0, -radius to 0.0, 0.0 to radius, 0.0 to -radius,
        radius * .707 to radius * .707, -radius * .707 to radius * .707, radius * .707 to -radius * .707, -radius * .707 to -radius * .707)

    /**
     * True when the footprint is an even hillside: one tilted plane explains every height
     * within [PLANE_METERS] (more for a wide footprint, which spans more of the slope's natural
     * curve), the plane is no steeper than [HILLSIDE_GRADE], and the ground
     * [REACH_METERS] up and down the slope keeps falling. The bank of a bridge, underpass or
     * embankment is a step a few metres high that flattens out on both sides, so it stays
     * ambiguous. On a hillside the height at the fix is off by about the slope times the
     * GPS error, which is far better than no height at all.
     * [sample] gives the model height at metres (east, north) from the fix.
     */
    fun hillside(offsets: List<Pair<Double, Double>>, heights: List<Double>, sample: (Double, Double) -> Double?): Boolean {
        if (offsets.size != heights.size || offsets.size < 4 || heights.any { !it.isFinite() }) return false
        val mx = offsets.sumOf { it.first } / offsets.size; val my = offsets.sumOf { it.second } / offsets.size
        val mz = heights.average()
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0; var sxz = 0.0; var syz = 0.0
        offsets.forEachIndexed { i, (x0, y0) ->
            val x = x0 - mx; val y = y0 - my; val z = heights[i] - mz
            sxx += x * x; syy += y * y; sxy += x * y; sxz += x * z; syz += y * z
        }
        val det = sxx * syy - sxy * sxy
        if (det <= 1e-9) return false
        val east = (sxz * syy - syz * sxy) / det; val north = (syz * sxx - sxz * sxy) / det
        val grade = hypot(east, north)
        if (grade < 1e-3 || grade > HILLSIDE_GRADE) return false
        val tolerance = maxOf(PLANE_METERS, offsets.maxOf { hypot(it.first, it.second) } * .1)
        if (offsets.indices.any { i -> abs(heights[i] - (mz + east * (offsets[i].first - mx) + north * (offsets[i].second - my))) > tolerance }) return false
        val up = sample(east / grade * REACH_METERS, north / grade * REACH_METERS)?.takeIf { it.isFinite() } ?: return false
        val down = sample(-east / grade * REACH_METERS, -north / grade * REACH_METERS)?.takeIf { it.isFinite() } ?: return false
        return up - down >= SUSTAINED * grade * 2 * REACH_METERS
    }
}

/** Immutable map snapshot. Missing/ambiguous cells stay missing; GPS heights are never substituted. */
class TerrainMapSnapshot(val revision: Long, val enabled: Boolean, private val grids: Map<TerrainTileKey, TerrainGrid>,
                         private val world: Map<WorldTileKey, WorldTerrainGrid> = emptyMap()) {
    val hasTiles get() = grids.isNotEmpty() || world.isNotEmpty()
    fun point(original: RunPoint): RunPoint {
        var height: Double? = null
        var model: TerrainModel? = null
        if (enabled && original.accuracyMeters.isFinite() && original.accuracyMeters in 0f..20f) {
            val xy = SpainTerrainProjection.project(original.latitude, original.longitude)
            val grid = TerrainTileKey.at(original)?.let(grids::get)
            if (xy != null && grid != null) {
                val radius = original.accuracyMeters.toDouble().coerceAtLeast(5.0)
                val offsets = TerrainFootprint.offsets(radius)
                val values = offsets.map { (dx, dy) -> grid.at(xy.first + dx, xy.second + dy) }
                val known = values.filterNotNull()
                val spread = if (known.size >= 2) known.max() - known.min() else 0.0
                // Reject steep edges/ambiguous location footprints, not a park-specific grade.
                // An even hillside is not ambiguous: one tilted plane explains it.
                if (values.all { it != null } && (spread <= 3.0 || TerrainFootprint.hillside(offsets, known) { dx, dy -> grid.at(xy.first + dx, xy.second + dy) }))
                    { height = values.first(); model = TerrainModel.IGN_MDT05 }
                // A bridge deck, the road below it, an underpass ramp or a wall share this
                // footprint. Coarser global data would only blur them and mix references.
                else if (values.first() != null && spread > 3.0)
                    return original.copy(altitudeMeters = null, verticalAccuracyMeters = null, terrainModel = TerrainModel.STRUCTURE)
            }
            if (height == null) {
                val radius = original.accuracyMeters.toDouble().coerceAtLeast(5.0)
                // All uncertainty probes use the center's resolution, including at zoom-band boundaries.
                val zoom = WorldTileKey.zoom(original.latitude)
                val dLat = radius / 111195.08; val dLon = dLat / cos(Math.toRadians(original.latitude))
                // The same footprint in metres (east, north), so the hillside test reads it alike.
                fun sample(east: Double, north: Double) =
                    sampleWorld(world, original.latitude + north / radius * dLat, ((original.longitude + east / radius * dLon + 540) % 360) - 180, zoom)
                val offsets = TerrainFootprint.offsets(radius)
                val values = offsets.map { (east, north) -> sample(east, north) }
                val known = values.filterNotNull()
                if (values.all { it != null } && (known.max() - known.min() <= 3.0 || TerrainFootprint.hillside(offsets, known, ::sample)))
                    { height = values.first(); model = TerrainModel.MAPZEN }
            }
        }
        return original.copy(altitudeMeters = height, verticalAccuracyMeters = null, terrainModel = model)
    }

    /** The detailed model at the fix itself, without the footprint check. Structure levels only. */
    fun ground(point: RunPoint): Double? {
        if (!enabled) return null
        val xy = SpainTerrainProjection.project(point.latitude, point.longitude) ?: return null
        return TerrainTileKey.at(point)?.let(grids::get)?.at(xy.first, xy.second)
    }

    fun project(run: RunSession) = run.copy(points = TerrainStructureGuard.resolve(TerrainStructureGuard.apply(run.points.map(::point)), ::ground),
        elevationSource = TerrainSource.TERRAIN_MAP)
}

/**
 * Heights next to an ambiguous structure may describe its deck, the road below or the
 * slope between, so [apply] hides them for a short distance on both sides, and [resolve]
 * then finds the runner's level from the clean heights around them. Display and
 * analysis only; recorded GPS observations are never changed.
 */
object TerrainStructureGuard {
    const val MARGIN_METERS = 15.0
    /** Longer hidden stretches stay unknown: a straight join would be a guess. */
    const val MAX_LEVEL_SPAN_METERS = 250.0
    /** Steeper than this over [GRADE_WINDOW_METERS], the model is not the surface underfoot. */
    const val RUNNABLE_GRADE = .15
    const val GRADE_WINDOW_METERS = 10.0
    const val MAX_JOIN_GRADE = .30
    /** A model dip or hump beyond this, below or above the join, names the level. */
    const val LEVEL_METERS = 1.5

    private class Continuity(points: List<RunPoint>) {
        val along = DoubleArray(points.size)
        val group = IntArray(points.size)
        init {
            for (index in 1 until points.size) {
                val joined = MarkerMotion.canInterpolate(points[index - 1], points[index])
                group[index] = if (joined) group[index - 1] else group[index - 1] + 1
                along[index] = if (joined) along[index - 1] + RunMetrics.distanceMeters(listOf(points[index - 1], points[index])) else 0.0
            }
        }
    }

    fun apply(points: List<RunPoint>): List<RunPoint> {
        if (points.none { it.terrainModel == TerrainModel.STRUCTURE }) return points
        val continuity = Continuity(points)
        val along = continuity.along
        val group = continuity.group
        val hidden = BooleanArray(points.size)
        points.forEachIndexed { index, point ->
            if (point.terrainModel != TerrainModel.STRUCTURE) return@forEachIndexed
            hidden[index] = true
            var before = index - 1
            while (before >= 0 && group[before] == group[index] && along[index] - along[before] <= MARGIN_METERS) hidden[before--] = true
            var after = index + 1
            while (after < points.size && group[after] == group[index] && along[after] - along[index] <= MARGIN_METERS) hidden[after++] = true
        }
        return points.mapIndexed { index, point ->
            if (hidden[index] && point.terrainModel != TerrainModel.STRUCTURE)
                point.copy(altitudeMeters = null, verticalAccuracyMeters = null, terrainModel = TerrainModel.STRUCTURE)
            else point
        }
    }

    /**
     * Gives each hidden stretch from [apply] a level, using clean detailed heights on both
     * sides. A runner's road never jumps: where the model's own heights through the
     * stretch stay runnable, they are the surface (a ramp beside a wall, a road under a
     * viaduct). Otherwise the model holds a different level than the runner's: lower
     * ground under a deck ([TerrainLevel.ABOVE]) or an embankment over a passage
     * ([TerrainLevel.BELOW]), and the runner's height is the join between the clean sides.
     * Stretches without clean heights on both sides stay unknown. Display and analysis only.
     */
    fun resolve(points: List<RunPoint>, ground: (RunPoint) -> Double?): List<RunPoint> {
        if (points.none { it.terrainModel == TerrainModel.STRUCTURE }) return points
        val continuity = Continuity(points)
        val result = points.toMutableList()
        var start = 0
        while (start < points.size) {
            if (points[start].terrainModel != TerrainModel.STRUCTURE) { start++; continue }
            var end = start
            while (end + 1 < points.size && points[end + 1].terrainModel == TerrainModel.STRUCTURE &&
                continuity.group[end + 1] == continuity.group[start]) end++
            level(points, result, start, end, continuity, ground)
            start = end + 1
        }
        return result
    }

    private fun level(points: List<RunPoint>, result: MutableList<RunPoint>, start: Int, end: Int, continuity: Continuity, ground: (RunPoint) -> Double?) {
        val before = start - 1; val after = end + 1
        if (before < 0 || after >= points.size) return
        val group = continuity.group; val along = continuity.along
        if (group[before] != group[start] || group[after] != group[start]) return
        if (points[before].terrainModel != TerrainModel.IGN_MDT05 || points[after].terrainModel != TerrainModel.IGN_MDT05) return
        val low = points[before].altitudeMeters?.takeIf { it.isFinite() } ?: return
        val high = points[after].altitudeMeters?.takeIf { it.isFinite() } ?: return
        val span = along[after] - along[before]
        if (!(span > 0.0 && span <= MAX_LEVEL_SPAN_METERS)) return
        fun join(index: Int) = low + (high - low) * (along[index] - along[before]) / span
        val model = (start..end).map { ground(points[it])?.takeIf(Double::isFinite) }
        val runnable = model.all { it != null } && runnable(
            listOf(along[before] to low) + (start..end).map { along[it] to model[it - start]!! } + (along[after] to high))
        if (runnable) {
            for (index in start..end) result[index] = points[index].copy(altitudeMeters = model[index - start],
                verticalAccuracyMeters = null, terrainModel = TerrainModel.IGN_MDT05, terrainLevel = TerrainLevel.GROUND)
            return
        }
        if (abs(high - low) / span > MAX_JOIN_GRADE) return
        val offset = (start..end).mapNotNull { index -> model[index - start]?.minus(join(index)) }.maxByOrNull(::abs) ?: 0.0
        val level = when { offset < -LEVEL_METERS -> TerrainLevel.ABOVE; offset > LEVEL_METERS -> TerrainLevel.BELOW; else -> TerrainLevel.GROUND }
        for (index in start..end) result[index] = points[index].copy(altitudeMeters = join(index),
            verticalAccuracyMeters = null, terrainModel = TerrainModel.IGN_MDT05, terrainLevel = level)
    }

    private fun runnable(profile: List<Pair<Double, Double>>): Boolean {
        var end = 0
        for (from in profile.indices) {
            if (end <= from) end = from + 1
            while (end < profile.size && profile[end].first - profile[from].first < GRADE_WINDOW_METERS) end++
            if (end >= profile.size) return true
            if (abs(profile[end].second - profile[from].second) / (profile[end].first - profile[from].first) > RUNNABLE_GRADE) return false
        }
        return true
    }

    /** Causal form for live coaching: only the distance after a structure can be hidden. */
    class Live {
        private var previous: RunPoint? = null
        private var traveled = 0.0
        private var hiddenUntil = Double.NEGATIVE_INFINITY
        fun reset() { previous = null; traveled = 0.0; hiddenUntil = Double.NEGATIVE_INFINITY }
        fun apply(projected: RunPoint): RunPoint {
            val prior = previous
            if (prior == null || !MarkerMotion.canInterpolate(prior, projected)) { traveled = 0.0; hiddenUntil = Double.NEGATIVE_INFINITY }
            else traveled += RunMetrics.distanceMeters(listOf(prior, projected))
            previous = projected
            if (projected.terrainModel == TerrainModel.STRUCTURE) { hiddenUntil = traveled + MARGIN_METERS; return projected }
            return if (traveled <= hiddenUntil) projected.copy(altitudeMeters = null, verticalAccuracyMeters = null, terrainModel = TerrainModel.STRUCTURE)
                else projected
        }
    }
}

/** Retains point identity and only projects appended fixes until the map snapshot changes. */
class TerrainProjector {
    private var revision = -1L; private var id: String? = null; private var originals = emptyList<RunPoint>()
    private var derived = emptyList<RunPoint>()
    private var guarded = emptyList<RunPoint>()
    fun project(run: RunSession, map: TerrainMapSnapshot): RunSession {
        val reusable = id == run.id && revision == map.revision && run.points.size >= originals.size &&
            (originals.isEmpty() || run.points[originals.lastIndex] === originals.last())
        val unchanged = reusable && run.points.size == originals.size
        derived = if (unchanged) derived
            else if (reusable) derived + run.points.drop(originals.size).map(map::point) else run.points.map(map::point)
        if (!unchanged) guarded = TerrainStructureGuard.resolve(TerrainStructureGuard.apply(derived), map::ground)
        id = run.id; revision = map.revision; originals = run.points
        return run.copy(points = guarded, elevationSource = TerrainSource.TERRAIN_MAP)
    }
}

/** Never send uncorroborated GPS elevation to an AI coach as terrain evidence. */
fun RunSession.withTrustedElevation(): RunSession = if (elevationSource == TerrainSource.TERRAIN_MAP) this
    else copy(points = points.map { it.copy(altitudeMeters = null, verticalAccuracyMeters = null, terrainModel = null) }, elevationSource = TerrainSource.UNKNOWN)
