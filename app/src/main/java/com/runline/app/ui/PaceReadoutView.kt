package com.runline.app.ui

import android.content.Context
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.runline.app.R
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Measure the digits themselves: unused column width belongs after the unit, not between it and the value. */
internal class PaceReadoutView @JvmOverloads constructor(context: Context, private val compact: Boolean = false) : ViewGroup(context) {
    private val maximumSp = if (compact) 40 else 56
    val number = RollingNumberView(context, maximumSp)
    private val unit = TextView(context).apply {
        text = context.getString(R.string.v16_unit_pace); textSize = if (compact) 18f else 20f
        includeFontPadding = false; maxLines = 1; setTextColor(context.getColor(R.color.on_surface_variant))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO // The number already includes its unit in its spoken label.
    }
    private val gap = (6 * resources.displayMetrics.density).roundToInt()
    private val measurePaint = TextPaint()
    init { addView(number); addView(unit) }
    private fun sp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), resources.displayMetrics)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        unit.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val limit = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else MeasureSpec.getSize(widthMeasureSpec)
        val room = (limit - unit.measuredWidth - gap).coerceAtLeast(1)
        var size = maximumSp
        measurePaint.set(number.paint)
        measurePaint.textSize = sp(size)
        while (size > 24 && ceil(measurePaint.measureText(number.text.toString())).toInt() > room) {
            size--; measurePaint.textSize = sp(size)
        }
        number.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(size))
        val digitsWidth = ceil(number.paint.measureText(number.text.toString())).toInt().coerceAtMost(room)
        val height = sp(if (compact) 48 else 66).roundToInt()
        number.measure(MeasureSpec.makeMeasureSpec(digitsWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        setMeasuredDimension(resolveSize(digitsWidth + gap + unit.measuredWidth, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            number.layout(width - number.measuredWidth, 0, width, number.measuredHeight)
            val unitTop = (number.baseline - unit.baseline).coerceAtLeast(0)
            val end = number.left - gap
            unit.layout(end - unit.measuredWidth, unitTop, end, unitTop + unit.measuredHeight)
        } else {
            number.layout(0, 0, number.measuredWidth, number.measuredHeight)
            val unitTop = (number.baseline - unit.baseline).coerceAtLeast(0)
            val start = number.right + gap
            unit.layout(start, unitTop, start + unit.measuredWidth, unitTop + unit.measuredHeight)
        }
    }
}
