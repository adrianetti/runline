package com.runline.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withRotation
import com.runline.app.R
import com.runline.app.core.TerrainDirection
import com.runline.app.core.TerrainEstimate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** A continuous grade instrument. Angle is visually amplified; the number is GPS grade. */
internal class SlopeIndicator(context: Context) : LinearLayout(context) {
    private val compact = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val line = SlopeLine(context)
    private val amount = TextView(context).apply {
        textSize = if (compact) 16f else 18f; includeFontPadding = false; gravity = Gravity.CENTER
        fontFeatureSettings = "tnum"; setTextColor(context.getColor(R.color.on_surface))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val label = TextView(context).apply {
        text = context.getString(R.string.island_grade); textSize = 12f; includeFontPadding = false
        gravity = Gravity.CENTER; setTextColor(context.getColor(R.color.on_surface_variant))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(line, LayoutParams(-1, dp(if (compact) 32 else 40)))
        addView(amount, LayoutParams(-1, -2))
        addView(label, LayoutParams(-1, -2).apply { topMargin = dp(if (compact) 2 else 4) })
        update(TerrainEstimate(), false)
    }
    fun update(estimate: TerrainEstimate, animate: Boolean = true) {
        val grade = estimate.gradePercent?.takeIf { it.isFinite() && estimate.direction != TerrainDirection.UNKNOWN }
        val name = context.getString(when (estimate.direction.takeIf { grade != null }) {
            TerrainDirection.UPHILL -> R.string.map_v9_uphill
            TerrainDirection.DOWNHILL -> R.string.map_v9_downhill
            TerrainDirection.FLAT -> R.string.map_v9_flat
            else -> R.string.map_v9_terrain_unknown
        })
        val percent = grade?.roundToInt()?.let { String.format(resources.configuration.locales[0], "%+d", it) }
        val text = percent?.let { context.getString(R.string.island_grade_value, it) } ?: "—"
        if (amount.text.toString() != text) amount.text = text
        contentDescription = if (percent == null) name else context.getString(R.string.map_v9_terrain_accessibility, name, percent)
        line.setGrade(grade, animate)
    }
    fun settle() = line.settle()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

private class SlopeLine(context: Context) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private var target: Double? = null
    private var shown = 0.0
    private var lastFrame = 0L
    private val neutral = context.getColor(R.color.on_surface_variant)
    private val uphill = context.getColor(R.color.grade_uphill)
    private val steep = context.getColor(R.color.grade_steep)
    private val downhill = context.getColor(R.color.grade_downhill)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setGrade(grade: Double?, animate: Boolean) {
        if (grade == target && animate) return
        val wasKnown = target != null
        target = grade
        if (!animate || !wasKnown || grade == null || !isShown || !ValueAnimator.areAnimatorsEnabled()) settle()
        else { lastFrame = SystemClock.uptimeMillis(); invalidate() }
    }
    fun settle() { shown = target ?: 0.0; lastFrame = 0L; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val goal = target
        if (!ValueAnimator.areAnimatorsEnabled()) shown = goal ?: 0.0
        if (goal != null && abs(goal - shown) > .01) {
            val now = SystemClock.uptimeMillis()
            val delta = (now - lastFrame).coerceIn(1L, 64L)
            shown += (goal - shown) * (1.0 - exp(-delta / 90.0))
            lastFrame = now
            postInvalidateOnAnimation()
        } else shown = goal ?: 0.0
        val density = resources.displayMetrics.density
        val cy = height / 2f
        val half = minOf(width / 2f - 8 * density, (height / 2f - 2 * density) / .47f).coerceAtLeast(0f)
        ink.strokeWidth = 4 * density
        ink.color = when {
            goal == null -> neutral
            shown > 1.5 -> ColorUtils.blendARGB(uphill, steep, ((shown - 3) / 7).toFloat().coerceIn(0f, 1f))
            shown < -1.5 -> downhill
            else -> neutral
        }
        // Blend gently into directional color rather than flash at a threshold.
        if (goal != null) ink.color = ColorUtils.blendARGB(neutral, ink.color, ((abs(shown) - 1.5) / 1.5).toFloat().coerceIn(0f, 1f))
        canvas.withRotation(-(shown * 2.5).coerceIn(-28.0, 28.0).toFloat(), width / 2f, cy) {
            if (goal == null) {
                ink.alpha = 95
                for (part in 0..2) {
                    val x = width / 2f - half + part * half * 2 / 3
                    drawLine(x, cy, x + half * 2 / 3 - 6 * density, cy, ink)
                }
            } else drawLine(width / 2f - half, cy, width / 2f + half, cy, ink)
        }
        ink.alpha = 255
    }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); if (!isVisible) settle() }
    override fun onDetachedFromWindow() { settle(); super.onDetachedFromWindow() }
}
