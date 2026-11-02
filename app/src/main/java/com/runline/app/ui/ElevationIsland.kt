package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import com.runline.app.R
import com.runline.app.core.ElevationTrace
import com.runline.app.core.RunPoint
import kotlin.math.roundToInt

/** Quiet, truthful terrain context behind the numbers. No height samples means no trace. */
internal class ElevationIsland(context: Context) : LinearLayout(context) {
    var maxHeight = Int.MAX_VALUE
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Path()
    private val area = Path()
    private var source: List<RunPoint>? = null
    private var trace = ElevationTrace(emptyList(), 0)

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(context.getColor(R.color.surface)); cornerRadius = dp(24).toFloat()
        }
        elevation = dp(3).toFloat()
        clipToOutline = true
        setWillNotDraw(false)
    }

    fun setPoints(points: List<RunPoint>) {
        if (points === source) return
        source = points
        trace = ElevationTrace.from(points)
        rebuild()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
            minOf(MeasureSpec.getSize(heightMeasureSpec), maxHeight), MeasureSpec.AT_MOST))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        line.reset(); area.reset()
        val heights = trace.sections.flatMap { section -> section.map { it.heightMeters } }
        val low = heights.minOrNull()
        val high = heights.maxOrNull()
        if (width <= 0 || height <= 0 || low == null || high == null || trace.durationMs <= 0) { invalidate(); return }
        val baseline = (height * .70f).coerceAtLeast(dp(32).toFloat())
        val profileHeight = minOf(dp(88).toFloat(), height * .32f)
        val range = (high - low).coerceAtLeast(3.0)
        trace.sections.forEach { section ->
            val sectionArea = Path()
            var firstX = 0f
            var lastX = 0f
            section.forEachIndexed { index, sample ->
                val x = (sample.positionMs.toDouble() / trace.durationMs * width).toFloat()
                val y = baseline - ((sample.heightMeters - low) / range * profileHeight).toFloat()
                if (index == 0) { firstX = x; line.moveTo(x, y); sectionArea.moveTo(x, y) }
                else { line.lineTo(x, y); sectionArea.lineTo(x, y) }
                lastX = x
            }
            sectionArea.lineTo(lastX, baseline + dp(8)); sectionArea.lineTo(firstX, baseline + dp(8)); sectionArea.close()
            area.addPath(sectionArea)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        ink.color = context.getColor(R.color.on_surface)
        ink.style = Paint.Style.FILL; ink.alpha = 10
        canvas.drawPath(area, ink)
        ink.style = Paint.Style.STROKE; ink.alpha = 27; ink.strokeWidth = resources.displayMetrics.density
        ink.strokeJoin = Paint.Join.ROUND; ink.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(line, ink)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
