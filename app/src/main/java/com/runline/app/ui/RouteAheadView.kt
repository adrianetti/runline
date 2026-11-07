package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.runline.app.R
import com.runline.app.core.HikingRoute
import com.runline.app.core.RouteClimbs
import kotlin.math.roundToInt

/**
 * The route's height from 300 m behind you to 2 km ahead, with your position marked. Ground
 * already walked is drawn lighter. Never squashed below a 40 m range, so gentle ground stays flat.
 */
class RouteAheadView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * density; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.on_surface)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = context.getColor(R.color.surface_variant) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var route: HikingRoute? = null
    private var done = Double.NaN
    private var from = 0.0
    private var to = 0.0
    private var heights: DoubleArray? = null
    private var low = 0.0
    private var span = 40.0
    private val behindLine = Path(); private val aheadLine = Path(); private val area = Path()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    /** Recomputed only after moving 10 m, so drawing never runs per GPS jitter. */
    fun set(route: HikingRoute, doneMeters: Double): Boolean {
        if (route === this.route && kotlin.math.abs(doneMeters - done) < 10) return heights != null
        this.route = route; done = doneMeters
        val total = route.distanceMeters
        to = (doneMeters + AHEAD).coerceAtMost(total); from = (to - AHEAD - BEHIND).coerceAtLeast(0.0)
        to = (from + AHEAD + BEHIND).coerceAtMost(total)
        heights = if (to - from < 100) null else RouteClimbs.profile(route, from, to, STEP)
        heights?.let { h ->
            val min = h.min(); val max = h.max()
            span = maxOf(MIN_RANGE, max - min); low = (min + max) / 2 - span / 2
            contentDescription = context.getString(R.string.v20_ahead_description, min.roundToInt(), max.roundToInt())
        }
        rebuild(); invalidate()
        return heights != null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); rebuild() }

    private fun x(distance: Double) = ((distance - from) / (to - from).coerceAtLeast(1.0) * (width - 2 * pad())).toFloat() + pad()
    private fun y(height: Double) = (this.height - pad() - (height - low) / span * (this.height - 2 * pad())).toFloat()
    private fun pad() = 5f * density

    private fun rebuild() {
        behindLine.reset(); aheadLine.reset(); area.reset()
        val h = heights ?: return
        if (width <= 0 || height <= 0) return
        var behindOpen = false; var aheadOpen = false
        h.forEachIndexed { i, value ->
            val d = minOf(to, from + i * STEP); val px = x(d); val py = y(value)
            if (i == 0) { area.moveTo(px, height.toFloat()); area.lineTo(px, py) } else area.lineTo(px, py)
            if (d <= done + STEP) { if (!behindOpen) { behindLine.moveTo(px, py); behindOpen = true } else behindLine.lineTo(px, py) }
            if (d >= done - STEP) { if (!aheadOpen) { aheadLine.moveTo(px, py); aheadOpen = true } else aheadLine.lineTo(px, py) }
        }
        area.lineTo(x(to), height.toFloat()); area.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = heights ?: return
        canvas.drawPath(area, fill)
        line.alpha = 90; canvas.drawPath(behindLine, line)
        line.alpha = 255; canvas.drawPath(aheadLine, line)
        val index = ((done - from) / STEP).coerceIn(0.0, (h.size - 1).toDouble())
        val k = index.toInt().coerceAtMost(h.size - 2).coerceAtLeast(0)
        val height = if (h.size < 2) h[0] else h[k] + (h[k + 1] - h[k]) * (index - k)
        dot.color = context.getColor(R.color.surface); canvas.drawCircle(x(done), y(height), 6f * density, dot)
        dot.color = context.getColor(R.color.on_surface); canvas.drawCircle(x(done), y(height), 4f * density, dot)
    }

    companion object {
        const val AHEAD = 2000.0
        const val BEHIND = 300.0
        const val STEP = 25.0
        const val MIN_RANGE = 40.0
    }
}
