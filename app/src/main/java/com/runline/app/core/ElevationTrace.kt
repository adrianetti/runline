package com.runline.app.core

/** A small display model of actual heights; breaks are retained, never filled in. */
data class ElevationTrace(val sections: List<List<Sample>>, val durationMs: Long) {
    data class Sample(val positionMs: Long, val heightMeters: Double)

    companion object {
        fun from(points: List<RunPoint>): ElevationTrace {
            val sections = mutableListOf<List<Sample>>()
            var section = mutableListOf<Sample>()
            var previous: RunPoint? = null
            var clock = 0L
            fun finish() {
                if (section.size >= 2) sections += section
                section = mutableListOf()
            }
            for (point in points) {
                val continuous = MarkerMotion.canInterpolate(previous, point)
                if (continuous) clock += point.timestampMs - previous!!.timestampMs else finish()
                if (continuous && !canJoinAltitude(previous, point)) finish()
                val height = point.altitudeMeters?.takeIf { point.hasUsableAltitude() }
                if (point.hasValidCoordinates() && point.timestampMs >= 0 &&
                    point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..30f) {
                    if (height != null) section += Sample(clock, height) else finish()
                    previous = point
                } else {
                    finish(); previous = null
                }
            }
            finish()
            return ElevationTrace(sections, clock)
        }
    }
}
