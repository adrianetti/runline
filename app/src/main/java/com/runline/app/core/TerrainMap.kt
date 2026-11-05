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
                val values = listOf(0.0 to 0.0, radius to 0.0, -radius to 0.0, 0.0 to radius, 0.0 to -radius,
                    radius * .707 to radius * .707, -radius * .707 to radius * .707,
                    radius * .707 to -radius * .707, -radius * .707 to -radius * .707)
                    .map { (dx, dy) -> grid.at(xy.first + dx, xy.second + dy) }
                // Reject steep edges/ambiguous location footprints, not a park-specific grade.
                if (values.all { it != null } && values.filterNotNull().let { it.max() - it.min() <= 3.0 }) { height = values.first(); model = TerrainModel.IGN_MDT05 }
            }
            if (height == null) {
                val radius = original.accuracyMeters.toDouble().coerceAtLeast(5.0)
                // All uncertainty probes use the center's resolution, including at zoom-band boundaries.
                val zoom = WorldTileKey.zoom(original.latitude)
                val dLat = radius / 111195.08; val dLon = dLat / cos(Math.toRadians(original.latitude))
                val values = listOf(0.0 to 0.0, dLat to 0.0, -dLat to 0.0, 0.0 to dLon, 0.0 to -dLon,
                    dLat * .707 to dLon * .707, -dLat * .707 to dLon * .707, dLat * .707 to -dLon * .707, -dLat * .707 to -dLon * .707)
                    .map { (lat, lon) -> sampleWorld(world, original.latitude + lat, ((original.longitude + lon + 540) % 360) - 180, zoom) }
                if (values.all { it != null } && values.filterNotNull().let { it.max() - it.min() <= 3.0 }) { height = values.first(); model = TerrainModel.MAPZEN }
            }
        }
        return original.copy(altitudeMeters = height, verticalAccuracyMeters = null, terrainModel = model)
    }
    fun project(run: RunSession) = run.copy(points = run.points.map(::point), elevationSource = TerrainSource.TERRAIN_MAP)
}

/** Retains point identity and only projects appended fixes until the map snapshot changes. */
class TerrainProjector {
    private var revision = -1L; private var id: String? = null; private var originals = emptyList<RunPoint>()
    private var derived = emptyList<RunPoint>()
    fun project(run: RunSession, map: TerrainMapSnapshot): RunSession {
        val reusable = id == run.id && revision == map.revision && run.points.size >= originals.size &&
            (originals.isEmpty() || run.points[originals.lastIndex] === originals.last())
        derived = if (reusable && run.points.size == originals.size) derived
            else if (reusable) derived + run.points.drop(originals.size).map(map::point) else run.points.map(map::point)
        id = run.id; revision = map.revision; originals = run.points
        return run.copy(points = derived, elevationSource = TerrainSource.TERRAIN_MAP)
    }
}

/** Never send uncorroborated GPS elevation to an AI coach as terrain evidence. */
fun RunSession.withTrustedElevation(): RunSession = if (elevationSource == TerrainSource.TERRAIN_MAP) this
    else copy(points = points.map { it.copy(altitudeMeters = null, verticalAccuracyMeters = null, terrainModel = null) }, elevationSource = TerrainSource.UNKNOWN)
