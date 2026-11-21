package com.runline.app.core

enum class ActivityType(
    val storageValue: String,
    val gpxType: String,
    val gpxName: String,
) {
    RUN("run", "running", "Run"),
    WALK("walk", "walking", "Walk"),
    HIKE("hike", "hiking", "Hike"),
    ;

    companion object {
        fun fromStorage(value: String?): ActivityType =
            entries.firstOrNull {
                it.storageValue.equals(value, ignoreCase = true)
            } ?: RUN
    }
}

data class RunPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val segment: Int = 0,
    val verticalAccuracyMeters: Float? = null,
    val terrainModel: TerrainModel? = null,
    val terrainLevel: TerrainLevel? = null,
)

internal fun RunPoint.hasUsableAltitude(): Boolean =
    altitudeMeters?.isFinite() == true &&
        (verticalAccuracyMeters == null || (verticalAccuracyMeters.isFinite() && verticalAccuracyMeters in 0f..8f))

internal fun canJoinAltitude(
    a: RunPoint?,
    b: RunPoint,
): Boolean =
    a != null &&
        a.terrainModel == b.terrainModel &&
        MarkerMotion.canInterpolate(a, b) && a.hasUsableAltitude() && b.hasUsableAltitude() &&
        kotlin.math.abs(b.altitudeMeters!! - a.altitudeMeters!!) <= maxOf(4.0, RunMetrics.distanceMeters(listOf(a, b)) * .5)

data class RunSession(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val activeDurationMs: Long,
    val points: List<RunPoint>,
    val activityType: ActivityType = ActivityType.RUN,
    val elevationSource: TerrainSource = TerrainSource.GPS,
)

val RunSession.activityEndedAtMs: Long get() {
    val latest = endedAtMs.coerceAtLeast(startedAtMs)
    val timerEnd = startedAtMs + activeDurationMs.coerceAtLeast(0)
    val lastFix =
        points
            .asSequence()
            .map { it.timestampMs }
            .filter { it in startedAtMs..latest }
            .maxOrNull()
    return maxOf(timerEnd, lastFix ?: timerEnd).coerceIn(startedAtMs, latest)
}

data class RunSplit(
    val index: Int,
    val distanceMeters: Double,
    val durationMs: Long,
)

internal fun RunPoint.hasValidCoordinates(): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0

internal fun continuousSegments(points: List<RunPoint>): List<List<RunPoint>> {
    val result = mutableListOf<List<RunPoint>>()
    var current = mutableListOf<RunPoint>()
    var segment: Int? = null
    var latestTimestamp = Long.MIN_VALUE

    fun finish() {
        if (current.isNotEmpty()) result.add(current)
        current = mutableListOf()
    }
    for (point in points) {
        if (point.segment != segment) {
            finish()
            segment = point.segment
            latestTimestamp = Long.MIN_VALUE
        }
        if (!point.hasValidCoordinates() || point.timestampMs < 0L || point.timestampMs <= latestTimestamp) {
            finish()
            continue
        }
        latestTimestamp = point.timestampMs
        current.add(point)
    }
    finish()
    return result
}
