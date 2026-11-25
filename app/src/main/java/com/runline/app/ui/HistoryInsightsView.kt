package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
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
    private val types = listOf<ActivityType?>(null) + ActivityType.entries
    private val periods = types.associateWith { HistorySummary.forPeriod(prepared, now, zone, it) }
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private var selected = initialType
    val selectedType: ActivityType? get() = selected

    init {
        orientation = VERTICAL
        val names = listOf(R.string.history_all, R.string.history_run, R.string.history_walk, R.string.history_hike).map(context::getString)
        addView(
            SegmentedControl(context, names, types.indexOf(selected)) { index ->
                selected = types[index]
                render()
                onTypeChanged(selected)
            }.apply { contentDescription = context.getString(R.string.history_filter_description, names[types.indexOf(selected)]) },
            row(bottom = Space.XL),
        )
        addView(content, LayoutParams(-1, -2))
        render()
    }

    private fun render() {
        val period = periods.getValue(selected)
        content.removeAllViews()
        val count = resources.getQuantityString(R.plurals.history_activity_count, period.activities.size, period.activities.size)
        val summary = context.getString(R.string.main_activity_date, context.getString(R.string.history_progress), count)
        content.addView(UiText.body(context, summary), row(bottom = Space.L))
        if (period.activities.isEmpty()) return
        val totals = LinearLayout(context)
        val paced = if (selected == null) periods.getValue(ActivityType.RUN) else period
        val pace = paced.paceSecondsPerKm?.let { formatPace(it, locale) } ?: DASH
        val minutes = period.activeDurationMs / MS_PER_MINUTE
        val hours = minutes >= MINUTES_PER_HOUR
        val time =
            if (hours) {
                String.format(locale, "%d:%02d", minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
            } else {
                minutes.toString()
            }
        val distance = number(period.distanceMeters / METERS_PER_KM, 1)
        val timeUnit = context.getString(if (hours) R.string.v26_unit_hours else R.string.v26_unit_minutes)
        totals.addView(field(R.string.v19_route_distance, distance, context.getString(R.string.island_km)), cell())
        totals.addView(field(R.string.active_time, time, timeUnit), cell())
        totals.addView(field(R.string.v26_average_pace, pace, context.getString(R.string.v26_unit_per_km)), cell())
        content.addView(totals)
        content.addView(WeeklyDistanceChart(context, period, locale), row(top = Space.L))
        if (selected != null && period.paceTrend.size >= MIN_TREND) {
            content.addView(UiText.label(context, context.getString(R.string.history_pace_title)), row(top = Space.XL))
            content.addView(PaceTrendChart(context, period, zone, locale), row(top = Space.S))
        }
    }

    private fun field(
        label: Int,
        value: String,
        unit: String,
    ) = DataFieldView(context, context.getString(label), value, unit)

    private fun cell() = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)

    private fun number(
        value: Double,
        decimals: Int,
    ) = String.format(locale, "%.${decimals}f", value)

    private fun row(
        top: Int = 0,
        bottom: Int = 0,
    ) = LayoutParams(-1, -2).apply {
        topMargin = UiText.dp(context, top)
        bottomMargin = UiText.dp(context, bottom)
    }

    private companion object {
        const val DASH = "\u2014"
        const val METERS_PER_KM = 1_000.0
        const val MS_PER_MINUTE = 60_000L
        const val MINUTES_PER_HOUR = 60
        const val MIN_TREND = 2
    }
}

private fun formatPace(
    seconds: Double,
    locale: Locale,
): String {
    val total = seconds.roundToInt().coerceAtLeast(0)
    return String.format(locale, "%d:%02d", total / 60, total % 60)
}

