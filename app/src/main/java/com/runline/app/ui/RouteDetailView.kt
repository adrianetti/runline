package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.runline.app.R
import com.runline.app.core.HikingRoute
import com.runline.app.core.RouteGeometry
import com.runline.app.core.RouteImportNaming
import com.runline.app.core.SunTimes
import com.runline.app.data.RouteEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

class RouteDetailView(
    context: Context,
    val route: HikingRoute,
    entry: RouteEntry?,
    summary: String?,
    notes: Map<String, String>,
    recording: Boolean,
    onBack: () -> Unit,
    onMore: (View) -> Unit,
    onStart: () -> Unit,
    onPreview: () -> Unit,
    onOpenRecording: () -> Unit,
) : FrameLayout(context) {
    val map = SatelliteMapView(context)
    private val compact = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val panel =
        object : LinearLayout(context) {
            var maxHeight = Int.MAX_VALUE

            override fun onMeasure(
                w: Int,
                h: Int,
            ) = super.onMeasure(w, MeasureSpec.makeMeasureSpec(minOf(maxHeight, MeasureSpec.getSize(h)), MeasureSpec.AT_MOST))
        }

    init {
        setBackgroundColor(context.getColor(R.color.surface))
        map.setRoute(route.runPoints())
        addView(map, LayoutParams(-1, -1))
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(
            UiButtons.floating(context, R.drawable.ic_back, context.getString(R.string.v19_route_back)).apply { setOnClickListener { onBack() } },
            LinearLayout.LayoutParams(dp(48), dp(48)),
        )
        top.addView(
            UiButtons.floating(context, R.drawable.ic_more, context.getString(R.string.v19_route_options)).apply { setOnClickListener { onMore(this) } },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) },
        )
        addView(
            UiButtons.floating(context, R.drawable.ic_preview, context.getString(R.string.v20_route_preview_description)).apply { setOnClickListener { onPreview() } },
            LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
                marginEnd = dp(16)
                topMargin = dp(PREVIEW_TOP_DP)
            },
        )
        addView(
            top,
            LayoutParams(-2, dp(48), Gravity.TOP or Gravity.START).apply {
                marginStart = dp(16)
                topMargin = dp(16)
            },
        )

        val stats = route.stats
        val locale = resources.configuration.locales[0]

        fun km(m: Double) = String.format(locale, "%.1f", m / 1000)
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(
            text(entry?.name ?: route.name, 24, bold = true).apply {
                maxLines = 3
                UiText.heading(this)
            },
        )
        (entry?.region)?.let { content.addView(text(it, 13, muted = true), row(top = 4)) }
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val metres = context.getString(R.string.v26_unit_metres)
        val dash = context.getString(R.string.replay_unavailable)

        fun height(value: Double?) = RouteField(value?.roundToInt()?.toString() ?: dash, if (value == null) "" else metres)

        fun pair(
            size: Int,
            first: Pair<Int, RouteField>,
            second: Pair<Int, RouteField>,
        ) {
            grid.addView(UiRows.hairline(context), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8) })
            grid.addView(
                LinearLayout(context).apply {
                    listOf(first, second).forEach { (name, field) ->
                        addView(DataFieldView(context, context.getString(name), field.value, field.unit, size), LinearLayout.LayoutParams(0, -2, 1f))
                    }
                },
                row(top = 8),
            )
        }
        val hours = stats.estimatedMinutes >= MINUTES_PER_HOUR
        val time =
            if (hours) {
                RouteField(String.format(locale, "%d:%02d", stats.estimatedMinutes / MINUTES_PER_HOUR, stats.estimatedMinutes % MINUTES_PER_HOUR), context.getString(R.string.v26_unit_hours))
            } else {
                RouteField(stats.estimatedMinutes.toString(), context.getString(R.string.v26_unit_minutes))
            }
        pair(TextScale.DATA, R.string.v19_route_distance to RouteField(km(stats.distanceMeters), context.getString(R.string.island_km)), R.string.v19_route_time to time)
        pair(TextScale.SECTION, R.string.v19_route_ascent to height(stats.ascentMeters), R.string.v19_route_descent to height(stats.descentMeters))
        pair(TextScale.SECTION, R.string.v19_route_highest to height(stats.maxElevationMeters), R.string.v19_route_lowest to height(stats.minElevationMeters))
        val shape =
            if (entry?.imported != false) {
                RouteFormat.shape(context, RouteImportNaming.shape(route))
            } else {
                context.getString(if (entry.loop) R.string.v19_shape_loop else R.string.v19_shape_line)
            }
        pair(TextScale.SECTION, R.string.v19_route_difficulty to RouteField(RouteFormat.difficulty(context, stats.difficulty), ""), R.string.v19_route_shape to RouteField(shape, ""))
        content.addView(grid)
        if (stats.maxElevationMeters != null) content.addView(RouteProfileView(context, route), LinearLayout.LayoutParams(-1, dp(88)).apply { topMargin = dp(16) })
        daylight(locale)?.let { line ->
            content.addView(
                LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        ImageView(context).apply {
                            setImageResource(R.drawable.ic_sunset)
                            imageTintList = ColorStateList.valueOf(context.getColor(R.color.on_surface))
                            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                        },
                        LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) },
                    )
                    addView(text(line, 14), LinearLayout.LayoutParams(0, -2, 1f))
                },
                row(top = 12),
            )
        }
        val places =
            route.waypoints
                .filter { it.name.isNotBlank() }
                .map { w -> w to route.points.minByOrNull { RouteGeometry.meters(it.latitude, it.longitude, w.latitude, w.longitude) }!!.distanceMeters }
                .sortedBy { it.second }
        if (places.isNotEmpty()) {
            content.addView(text(context.getString(R.string.v19_route_waypoints), 16, bold = true), row(top = 20, bottom = 4))
            places.forEach { (w, along) ->
                content.addView(
                    LinearLayout(context).apply {
                        setPadding(0, dp(6), 0, dp(6))
                        addView(text(context.getString(R.string.v19_km, km(along)), 14, muted = true).apply { minWidth = dp(64) })
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                addView(text(w.name, 14))
                                notes[w.name]?.let { addView(text(it, 13, muted = true), row(top = 2)) }
                            },
                            LinearLayout.LayoutParams(0, -2, 1f),
                        )
                    },
                )
            }
        }
        summary?.let { content.addView(text(it, 14), row(top = 16)) }
        content.addView(text(context.getString(if (entry?.imported == true) R.string.v19_route_attribution_imported else R.string.v19_route_attribution_catalog), 12, muted = true), row(top = 16))

        panel.orientation = LinearLayout.VERTICAL
        panel.background =
            GradientDrawable().apply {
                setColor(context.getColor(R.color.surface))
                cornerRadius = dp(24).toFloat()
            }
        panel.elevation = dp(3).toFloat()
        panel.setPadding(dp(20), dp(16), dp(20), dp(12))
        panel.addView(
            ScrollView(context).apply {
                isFillViewport = false
                addView(content)
            },
            LinearLayout.LayoutParams(-1, 0, 1f),
        )
        val name = entry?.name ?: route.name
        val actions =
            UiButtons.primary(
                context,
                context.getString(if (recording) R.string.v20_route_open_recording else R.string.v20_route_start),
                if (recording) R.drawable.ic_route else R.drawable.ic_play,
            ).apply {
                contentDescription = if (recording) context.getString(R.string.v20_route_recording) else context.getString(R.string.v20_route_start_description, name)
                setOnClickListener { if (recording) onOpenRecording() else onStart() }
            }
        panel.addView(actions, row(top = 12))
        addView(panel, LayoutParams(if (compact) dp(360) else -1, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(16), 0, dp(16), dp(16)) })
        panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> insets() }
        UiMotion.rise(panel)
    }

    private fun daylight(locale: java.util.Locale): String? {
        val start = route.points.first()
        val sunset = SunTimes.sunset(LocalDate.now().toEpochDay(), start.latitude, start.longitude) ?: return null
        val now = System.currentTimeMillis()
        val fmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault())
        if (sunset < now) return context.getString(R.string.v19_route_after_dark, fmt.format(Instant.ofEpochMilli(sunset)))
        val finish = now + route.stats.estimatedMinutes * 60_000L
        return context.getString(
            if (finish > sunset) R.string.v19_route_finish_after_sunset else R.string.v19_route_finish_before_sunset,
            fmt.format(Instant.ofEpochMilli(sunset)),
            fmt.format(Instant.ofEpochMilli(finish)),
        )
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val h = MeasureSpec.getSize(heightMeasureSpec)
        panel.maxHeight = if (compact) (h - dp(88)).coerceAtLeast(dp(180)) else (h * 0.55f).roundToInt().coerceAtLeast(dp(220))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun insets() {
        if (panel.height <= 0) return
        if (compact) map.setOverlayInsets(panel.width + dp(32), dp(72), 0, 0) else map.setOverlayInsets(0, dp(72), 0, panel.height + dp(16))
        map.fitRoute()
    }

    private fun text(
        value: String,
        size: Int,
        bold: Boolean = false,
        muted: Boolean = false,
    ) = TextView(context).apply {
        text = value
        textSize = size.toFloat()
        fontFeatureSettings = "tnum"
        setTextColor(context.getColor(if (muted) R.color.on_surface_variant else R.color.on_surface))
        UiText.restyle(this, size, bold)
    }

    private fun row(
        top: Int = 0,
        bottom: Int = 0,
    ) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private data class RouteField(
        val value: String,
        val unit: String,
    )

    private companion object {
        const val PREVIEW_TOP_DP = 16
        const val MINUTES_PER_HOUR = 60
    }
}

