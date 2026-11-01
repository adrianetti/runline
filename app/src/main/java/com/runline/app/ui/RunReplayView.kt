package com.runline.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.R
import com.runline.app.core.RunReplay
import java.util.Locale
import kotlin.math.roundToInt

/** Playback is view-only. It never changes, starts, or stops a recording. */
class RunReplayView(context: Context, private val replay: RunReplay) : LinearLayout(context) {
    val map = SatelliteMapView(context)
    var positionMs: Long = 0L
        private set
    var playbackSpeed: Int = 1
        private set
    private var playing = false
    private var lastTick = 0L
    private var resumed = false
    private val handler = Handler(Looper.getMainLooper())
    private val play = control("Play", R.drawable.ic_play)
    private val speed = control("1×", R.drawable.ic_clock)
    private val seek = SeekBar(context).apply { max = 1000; contentDescription = "Replay position"; minimumHeight = dp(48) }
    private val clock = label("", 13, muted = true)
    private val speedValue = label("—", 24, bold = true)
    private val paceValue = label("—", 24, bold = true)
    private val altitudeValue = label("—", 24, bold = true)
    private val ascentValue = label("—", 24, bold = true)
    private val distanceValue = label("—", 24, bold = true)
    private val descentValue = label("—", 24, bold = true)
    private val profile = AltitudeProfileView(context, replay)
    private val tick = object : Runnable {
        override fun run() {
            if (!playing || !resumed) return
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastTick).coerceAtLeast(0L)
            lastTick = now
            setPosition((positionMs + delta * playbackSpeed).coerceAtMost(replay.durationMs))
            if (positionMs >= replay.durationMs) pausePlayback() else handler.postDelayed(this, 50L)
        }
    }

    init {
        orientation = VERTICAL
        map.setRoute(replay.segments.flatten())
        addView(map, LayoutParams(-1, dp(if (resources.configuration.screenHeightDp < 500) 160 else 300)))
        val controls = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        controls.addView(play, LayoutParams(0, -2, 1f))
        controls.addView(speed, LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        addView(controls, spacing(top = 8))
        play.isEnabled = replay.durationMs > 0L
        speed.isEnabled = replay.durationMs > 0L
        seek.isEnabled = replay.durationMs > 0L
        play.setOnClickListener { if (playing) pausePlayback() else startPlayback() }
        speed.contentDescription = "Playback speed, 1 times"
        speed.setOnClickListener {
            val values = intArrayOf(1, 5, 10, 30, 60)
            MaterialAlertDialogBuilder(context).setTitle("Playback speed")
                .setSingleChoiceItems(values.map { "${it}×" }.toTypedArray(), values.indexOf(playbackSpeed)) { dialog, index ->
                    // Account for time at the old multiplier before switching.
                    if (playing) tick.run()
                    setPlaybackSpeed(values[index]); lastTick = SystemClock.elapsedRealtime()
                    handler.removeCallbacks(tick); if (playing && resumed) handler.postDelayed(tick, 50)
                    dialog.dismiss()
                }.setNegativeButton("Cancel", null).show()
        }
        addView(seek, spacing())
        addView(clock, spacing(bottom = 16))
        addMetrics("Speed · km/h", speedValue, "Pace /km", paceValue)
        addMetrics("GPS altitude · m", altitudeValue, "Climbed · m", ascentValue)
        addMetrics("Replayed · km", distanceValue, "Descended · m", descentValue)
        addView(label("Altitude", 20, bold = true), spacing(top = 8, bottom = 8))
        addView(profile, LayoutParams(-1, dp(144)))
        addView(label("GPS time · pauses and gaps skipped", 13, muted = true), spacing(top = 8))
        setPosition(0L)
    }

    fun restore(position: Long, multiplier: Int) {
        setPlaybackSpeed(multiplier)
        setPosition(position.coerceIn(0L, replay.durationMs))
    }

    fun onHostResume() { resumed = true; map.onHostResume() }
    fun onHostPause() { resumed = false; pausePlayback(); map.onHostPause() }
    fun dispose() { resumed = false; pausePlayback(); map.dispose() }
    override fun onDetachedFromWindow() { pausePlayback(); super.onDetachedFromWindow() }

    private fun startPlayback() {
        if (replay.durationMs <= 0L || !resumed) return
        if (positionMs >= replay.durationMs) setPosition(0L)
        playing = true; lastTick = SystemClock.elapsedRealtime()
        play.text = "Pause"; play.setIconResource(R.drawable.ic_pause)
        handler.removeCallbacks(tick); handler.post(tick)
    }
    private fun pausePlayback() {
        playing = false; handler.removeCallbacks(tick)
        play.text = if (positionMs >= replay.durationMs && replay.durationMs > 0) "Replay" else "Play"
        play.setIconResource(R.drawable.ic_play)
    }
    private fun setPlaybackSpeed(value: Int) {
        playbackSpeed = value.takeIf { it in listOf(1, 5, 10, 30, 60) } ?: 1
        speed.text = "$playbackSpeed×"; speed.contentDescription = "Playback speed, $playbackSpeed times"
    }
    private fun setPosition(value: Long) {
        positionMs = value.coerceIn(0L, replay.durationMs)
        val frame = replay.at(positionMs)
        map.setMarker(frame?.point)
        setText(speedValue, number(frame?.speedMetersPerSecond?.times(3.6), 1))
        setText(paceValue, pace(frame?.paceSecondsPerKm))
        setText(altitudeValue, number(frame?.point?.altitudeMeters, 0))
        setText(ascentValue, number(frame?.elevationGainMeters, 0))
        setText(descentValue, number(frame?.elevationLossMeters, 0))
        setText(distanceValue, number(frame?.distanceMeters?.div(1000), 2))
        setText(clock, "${duration(positionMs)} / ${duration(replay.durationMs)}")
        seek.progress = if (replay.durationMs > 0) (positionMs.toDouble() / replay.durationMs * seek.max).roundToInt() else 0
        val description = "Replay position ${duration(positionMs)} of ${duration(replay.durationMs)}"
        if (seek.contentDescription != description) seek.contentDescription = description
        profile.positionMs = positionMs
    }
    private fun addMetrics(left: String, leftValue: TextView, right: String, rightValue: TextView) {
        fun column(name: String, value: TextView) = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(label(name, 13, muted = true)); addView(value, spacing(top = 5))
        }
        val row = LinearLayout(context)
        row.addView(column(left, leftValue), LayoutParams(0, -2, 1f))
        row.addView(column(right, rightValue), LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        addView(row, spacing(bottom = 16))
    }
    private fun label(value: String, size: Int, bold: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); includeFontPadding = false; fontFeatureSettings = "tnum"
        setTextColor(context.getColor(if (muted) R.color.on_surface_variant else R.color.on_surface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun control(value: String, icon: Int) = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = value; isAllCaps = false; textSize = 14f; minWidth = 0; minimumWidth = 0
        minHeight = dp(52); minimumHeight = dp(52); cornerRadius = dp(12)
        setIconResource(icon); iconSize = dp(20); iconPadding = dp(5); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        setPadding(dp(6), dp(8), dp(6), dp(8))
    }
    private fun spacing(top: Int = 0, bottom: Int = 0) = LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun setText(view: TextView, value: String) { if (view.text != value) view.text = value }
    private fun number(value: Double?, decimals: Int): String = if (value == null || !value.isFinite()) "—" else String.format(Locale.getDefault(), "%.${decimals}f", value)
    private fun pace(value: Double?): String = if (value == null || !value.isFinite() || value < 0) "—" else value.roundToInt().let { String.format(Locale.getDefault(), "%d:%02d", it / 60, it % 60) }
    private fun duration(ms: Long): String { val s = ms / 1000; return if (s >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60) }

    init {
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) setPosition((progress.toDouble() / bar.max * replay.durationMs).toLong())
            }
            override fun onStartTrackingTouch(bar: SeekBar) { pausePlayback() }
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }
}