private class WeeklyDistanceChart(
    context: Context,
    period: HistoryPeriod,
    locale: Locale,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val textSize = 14f * resources.displayMetrics.scaledDensity
    private val ink = context.getColor(R.color.on_surface)
    private val muted = context.getColor(R.color.on_surface_variant)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val weeks = period.weeks
    private val formatter = DateTimeFormatter.ofPattern("d MMM", locale)
    private val labels = weeks.map { it.startsOn.format(formatter) }
    private val values = weeks.map { String.format(locale, "%.1f", it.distanceMeters / 1000) }
    private val peak = weeks.maxOf { it.distanceMeters }.coerceAtLeast(1000.0)
    private val outline = 2 * density
    private val valueFace = Type.display(context)
    private val chartHeight = 88 * density
    private val valueSize = 18f * resources.displayMetrics.scaledDensity
    private var growth = 0f
    private var growing: android.animation.ValueAnimator? = null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription =
            weeks.indices.joinToString(". ") { i ->
                context.getString(R.string.history_week_value, labels[i], values[i])
            }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        growing = UiMotion.grow(this) { growth = it }
    }

    override fun onDetachedFromWindow() {
        growing?.cancel()
        growth = 1f
        super.onDetachedFromWindow()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (chartHeight + 4 * textSize).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.textSize = textSize
        paint.typeface = Typeface.DEFAULT
        val column = width / 4f
        val baseline = height - textSize * 2f
        val top = textSize * 2f
        val range = (baseline - top).coerceAtLeast(1f)
        paint.color = muted
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, baseline - density, width.toFloat(), baseline, paint)
        weeks.forEachIndexed { index, week ->
            val center = column * (index + .5f)
            val barWidth = minOf(column * .48f, 36 * density)
            val barTop = baseline - (range * week.distanceMeters / peak).toFloat() * growth
            paint.color = ink
            val current = index == weeks.lastIndex
            paint.style = if (current) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = outline
            val inset = if (current) outline / 2 else 0f
            if (week.distanceMeters > 0) {
                canvas.drawRoundRect(
                    center - barWidth / 2 + inset,
                    barTop + inset,
                    center + barWidth / 2 - inset,
                    baseline - inset,
                    3 * density,
                    3 * density,
                    paint,
                )
                paint.style = Paint.Style.FILL
            } else {
                paint.style = Paint.Style.FILL
            }
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = valueFace
            paint.textSize = valueSize
            if (week.distanceMeters > 0) canvas.drawText(values[index], center, max(valueSize, barTop - valueSize * .45f), paint)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = textSize
            paint.color = muted
            canvas.drawText(labels[index], center, baseline + textSize * 1.5f, paint)
        }
    }
}

private class PaceTrendChart(
    context: Context,
    period: HistoryPeriod,
    zone: ZoneId,
    locale: Locale,
) : View(context) {
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
        contentDescription =
            points.joinToString(". ") { point ->
                context.getString(
                    R.string.history_pace_value,
                    dateFormatter.format(Instant.ofEpochMilli(point.startedAtMs)),
                    formatPace(point.paceSecondsPerKm!!, locale),
                )
            }
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (130 * density + 4 * textSize).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.textSize = textSize
        paint.typeface = Typeface.DEFAULT
        paint.style = Paint.Style.FILL
        val left = tickLabels.maxOf { paint.measureText(it) } + 14 * density
        val right = width - max(8 * density, paint.measureText(lastDate) / 2)
        val top = textSize
        val bottom = height - textSize * 2.2f
        val range = (bottom - top).coerceAtLeast(1f)
        val usable = (right - left).coerceAtLeast(1f)
        tickLabels.forEachIndexed { i, label ->
            val y = top + range * i / 2
            paint.color = line
            paint.strokeWidth = density
            canvas.drawLine(left, y, right, y, paint)
            paint.color = muted
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(label, left - 8 * density, y + textSize * .32f, paint)
        }
        path.reset()
        values.forEachIndexed { i, value ->
            val x = left + usable * i / values.lastIndex
            val y = top + ((value - fastest) / (slowest - fastest) * range).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.color = ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2 * density
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        values.forEachIndexed { i, value ->
            val x = left + usable * i / values.lastIndex
            val y = top + ((value - fastest) / (slowest - fastest) * range).toFloat()
            canvas.drawCircle(x, y, 3 * density, paint)
        }
        paint.color = muted
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(firstDate, left, height - textSize * .35f, paint)
        canvas.drawText(lastDate, right, height - textSize * .35f, paint)
    }
}
