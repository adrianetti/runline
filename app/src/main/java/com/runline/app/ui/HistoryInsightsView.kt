package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.runline.app.R
import com.runline.app.core.ActivityType
import com.runline.app.core.HistoryPeriod
import com.runline.app.core.HistorySummary
import com.runline.app.core.RunSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** A compact, accessible overview. Its type filter affects charts, not the history list. */
class HistoryInsightsView(
    context: Context,
    runs: List<RunSession>,
    initialType: ActivityType? = null,
    private val onTypeChanged: (ActivityType?) -> Unit = {},
) : LinearLayout(context) {
    private val zone = ZoneId.systemDefault()
    private val locale: Locale = resources.configuration.locales[0]
    private val now = System.currentTimeMillis()
    private val prepared = HistorySummary.prepare(runs)
    private val periods = (listOf<ActivityType?>(null) + ActivityType.entries).associateWith {
        HistorySummary.forPeriod(prepared, now, zone, it)
    }
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val choices = mutableMapOf<ActivityType?, MaterialButton>()
    private var selected = initialType
    val selectedType: ActivityType? get() = selected

    init {
        orientation = VERTICAL
        addView(label(context.getString(R.string.history_progress), 24, bold = true))
        val filters = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        periods.keys.forEach { type ->
            val name = context.getString(when (type) {
                null -> R.string.history_all
                ActivityType.RUN -> R.string.history_run
                ActivityType.WALK -> R.string.history_walk
                ActivityType.HIKE -> R.string.history_hike
            })
            val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = name; isAllCaps = false; textSize = 14f
                minWidth = 0; minimumWidth = 0; minHeight = dp(48); minimumHeight = dp(48)
                setPadding(dp(14), 0, dp(14), 0); insetTop = dp(3); insetBottom = dp(3)
                contentDescription = context.getString(R.string.history_filter_description, name)
                setOnClickListener {
                    if (selected != type) {
                        selected = type
                        render()
                        onTypeChanged(type)
                    }
                }
            }
            choices[type] = button
            filters.addView(button, LayoutParams(-2, dp(48)).apply { marginEnd = dp(8) })
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(filters)
        }, LayoutParams(-1, dp(56)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        addView(content, LayoutParams(-1, -2))
        render()
    }

    private fun render() {
        choices.forEach { (type, button) ->
            val active = type == selected
            button.isSelected = active
            button.backgroundTintList = ColorStateList.valueOf(context.getColor(if (active) R.color.primary else R.color.surface))
            button.setTextColor(context.getColor(if (active) R.color.on_primary else R.color.on_surface))
            button.strokeColor = ColorStateList.valueOf(context.getColor(R.color.outline))
            button.strokeWidth = if (active) 0 else dp(1)
        }
        val period = periods.getValue(selected)
        content.removeAllViews()
        if (period.activities.isEmpty()) {
            content.addView(label(context.getString(R.string.history_empty), 15, muted = true), row(bottom = 24))
            return
        }
        val totals = LinearLayout(context)
        totals.addView(metric(context.getString(R.string.history_distance), number(period.distanceMeters / 1000, 1)), LayoutParams(0, -2, 1f))
        totals.addView(metric(context.getString(R.string.history_active_time), duration(period.activeDurationMs)), LayoutParams(0, -2, 1f).apply { marginStart = dp(16) })
        content.addView(totals)
        content.addView(label(resources.getQuantityString(R.plurals.history_activity_count, period.activities.size, period.activities.size), 13, muted = true), row(top = 8))
        content.addView(label(context.getString(R.string.history_weekly_distance), 18, bold = true), row(top = 24))
        content.addView(WeeklyDistanceChart(context, period, locale), row(top = 8))
        content.addView(label(context.getString(R.string.history_week_note), 12, muted = true), row(top = 8))
        if (selected == null) {
            content.addView(label(context.getString(R.string.history_pace_type_hint), 14, muted = true), row(top = 20, bottom = 24))
            return
        }
        content.addView(label(context.getString(R.string.history_pace_title), 18, bold = true), row(top = 24))
        val trend = period.paceTrend
        if (trend.size < 2) {
            content.addView(label(context.getString(R.string.history_pace_empty), 14, muted = true), row(top = 8, bottom = 24))
            return
        }
        content.addView(PaceTrendChart(context, period, zone, locale), row(top = 8))
        content.addView(label(context.getString(R.string.history_pace_note, trend.size), 12, muted = true), row(top = 8))
        period.paceSecondsPerKm?.let { value ->
            content.addView(label(context.getString(R.string.history_period_pace, pace(value)), 14), row(top = 12))
            content.addView(label(context.getString(R.string.history_pace_weight_note), 12, muted = true), row(top = 4, bottom = 24))
        }
    }

    private fun metric(name: String, value: String) = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(label(name, 13, muted = true))
        addView(label(value, 24, bold = true), row(top = 4))
    }
    private fun label(value: String, size: Int, bold: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); includeFontPadding = false
        setTextColor(context.getColor(if (muted) R.color.on_surface_variant else R.color.on_surface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun number(value: Double, decimals: Int) = String.format(locale, "%.${decimals}f", value)
    private fun duration(value: Long): String {
        val minutes = value / 60_000
        return if (minutes >= 60) context.getString(R.string.history_time_hours_minutes, minutes / 60, minutes % 60)
        else context.getString(R.string.history_time_minutes, minutes)
    }
    private fun pace(value: Double) = formatPace(value, locale)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun row(top: Int = 0, bottom: Int = 0) = LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
}

private fun formatPace(seconds: Double, locale: Locale): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    return String.format(locale, "%d:%02d", total / 60, total % 60)
}

