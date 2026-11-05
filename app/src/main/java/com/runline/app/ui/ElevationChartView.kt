package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.runline.app.R
import com.runline.app.core.ObservedElevationTrace
import com.runline.app.core.ReplayTerrain
import com.runline.app.core.RunReplay
import com.runline.app.core.TerrainDirection
import kotlin.math.abs
import kotlin.math.roundToInt

/** Scrubbing shares replay's clock. Vertical gestures remain the sheet's scroll. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed only in code with a recording.
internal class ElevationChartView(
    context: Context,
    private val replay: RunReplay,
    private val terrain: ReplayTerrain,
    initialPositionMs: Long,
    private val onPosition: (Long) -> Unit,
) : LinearLayout(context) {
    private val trace = ObservedElevationTrace.fromReplay(replay)
    private val heightOrigin = trace.sections.firstOrNull()?.firstOrNull()?.heightMeters
    private val readout = text(14)
    private val description = text(12).apply { setTextColor(context.getColor(R.color.on_surface_variant)) }
    private val chart = Plot(context)
    private val slider = SeekBar(context).apply {
        max = 10_000; minimumHeight = dp(48)
        isEnabled = replay.durationMs > 0 && heightOrigin != null
        keyProgressIncrement = 100
    }
    private var selected = 0L
    private val locale get() = resources.configuration.locales[0]
    init {
        orientation = VERTICAL
        addView(readout, LayoutParams(-1, -2))
        addView(description, LayoutParams(-1, -2).apply { topMargin = dp(6) })
        addView(chart, LayoutParams(-1, dp(170)).apply { topMargin = dp(8) })
        addView(slider, LayoutParams(-1, dp(48)))
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) select((progress.toDouble() / bar.max * replay.durationMs).toLong(), true)
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        select(initialPositionMs, false)
    }
    private fun select(position: Long, fromUser: Boolean) {
        selected = position.coerceIn(0, replay.durationMs)
        val frame = replay.at(selected)
        val sample = frame?.let { trace.at(selected, it.segmentIndex) }
        val height = sample?.heightMeters?.let { value -> heightOrigin?.let { value - it } }
        readout.text = context.getString(R.string.elevation_selected, duration(selected),
            frame?.distanceMeters?.let { String.format(locale, "%.2f", it / 1000) } ?: "—",
            height?.let { String.format(locale, "%+.0f", it) } ?: "—")
        val estimate = frame?.let { terrain.at(selected, it.segmentIndex) }
        val name = context.getString(when (estimate?.direction) {
            TerrainDirection.UPHILL -> R.string.map_v9_uphill
            TerrainDirection.DOWNHILL -> R.string.map_v9_downhill
            TerrainDirection.FLAT -> R.string.map_v9_flat
            else -> R.string.map_v9_terrain_unknown
        })
        val grade = estimate?.gradePercent?.let { context.getString(R.string.island_grade_value, String.format(locale, "%+d", it.roundToInt())) }
        description.text = if (grade == null) name else context.getString(R.string.elevation_selected_terrain, name, grade)
        slider.progress = if (replay.durationMs > 0) (selected.toDouble() / replay.durationMs * slider.max).roundToInt() else 0
        slider.contentDescription = "${context.getString(R.string.replay_elevation)}. ${readout.text}. ${description.text}. ${context.getString(R.string.elevation_explore)}"
        chart.invalidate()
        if (fromUser) onPosition(selected)
    }
    private inner class Plot(context: Context) : View(context) {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val low = trace.sections.flatten().minOfOrNull { it.heightMeters }
        private val high = trace.sections.flatten().maxOfOrNull { it.heightMeters }
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val pad get() = dp(8).toFloat()
        private val top get() = dp(28).toFloat()
        private val bottom get() = height - dp(28).toFloat()
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        private fun x(position: Long) = pad + if (replay.durationMs > 0) (position.toDouble() / replay.durationMs * (width - 2 * pad)).toFloat() else 0f
        private fun y(height: Double) = bottom - ((height - (low ?: height)) / ((high ?: height) - (low ?: height)).coerceAtLeast(1.0) * (bottom - top)).toFloat()
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            path.reset()
            trace.sections.forEach { section -> section.forEachIndexed { index, sample ->
                if (index == 0) path.moveTo(x(sample.positionMs), y(sample.heightMeters))
                else path.lineTo(x(sample.positionMs), y(sample.heightMeters))
            } }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            ink.style = Paint.Style.FILL; ink.textSize = 12 * resources.displayMetrics.scaledDensity
            ink.color = context.getColor(R.color.on_surface_variant)
            if (low == null || high == null || heightOrigin == null) {
                ink.textAlign = Paint.Align.CENTER
                canvas.drawText(context.getString(R.string.replay_no_elevation), width / 2f, height / 2f, ink)
                return
            }
            ink.textAlign = Paint.Align.LEFT
            canvas.drawText(context.getString(R.string.replay_metres, String.format(locale, "%+.0f", high - heightOrigin)), pad, top - dp(8), ink)
            canvas.drawText(context.getString(R.string.replay_metres, String.format(locale, "%+.0f", low - heightOrigin)), pad, height - dp(6).toFloat(), ink)
            ink.textAlign = Paint.Align.RIGHT
            canvas.drawText(duration(replay.durationMs), width - pad, height - dp(6).toFloat(), ink)
            ink.color = context.getColor(R.color.outline); ink.strokeWidth = resources.displayMetrics.density
            canvas.drawLine(x(selected), top, x(selected), bottom, ink)
            ink.style = Paint.Style.STROKE; ink.strokeWidth = dp(2).toFloat()
            ink.strokeJoin = Paint.Join.ROUND; ink.strokeCap = Paint.Cap.ROUND
            ink.color = context.getColor(R.color.primary); canvas.drawPath(path, ink)
            val frame = replay.at(selected)
            frame?.let { trace.at(selected, it.segmentIndex) }?.let {
                ink.style = Paint.Style.FILL
                ink.color = context.getColor(R.color.surface)
                canvas.drawCircle(x(selected), y(it.heightMeters), dp(6).toFloat(), ink)
                ink.color = context.getColor(R.color.primary)
                canvas.drawCircle(x(selected), y(it.heightMeters), dp(4).toFloat(), ink)
            }
        }
        private fun selectX(x: Float) {
            val fraction = ((x - pad) / (width - 2 * pad).coerceAtLeast(1f)).coerceIn(0f, 1f)
            select((fraction * replay.durationMs).toLong(), true)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!slider.isEnabled) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y; dragging = false
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        val dx = abs(event.x - downX); val dy = abs(event.y - downY)
                        if (dy > slop && dy > dx) { parent.requestDisallowInterceptTouchEvent(false); return false }
                        if (dx > slop) dragging = true
                    }
                    if (dragging) selectX(event.x)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    selectX(event.x); dragging = false; parent.requestDisallowInterceptTouchEvent(false)
                    performClick(); return true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    dragging = false; parent.requestDisallowInterceptTouchEvent(false); return false
                }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }
    private fun text(size: Int) = TextView(context).apply {
        textSize = size.toFloat(); includeFontPadding = false; fontFeatureSettings = "tnum"
        setTextColor(context.getColor(R.color.on_surface))
    }
    private fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) String.format(locale, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
        else String.format(locale, "%02d:%02d", s / 60, s % 60)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