/** Original altitude fixes preserve every missing-data boundary; the path is cached on resize. */
private class AltitudeProfileView(context: Context, replay: RunReplay) : View(context) {
    private data class Sample(val time: Long, val altitude: Double?, val segment: Int)
    private val samples: List<Sample> = buildList {
        var offset = 0L
        replay.segments.forEachIndexed { index, points ->
            if (points.isNotEmpty()) {
                val first = points.first().timestampMs
                points.forEach { add(Sample(offset + it.timestampMs - first, it.altitudeMeters?.takeIf(Double::isFinite), index)) }
                offset += points.last().timestampMs - first
            }
        }
    }
    private val durationMs = replay.durationMs
    private val heights = samples.mapNotNull { it.altitude }
    private val low = heights.minOrNull()
    private val high = heights.maxOrNull()
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    var positionMs = 0L
        set(value) { field = value; invalidate() }
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = if (low == null || high == null) "No GPS altitude data" else "GPS altitude profile, ${low.roundToInt()} to ${high.roundToInt()} metres. Replay position is controlled by the slider above."
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        path.reset()
        if (low == null || high == null) return
        val d = resources.displayMetrics.density
        val top = 22 * d; val bottom = h - 24 * d; val left = 2 * d; val right = w - 2 * d
        val range = (high - low).coerceAtLeast(1.0)
        var previous: Sample? = null
        samples.forEach { sample ->
            val altitude = sample.altitude
            if (altitude == null) { previous = null; return@forEach }
            val x = if (durationMs > 0) left + (sample.time.toDouble() / durationMs * (right - left)).toFloat() else left
            val y = (bottom - (altitude - low) / range * (bottom - top)).toFloat()
            if (previous == null || previous?.segment != sample.segment) path.moveTo(x, y) else path.lineTo(x, y)
            previous = sample
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        ink.color = context.getColor(R.color.on_surface_variant)
        ink.textSize = 12 * resources.displayMetrics.scaledDensity
        ink.style = Paint.Style.FILL
        if (low == null || high == null) {
            ink.textAlign = Paint.Align.CENTER
            canvas.drawText("No GPS altitude data", width / 2f, height / 2f, ink)
            return
        }
        val top = 22 * d; val bottom = height - 24 * d; val left = 2 * d; val right = width - 2 * d
        ink.textAlign = Paint.Align.LEFT
        canvas.drawText("${high.roundToInt()} m", left, 14 * d, ink)
        canvas.drawText("${low.roundToInt()} m", left, height - 3 * d, ink)
        ink.style = Paint.Style.STROKE; ink.strokeWidth = 2 * d; ink.strokeJoin = Paint.Join.ROUND
        ink.color = context.getColor(R.color.primary); canvas.drawPath(path, ink)
        val x = if (durationMs > 0L) left + (positionMs.toDouble() / durationMs * (right - left)).toFloat() else left
        ink.color = context.getColor(R.color.outline); ink.strokeWidth = d
        canvas.drawLine(x, top, x, bottom, ink)
        ink.style = Paint.Style.FILL
    }
}