/** Four labelled observations rather than an unlabelled sparkline. */
private class WeeklyDistanceChart(context: Context, period: HistoryPeriod, locale: Locale) : View(context) {
    private val density = resources.displayMetrics.density
    private val textSize = 12f * resources.displayMetrics.scaledDensity
    private val ink = context.getColor(R.color.on_surface)
    private val muted = context.getColor(R.color.on_surface_variant)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val weeks = period.weeks
    private val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
    private val labels = weeks.map { it.startsOn.format(formatter) }
    private val values = weeks.map { String.format(locale, "%.1f", it.distanceMeters / 1000) }
    private val peak = weeks.maxOf { it.distanceMeters }.coerceAtLeast(1000.0)
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = weeks.indices.joinToString(". ") { i ->
            context.getString(R.string.history_week_value, labels[i], values[i])
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (150 * density + 4 * textSize).roundToInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.textSize = textSize; paint.typeface = Typeface.DEFAULT
        val column = width / 4f
        val baseline = height - textSize * 2f
        val top = textSize * 2f
        val range = (baseline - top).coerceAtLeast(1f)
        weeks.forEachIndexed { index, week ->
            val center = column * (index + .5f)
            val barWidth = minOf(column * .48f, 36 * density)
            val barTop = baseline - (range * week.distanceMeters / peak).toFloat()
            paint.color = ink; paint.style = Paint.Style.FILL
            if (week.distanceMeters > 0) canvas.drawRoundRect(center - barWidth / 2, barTop, center + barWidth / 2,
                baseline, 3 * density, 3 * density, paint)
            else canvas.drawRect(center - barWidth / 2, baseline - density, center + barWidth / 2, baseline, paint)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(values[index], center, max(textSize, barTop - textSize * .45f), paint)
            paint.color = muted
            canvas.drawText(labels[index], center, baseline + textSize * 1.5f, paint)
        }
    }
}

/** Pace uses an inverted axis: a faster (smaller) min/km value is higher. */
private class PaceTrendChart(context: Context, period: HistoryPeriod, zone: ZoneId, locale: Locale) : View(context) {
    private val density = resources.displayMetrics.density
    private val textSize = 12f * resources.displayMetrics.scaledDensity
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val points = period.paceTrend
    private val values = points.map { requireNotNull(it.paceSecondsPerKm) }
    private val padding = max(15.0, (values.maxOrNull()!! - values.minOrNull()!!) * .1)
    private val fastest = max(0.0, values.minOrNull()!! - padding)
    private val slowest = values.maxOrNull()!! + padding
    private val tickLabels = listOf(fastest, (fastest + slowest) / 2, slowest).map { formatPace(it, locale) }
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", locale).withZone(zone)
    private val firstDate = dateFormatter.format(Instant.ofEpochMilli(points.first().startedAtMs))
    private val lastDate = dateFormatter.format(Instant.ofEpochMilli(points.last().startedAtMs))
    private val ink = context.getColor(R.color.on_surface)
    private val muted = context.getColor(R.color.on_surface_variant)
    private val line = context.getColor(R.color.outline)
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = points.joinToString(". ") { point -> context.getString(R.string.history_pace_value,
            dateFormatter.format(Instant.ofEpochMilli(point.startedAtMs)), formatPace(point.paceSecondsPerKm!!, locale)) }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (130 * density + 4 * textSize).roundToInt())
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.textSize = textSize; paint.typeface = Typeface.DEFAULT; paint.style = Paint.Style.FILL
        val left = tickLabels.maxOf { paint.measureText(it) } + 14 * density
        val right = width - max(8 * density, paint.measureText(lastDate) / 2)
        val top = textSize
        val bottom = height - textSize * 2.2f
        val range = (bottom - top).coerceAtLeast(1f)
        val usable = (right - left).coerceAtLeast(1f)
        tickLabels.forEachIndexed { i, label ->
            val y = top + range * i / 2
            paint.color = line; paint.strokeWidth = density
            canvas.drawLine(left, y, right, y, paint)
            paint.color = muted; paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, left - 8 * density, y + textSize * .32f, paint)
        }
        path.reset()
        values.forEachIndexed { i, value ->
            val x = left + usable * i / values.lastIndex
            val y = top + ((value - fastest) / (slowest - fastest) * range).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = ink; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * density
        paint.strokeJoin = Paint.Join.ROUND; canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        values.forEachIndexed { i, value ->
            val x = left + usable * i / values.lastIndex
            val y = top + ((value - fastest) / (slowest - fastest) * range).toFloat()
            canvas.drawCircle(x, y, 3 * density, paint)
        }
        paint.color = muted; paint.textAlign = Paint.Align.CENTER
        canvas.drawText(firstDate, left, height - textSize * .35f, paint)
        canvas.drawText(lastDate, right, height - textSize * .35f, paint)
    }
}