class RouteProfileView(
    context: Context,
    private val route: HikingRoute,
) : View(context) {
    private val line =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
            color = context.getColor(R.color.on_surface)
            strokeJoin = Paint.Join.ROUND
        }
    private val fill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = context.getColor(R.color.surface_variant)
        }
    private val label =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.on_surface_variant)
            textSize = 11f * resources.displayMetrics.scaledDensity
        }

    init {
        val stats = route.stats
        contentDescription =
            context.getString(
                R.string.v19_route_profile_description,
                stats.minElevationMeters?.roundToInt() ?: 0,
                stats.maxElevationMeters?.roundToInt() ?: 0,
            )
    }

    private val path = Path()
    private val area = Path()
    private val heights = route.points.filter { it.elevationMeters != null }
    private val range =
        if (heights.size < 2) {
            ""
        } else {
            context.getString(
                R.string.replay_metres,
                "${heights.minOf { it.elevationMeters!! }.roundToInt()}–${heights.maxOf { it.elevationMeters!! }.roundToInt()}",
            )
        }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        path.reset()
        area.reset()
        if (heights.size < 2) return
        val low = heights.minOf { it.elevationMeters!! }
        val high = heights.maxOf { it.elevationMeters!! }
        val span = maxOf(40.0, high - low)
        val base = (low + high) / 2 - span / 2
        val total = route.distanceMeters
        val pad = label.textSize + 4
        val width = w.toFloat()
        val height = h.toFloat() - pad
        heights.forEachIndexed { i, p ->
            val x = (p.distanceMeters / total * width).toFloat()
            val y = (height - (p.elevationMeters!! - base) / span * (height - 4) - 2).toFloat()
            if (i == 0) {
                path.moveTo(x, y)
                area.moveTo(x, height)
                area.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(width, height)
        area.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (heights.size < 2) return
        canvas.drawPath(area, fill)
        canvas.drawPath(path, line)
        canvas.drawText(range, 0f, height - 2f, label)
    }
}
