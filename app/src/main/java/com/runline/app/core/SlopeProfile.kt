package com.runline.app.core

/** Fixed temporal scale, no future path or bridges over missing heights. Display only. */
data class SlopeProfile(val heights: List<Double?>) {
    companion object {
        const val WINDOW_MS = 45_000L
        const val COUNT = 32
        val EMPTY = SlopeProfile(List(COUNT) { null })
        fun at(trace: ObservedElevationTrace, positionMs: Long, segment: Int): SlopeProfile {
            var smoothed: Double? = null
            val values = (0 until COUNT).map { index ->
                val at = positionMs - WINDOW_MS + WINDOW_MS * index / (COUNT - 1)
                val height = if (at < 0) null else trace.at(at, segment)?.heightMeters
                smoothed = if (height == null) null else smoothed?.let { it + (height - it) * .55 } ?: height
                smoothed
            }
            return SlopeProfile(values)
        }
    }
}
