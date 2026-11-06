package com.runline.app.core

import kotlin.math.*

/**
 * Analysis-only provenance; never persisted as an original GPS observation.
 * [STRUCTURE] carries no height: a detailed model covers the place, but a bridge,
 * underpass, embankment or wall makes the runner's level ambiguous there.
 */
enum class TerrainModel { IGN_MDT05, MAPZEN, STRUCTURE }

/** Public, versioned cache identity. Mercator cannot represent the polar caps. */
data class WorldTileKey(val zoom: Int, val x: Int, val y: Int) {
    init { require(zoom in 8..12 && x in 0 until (1 shl zoom) && y in 0 until (1 shl zoom)) }
    val name get() = "mapzen-v1-$zoom-$x-$y"
    fun url() = "https://elevation-tiles-prod.s3.amazonaws.com/terrarium/$zoom/$x/$y.png"
    companion object {
        const val MAX_LATITUDE = 85.0511287798066
        fun zoom(latitude: Double) = floor(log2(40075016.686 * cos(Math.toRadians(latitude)) / (256 * 30))).toInt().coerceIn(8, 12)
        fun pixels(latitude: Double, longitude: Double, zoom: Int): Pair<Double, Double>? {
            if (!latitude.isFinite() || !longitude.isFinite() || abs(latitude) > MAX_LATITUDE || longitude !in -180.0..180.0) return null
            val size = (1 shl zoom) * 256.0
            val lon = if (longitude == 180.0) -180.0 else longitude
            return ((lon + 180) / 360 * size) to ((1 - asinh(tan(Math.toRadians(latitude))) / PI) / 2 * size)
        }
        fun at(latitude: Double, longitude: Double, z: Int = zoom(latitude)): WorldTileKey? = pixels(latitude, longitude, z)?.let {
            WorldTileKey(z, floor(it.first / 256).toInt(), floor(it.second / 256).toInt().coerceIn(0, (1 shl z) - 1))
        }
        fun neighbor(key: WorldTileKey, dx: Int, dy: Int): WorldTileKey? {
            val size = 1 shl key.zoom; val y = key.y + dy
            return if (y in 0 until size) key.copy(x = Math.floorMod(key.x + dx, size), y = y) else null
        }
        /** Enough neighbors for the uncertainty footprint and cross-tile interpolation. */
        fun needed(point: RunPoint): Set<WorldTileKey> {
            val key = at(point.latitude, point.longitude) ?: return emptySet()
            val xy = pixels(point.latitude, point.longitude, key.zoom)!!
            val margin = 55 / (40075016.686 * cos(Math.toRadians(point.latitude)) / ((1 shl key.zoom) * 256)) + 1
            val xs = listOf(xy.first - margin, xy.first + margin).map { floor(it / 256).toInt() }.distinct()
            val ys = listOf(xy.second - margin, xy.second + margin).map { floor(it / 256).toInt() }.distinct()
            return xs.flatMap { x -> ys.mapNotNull { y -> neighbor(key, x - key.x, y - key.y) } }.toSet()
        }
    }
}

class WorldTerrainGrid private constructor(private val heights: FloatArray) {
    fun at(x: Int, y: Int) = heights[y * 256 + x].toDouble().takeIf { it.isFinite() }
    companion object {
        fun fromArgb(pixels: IntArray): WorldTerrainGrid {
            require(pixels.size == 256 * 256)
            return WorldTerrainGrid(FloatArray(pixels.size) { i ->
                val p = pixels[i]; require(p ushr 24 == 255)
                val value = ((p ushr 16 and 255) * 256 + (p ushr 8 and 255) + (p and 255) / 256.0 - 32768)
                // Nodata and bathymetry are not a runner's elevation. Below-sea-level land remains valid.
                if (value in -500.0..9000.0) value.toFloat() else Float.NaN
            })
        }
    }
}

/** Bilinear sampling uses adjacent tiles at pixel centers, including the date line. */
fun sampleWorld(grids: Map<WorldTileKey, WorldTerrainGrid>, latitude: Double, longitude: Double,
                zoom: Int = WorldTileKey.zoom(latitude)): Double? {
    val z = zoom; val xy = WorldTileKey.pixels(latitude, longitude, z) ?: return null
    val x = xy.first - .5; val y = xy.second - .5; val ix = floor(x).toInt(); val iy = floor(y).toInt()
    val size = (1 shl z) * 256
    fun pixel(px: Int, py: Int): Double? {
        if (py !in 0 until size) return null
        val wrapped = Math.floorMod(px, size)
        return grids[WorldTileKey(z, wrapped / 256, py / 256)]?.at(wrapped % 256, py % 256)
    }
    val a = pixel(ix, iy) ?: return null; val b = pixel(ix + 1, iy) ?: return null
    val c = pixel(ix, iy + 1) ?: return null; val d = pixel(ix + 1, iy + 1) ?: return null
    val fx = x - ix; val fy = y - iy
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
}

sealed class TerrainDownloadKey {
    abstract val name: String
    abstract val url: String
    abstract val maxBytes: Int
    data class Spain(val key: TerrainTileKey) : TerrainDownloadKey() {
        override val name get() = key.name + ".asc"
        override val url get() = key.url()
        override val maxBytes get() = TerrainGrid.MAX_BYTES
    }
    data class World(val key: WorldTileKey) : TerrainDownloadKey() {
        override val name get() = key.name + ".png"
        override val url get() = key.url()
        override val maxBytes get() = 512_000
    }
    companion object {
        fun forPoint(point: RunPoint): Set<TerrainDownloadKey> = buildSet {
            WorldTileKey.needed(point).forEach { add(World(it)) }
            TerrainTileKey.at(point)?.let { add(Spain(it)) }
        }
    }
}

/** One optional saved area. No route, country download or background location tracking. */
data class TerrainDownloadArea(val latitude: Double, val longitude: Double) {
    init { require(latitude.isFinite() && longitude.isFinite() && abs(latitude) < 85.0 && longitude in -180.0..180.0) }
    val keys: Set<TerrainDownloadKey> get() = buildSet {
        // Include both latitude bands when a 5 km area straddles a zoom transition.
        val span = 5000.0 / 111000
        listOf(latitude - span, latitude, latitude + span).map(WorldTileKey::zoom).distinct().forEach { zoom ->
            val center = WorldTileKey.at(latitude, longitude, zoom)!!
            for (dx in -1..1) for (dy in -1..1) WorldTileKey.neighbor(center, dx, dy)?.let { add(TerrainDownloadKey.World(it)) }
        }
        TerrainTileKey.at(RunPoint(latitude, longitude, 0, 0f))?.let { center ->
            for (dx in -1..1) for (dy in -1..1) add(TerrainDownloadKey.Spain(center.copy(eastKm = center.eastKm + dx, northKm = center.northKm + dy)))
        }
    }
    val requiredKeys get() = keys.filterIsInstance<TerrainDownloadKey.World>().toSet()
    val maxBytes get() = keys.sumOf { it.maxBytes.toLong() }
}
