package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.runline.app.R
import com.runline.app.core.ObservedElevationTrace
import com.runline.app.core.RunPoint
import com.runline.app.core.RunReplay
import com.runline.app.core.TerrainDirection
import com.runline.app.core.TerrainEstimate
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
        low = minimum
        baseline = (height * .70f).coerceAtLeast(dp(32).toFloat())
        profileHeight = minOf(dp(88).toFloat(), height * .32f)
        range = (high - low).coerceAtLeast(3.0)
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

    private fun x(sample: ObservedElevationTrace.Sample) =
        dp(8) + (sample.positionMs.toDouble() / trace.durationMs * (width - dp(16)).coerceAtLeast(0)).toFloat()
    private fun y(sample: ObservedElevationTrace.Sample) = baseline - ((sample.heightMeters - low) / range * profileHeight).toFloat()

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

/** One quiet status line. Unknown never borrows the flat icon or a fabricated 0%. */
internal class TerrainLabel(context: Context) : AppCompatTextView(context) {
    private var shown: Pair<TerrainDirection, String>? = null
    init {
        textSize = 12f; includeFontPadding = false
        setTextColor(context.getColor(R.color.on_surface_variant))
        compoundDrawablePadding = dp(5)
        update(TerrainEstimate())
    }
    fun update(value: TerrainEstimate) {
        val direction = value.direction.takeIf { value.gradePercent?.isFinite() == true } ?: TerrainDirection.UNKNOWN
        val name = context.getString(when (direction) {
            TerrainDirection.UPHILL -> R.string.map_v9_uphill
            TerrainDirection.DOWNHILL -> R.string.map_v9_downhill
            TerrainDirection.FLAT -> R.string.map_v9_flat
            TerrainDirection.UNKNOWN -> R.string.map_v9_terrain_unknown
        })
        val amount = value.gradePercent?.takeIf { direction != TerrainDirection.UNKNOWN }?.let {
            // Whole percentages match the precision warranted by this GPS estimate.
            String.format(resources.configuration.locales[0], "%+d", it.roundToInt())
        }
        val label = if (amount == null) name else context.getString(R.string.map_v9_terrain_grade, name, amount)
        val next = direction to label
        if (shown == next) return
        shown = next; text = label
        contentDescription = if (amount == null) name else context.getString(R.string.map_v9_terrain_accessibility, name, amount)
        val icon = when (direction) {
            TerrainDirection.UPHILL -> R.drawable.ic_slope_up
            TerrainDirection.DOWNHILL -> R.drawable.ic_slope_down
            TerrainDirection.FLAT -> R.drawable.ic_flat
            TerrainDirection.UNKNOWN -> null
        }
        val drawable = icon?.let { context.getDrawable(it)!!.mutate().apply {
            setTint(context.getColor(R.color.on_surface_variant)); setBounds(0, 0, dp(18), dp(18))
        } }
        setCompoundDrawablesRelative(drawable, null, null, null)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
