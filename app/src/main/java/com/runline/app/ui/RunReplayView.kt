package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
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

/**
 * Full-screen, display-only playback. Opening details never changes the saved activity.
 * With [preview], it plays a planned route instead: no export, options or activity details.
 */
class RunReplayView(
    context: Context,
    private val replay: RunReplay,
    private val preview: Boolean = false,
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
    private var statsDialog: androidx.appcompat.app.AlertDialog? = null
    private var playbackGeneration = 0L
    private val compact = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp
    private val handler = Handler(Looper.getMainLooper())
    private val panel = ElevationIsland(context)
    private val readout = RunReadoutView(context)
    private val terrainRepository = com.runline.app.terrain.TerrainRepository.get(context)
    /** A previewed route with its own heights shows them; terrain maps fill in only without. */
    private val routeHeights = preview && com.runline.app.core.RoutePreview.hasHeights(replay.session)
    private var elevationReplay = if (routeHeights) replay else RunReplay(terrainRepository.snapshot.project(replay.session))
    private var replayTerrain = ReplayTerrain(elevationReplay)
    private var terrainRevision = -1L
    private var terrainPending = false
    private var terrainStatus = ""
    private var terrainDetailsChanged: (() -> Unit)? = null
    private val terrainListener: () -> Unit = { refreshTerrain() }
    private val play = control("", R.drawable.ic_play).apply {
        contentDescription = context.getString(R.string.replay_play)
        iconPadding = 0
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.primary))
        iconTint = ColorStateList.valueOf(context.getColor(R.color.on_primary))
    }
    private val speed = SeekBar(context).apply { max = ReplayPresentation.speeds.lastIndex; contentDescription = context.getString(R.string.replay_playback_speed) }
    private val speedValue = label("", 14)
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
        map.setProgressiveRoute(true)
        val displayPoints = replay.segments.flatMapIndexed { index, points -> points.map { it.copy(segment = index) } }
        map.setRoute(displayPoints)
        panel.setReplay(elevationReplay)
        replay.at(0)?.point?.let { map.setCenter(it.latitude, it.longitude) }
        addView(map, LayoutParams(-1, -1))
        val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(floating(context.getString(if (preview) R.string.v20_preview_back else R.string.replay_back), R.drawable.ic_back).apply {
            contentDescription = context.getString(if (preview) R.string.v20_preview_back_description else R.string.replay_back_to_activities)
            setOnClickListener { pausePlayback(); onBack() }
        }, LinearLayout.LayoutParams(-2, dp(48)))
        if (!preview) top.addView(floating("", R.drawable.ic_more).apply {
            contentDescription = context.getString(R.string.replay_options)
            iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { pausePlayback(); onMore(this) }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        addView(top, LayoutParams(-2, dp(48), Gravity.TOP or Gravity.START).apply {
            marginStart = dp(16); topMargin = dp(16)
        })
        if (!preview) addView(floating("", R.drawable.ic_download).apply {
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
            if (!preview) setOnClickListener { showDetails() }
        }
        information.addView(readout, row())
        panel.setChartContent(readout)
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            addView(information, android.view.ViewGroup.LayoutParams(-1, -2))
        }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val stackSpeed = resources.configuration.fontScale > 1.5f || resources.configuration.screenWidthDp < 360
        val controls = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        controls.addView(play, LinearLayout.LayoutParams(dp(64), dp(48)))
        val speedColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        speedColumn.addView(speed, LinearLayout.LayoutParams(-1, dp(48)))
        val endpoints = LinearLayout(context).apply { setPadding(dp(16), 0, dp(16), 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        endpoints.addView(label(context.getString(R.string.replay_multiplier, 1), 12, muted = true), LinearLayout.LayoutParams(0, -2, 1f))
        endpoints.addView(label(context.getString(R.string.replay_multiplier, 480), 12, muted = true))
        speedColumn.addView(endpoints)
        if (stackSpeed) controls.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        else controls.addView(speedColumn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        controls.addView(speedValue, LinearLayout.LayoutParams(-2, -1).apply { marginStart = dp(4) })
        speedValue.gravity = Gravity.CENTER_VERTICAL
        if (!preview) controls.addView(control("", R.drawable.ic_info).apply {
            contentDescription = context.getString(R.string.replay_open_details)
            iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { showDetails() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        panel.addView(controls, row(top = if (compact) 8 else 12))
        if (stackSpeed) panel.addView(speedColumn, row())
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
        speed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) setPlaybackSpeed(ReplayPresentation.speeds[progress]) }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
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
    fun onHostResume() { resumed = true; map.onHostResume(); if (!routeHeights) terrainRepository.request(replay.session.points); refreshTerrain() }
    fun onHostPause() { resumed = false; playbackGeneration++; pausePlayback(); map.onHostPause() }
    fun dispose() { terrainRepository.unlisten(terrainListener); disposed = true; resumed = false; pausePlayback(); details?.dismiss(); details = null; statsDialog?.dismiss(); statsDialog = null; map.dispose() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!routeHeights) { terrainRepository.listen(terrainListener); terrainRepository.request(replay.session.points) }
        refreshTerrain()
    }
    override fun onDetachedFromWindow() { terrainRepository.unlisten(terrainListener); pausePlayback(); super.onDetachedFromWindow() }
    private fun refreshTerrain() {
        if (disposed) return
        if (routeHeights) { setPosition(positionMs, smoothHeading = false); return }
        val snapshot = terrainRepository.snapshot
        if (snapshot.revision != terrainRevision && !snapshot.hasTiles) {
            elevationReplay = RunReplay(snapshot.project(replay.session)); replayTerrain = ReplayTerrain(elevationReplay)
            terrainRevision = snapshot.revision; panel.setReplay(elevationReplay); terrainDetailsChanged?.invoke()
        }
        if (snapshot.revision != terrainRevision && !terrainPending) {
            terrainPending = true
            terrainRepository.replay(replay.session) { revision, resolved, terrain ->
                terrainPending = false
                if (!disposed) {
                    if (revision == terrainRepository.snapshot.revision) {
                        elevationReplay = resolved; replayTerrain = terrain; terrainRevision = revision
                        panel.setReplay(elevationReplay); terrainDetailsChanged?.invoke()
                    }
                    refreshTerrain()
                }
            }
        }
        val status = TerrainUi.status(context, replay.session.points, elevationReplay.session.points, terrainRepository)
        if (status != terrainStatus) { terrainStatus = status; terrainDetailsChanged?.invoke() }
        setPosition(positionMs, smoothHeading = false)
    }

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
        if (playing) {
            val now = SystemClock.elapsedRealtime()
            setPosition((positionMs + (now - lastTick).coerceAtLeast(0L) * playbackSpeed).coerceAtMost(replay.durationMs))
            lastTick = now
        }
        playbackSpeed = value.takeIf { it in ReplayPresentation.speeds } ?: ReplayPresentation.defaultSpeed(replay.durationMs)
        speed.progress = ReplayPresentation.speeds.indexOf(playbackSpeed)
        speedValue.text = context.getString(R.string.replay_multiplier, playbackSpeed)
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
            elevationReplay.at(positionMs)?.elevationGainMeters, elevationReplay.at(positionMs)?.elevationLossMeters, animate = animate,
            profile = frame?.let { panel.slopeProfile(positionMs, it.segmentIndex) } ?: com.runline.app.core.SlopeProfile.EMPTY)
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
        var actions: androidx.appcompat.widget.PopupMenu? = null
        header.addView(floating("", R.drawable.ic_more).apply {
            contentDescription = context.getString(R.string.replay_options)
            iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener {
                actions = androidx.appcompat.widget.PopupMenu(context, this).apply {
                    menu.add(0, 1, 0, R.string.activity_action_edit).setIcon(R.drawable.ic_edit).icon?.mutate()?.setTint(context.getColor(R.color.on_surface))
                    if (AiSettings.isReady(context) || com.runline.app.ai.AiReviewStore(context).get(run.id) != null)
                        menu.add(0, 2, 1, R.string.activity_action_review).setIcon(R.drawable.ic_ai).icon?.mutate()?.setTint(context.getColor(R.color.on_surface))
                    setForceShowIcon(true)
                    setOnMenuItemClickListener { item ->
                        details?.dismiss()
                        if (item.itemId == 1) onEdit() else onReview()
                        true
                    }
                    show()
                }
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })
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
        addDetail(content, R.string.replay_total_distance, context.getString(R.string.replay_kilometres, number(summary.distanceMeters / 1000, 2)))
        // Stops are derived from the saved fixes, so older activities gain moving time too.
        val stops = com.runline.app.core.StopDetection.detect(run.points, run.activityType)
        val moving = com.runline.app.core.StopDetection.movingDurationMs(run, stops)
        val autoPause = com.runline.app.tracking.RecordingSettings.autoPause(context)
        if (stops.isEmpty()) addDetail(content, R.string.replay_active_time, duration(run.activeDurationMs))
        else {
            addDetail(content, R.string.v17_moving_time, duration(moving))
            addDetail(content, R.string.v17_elapsed_time, duration(run.activeDurationMs))
            addDetail(content, R.string.v17_stops, context.resources.getQuantityString(R.plurals.v17_stops_value, stops.size, stops.size,
                duration(com.runline.app.core.StopDetection.totalMs(stops))))
        }
        addDetail(content, R.string.replay_average_pace, context.getString(R.string.replay_per_km,
            pace(RunMetrics.paceSecondsPerKm(summary.distanceMeters, if (autoPause) moving else run.activeDurationMs))))
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(context.getString(R.string.replay_elevation), 22, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(floating("", R.drawable.ic_info).apply {
                contentDescription = context.getString(R.string.map_v8_stats_info)
                iconPadding = 0; setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { showStatsInfo() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }, row(top = 16, bottom = 8))
        val elevationContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(elevationContent)
        var selectedAltitude: TextView? = null
        var altitudeRange: TextView? = null
        fun rebuildElevation() {
            elevationContent.removeAllViews()
            elevationContent.addView(label(TerrainUi.status(context, run.points, elevationReplay.session.points, terrainRepository), 12, muted = true), row(bottom = 12))
            val totals = LinearLayout(context)
            val summary = elevationReplay.summary
            listOf(R.string.replay_total_climbed to summary.elevationGainMeters, R.string.replay_total_descended to summary.elevationLossMeters).forEachIndexed { index, (name, amount) ->
                totals.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(context.getString(name), 13, muted = true))
                    addView(label(context.getString(R.string.replay_metres, number(amount, 0)), 26, bold = true), row(top = 6))
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(12) })
            }
            elevationContent.addView(totals)
            elevationContent.addView(ElevationChartView(context, elevationReplay, replayTerrain, positionMs) { selected ->
                setPosition(selected, smoothHeading = false)
                selectedAltitude?.text = context.getString(R.string.replay_metres, number(elevationReplay.at(selected)?.point?.altitudeMeters, 0))
            }, row(top = 16))
            selectedAltitude?.text = context.getString(R.string.replay_metres, number(elevationReplay.at(positionMs)?.point?.altitudeMeters, 0))
            altitudeRange?.text = context.getString(R.string.replay_metres_range, number(summary.minAltitudeMeters, 0), number(summary.maxAltitudeMeters, 0))
        }
        terrainDetailsChanged = ::rebuildElevation
        rebuildElevation()
        content.addView(label(context.getString(R.string.replay_more_statistics), 22, bold = true), row(top = 24, bottom = 8))
        addDetail(content, R.string.replay_average_speed, context.getString(R.string.replay_kmh, number(summary.averageSpeedMetersPerSecond?.times(3.6), 1)))
        addDetail(content, R.string.replay_peak_speed, context.getString(R.string.replay_kmh, number(summary.maxSpeedMetersPerSecond?.times(3.6), 1)))
        addDetail(content, R.string.replay_elapsed_time, duration((run.endedAtMs - run.startedAtMs).coerceAtLeast(0)))
        addDetail(content, R.string.replay_observed_time, duration(replay.durationMs))
        selectedAltitude = addDetail(content, R.string.replay_current_altitude, context.getString(R.string.replay_metres, number(elevationReplay.at(positionMs)?.point?.altitudeMeters, 0)))
        altitudeRange = addDetail(content, R.string.replay_altitude_range, context.getString(R.string.replay_metres_range, number(elevationReplay.summary.minAltitudeMeters, 0), number(elevationReplay.summary.maxAltitudeMeters, 0)))
        addDetail(content, R.string.replay_gps_samples, java.text.NumberFormat.getIntegerInstance(locale()).format(run.points.size))
        content.addView(label(context.getString(R.string.replay_splits), 22, bold = true), row(top = 24, bottom = 8))
        // Computed only when details open, never for each playback frame.
        val splits = RunMetrics.splits(run.copy(points = replay.segments.flatten()), if (autoPause) stops else emptyList())
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
            dialog.setOnDismissListener { terrainDetailsChanged = null; actions?.dismiss(); actions = null; details = null }
            dialog.show()
        }
    }

    private fun showStatsInfo() {
        if (disposed || statsDialog?.isShowing == true) return
        statsDialog = MaterialAlertDialogBuilder(context).setTitle(R.string.map_v8_stats_info)
            .setMessage(listOf(R.string.terrain_maps_stats_info, R.string.replay_estimate_note,
                R.string.replay_timing_note, R.string.v7_lap_note).joinToString("\n\n") { context.getString(it) })
            .setPositiveButton(R.string.map_close, null).create().also { dialog ->
                dialog.setOnDismissListener { statsDialog = null }
                dialog.show()
            }
    }

    private fun addDetail(parent: LinearLayout, resource: Int, amount: String) = addDetail(parent, context.getString(resource), amount)
    private fun addDetail(parent: LinearLayout, name: String, amount: String): TextView {
        val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
        line.addView(label(name, 14, muted = true), LinearLayout.LayoutParams(0, -2, 1f))
        val value = label(amount, 16, bold = true).apply { gravity = Gravity.END }
        line.addView(value, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        parent.addView(line)
        parent.addView(View(context).apply { setBackgroundColor(context.getColor(R.color.surface_variant)) }, LinearLayout.LayoutParams(-1, dp(1)))
        return value
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
