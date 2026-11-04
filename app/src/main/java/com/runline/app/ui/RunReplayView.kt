package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.R
import com.runline.app.ai.AiSettings
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunReplay
import com.runline.app.core.ReplayPresentation
import com.runline.app.core.LapAnalysis
import com.runline.app.core.RoutineType
import com.runline.app.core.ReplayTerrain
import com.runline.app.core.TerrainEstimate
import com.runline.app.data.RunStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Full-screen, display-only playback. Opening details never changes the saved activity. */
class RunReplayView(
    context: Context,
    private val replay: RunReplay,
    onBack: () -> Unit = {},
    onExport: () -> Unit = {},
    onMore: (View) -> Unit = {},
    private val onEdit: () -> Unit = {},
    private val onReview: () -> Unit = {},
) : FrameLayout(context) {
    val map = SatelliteMapView(context)
    var positionMs: Long = 0L
        private set
    var playbackSpeed: Int = ReplayPresentation.defaultSpeed(replay.durationMs)
        private set
    private var playing = false
    private var lastTick = 0L
    private var resumed = false
    private var disposed = false
    private var fitted = false
    private var details: BottomSheetDialog? = null
    private var speedDialog: androidx.appcompat.app.AlertDialog? = null
    private var statsDialog: androidx.appcompat.app.AlertDialog? = null
    private var playbackGeneration = 0L
    private val compact = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val handler = Handler(Looper.getMainLooper())
    private val panel = ElevationIsland(context)
    private val readout = RunReadoutView(context)
    private val replayTerrain = ReplayTerrain(replay)
    private val play = control("", R.drawable.ic_play).apply {
        contentDescription = context.getString(R.string.replay_play)
        iconPadding = 0
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.primary))
        iconTint = ColorStateList.valueOf(context.getColor(R.color.on_primary))
    }
    private val speed = control(context.getString(R.string.replay_multiplier, 1), R.drawable.ic_clock)
    private val seek = SeekBar(context).apply {
        max = 10_000; minimumHeight = dp(48)
        contentDescription = context.getString(R.string.replay_position)
    }
    private val clock = label("", 12, muted = true)
    private val tick = object : Runnable {
        override fun run() {
            if (!playing || !resumed || disposed) return
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastTick).coerceAtLeast(0L)
            lastTick = now
            setPosition((positionMs + delta * playbackSpeed).coerceAtMost(replay.durationMs))
            if (positionMs >= replay.durationMs) pausePlayback() else handler.postDelayed(this, 33L)
        }
    }

    init {
        map.setReplayMode()
        val displayPoints = replay.segments.flatMapIndexed { index, points -> points.map { it.copy(segment = index) } }
        map.setRoute(displayPoints)
        panel.setReplay(replay)
        replay.at(0)?.point?.let { map.setCenter(it.latitude, it.longitude) }
        addView(map, LayoutParams(-1, -1))
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(floating(context.getString(R.string.replay_back), R.drawable.ic_back).apply {
            contentDescription = context.getString(R.string.replay_back_to_activities)
            setOnClickListener { pausePlayback(); onBack() }
        }, LinearLayout.LayoutParams(-2, dp(48)))
        top.addView(floating("", R.drawable.ic_more).apply {
            contentDescription = context.getString(R.string.replay_options)
            iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { pausePlayback(); onMore(this) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        addView(top, LayoutParams(-2, dp(48), Gravity.TOP or Gravity.START).apply {
            marginStart = dp(16); topMargin = dp(16)
        })
        addView(floating("", R.drawable.ic_download).apply {
            contentDescription = context.getString(R.string.replay_export_file)
            iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
            isEnabled = replay.durationMs > 0L
            setOnClickListener { pausePlayback(); onExport() }
        }, LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
            marginEnd = dp(16); topMargin = dp(16)
        })

        panel.setPadding(dp(20), dp(if (compact) 12 else 16), dp(20), dp(12))
        val information = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setOnClickListener { showDetails() }
        }
        information.addView(readout, row())
        panel.setChartContent(readout)
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            addView(information, android.view.ViewGroup.LayoutParams(-1, -2))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(context)
        controls.addView(play, LinearLayout.LayoutParams(dp(64), dp(48)))
        controls.addView(speed, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
        controls.addView(control("", R.drawable.ic_info).apply {
            contentDescription = context.getString(R.string.replay_open_details)
            iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { showDetails() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        panel.addView(controls, row(top = if (compact) 8 else 12))
        val timeline = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        timeline.addView(seek, LinearLayout.LayoutParams(0, dp(48), 1f))
        timeline.addView(clock, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        panel.addView(timeline)
        addView(panel, LayoutParams(if (compact) dp(340) else -1, -2, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(dp(16), 0, dp(16), dp(16))
        })
        panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateInsets() }
        play.isEnabled = replay.durationMs > 0L
        speed.isEnabled = replay.durationMs > 0L
        seek.isEnabled = replay.durationMs > 0L
        play.setOnClickListener { if (playing) pausePlayback() else startPlayback() }
        speed.setOnClickListener {
            val wasPlaying = playing
            val generation = playbackGeneration
            pausePlayback()
            val values = ReplayPresentation.speeds
            val dialog = MaterialAlertDialogBuilder(context).setTitle(R.string.replay_playback_speed)
                .setSingleChoiceItems(values.map { context.getString(R.string.replay_multiplier, it) }.toTypedArray(), values.indexOf(playbackSpeed)) { dialog, index ->
                    setPlaybackSpeed(values[index]); dialog.dismiss()
                }.setNegativeButton(R.string.replay_cancel, null).create()
            speedDialog = dialog
            dialog.setOnDismissListener {
                speedDialog = null
                if (wasPlaying && generation == playbackGeneration && resumed && !disposed) startPlayback()
            }
            dialog.show()
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) setPosition((progress.toDouble() / bar.max * replay.durationMs).toLong(), smoothHeading = false)
            }
            override fun onStartTrackingTouch(bar: SeekBar) { pausePlayback() }
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        setPlaybackSpeed(playbackSpeed)
        setPosition(0L, smoothHeading = false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        // Apply the cap before measuring children. A requestLayout from onSizeChanged
        // can be swallowed by the current layout pass, leaving the header covered.
        panel.maxHeight = if (compact) (h - dp(88)).coerceAtLeast(dp(180)) else (h * 0.55f).roundToInt().coerceAtLeast(dp(200))
        val params = panel.layoutParams as LayoutParams
        params.width = if (compact) minOf(dp(340), (w * 0.45).roundToInt()) else -1
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { updateInsets() }
    }

    private fun updateInsets() {
        if (disposed || panel.height <= 0) return
        if (compact) map.setOverlayInsets(panel.width + dp(32), dp(72), 0, 0)
        else map.setOverlayInsets(0, dp(72), 0, panel.height + dp(16))
        if (!fitted) { fitted = true; map.setFollowing(true) }
    }

    fun restore(position: Long, multiplier: Int) {
        setPlaybackSpeed(multiplier.takeIf { it in ReplayPresentation.speeds } ?: ReplayPresentation.defaultSpeed(replay.durationMs))
        setPosition(position.coerceIn(0L, replay.durationMs), smoothHeading = false)
    }
    fun onHostResume() { resumed = true; map.onHostResume() }
    fun onHostPause() { resumed = false; playbackGeneration++; pausePlayback(); map.onHostPause() }
    fun dispose() { disposed = true; resumed = false; pausePlayback(); details?.dismiss(); details = null; speedDialog?.dismiss(); speedDialog = null; statsDialog?.dismiss(); statsDialog = null; map.dispose() }
    override fun onDetachedFromWindow() { pausePlayback(); super.onDetachedFromWindow() }

    private fun startPlayback() {
        if (replay.durationMs <= 0L || !resumed || disposed) return
        if (positionMs >= replay.durationMs) setPosition(0L, smoothHeading = false)
        playing = true; lastTick = SystemClock.elapsedRealtime()
        play.contentDescription = context.getString(R.string.replay_pause); play.setIconResource(R.drawable.ic_pause)
        handler.removeCallbacks(tick); handler.post(tick)
    }
    private fun pausePlayback() {
        playing = false; handler.removeCallbacks(tick); readout.settle()
        play.contentDescription = context.getString(if (positionMs >= replay.durationMs && replay.durationMs > 0) R.string.replay_restart else R.string.replay_play)
        play.setIconResource(R.drawable.ic_play)
    }
    private fun setPlaybackSpeed(value: Int) {
        playbackSpeed = value.takeIf { it in ReplayPresentation.speeds } ?: ReplayPresentation.defaultSpeed(replay.durationMs)
        speed.text = context.getString(R.string.replay_multiplier, playbackSpeed)
        speed.contentDescription = context.getString(R.string.replay_speed_description, playbackSpeed)
    }
    private fun setPosition(value: Long, smoothHeading: Boolean = true) {
        val previousSegment = replay.at(positionMs)?.segmentIndex
        positionMs = value.coerceIn(0L, replay.durationMs)
        val frame = replay.at(positionMs)
        panel.setPosition(positionMs, frame?.segmentIndex)
        val animate = smoothHeading && playing && previousSegment == frame?.segmentIndex
        map.setReplayMarker(frame?.point, ReplayPresentation.bearing(replay, positionMs), smoothHeading)
        readout.update(frame?.paceSecondsPerKm, frame?.distanceMeters, positionMs,
            frame?.let { replayTerrain.at(positionMs, it.segmentIndex) } ?: TerrainEstimate(),
            frame?.elevationGainMeters, frame?.elevationLossMeters, animate = animate)
        setText(clock, duration(replay.durationMs))
        clock.contentDescription = context.getString(R.string.replay_time_of, duration(positionMs), duration(replay.durationMs))
        seek.progress = if (replay.durationMs > 0) (positionMs.toDouble() / replay.durationMs * seek.max).roundToInt() else 0
        val description = context.getString(R.string.replay_position_description, duration(positionMs), duration(replay.durationMs))
        if (seek.contentDescription != description) seek.contentDescription = description
    }

    /** Pause on the selected frame so its detail values and profile cursor remain stable. */
    private fun showDetails() {
        if (disposed || details?.isShowing == true) return
        pausePlayback()
        val run = replay.session
        val summary = replay.summary
        val frame = replay.at(positionMs)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(32))
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(context.getString(R.string.replay_activity_details), 24, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(floating("", R.drawable.ic_close).apply {
            contentDescription = context.getString(R.string.replay_close_details)
            iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { details?.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.setPadding(dp(24), dp(12), dp(16), dp(8))
        val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale()).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(run.startedAtMs))
        content.addView(label(date, 13, muted = true), row(top = 4, bottom = 20))
        val notes = runCatching { RunStore(context).details(run.id) }.getOrNull()
        notes?.title?.takeIf { it.isNotBlank() }?.let { content.addView(label(it, 22, bold = true), row(bottom = 12)) }
        notes?.description?.takeIf { it.isNotBlank() }?.let { content.addView(label(it, 16), row(bottom = 12)) }
        notes?.effort?.let { addDetail(content, R.string.details_effort, context.getString(R.string.details_effort_value, it)) }
        notes?.routine?.takeIf { it != RoutineType.NONE }?.let { routine ->
            addDetail(content, R.string.details_routine, context.getString(when(routine) {
                RoutineType.EASY -> R.string.details_routine_easy; RoutineType.LONG -> R.string.details_routine_long
                RoutineType.TEMPO -> R.string.details_routine_tempo; RoutineType.INTERVALS -> R.string.details_routine_intervals
                RoutineType.RECOVERY -> R.string.details_routine_recovery; RoutineType.RACE -> R.string.details_routine_race
                RoutineType.OTHER -> R.string.details_routine_other; RoutineType.NONE -> R.string.details_routine_none
            }))
        }
        notes?.photoIds?.takeIf { it.isNotEmpty() }?.let {
            content.addView(ActivityPhotosView(context, run.id, it), row(top = 8, bottom = 16))
        }
        content.addView(control(context.getString(R.string.v7_edit_activity), R.drawable.ic_edit).apply {
            setOnClickListener { details?.dismiss(); onEdit() }
        }, row(bottom = 8))
        if (AiSettings.isReady(context)) {
            content.addView(control(context.getString(R.string.v7_run_review), R.drawable.ic_info).apply {
                setOnClickListener { details?.dismiss(); onReview() }
            }, row(bottom = 20))
        }
        addDetail(content, R.string.replay_total_distance, context.getString(R.string.replay_kilometres, number(summary.distanceMeters / 1000, 2)))
        addDetail(content, R.string.replay_active_time, duration(run.activeDurationMs))
        addDetail(content, R.string.replay_average_pace, context.getString(R.string.replay_per_km, pace(RunMetrics.paceSecondsPerKm(summary.distanceMeters, run.activeDurationMs))))
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(context.getString(R.string.replay_elevation), 22, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(floating("", R.drawable.ic_info).apply {
                contentDescription = context.getString(R.string.map_v8_stats_info)
                iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { showStatsInfo() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }, row(top = 16, bottom = 8))
        val elevation = LinearLayout(context)
        listOf(R.string.replay_total_climbed to summary.elevationGainMeters, R.string.replay_total_descended to summary.elevationLossMeters).forEachIndexed { index, (name, amount) ->
            elevation.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(context.getString(name), 13, muted = true))
                addView(label(context.getString(R.string.replay_metres, number(amount, 0)), 26, bold = true), row(top = 6))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(12) })
        }
        content.addView(elevation)
        val profile = ElevationProfileView(context, replay).apply { selectedPositionMs = positionMs }
        content.addView(profile, LinearLayout.LayoutParams(-1, dp(160)).apply { topMargin = dp(16) })
        content.addView(label(context.getString(R.string.replay_more_statistics), 22, bold = true), row(top = 24, bottom = 8))
        addDetail(content, R.string.replay_average_speed, context.getString(R.string.replay_kmh, number(summary.averageSpeedMetersPerSecond?.times(3.6), 1)))
        addDetail(content, R.string.replay_peak_speed, context.getString(R.string.replay_kmh, number(summary.maxSpeedMetersPerSecond?.times(3.6), 1)))
        addDetail(content, R.string.replay_elapsed_time, duration((run.endedAtMs - run.startedAtMs).coerceAtLeast(0)))
        addDetail(content, R.string.replay_observed_time, duration(replay.durationMs))
        addDetail(content, R.string.replay_current_altitude, context.getString(R.string.replay_metres, number(frame?.point?.altitudeMeters, 0)))
        addDetail(content, R.string.replay_altitude_range, context.getString(R.string.replay_metres_range, number(summary.minAltitudeMeters, 0), number(summary.maxAltitudeMeters, 0)))
        addDetail(content, R.string.replay_gps_samples, java.text.NumberFormat.getIntegerInstance(locale()).format(run.points.size))
        content.addView(label(context.getString(R.string.replay_splits), 22, bold = true), row(top = 24, bottom = 8))
        // Computed only when details open, never for each playback frame.
        val splits = RunMetrics.splits(run.copy(points = replay.segments.flatten()))
        if (splits.isEmpty()) content.addView(label(context.getString(R.string.replay_not_enough_gps), 14, muted = true))
        else splits.forEach { split ->
            val name = if (split.distanceMeters >= 999.5) context.getString(R.string.replay_km_split, split.index)
                else context.getString(R.string.replay_last_split, number(split.distanceMeters / 1000, 2))
            addDetail(content, name, context.getString(R.string.replay_per_km, pace(RunMetrics.paceSecondsPerKm(split.distanceMeters, split.durationMs))))
        }
        val laps = LapAnalysis.analyze(run.points)
        if (laps.laps.isNotEmpty()) {
            content.addView(label(context.getString(R.string.map_v8_estimated_laps), 22, bold = true), row(top = 24, bottom = 8))
            laps.laps.forEach { lap ->
                addDetail(content, context.getString(R.string.v7_lap_number, lap.number),
                    context.getString(R.string.v7_lap_stats, number(lap.distanceMeters / 1000, 2), duration(lap.durationMs),
                        pace(RunMetrics.paceSecondsPerKm(lap.distanceMeters, lap.durationMs))))
            }
            laps.latestComparison?.slowerSector?.let { sector ->
                content.addView(label(context.getString(R.string.v7_lap_sector_comparison, sector.number,
                    number(sector.paceChangePercent, 0)), 14, muted = true), row(top = 8))
            }
        }
        val scroll = ScrollView(context).apply { addView(content) }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(-1, -2))
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        details = BottomSheetDialog(context).also { dialog ->
            dialog.setContentView(body)
            val activity = context as? AppCompatActivity
            if (activity != null) StableSheet.configure(dialog, activity)
            else {
                dialog.setCanceledOnTouchOutside(false)
                dialog.setOnShowListener {
                    dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                        sheet.setBackgroundColor(context.getColor(R.color.surface))
                        sheet.layoutParams = sheet.layoutParams.apply { height = (resources.displayMetrics.heightPixels * .92f).roundToInt() }
                        BottomSheetBehavior.from(sheet).apply {
                            state = BottomSheetBehavior.STATE_EXPANDED; skipCollapsed = true; isDraggable = false
                        }
                    }
                }
            }
            dialog.setOnDismissListener { details = null }
            dialog.show()
        }
    }

    private fun showStatsInfo() {
        if (disposed || statsDialog?.isShowing == true) return
        statsDialog = MaterialAlertDialogBuilder(context).setTitle(R.string.map_v8_stats_info)
            .setMessage(listOf(R.string.replay_profile_note, R.string.replay_estimate_note,
                R.string.replay_timing_note, R.string.v7_lap_note).joinToString("\n\n") { context.getString(it) })
            .setPositiveButton(R.string.map_close, null).create().also { dialog ->
                dialog.setOnDismissListener { statsDialog = null }
                dialog.show()
            }
    }

    private fun addDetail(parent: LinearLayout, resource: Int, amount: String) = addDetail(parent, context.getString(resource), amount)
    private fun addDetail(parent: LinearLayout, name: String, amount: String) {
        val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        line.addView(label(name, 14, muted = true), LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(label(amount, 16, bold = true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        parent.addView(line)
        parent.addView(View(context).apply { setBackgroundColor(context.getColor(R.color.surface_variant)) }, LinearLayout.LayoutParams(-1, dp(1)))
    }
    private fun label(value: String, size: Int, bold: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); includeFontPadding = false; fontFeatureSettings = "tnum"
        setTextColor(context.getColor(if (muted) R.color.on_surface_variant else R.color.on_surface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun control(value: String, icon: Int) = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        text = value; isAllCaps = false; textSize = 14f; minWidth = 0; minimumWidth = 0
        minHeight = dp(48); minimumHeight = dp(48); insetTop = 0; insetBottom = 0; cornerRadius = dp(12)
        setIconResource(icon); iconSize = dp(20); iconPadding = dp(5); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        setPadding(dp(6), dp(6), dp(6), dp(6))
    }
    private fun floating(value: String, icon: Int) = MaterialButton(context).apply {
        text = value; isAllCaps = false; textSize = 14f
        minHeight = dp(48); minimumHeight = dp(48); minWidth = 0; minimumWidth = 0
        insetTop = 0; insetBottom = 0; cornerRadius = dp(24)
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.surface))
        setTextColor(context.getColor(R.color.on_surface)); iconTint = ColorStateList.valueOf(context.getColor(R.color.on_surface))
        setIconResource(icon); iconSize = dp(22); iconPadding = dp(8); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        setPadding(dp(16), 0, dp(16), 0)
    }
    private fun row(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun setText(view: TextView, value: String) { if (view.text != value) view.text = value }
    private fun locale() = resources.configuration.locales[0] ?: Locale.getDefault()
    private fun number(value: Double?, decimals: Int): String = if (value == null || !value.isFinite()) context.getString(R.string.replay_unavailable) else String.format(locale(), "%.${decimals}f", value)
    private fun pace(value: Double?): String = if (value == null || !value.isFinite() || value < 0) context.getString(R.string.replay_unavailable) else value.roundToInt().let { String.format(locale(), "%d:%02d", it / 60, it % 60) }
    private fun duration(ms: Long): String { val s = ms.coerceAtLeast(0) / 1000; return if (s >= 3600) String.format(locale(), "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) else String.format(locale(), "%02d:%02d", s / 60, s % 60) }

}

/** Relative elevation preserves climbs, descents and every missing-data boundary. */
private class ElevationProfileView(context: Context, replay: RunReplay) : View(context) {
    private data class Sample(val time: Long, val elevation: Double?, val segment: Int)
    private val samples: List<Sample> = buildList {
        var offset = 0L
        val baseline = replay.segments.asSequence().flatten().mapNotNull { it.altitudeMeters?.takeIf(Double::isFinite) }.firstOrNull()
        replay.segments.forEachIndexed { index, points ->
            if (points.isNotEmpty()) {
                val first = points.first().timestampMs
                points.forEach { point -> add(Sample(offset + point.timestampMs - first,
                    point.altitudeMeters?.takeIf(Double::isFinite)?.let { altitude -> baseline?.let { altitude - it } }, index)) }
                offset += points.last().timestampMs - first
            }
        }
    }
    private val durationMs = replay.durationMs
    private val heights = samples.mapNotNull { it.elevation }
    private val low = heights.minOrNull()
    private val high = heights.maxOrNull()
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    var selectedPositionMs = 0L
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = if (low == null || high == null) context.getString(R.string.replay_no_elevation)
            else context.getString(R.string.replay_profile_description, signed(low), signed(high))
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        path.reset()
        if (low == null || high == null) return
        val d = resources.displayMetrics.density
        val top = 24 * d; val bottom = h - 24 * d; val left = 2 * d; val right = w - 2 * d
        val range = (high - low).coerceAtLeast(1.0)
        var previous: Sample? = null
        samples.forEach { sample ->
            val elevation = sample.elevation
            if (elevation == null) { previous = null; return@forEach }
            val x = if (durationMs > 0) left + (sample.time.toDouble() / durationMs * (right - left)).toFloat() else left
            val y = (bottom - (elevation - low) / range * (bottom - top)).toFloat()
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
            canvas.drawText(context.getString(R.string.replay_no_elevation), width / 2f, height / 2f, ink)
            return
        }
        val top = 24 * d; val bottom = height - 24 * d; val left = 2 * d; val right = width - 2 * d
        ink.textAlign = Paint.Align.LEFT
        canvas.drawText(context.getString(R.string.replay_metres, signed(high)), left, 16 * d, ink)
        canvas.drawText(context.getString(R.string.replay_metres, signed(low)), left, height - 3 * d, ink)
        ink.style = Paint.Style.STROKE; ink.strokeWidth = 2 * d; ink.strokeJoin = Paint.Join.ROUND
        ink.color = context.getColor(R.color.primary); canvas.drawPath(path, ink)
        val x = if (durationMs > 0L) left + (selectedPositionMs.toDouble() / durationMs * (right - left)).toFloat() else left
        ink.color = context.getColor(R.color.outline); ink.strokeWidth = d
        canvas.drawLine(x, top, x, bottom, ink)
        ink.style = Paint.Style.FILL
    }
    private fun signed(value: Double): String = String.format(resources.configuration.locales[0], "%+.0f", value)
}
