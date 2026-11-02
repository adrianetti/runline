package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.runline.app.R
import com.runline.app.core.RunMetrics
import com.runline.app.core.ElevationProgress
import com.runline.app.core.RunPoint
import com.runline.app.core.TerrainEstimate
import com.runline.app.core.TerrainTrend
import com.runline.app.tracking.RecordingStatus
import com.runline.app.tracking.TrackingState
import kotlin.math.roundToInt

/** A map-first recording surface. It only renders the independently saved service state. */
class ActiveRunView(
    context: Context,
    initial: TrackingState,
    onOpenRuns: () -> Unit,
    onOptions: (View) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) : FrameLayout(context) {
    val map = SatelliteMapView(context)
    private val compact = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val panel = ElevationIsland(context)
    private val distance = value()
    private val time = value()
    private val pace = value()
    private val gps = label(context.getString(R.string.active_loading), 12, muted = true)
    private val extra = label("", 12, muted = true)
    private val terrain = TerrainLabel(context)
    private val terrainTrend = TerrainTrend()
    private var terrainCount = 0
    private var terrainLast: RunPoint? = null
    private var terrainEstimate = TerrainEstimate()
    private val elevation = ElevationProgress()
    private val heading = label(context.getString(when (initial.status) {
        RecordingStatus.PAUSED -> R.string.active_paused
        RecordingStatus.INTERRUPTED -> R.string.active_recovered
        else -> R.string.active_recording
    }), 16, bold = true)

    init {
        map.setNavigationMode(true)
        map.setFollowing(true)
        addView(map, LayoutParams(-1, -1))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(floatingButton(context.getString(R.string.main_activities), R.drawable.ic_back).apply {
            contentDescription = context.getString(R.string.active_open_activities)
            setOnClickListener { onOpenRuns() }
        }, LinearLayout.LayoutParams(-2, dp(48)))
        header.addView(floatingButton("", R.drawable.ic_more).apply {
            contentDescription = context.getString(R.string.active_options)
            setPadding(dp(12), 0, dp(12), 0)
            iconPadding = 0
            setOnClickListener { onOptions(this) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        addView(header, LayoutParams(-2, dp(48), Gravity.TOP or Gravity.START).apply {
            topMargin = dp(16); marginStart = dp(16)
        })

        panel.setPadding(dp(16), dp(16), dp(16), dp(12))
        val information = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        information.addView(heading, row(bottom = 4))
        information.addView(gps, row(bottom = 12))
        val metrics = LinearLayout(context)
        listOf(Triple(context.getString(R.string.active_distance), distance, 0), Triple(context.getString(R.string.active_time), time, 1), Triple(context.getString(R.string.active_pace), pace, 2)).forEach { (name, amount, index) ->
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(name, 12, muted = true))
                addView(amount, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(3) })
            }
            metrics.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(8) })
        }
        information.addView(metrics)
        information.addView(terrain, row(top = 4))
        if (!compact) information.addView(extra, row(top = 4))
        val scroll = ScrollView(context).apply {
            isFillViewport = false; clipToPadding = false
            addView(information, android.view.ViewGroup.LayoutParams(-1, -2))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(context)
        val paused = initial.status != RecordingStatus.RECORDING
        controls.addView(action(if (paused) context.getString(R.string.active_resume) else context.getString(R.string.active_pause), if (paused) R.drawable.ic_play else R.drawable.ic_pause).apply {
            setOnClickListener { if (paused) onResume() else onPause() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (paused) controls.addView(action(context.getString(R.string.active_finish), R.drawable.ic_stop, outlined = true).apply {
            setOnClickListener { onFinish() }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        panel.addView(controls, row(top = 12))
        addView(panel, LayoutParams(if (compact) dp(340) else -1, -2, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(16), 0, dp(16), dp(16))
        })
        panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateInsets() }
        update(initial)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        panel.maxHeight = if (compact) (h - dp(88)).coerceAtLeast(dp(150)) else (h * 0.55f).roundToInt().coerceAtLeast(dp(180))
        val params = panel.layoutParams as LayoutParams
        params.width = if (compact) minOf(dp(340), (w * 0.45).roundToInt()) else -1
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { updateInsets() }
    }

    private fun updateInsets() {
        if (panel.height <= 0) return
        if (compact) map.setOverlayInsets(panel.width + dp(32), dp(16), 0, 0)
        else map.setOverlayInsets(0, dp(72), 0, panel.height + dp(16))
    }

    fun update(state: TrackingState) {
        val run = state.run ?: return
        val points = run.points
        panel.setPoints(points)
        if (points.size < terrainCount || (terrainCount > 0 && points.getOrNull(terrainCount - 1) != terrainLast)) {
            terrainTrend.reset(); terrainCount = 0; terrainEstimate = TerrainEstimate()
        }
        for (index in terrainCount until points.size) terrainEstimate = terrainTrend.update(points[index])
        terrainCount = points.size; terrainLast = points.lastOrNull()
        val fresh = terrainLast?.let { System.currentTimeMillis() - it.timestampMs in 0L..15_000L } == true
        terrain.update(if (state.status == RecordingStatus.RECORDING && fresh) terrainEstimate else TerrainEstimate())
        map.setLiveMotionEnabled(state.status == RecordingStatus.RECORDING)
        map.setLocationWaitingMessage(if (state.status == RecordingStatus.RECORDING) state.message else context.getString(if (state.status == RecordingStatus.PAUSED) R.string.active_paused_hint else R.string.active_recovered_hint))
        map.setRoute(points)
        map.setMarker(points.lastOrNull())
        val meters = RunMetrics.distanceMeters(points)
        change(distance, number(meters / 1000, 2))
        change(time, duration(run.activeDurationMs))
        change(pace, pace(RunMetrics.paceSecondsPerKm(meters, run.activeDurationMs)))
        elevation.update(points)
        change(extra, context.getString(R.string.active_elevation, number(elevation.climbedMeters, 0), number(elevation.descendedMeters, 0)))
        val status = when {
            state.status == RecordingStatus.INTERRUPTED -> state.message ?: context.getString(R.string.active_recovered_hint)
            state.status == RecordingStatus.PAUSED -> state.message?.takeUnless { state.messageRes == R.string.tracking_paused } ?: context.getString(R.string.active_paused_hint)
            state.message != null -> state.message
            state.accuracyMeters != null -> context.getString(R.string.active_accuracy, state.accuracyMeters.roundToInt())
            else -> context.getString(R.string.active_loading)
        }
        change(gps, status)
    }

    private fun value() = label("—", 26, bold = true).apply {
        maxLines = 1; gravity = Gravity.CENTER_VERTICAL; fontFeatureSettings = "tnum"
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 16, 26, 1, TypedValue.COMPLEX_UNIT_SP)
    }
    private fun label(textValue: String, size: Int, bold: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = textValue; textSize = size.toFloat(); includeFontPadding = false
        setTextColor(context.getColor(if (muted) R.color.on_surface_variant else R.color.on_surface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun floatingButton(textValue: String, iconId: Int) = MaterialButton(context).apply {
        text = textValue; isAllCaps = false; textSize = 14f
        minHeight = dp(48); minimumHeight = dp(48); minWidth = 0; minimumWidth = 0
        insetTop = 0; insetBottom = 0; cornerRadius = dp(24)
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.surface))
        setTextColor(context.getColor(R.color.on_surface)); iconTint = ColorStateList.valueOf(context.getColor(R.color.on_surface))
        setIconResource(iconId); iconSize = dp(22); iconPadding = dp(8); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        setPadding(dp(16), 0, dp(16), 0)
    }
    private fun action(textValue: String, iconId: Int, outlined: Boolean = false) = MaterialButton(context, null,
        if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
        text = textValue; isAllCaps = false; minHeight = dp(if (compact) 48 else 60); minimumHeight = minHeight
        cornerRadius = dp(16); setIconResource(iconId); iconSize = dp(22); iconPadding = dp(8)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        setPadding(dp(10), dp(8), dp(10), dp(8))
    }
    private fun row(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun change(view: TextView, value: String) { if (view.text != value) view.text = value }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun number(value: Double?, decimals: Int) = if (value == null || !value.isFinite()) "—" else String.format(resources.configuration.locales[0], "%.${decimals}f", value)
    private fun pace(value: Double?): String = if (value == null || !value.isFinite() || value < 0) "—" else value.roundToInt().let { String.format(resources.configuration.locales[0], "%d:%02d", it / 60, it % 60) }
    private fun duration(ms: Long): String { val s = ms / 1000; return if (s >= 3600) String.format(resources.configuration.locales[0], "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format(resources.configuration.locales[0], "%02d:%02d", s / 60, s % 60) }

}
