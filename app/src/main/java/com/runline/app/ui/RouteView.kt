package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.runline.app.R
import com.runline.app.core.RunPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** Offline route geometry only: no downloaded map, tile service, or location transmission. */
class RouteView(context: Context) : View(context) {
    var points: List<RunPoint> = emptyList()
        set(value) { if (field === value) return; field = value; contentDescription = if (value.size < 2) context.getString(R.string.route_no_gps) else context.getString(R.string.route_samples, value.size); invalidate() }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density get() = resources.displayMetrics.density
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; contentDescription = context.getString(R.string.route_no_gps) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(context.getColor(R.color.surface_variant))
        val valid = points.filter { it.latitude.isFinite() && it.longitude.isFinite() }
        if (valid.size < 2) {
            ink.color = context.getColor(R.color.on_surface_variant)
            ink.textSize = 14 * resources.displayMetrics.scaledDensity
            ink.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.route_no_gps), width / 2f, height / 2f, ink)
            return
        }
        val latCenter = valid.map { it.latitude }.average()
        val lngOrigin = valid.first().longitude
        fun lngDelta(lng: Double): Double = ((lng - lngOrigin + 540.0) % 360.0) - 180.0
        val projected = valid.map { Pair(lngDelta(it.longitude) * cos(Math.toRadians(latCenter)), -it.latitude) }
        val minX = projected.minOf { it.first }; val maxX = projected.maxOf { it.first }
        val minY = projected.minOf { it.second }; val maxY = projected.maxOf { it.second }
        val pad = 28f * density
        val scale = min((width - 2 * pad) / max(maxX - minX, 0.00005), (height - 2 * pad) / max(maxY - minY, 0.00005))
        val offsetX = (width - (maxX - minX) * scale) / 2
        val offsetY = (height - (maxY - minY) * scale) / 2
        fun x(i: Int) = (offsetX + (projected[i].first - minX) * scale).toFloat()
        fun y(i: Int) = (offsetY + (projected[i].second - minY) * scale).toFloat()
        val path = Path()
        valid.indices.forEach { i ->
            if (i == 0 || valid[i].segment != valid[i - 1].segment) path.moveTo(x(i), y(i)) else path.lineTo(x(i), y(i))
        }
        ink.color = context.getColor(R.color.primary); ink.style = Paint.Style.STROKE
        ink.strokeWidth = 3.5f * density; ink.strokeCap = Paint.Cap.ROUND; ink.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, ink)
        ink.style = Paint.Style.FILL
        canvas.drawCircle(x(0), y(0), 5f * density, ink)
        val last = valid.lastIndex
        canvas.drawRect(x(last)-5*density, y(last)-5*density, x(last)+5*density, y(last)+5*density, ink)
    }
}
