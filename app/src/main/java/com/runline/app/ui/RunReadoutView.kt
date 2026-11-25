package com.runline.app.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.runline.app.R
import com.runline.app.core.TerrainEstimate
import kotlin.math.roundToInt

internal class RunReadoutView(
    context: Context,
) : LinearLayout(context) {
    private val compact = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val paceReadout = PaceReadoutView(context, compact)
    private val pace = paceReadout.number
    private val fieldSize = if (compact) TextScale.VALUE else TextScale.DATA
    private val distance = RollingNumberView(context, fieldSize, TextScale.SECTION)
    private val time = RollingNumberView(context, fieldSize, TextScale.SECTION)
    private val slope = SlopeIndicator(context)
    private val ascent = minor(R.string.v19_route_ascent, R.string.v26_unit_metres)
    private val descent = minor(R.string.v19_route_descent, R.string.v26_unit_metres)
    private val average = minor(R.string.v26_average_pace, R.string.v26_unit_per_km)
    private val locale get() = resources.configuration.locales[0]
    val profile = View(context).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    init {
        orientation = VERTICAL
        val stacked = resources.configuration.fontScale > STACK_FONT_SCALE || (!compact && resources.configuration.screenWidthDp < NARROW_DP)
        val primary =
            LinearLayout(context).apply {
                orientation = if (stacked) VERTICAL else HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        primary.addView(paceReadout, if (stacked) LayoutParams(-1, -2) else LayoutParams(0, -2, 1f))
        primary.addView(slope, LayoutParams(dp(SLOPE_DP), -2).apply { if (stacked) gravity = Gravity.END else marginStart = dp(Space.M) })
        addView(primary, LayoutParams(-1, -2))
        addView(UiRows.hairline(context), rule(Space.S))
        val secondary = LinearLayout(context).apply { gravity = Gravity.BOTTOM }
        secondary.addView(DataFieldView(context, context.getString(R.string.v19_route_distance), distance, context.getString(R.string.island_km)), LayoutParams(0, -2, 1f))
        secondary.addView(DataFieldView(context, context.getString(R.string.active_time), time), LayoutParams(0, -2, 1f))
        addView(secondary, LayoutParams(-1, -2).apply { topMargin = dp(Space.S) })
        if (!compact) {
            addView(UiRows.hairline(context), rule(Space.S))
            val footer = LinearLayout(context)
            val climbs = LinearLayout(context)
            listOf(ascent, descent).forEach { climbs.addView(it, LayoutParams(0, -2, 1f)) }
            listOf(climbs, average).forEach { footer.addView(it, LayoutParams(0, -2, 1f)) }
            addView(footer, LayoutParams(-1, -2).apply { topMargin = dp(Space.S) })
            addView(profile, LayoutParams(-1, dp(PROFILE_DP)).apply { topMargin = dp(Space.M) })
        }
    }

    fun update(
        paceSeconds: Double?,
        distanceMeters: Double?,
        durationMs: Long,
        terrain: TerrainEstimate,
        climbedMeters: Double?,
        descendedMeters: Double?,
        averagePace: Double? = null,
        animate: Boolean = true,
        profile: com.runline.app.core.SlopeProfile = com.runline.app.core.SlopeProfile.EMPTY,
    ) {
        val paceValue = paceSeconds?.takeIf { it.isFinite() && it > 0 && it < 6_000 }?.roundToInt()
        val paceText = paceValue?.let { String.format(locale, "%d:%02d", it / 60, it % 60) } ?: "—"
        pace.setValue(paceText, paceValue?.toDouble(), animate)
        pace.contentDescription = context.getString(R.string.island_pace_accessibility, paceText)
        val km = distanceMeters?.takeIf { it.isFinite() && it >= 0 }?.div(1000)
        val distanceText = number(km, 2)
        distance.setValue(distanceText, km, animate)
        distance.contentDescription = context.getString(R.string.replay_kilometres, distanceText)
        val seconds = durationMs.coerceAtLeast(0) / 1_000
        val duration =
            if (seconds >= 3600) {
                String.format(locale, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            } else {
                String.format(locale, "%02d:%02d", seconds / 60, seconds % 60)
            }
        time.setValue(duration, seconds.toDouble(), animate)
        time.contentDescription = context.getString(R.string.island_time_accessibility, duration)
        slope.update(terrain, animate, profile)
        ascent.set(number(climbedMeters, 0))
        descent.set(number(descendedMeters, 0))
        ascent.contentDescription = context.getString(R.string.island_climbed_accessibility, number(climbedMeters, 0))
        descent.contentDescription = context.getString(R.string.island_descended_accessibility, number(descendedMeters, 0))
        val averageValue = averagePace?.takeIf { it.isFinite() && it > 0 && it < MAX_PACE_SECONDS }?.roundToInt()
        average.visibility = if (averagePace != null) VISIBLE else INVISIBLE
        average.set(averageValue?.let { String.format(locale, "%d:%02d", it / SECONDS_PER_MINUTE, it % SECONDS_PER_MINUTE) } ?: DASH)
    }

    fun settle() {
        pace.settle()
        distance.settle()
        time.settle()
        slope.settle()
    }

    private fun minor(
        label: Int,
        unit: Int,
    ) = DataFieldView(context, context.getString(label), DASH, context.getString(unit), TextScale.SECTION)

    private fun rule(top: Int) = LayoutParams(-1, dp(Space.HAIRLINE)).apply { topMargin = dp(top) }

    private fun number(
        value: Double?,
        decimals: Int,
    ) = value?.takeIf { it.isFinite() }?.let { String.format(locale, "%.${decimals}f", it) } ?: "—"

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val DASH = "\u2014"
        const val STACK_FONT_SCALE = 1.5f
        const val NARROW_DP = 360
        const val SLOPE_DP = 88
        const val PROFILE_DP = 48
        const val MAX_PACE_SECONDS = 6_000
        const val SECONDS_PER_MINUTE = 60
    }
}
