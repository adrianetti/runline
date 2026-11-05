package com.runline.app.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.runline.app.R
import com.runline.app.core.TerrainEstimate
import kotlin.math.roundToInt

/** Shared visual hierarchy for live recording and playback: pace, distance, supporting data. */
internal class RunReadoutView(context: Context) : LinearLayout(context) {
    private val compact = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val paceReadout = PaceReadoutView(context, compact)
    private val pace = paceReadout.number
    private val distance = RollingNumberView(context, if (compact) 24 else 32, 22)
    private val time = RollingNumberView(context, 20, 16)
    private val slope = SlopeIndicator(context)
    private val ascent = minorMetric(R.drawable.ic_slope_up)
    private val descent = minorMetric(R.drawable.ic_slope_down)
    private val average = label("", 12)
    private val locale get() = resources.configuration.locales[0]

    init {
        orientation = VERTICAL
        val stacked = resources.configuration.fontScale > 1.5f || (!compact && resources.configuration.screenWidthDp < 360)
        val primary = LinearLayout(context).apply { orientation = if (stacked) VERTICAL else HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        primary.addView(paceReadout, if (stacked) LayoutParams(-1, -2) else LayoutParams(0, -2, 1f))
        primary.addView(slope, LayoutParams(dp(88), -2).apply { if (stacked) { topMargin = dp(8); gravity = Gravity.END } else marginStart = dp(12) })
        addView(primary, LayoutParams(-1, -2))

        val secondary = LinearLayout(context).apply { gravity = Gravity.BOTTOM }
        if (compact) {
            val traveled = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            traveled.addView(distance, LayoutParams(0, scaled(32), 1f))
            traveled.addView(label(context.getString(R.string.island_km), 12), LayoutParams(-2, -2).apply { marginStart = dp(4) })
            secondary.addView(traveled, LayoutParams(0, -2, 1f))
            secondary.addView(time, LayoutParams(0, scaled(32), 1f).apply { marginStart = dp(20) })
        } else {
            secondary.addView(column(context.getString(R.string.active_distance), distance), LayoutParams(0, -2, 1f))
            secondary.addView(column(context.getString(R.string.active_time), time), LayoutParams(0, -2, 1f).apply { marginStart = dp(20) })
        }
        addView(secondary, LayoutParams(-1, -2).apply { topMargin = dp(if (compact) 4 else 12) })

        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(ascent, LayoutParams(0, -2, 1f))
        footer.addView(descent, LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        addView(footer, LayoutParams(-1, -2).apply { topMargin = dp(12) })
        if (compact) footer.visibility = GONE
        average.visibility = GONE
        addView(average, LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }

    fun update(paceSeconds: Double?, distanceMeters: Double?, durationMs: Long, terrain: TerrainEstimate,
               climbedMeters: Double?, descendedMeters: Double?, averagePace: Double? = null, animate: Boolean = true, profile: com.runline.app.core.SlopeProfile = com.runline.app.core.SlopeProfile.EMPTY) {
        val paceValue = paceSeconds?.takeIf { it.isFinite() && it > 0 && it < 6_000 }?.roundToInt()
        val paceText = paceValue?.let { String.format(locale, "%d:%02d", it / 60, it % 60) } ?: "—"
        pace.setValue(paceText, paceValue?.toDouble(), animate)
        pace.contentDescription = context.getString(R.string.island_pace_accessibility, paceText)
        val km = distanceMeters?.takeIf { it.isFinite() && it >= 0 }?.div(1000)
        val distanceText = number(km, 2)
        distance.setValue(distanceText, km, animate)
        distance.contentDescription = context.getString(R.string.replay_kilometres, distanceText)
        val seconds = durationMs.coerceAtLeast(0) / 1_000
        val duration = if (seconds >= 3600) String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format(locale, "%02d:%02d", seconds / 60, seconds % 60)
        time.setValue(duration, seconds.toDouble(), animate)
        time.contentDescription = context.getString(R.string.island_time_accessibility, duration)
        slope.update(terrain, animate, profile)
        val up = number(climbedMeters, 0); val down = number(descendedMeters, 0)
        change(ascent, context.getString(R.string.island_metres, up))
        change(descent, context.getString(R.string.island_metres, down))
        ascent.contentDescription = context.getString(R.string.island_climbed_accessibility, up)
        descent.contentDescription = context.getString(R.string.island_descended_accessibility, down)
        val averageValue = averagePace?.takeIf { it.isFinite() && it > 0 && it < 6_000 }?.roundToInt()
        average.visibility = if (averageValue != null && !compact) VISIBLE else GONE
        averageValue?.let {
            change(average, context.getString(R.string.island_average, String.format(locale, "%d:%02d", it / 60, it % 60)))
        }
    }


    fun settle() { pace.settle(); distance.settle(); time.settle(); slope.settle() }

    private fun column(title: String, number: RollingNumberView) = LinearLayout(context).apply {
        orientation = VERTICAL
        addView(label(title, 12), LayoutParams(-1, -2))
        // Height follows sp as well as width, preserving large-font baselines.
        val heightSp = when (number) { pace -> if (compact) 44 else 66; else -> 40 }
        addView(number, LayoutParams(-1, scaled(heightSp)).apply { topMargin = dp(2) })
    }
    private fun minorMetric(icon: Int) = label("—", 14).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); maxLines = 1
        compoundDrawablePadding = dp(6)
        setCompoundDrawablesRelative(AppCompatResources.getDrawable(context, icon)!!.mutate().apply {
            setTint(context.getColor(R.color.on_surface_variant)); setBounds(0, 0, dp(18), dp(18))
        }, null, null, null)
    }
    private fun label(value: String, size: Int) = TextView(context).apply {
        text = value; textSize = size.toFloat(); includeFontPadding = false
        setTextColor(context.getColor(R.color.on_surface_variant))
    }
    private fun number(value: Double?, decimals: Int) = value?.takeIf { it.isFinite() }?.let { String.format(locale, "%.${decimals}f", it) } ?: "—"
    private fun change(view: TextView, text: String) { if (view.text.toString() != text) view.text = text }
    private fun scaled(value: Int) = (value * resources.displayMetrics.scaledDensity).roundToInt()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
