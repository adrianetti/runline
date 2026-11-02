package com.runline.app.core

/** Screen coordinates are physical pixels. Never clamp an off-map fix to the edge. */
object MarkerViewport {
    data class Placement(val left: Float, val top: Float)
    fun place(x: Float, y: Float, width: Int, height: Int, markerSize: Int): Placement? {
        if (!x.isFinite() || !y.isFinite() || width <= 0 || height <= 0 || markerSize <= 0 ||
            x < 0f || y < 0f || x > width || y > height) return null
        val half = markerSize / 2f
        return Placement(x - half, y - half)
    }
}
