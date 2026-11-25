package com.runline.app.core

import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan

data class RouteFrame(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

object RouteFit {
    const val MAX_LATITUDE = 85.051129
    const val MIN_ZOOM = 1.0
    const val MAX_ZOOM = 17.0
    private const val TILE_DP = 512.0
    private const val MIN_SPAN = 0.000002
    private const val CLEARANCE_DP = 96.0
    private const val MIN_USABLE_DP = 80.0
    private const val FULL_TURN = 360.0
    private const val HALF_TURN = 180.0

    fun usable(
        extentDp: Double,
        paddingDp: Double,
        overlayDp: Double,
    ): Double {
        val decoration = paddingDp - overlayDp
        return max(extentDp - paddingDp - max(CLEARANCE_DP - decoration, 0.0), MIN_USABLE_DP)
    }

    fun frame(
        points: List<RunPoint>,
        usableWidthDp: Double,
        usableHeightDp: Double,
    ): RouteFrame? {
        val valid = points.filter { it.hasValidCoordinates() && abs(it.latitude) < MAX_LATITUDE }
        if (valid.isEmpty()) return null
        val origin = valid.first().longitude
        val longitudes = valid.map { origin + ((it.longitude - origin + FULL_TURN + HALF_TURN) % FULL_TURN) - HALF_TURN }
        val rows = valid.map { mercator(it.latitude) }
        val spanX = max((longitudes.max() - longitudes.min()) / FULL_TURN, MIN_SPAN)
        val spanY = max(rows.max() - rows.min(), MIN_SPAN)
        val zoom = min(log2(usableWidthDp / TILE_DP / spanX), log2(usableHeightDp / TILE_DP / spanY))
        val longitude = ((longitudes.max() + longitudes.min()) / 2 + FULL_TURN + HALF_TURN) % FULL_TURN - HALF_TURN
        val row = (rows.max() + rows.min()) / 2
        val latitude = Math.toDegrees(atan(sinh(Math.PI * (1.0 - 2.0 * row))))
        return RouteFrame(latitude, longitude, zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
    }

    fun mercator(latitude: Double) = (1.0 - asinh(tan(Math.toRadians(latitude))) / Math.PI) / 2.0
}
