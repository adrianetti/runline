package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import androidx.core.graphics.withClip
import com.runline.app.R
import com.runline.app.core.ObservedElevationTrace
import com.runline.app.core.RunPoint
import com.runline.app.core.RunReplay
import kotlin.math.roundToInt

/** Quiet, truthful terrain context behind the numbers. No height samples means no trace. */
internal class ElevationIsland(context: Context) : LinearLayout(context) {
    var maxHeight = Int.MAX_VALUE
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Path()
    private val area = Path()
    private var source: List<RunPoint>? = null
    private var trace = ObservedElevationTrace.fromPoints(emptyList())
    private var cursor: ObservedElevationTrace.Sample? = null
    private var low = 0.0
    private var range = 3.0
    private var baseline = 0f
    private var profileHeight = 0f
    private var chartContent: View? = null
    private val chartBounds = Rect()

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            setColor(context.getColor(R.color.surface)); cornerRadius = dp(24).toFloat()
        }
        elevation = dp(3).toFloat()
        clipToOutline = true
        setWillNotDraw(false)
    }

    fun slopeProfile(positionMs: Long = trace.durationMs, segmentIndex: Int = trace.latestSegmentIndex) =
        com.runline.app.core.SlopeProfile.at(trace, positionMs, segmentIndex)

    fun setChartContent(view: View) { chartContent = view }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) rebuild()
    }

    fun setPoints(points: List<RunPoint>) {
        if (points === source) return
        source = points
        trace = ObservedElevationTrace.fromPoints(points)
        cursor = trace.latestCursor
        rebuild()
    }

    fun setReplay(replay: RunReplay) {
        source = null
        trace = ObservedElevationTrace.fromReplay(replay)
        cursor = null
        rebuild()
    }

    fun setPosition(positionMs: Long, segmentIndex: Int?) {
        val next = segmentIndex?.let { trace.at(positionMs, it) }
        if (next != cursor) { cursor = next; invalidate() }
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
        val minimum = heights.minOrNull()
        val high = heights.maxOrNull()
        if (width <= 0 || height <= 0 || minimum == null || high == null || trace.durationMs <= 0) { invalidate(); return }
        // A subtle whole-run backdrop: at least 10 m tall, centred, so flat runs stay flat.
        range = (high - minimum).coerceAtLeast(10.0)
        low = minimum + (high - minimum) / 2 - range / 2
        val bounds = Rect()
        chartContent?.let {
            it.getDrawingRect(bounds); offsetDescendantRectToMyCoords(it, bounds)
            var parent = it.parent as? View
            while (parent != null && parent !== this) {
                val clip = Rect(0, 0, parent.width, parent.height)
                offsetDescendantRectToMyCoords(parent, clip)
                if (!bounds.intersect(clip)) bounds.setEmpty()
                parent = parent.parent as? View
            }
        }
        chartBounds.set(bounds)
        baseline = if (bounds.height() > 0) bounds.bottom.toFloat() else height * .60f
        profileHeight = minOf(dp(64).toFloat(), (bounds.height().takeIf { it > 0 } ?: height) * .32f)
        trace.sections.forEach { section ->
            val sectionArea = Path()
            var firstX = 0f
            var lastX = 0f
            section.forEachIndexed { index, sample ->
                val x = x(sample)
                val y = y(sample)
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
        if (chartBounds.isEmpty) return
        canvas.withClip(0, chartBounds.top, width, chartBounds.bottom) {
        ink.color = context.getColor(R.color.on_surface)
        ink.style = Paint.Style.FILL; ink.alpha = 10
        canvas.drawPath(area, ink)
        ink.style = Paint.Style.STROKE; ink.alpha = 27; ink.strokeWidth = resources.displayMetrics.density
        ink.strokeJoin = Paint.Join.ROUND; ink.strokeCap = Paint.Cap.ROUND
        canvas.drawPath(line, ink)
        cursor?.takeIf { trace.durationMs > 0 && trace.sections.isNotEmpty() }?.let {
            ink.style = Paint.Style.FILL; ink.alpha = 255
            ink.color = context.getColor(R.color.surface)
            canvas.drawCircle(x(it), y(it), dp(5).toFloat(), ink)
            ink.color = context.getColor(R.color.on_surface); ink.alpha = 190
            canvas.drawCircle(x(it), y(it), dp(3).toFloat(), ink)
        }
        }
    }

    private fun x(sample: ObservedElevationTrace.Sample) =
        dp(8) + (sample.positionMs.toDouble() / trace.durationMs * (width - dp(16)).coerceAtLeast(0)).toFloat()
    private fun y(sample: ObservedElevationTrace.Sample) = baseline - ((sample.heightMeters - low) / range * profileHeight).toFloat()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
