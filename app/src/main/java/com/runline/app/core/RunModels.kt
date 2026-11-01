package com.runline.app.core

/** Stored independently of display language. Unknown/legacy values remain compatible. */
enum class ActivityType(val storageValue: String, val gpxType: String, val gpxName: String) {
    RUN("run", "running", "Run"),
    WALK("walk", "walking", "Walk"),
    HIKE("hike", "hiking", "Hike");

    companion object {
        fun fromStorage(value: String?): ActivityType = entries.firstOrNull {
            it.storageValue.equals(value, ignoreCase = true)
        } ?: RUN
    }
}

/** One accepted GPS fix. [segment] changes whenever recording resumes after a pause. */
data class RunPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
    val segment: Int = 0,
)

/** The original fixes and timer duration remain available for future analyses. */
data class RunSession(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val activeDurationMs: Long,
    val points: List<RunPoint>,
    val activityType: ActivityType = ActivityType.RUN,
)

/** A kilometre, or the final partial kilometre, measured from recorded GPS samples. */
data class RunSplit(
    val index: Int,
    val distanceMeters: Double,
    val durationMs: Long,
)

internal fun RunPoint.hasValidCoordinates(): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0

/**
 * Keep recording order. Pauses, invalid fixes, and clock regressions break a path.
 * A timestamp high-water mark prevents an out-of-order sample from inflating duration.
 */
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
