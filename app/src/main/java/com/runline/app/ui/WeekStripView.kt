package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.View
import com.runline.app.R
import com.runline.app.core.WeekProgress
import java.time.DayOfWeek
import java.time.ZoneOffset
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
    private val rule = UiText.dp(context, Space.HAIRLINE) * 2f
    private val lane = UiText.dp(context, LANE_DP).toFloat()
    private val gap = UiText.dp(context, Space.S).toFloat()
    private val dot = UiText.dp(context, Space.XS).toFloat()
    private val textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TextScale.CAPTION.toFloat(), resources.displayMetrics)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.v26_week_days, week.activeDayCount)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = textSize
        paint.typeface = Type.body()
    }

    constructor(context: Context) : this(context, WeekProgress.of(emptyList(), 0L, ZoneOffset.UTC), Locale.ROOT)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (lane + gap + textSize * LINE_HEIGHT + dot * 2).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val slot = (width + gap) / WeekProgress.DAYS
        initials.forEachIndexed { index, initial ->
            val left = index * slot
            val right = left + slot - gap
            val active = week.activeDays[index]
            val inset = if (active) 0f else (lane - rule) / 2
            val center = (left + right) / 2
            paint.color = if (active) ink else muted
            canvas.drawRoundRect(left, inset, right, lane - inset, lane / 2, lane / 2, paint)
            paint.color = muted
            canvas.drawText(initial, center, lane + gap + textSize, paint)
            if (index == week.todayIndex) {
                paint.color = ink
                canvas.drawCircle(center, lane + gap + textSize * LINE_HEIGHT + dot, dot / 2, paint)
            }
        }
    }

    private companion object {
        const val LANE_DP = 6
        const val LINE_HEIGHT = 1.3f
    }
}
