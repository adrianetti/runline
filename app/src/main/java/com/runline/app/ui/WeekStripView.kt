package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import com.runline.app.R
import com.runline.app.core.WeekProgress
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

internal class WeekStripView(
    context: Context,
    private val week: WeekProgress,
    locale: Locale,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val initials = DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, locale).uppercase(locale) }
    private val ink = context.getColor(R.color.on_surface)
    private val muted = context.getColor(R.color.on_surface_variant)
    private val thin = UiText.dp(context, Space.HAIRLINE).toFloat()
    private val thick = UiText.dp(context, Space.HAIRLINE) * 2f
    private val lane = UiText.dp(context, LANE_DP).toFloat()
    private val gap = UiText.dp(context, Space.S).toFloat()
    private val textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TextScale.CAPTION.toFloat(), resources.displayMetrics)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.v26_week_days, week.activeDayCount)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = textSize
        paint.typeface = Type.body()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (lane + gap + textSize * LINE_HEIGHT).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val slot = (width + gap) / WeekProgress.DAYS
        initials.forEachIndexed { index, initial ->
            val left = index * slot
            val right = left + slot - gap
            val today = index == week.todayIndex
            val stroke = if (today) thick else thin
            paint.color = if (week.activeDays[index] || today) ink else muted
            paint.style = if (week.activeDays[index]) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = stroke
            canvas.drawRoundRect(left + stroke / 2, stroke / 2, right - stroke / 2, lane - stroke / 2, lane / 2, lane / 2, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (today) ink else muted
            canvas.drawText(initial, (left + right) / 2, lane + gap + textSize, paint)
        }
    }

    private companion object {
        const val LANE_DP = 8
        const val LINE_HEIGHT = 1.3f
    }
}
