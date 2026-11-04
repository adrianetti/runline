package com.runline.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import androidx.core.graphics.withClip
import com.runline.app.R

/** Native rolling digits, with real TextView text for measurement and accessibility. */
internal class RollingNumberView @JvmOverloads constructor(context: Context, sizeSp: Int = 32, minimumSp: Int = sizeSp) : AppCompatTextView(context) {
    private data class Reading(val text: String, val number: Double?)
    private var reading = Reading("—", null)
    private var previous = ""
    private var pending: Reading? = null
    private var animator: ValueAnimator? = null
    private var fraction = 1f
    private var direction = 1f

    init {
        text = reading.text; textSize = sizeSp.toFloat(); includeFontPadding = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fontFeatureSettings = "tnum"; maxLines = 1
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(context.getColor(R.color.on_surface))
        if (minimumSp < sizeSp) TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this, minimumSp, sizeSp, 1, TypedValue.COMPLEX_UNIT_SP)
        // Values remain discoverable without announcing every second over the coach/music.
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
    }

    fun setValue(value: String, number: Double? = null, animate: Boolean = true) {
        val next = Reading(value, number)
        if (!animate || !isShown || !ValueAnimator.areAnimatorsEnabled() || number == null || reading.number == null) {
            settle(); reading = next; text = value; return
        }
        if (animator != null) { pending = next.takeIf { it.text != reading.text }; return }
        if (value == reading.text) return
        previous = reading.text
        direction = if (number < reading.number!!) -1f else 1f
        reading = next; text = value; fraction = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280L
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener { fraction = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null; fraction = 1f; previous = ""; invalidate()
                    val queued = pending; pending = null
                    queued?.let { setValue(it.text, it.number) }
                }
            })
            start()
        }
    }

    /** Seeking, pausing and leaving the screen must show the exact latest value. */
    fun settle() {
        val queued = pending; pending = null
        animator?.apply { removeAllListeners(); removeAllUpdateListeners(); cancel() }
        animator = null; fraction = 1f; previous = ""
        queued?.let { reading = it; text = it.text }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (fraction >= 1f || previous.isEmpty()) { super.onDraw(canvas); return }
        if (!ValueAnimator.areAnimatorsEnabled()) { settle(); super.onDraw(canvas); return }
        val current = reading.text
        val base = baseline.toFloat()
        val left = compoundPaddingLeft + (layout?.getLineLeft(0) ?: 0f)
        val height = paint.fontMetrics.let { it.descent - it.ascent }
        paint.color = currentTextColor
        val alpha = paint.alpha
        canvas.withClip(compoundPaddingLeft.toFloat(), base + paint.fontMetrics.ascent - 2,
            (width - compoundPaddingRight).toFloat(), base + paint.fontMetrics.descent + 2) {
          current.forEachIndexed { index, char ->
            val prior = previous.getOrNull(index + previous.length - current.length)
            val x = left + paint.measureText(current, 0, index)
            if (prior == char || !char.isDigit()) canvas.drawText(char.toString(), x, base, paint)
            else {
                if (prior?.isDigit() == true) {
                    paint.alpha = (alpha * (1f - fraction)).toInt()
                    canvas.drawText(prior.toString(), x, base - direction * fraction * height, paint)
                }
                paint.alpha = (alpha * fraction).toInt()
                canvas.drawText(char.toString(), x, base + direction * (1f - fraction) * height, paint)
                paint.alpha = alpha
            }
          }
        }
        paint.alpha = alpha
    }

    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); if (!isVisible) settle() }
    override fun onDetachedFromWindow() { settle(); super.onDetachedFromWindow() }
}
