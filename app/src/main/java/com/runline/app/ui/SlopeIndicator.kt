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
import android.graphics.Path
import com.runline.app.core.SlopeProfile
import com.runline.app.R
import com.runline.app.core.TerrainDirection
import com.runline.app.core.TerrainEstimate
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Recent observed terrain shape paired with an independently validated GPS grade. */
internal class SlopeIndicator(context: Context) : LinearLayout(context) {
    private val compact = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val line = SlopeLine(context)
    private val amount = TextView(context).apply {
        textSize = if (compact) 16f else 18f; includeFontPadding = false; gravity = Gravity.CENTER
        fontFeatureSettings = "tnum"; setTextColor(context.getColor(R.color.on_surface))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val label = TerrainLabel(context).apply {
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
    fun update(estimate: TerrainEstimate, animate: Boolean = true, profile: SlopeProfile = SlopeProfile.EMPTY) {
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
        label.setTerrain(name, animate)
        contentDescription = if (percent == null) name else context.getString(R.string.map_v9_terrain_accessibility, name, percent)
        line.setGrade(grade, profile, animate)
    }
    fun settle() { line.settle(); label.settle() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

/** One short shared-axis flip per state change, never per GPS/render frame. */
private class TerrainLabel(context: Context) : androidx.appcompat.widget.AppCompatTextView(context) {
    private var target = ""
    private var motion: ValueAnimator? = null
    init { maxLines = 1; cameraDistance = 8_000 * resources.displayMetrics.density }
    fun setTerrain(value: String, animate: Boolean) {
        if (value == target) { if (!animate) settle(); return }
        settle()
        val old = text.toString()
        target = value
        if (!animate || !isShown || !ValueAnimator.areAnimatorsEnabled() || old.isBlank()) { settle(); return }
        motion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener {
                val progress = it.animatedValue as Float
                if (progress >= .5f) text = target
                rotationX = if (progress < .5f) 180 * progress else -180 * (1 - progress)
                alpha = abs(2 * progress - 1)
            }
            start()
        }
    }
    fun settle() { motion?.cancel(); motion = null; text = target; rotationX = 0f; alpha = 1f }
    override fun onDetachedFromWindow() { settle(); super.onDetachedFromWindow() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); if (!isVisible) settle() }
}

/** Same compact footprint, now a rolling 45-second observed elevation profile. */
private class SlopeLine(context: Context) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private var targetGrade: Double? = null
    private var shownGrade = 0.0
    private var target = List<Float?>(SlopeProfile.COUNT) { null }
    private var shown = target
    private var lastFrame = 0L
    private val neutral = context.getColor(R.color.on_surface_variant)
    private val uphill = context.getColor(R.color.grade_uphill)
    private val steep = context.getColor(R.color.grade_steep)
    private val downhill = context.getColor(R.color.grade_downhill)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setGrade(grade: Double?, profile: SlopeProfile, animate: Boolean) {
        val valid = profile.heights.filterNotNull()
        val low = valid.minOrNull() ?: 0.0
        val range = ((valid.maxOrNull() ?: low) - low).coerceAtLeast(3.0)
        val middle = low + ((valid.maxOrNull() ?: low) - low) / 2
        val next = profile.heights.map { it?.let { ((it - middle) / range).toFloat() } }
        if (next == target && grade == targetGrade && animate) return
        val hadProfile = target.any { it != null }
        target = next; targetGrade = grade
        if (!animate || !hadProfile || !isShown || !ValueAnimator.areAnimatorsEnabled()) settle()
        else { lastFrame = SystemClock.uptimeMillis(); invalidate() }
    }
    fun settle() { shown = target; shownGrade = targetGrade ?: 0.0; lastFrame = 0; invalidate() }
    override fun onDraw(canvas: Canvas) {
        if (!ValueAnimator.areAnimatorsEnabled()) { shown = target; shownGrade = targetGrade ?: 0.0 }
        val now = SystemClock.uptimeMillis()
        val alpha = (1.0 - exp(-(now - lastFrame).coerceIn(1, 64) / 280.0)).toFloat()
        var moving = false
        shown = target.mapIndexed { index, value ->
            val old = shown.getOrNull(index)
            if (value == null || old == null) value
            else if (abs(value - old) > .001f) { moving = true; old + (value - old) * alpha } else value
        }
        val grade = targetGrade ?: 0.0
        if (abs(shownGrade - grade) > .01) { moving = true; shownGrade += (grade - shownGrade) * alpha } else shownGrade = grade
        lastFrame = now
        if (moving) postInvalidateOnAnimation()
        val density = resources.displayMetrics.density
        val padding = 6 * density
        fun x(index: Int) = padding + index.toFloat() / (SlopeProfile.COUNT - 1) * (width - padding * 2)
        fun y(value: Float) = height / 2f - value * (height - padding * 2)
        ink.color = when {
            targetGrade == null -> neutral
            shownGrade > 1.5 -> ColorUtils.blendARGB(uphill, steep, ((shownGrade - 3) / 7).toFloat().coerceIn(0f, 1f))
            shownGrade < -1.5 -> downhill
            else -> neutral
        }
        if (targetGrade != null) ink.color = ColorUtils.blendARGB(neutral, ink.color, ((abs(shownGrade) - 1.5) / 1.5).toFloat().coerceIn(0f, 1f))
        ink.style = Paint.Style.STROKE; ink.strokeWidth = 2.5f * density
        path.reset()
        var segmentStart = 0
        while (segmentStart < shown.size) {
            while (segmentStart < shown.size && shown[segmentStart] == null) segmentStart++
            if (segmentStart == shown.size) break
            var end = segmentStart
            while (end + 1 < shown.size && shown[end + 1] != null) end++
            path.moveTo(x(segmentStart), y(shown[segmentStart]!!))
            // Midpoint quadratics share tangents, avoiding a tiny horizontal plateau at every bin.
            for (index in segmentStart until end) {
                path.quadTo(x(index), y(shown[index]!!),
                    (x(index) + x(index + 1)) / 2, (y(shown[index]!!) + y(shown[index + 1]!!)) / 2)
            }
            path.lineTo(x(end), y(shown[end]!!))
            segmentStart = end + 1
        }
        canvas.drawPath(path, ink)
        shown.lastOrNull()?.let { ink.style = Paint.Style.FILL; canvas.drawCircle(x(shown.lastIndex), y(it), 3 * density, ink) }
        // No sample means no terrain: a faint baseline is a placeholder, never a zero grade.
        if (shown.all { it == null }) {
            ink.alpha = 70; ink.strokeWidth = density
            canvas.drawLine(padding, height / 2f, width - padding, height / 2f, ink)
            ink.alpha = 255
        }
    }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); if (!isVisible) settle() }
    override fun onDetachedFromWindow() { settle(); super.onDetachedFromWindow() }
}
