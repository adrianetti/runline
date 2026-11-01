package com.runline.app.core

import kotlin.math.abs

/** Incremental elevation totals for a live display; never alters recorded GPS samples. */
class ElevationProgress {
    var climbedMeters: Double? = null
        private set
    var descendedMeters: Double? = null
        private set
    private var count = 0
    private var lastInput: RunPoint? = null
    private var previous: RunPoint? = null
    private var highWater = Long.MIN_VALUE
    private var segment: Int? = null
    private var anchor: Double? = null
    private var gain = 0.0
    private var loss = 0.0
    private var hasPair = false

    fun update(points: List<RunPoint>) {
        if (points.size < count || (count > 0 && points.getOrNull(count - 1) != lastInput)) reset()
        for (index in count until points.size) accept(points[index])
        count = points.size
        lastInput = points.lastOrNull()
        climbedMeters = gain.takeIf { hasPair }
        descendedMeters = loss.takeIf { hasPair }
    }

    private fun accept(point: RunPoint) {
        if (segment != point.segment) {
            segment = point.segment; highWater = Long.MIN_VALUE; anchor = null; previous = null
        }
        if (!point.hasValidCoordinates() || point.timestampMs < 0 || point.timestampMs <= highWater) {
            anchor = null; previous = null; return
        }
        highWater = point.timestampMs
        if (previous?.let { point.timestampMs - it.timestampMs > RunReplay.MAX_GPS_GAP_MS } == true) anchor = null
        previous = point
        val altitude = point.altitudeMeters?.takeIf { it.isFinite() }
        val prior = anchor
        if (altitude == null) anchor = null
        else if (prior == null) anchor = altitude
        else {
            hasPair = true
            val delta = altitude - prior
            if (abs(delta) >= 3.0) {
                if (delta > 0) gain += delta else loss -= delta
                anchor = altitude
            }
        }
    }
    private fun reset() {
        count = 0; lastInput = null; previous = null; segment = null; highWater = Long.MIN_VALUE
        anchor = null; gain = 0.0; loss = 0.0; hasPair = false
    }
}
