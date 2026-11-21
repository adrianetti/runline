package com.runline.app.core

data class SlopeProfile(
    val heights: List<Double?>,
    val spanMeters: Double? = null,
) {
    fun drawRange(): Double {
        val valid = heights.filterNotNull()
        val observed = if (valid.isEmpty()) 0.0 else valid.max() - valid.min()
        return maxOf(3.0, observed, (spanMeters ?: 0.0) * MAX_DRAWN_GRADE)
    }

    companion object {
        const val WINDOW_MS = 45_000L
        const val COUNT = 32
        const val MAX_DRAWN_GRADE = .12
        val EMPTY = SlopeProfile(List(COUNT) { null })

        fun at(
            trace: ObservedElevationTrace,
            positionMs: Long,
            segment: Int,
        ): SlopeProfile {
            var smoothed: Double? = null
            var first: Long? = null
            var last: Long? = null
            val values =
                (0 until COUNT).map { index ->
                    val at = positionMs - WINDOW_MS + WINDOW_MS * index / (COUNT - 1)
                    val height = if (at < 0) null else trace.at(at, segment)?.heightMeters
                    if (height != null) {
                        if (first == null) first = at
                        last = at
                    }
                    smoothed = if (height == null) null else smoothed?.let { it + (height - it) * .55 } ?: height
                    smoothed
                }
            val span =
                first?.let { start ->
                    last?.let { end ->
                        val a = trace.distanceAt(start, segment)
                        val b = trace.distanceAt(end, segment)
                        if (a != null && b != null) b - a else null
                    }
                }
            return SlopeProfile(values, span)
        }
    }
}
