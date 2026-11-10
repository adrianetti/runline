package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.runline.app.R
import com.runline.app.core.LivePace
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
    private val readout = RunReadoutView(context)
    private val gps = label(context.getString(R.string.active_loading), 12, muted = true)
    private val terrainRepository = com.runline.app.terrain.TerrainRepository.get(context)
    private val terrainProjector = com.runline.app.core.TerrainProjector()
    private var lastState = initial
    private val terrainListener: () -> Unit = { if (isAttachedToWindow) update(lastState) }
    private val terrainTrend = TerrainTrend(com.runline.app.core.TerrainSource.TERRAIN_MAP)
    private var terrainCount = 0
    private var terrainLast: RunPoint? = null
    private var terrainEstimate = TerrainEstimate()
    private val elevation = ElevationProgress()
    private val routeIcon = android.widget.ImageView(context).apply {
        setImageResource(R.drawable.ic_route); imageTintList = ColorStateList.valueOf(context.getColor(R.color.on_surface))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val routeText = label("", 14).apply { maxLines = 3 }
    /** Street directions to the start, handed to the phone's maps app; only while still on the way there. */
    private var routeStart: Pair<Double, Double>? = null
    private val directions = com.google.android.material.button.MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
        setIconResource(R.drawable.ic_directions); iconPadding = 0; iconSize = dp(24); minWidth = 0; minimumWidth = 0
        minHeight = dp(48); minimumHeight = dp(48); setPadding(dp(12), 0, dp(12), 0); visibility = GONE
        iconTint = ColorStateList.valueOf(context.getColor(R.color.on_surface))
        contentDescription = context.getString(R.string.v21_directions); tooltipText = contentDescription
        setOnClickListener { routeStart?.let { (lat, lon) -> RouteDirections.open(context, lat, lon) } }
    }
    private val routeRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL; visibility = GONE
        addView(routeIcon, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) })
        addView(routeText, LinearLayout.LayoutParams(0, -2, 1f))
        addView(directions, LinearLayout.LayoutParams(dp(48), dp(48)))
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    /** GPS-style: arrow, distance and instruction for the next manoeuvre. */
    private val turnArrow = android.widget.ImageView(context).apply {
        imageTintList = ColorStateList.valueOf(context.getColor(R.color.on_surface)); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val turnDistance = label("", if (compact) 22 else 26, bold = true).apply { fontFeatureSettings = "tnum" }
    private val turnText = label("", 15).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
    private val turnRow = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL; visibility = GONE
        addView(turnArrow, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
        addView(turnDistance, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
        addView(turnText, LinearLayout.LayoutParams(0, -2, 1f))
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val ahead = RouteAheadView(context).apply { visibility = GONE }
    private val heading = label(context.getString(when (initial.status) {
        RecordingStatus.PAUSED -> R.string.active_paused
        RecordingStatus.INTERRUPTED -> R.string.active_recovered
        else -> R.string.active_recording
    }), if (compact) 14 else 16, bold = true)

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

        panel.setPadding(dp(20), dp(if (compact) 12 else 16), dp(20), dp(12))
        val information = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        information.addView(heading, row(bottom = 4))
        information.addView(turnRow, row(top = 4, bottom = 8))
        information.addView(routeRow, row(bottom = 6))
        information.addView(ahead, LinearLayout.LayoutParams(-1, dp(if (compact) 36 else 44)).apply { bottomMargin = dp(8) })
        information.addView(gps, row(bottom = if (compact) 6 else 12))
        information.addView(readout, row())
        panel.setChartContent(readout)
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

    override fun onAttachedToWindow() { super.onAttachedToWindow(); terrainRepository.listen(terrainListener); lastState.run?.points?.let(terrainRepository::request); update(lastState) }
    override fun onDetachedFromWindow() { terrainRepository.unlisten(terrainListener); super.onDetachedFromWindow() }

    fun update(state: TrackingState) {
        lastState = state
        val run = state.run ?: return
        val points = terrainProjector.project(run, terrainRepository.snapshot).points
        panel.setPoints(points)
        if (points.size < terrainCount || (terrainCount > 0 && points.getOrNull(terrainCount - 1) != terrainLast)) {
            terrainTrend.reset(); terrainCount = 0; terrainEstimate = TerrainEstimate()
        }
        for (index in terrainCount until points.size) terrainEstimate = terrainTrend.update(points[index])
        terrainCount = points.size; terrainLast = points.lastOrNull()
        val fresh = terrainLast?.let { System.currentTimeMillis() - it.timestampMs in 0L..15_000L } == true
        val currentTerrain = if (state.status == RecordingStatus.RECORDING && fresh) terrainEstimate else TerrainEstimate()
        map.setLiveMotionEnabled(state.status == RecordingStatus.RECORDING)
        map.setLocationWaitingMessage(if (state.status == RecordingStatus.RECORDING) state.message else context.getString(if (state.status == RecordingStatus.PAUSED) R.string.active_paused_hint else R.string.active_recovered_hint))
        map.setPlannedRoute(state.route?.points)
        map.setRoute(run.points)
        map.setMarker(run.points.lastOrNull())
        showRoute(state.route, state.status == RecordingStatus.RECORDING)
        val meters = RunMetrics.distanceMeters(points)
        elevation.update(points)
        val stopped = state.status == RecordingStatus.RECORDING && state.stoppedSinceMs != null
        val autoPause = com.runline.app.tracking.RecordingSettings.autoPause(context)
        // Auto-pause: the clock and average stop at traffic lights; the GPS keeps recording.
        val duration = if (autoPause) com.runline.app.core.StopDetection.movingDurationMs(run, openStopSinceMs = state.stoppedSinceMs) else run.activeDurationMs
        change(heading, context.getString(when {
            stopped -> if (autoPause) R.string.v17_stopped_auto_paused else R.string.v17_stopped
            state.status == RecordingStatus.PAUSED -> R.string.active_paused
            state.status == RecordingStatus.INTERRUPTED -> R.string.active_recovered
            else -> R.string.active_recording
        }))
        readout.update(
            paceSeconds = if (state.status == RecordingStatus.RECORDING && !state.gpsStale && !stopped) LivePace.secondsPerKm(points, System.currentTimeMillis()) else null,
            distanceMeters = meters, durationMs = duration, terrain = currentTerrain,
            climbedMeters = elevation.climbedMeters, descendedMeters = elevation.descendedMeters,
            averagePace = RunMetrics.paceSecondsPerKm(meters, duration),
            animate = state.status == RecordingStatus.RECORDING, profile = panel.slopeProfile(),
        )
        val status = when {
            state.status == RecordingStatus.INTERRUPTED -> state.message ?: context.getString(R.string.active_recovered_hint)
            state.status == RecordingStatus.PAUSED -> state.message?.takeUnless { state.messageRes == R.string.tracking_paused } ?: context.getString(R.string.active_paused_hint)
            state.message != null -> state.message
            state.accuracyMeters != null -> context.getString(R.string.active_accuracy, state.accuracyMeters.roundToInt())
            else -> context.getString(R.string.active_loading)
        }
        change(gps, status)
        val routineStatus = state.messageRes in setOf(null, R.string.tracking_paused, R.string.tracking_recovered_hint)
        gps.visibility = if (compact && routineStatus) GONE else VISIBLE
        heading.contentDescription = "${heading.text}. $status"
    }

    /** Distance and ascent left, arrival against sunset, the next named place, or which way the path is. */
    private fun showRoute(route: com.runline.app.tracking.LiveRoute?, recording: Boolean) {
        routeRow.visibility = if (route == null) GONE else VISIBLE
        showGuidance(route)
        if (route == null) return
        val p = route.progress
        val locale = resources.configuration.locales[0]
        fun km(m: Double) = String.format(locale, "%.1f", m / 1000)
        val wrongWay = p?.wrongWay == true && p.state == com.runline.app.core.RouteState.ON_ROUTE
        val off = p?.state == com.runline.app.core.RouteState.OFF_ROUTE || wrongWay
        val approaching = p?.state == com.runline.app.core.RouteState.APPROACHING
        routeStart = route.points.firstOrNull()?.let { it.latitude to it.longitude }
        directions.visibility = if (approaching && recording && (p?.distanceFromRouteMeters ?: 0.0) > 150 && routeStart != null) VISIBLE else GONE
        val here = lastState.run?.points?.lastOrNull()
        val start = routeStart
        if (approaching && here != null && start != null) map.setApproachLine(here.latitude, here.longitude, start.first, start.second)
        else map.clearApproachLine()
        val text = when {
            p == null -> route.name
            wrongWay -> context.getString(R.string.v21_island_wrong_way)
            else -> when (p.state) {
            com.runline.app.core.RouteState.APPROACHING -> approachText(p)
            com.runline.app.core.RouteState.OFF_ROUTE -> p.side?.let {
                context.getString(R.string.v19_island_off, p.distanceFromRouteMeters.roundToInt(), context.getString(when (it) {
                    com.runline.app.core.RouteSide.LEFT -> R.string.v19_side_left; com.runline.app.core.RouteSide.RIGHT -> R.string.v19_side_right
                    com.runline.app.core.RouteSide.AHEAD -> R.string.v19_side_ahead; com.runline.app.core.RouteSide.BEHIND -> R.string.v19_side_behind
                }))
            } ?: context.getString(R.string.v19_island_off_plain, p.distanceFromRouteMeters.roundToInt())
            com.runline.app.core.RouteState.FINISHED -> context.getString(R.string.v19_route_complete_short)
            com.runline.app.core.RouteState.ON_ROUTE -> {
                val arrival = System.currentTimeMillis() + (p.minutesRemaining * 60_000).toLong()
                val time = { ms: Long -> java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(locale)
                    .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())) }
                val clock = time(arrival)
                val main = p.ascentRemainingMeters?.let { context.getString(R.string.v19_island_route, km(p.remainingMeters), it.roundToInt(), clock) }
                    ?: context.getString(R.string.v19_island_route_no_ascent, km(p.remainingMeters), clock)
                val sunset = route.sunsetMs?.takeIf { recording && arrival > it }?.let { "\n" + context.getString(R.string.v19_island_after_sunset, time(it)) }.orEmpty()
                val next = p.nextWaypoint?.takeIf { it.name.isNotBlank() }?.let { w ->
                    val ahead = p.nextWaypointMeters ?: 0.0
                    "\n" + if (ahead < 1000) context.getString(R.string.v20_island_next_m, w.name, ((ahead / 10).roundToInt() * 10).coerceAtLeast(10))
                        else context.getString(R.string.v19_island_next, w.name, km(ahead))
                }.orEmpty()
                main + sunset + climbLine(route.guidance, p.doneMeters) + next
            }
        } }
        change(routeText, text)
        routeText.typeface = Typeface.create(if (off) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        routeIcon.setImageResource(if (off) R.drawable.ic_warning else R.drawable.ic_route)
        routeRow.contentDescription = "${route.name}. $text"
    }

    /** "Start 450 m to your left", or a compass point before you are moving; as the crow flies. */
    private fun approachText(p: com.runline.app.core.RouteProgress): String {
        val meters = com.runline.app.core.RouteApproach.round(p.distanceFromRouteMeters)
        val distance = if (meters >= 1000) context.getString(R.string.v20_island_km, String.format(resources.configuration.locales[0], "%.1f", meters / 1000.0))
            else context.getString(R.string.v20_island_meters, meters)
        val where = p.side?.let { context.getString(when (it) {
            com.runline.app.core.RouteSide.LEFT -> R.string.v19_side_left; com.runline.app.core.RouteSide.RIGHT -> R.string.v19_side_right
            com.runline.app.core.RouteSide.AHEAD -> R.string.v19_side_ahead; com.runline.app.core.RouteSide.BEHIND -> R.string.v19_side_behind
        }) } ?: p.bearingToRouteDegrees?.let { context.getString(when (com.runline.app.core.Compass.of(it)) {
            com.runline.app.core.Compass.NORTH -> R.string.v21_compass_n; com.runline.app.core.Compass.NORTH_EAST -> R.string.v21_compass_ne
            com.runline.app.core.Compass.EAST -> R.string.v21_compass_e; com.runline.app.core.Compass.SOUTH_EAST -> R.string.v21_compass_se
            com.runline.app.core.Compass.SOUTH -> R.string.v21_compass_s; com.runline.app.core.Compass.SOUTH_WEST -> R.string.v21_compass_sw
            com.runline.app.core.Compass.WEST -> R.string.v21_compass_w; com.runline.app.core.Compass.NORTH_WEST -> R.string.v21_compass_nw
        }) }
        return where?.let { context.getString(R.string.v21_island_start, distance, it) } ?: context.getString(R.string.v21_island_start_plain, distance)
    }

    /** "Climb ahead: 110 m over 1.2 km, ~9%" from 2 km out, then the height still to climb. */
    private fun climbLine(guidance: com.runline.app.core.RouteGuidanceView?, done: Double): String {
        val climb = guidance?.climb ?: return ""
        val locale = resources.configuration.locales[0]
        val inside = (guidance.climbMeters ?: 0.0) <= 0
        val height = if (inside) kotlin.math.abs(climb.heightMeters) * ((climb.endMeters - done) / climb.lengthMeters).coerceIn(0.0, 1.0) else kotlin.math.abs(climb.heightMeters)
        val length = if (inside) (climb.endMeters - done).coerceAtLeast(0.0) else climb.lengthMeters
        val res = when {
            inside && climb.uphill -> R.string.v20_island_climbing; inside -> R.string.v20_island_descending
            climb.uphill -> R.string.v20_island_climb; else -> R.string.v20_island_descent
        }
        return "\n" + context.getString(res, height.roundToInt(), String.format(locale, "%.1f", length / 1000), climb.gradePercent.roundToInt())
    }

    /** The arrow row and the elevation ahead, only while on the route. */
    private fun showGuidance(route: com.runline.app.tracking.LiveRoute?) {
        val p = route?.progress
        val onRoute = p?.state == com.runline.app.core.RouteState.ON_ROUTE
        val g = route?.guidance
        val maneuver = g?.maneuver.takeIf { onRoute }
        turnRow.visibility = if (maneuver == null) GONE else VISIBLE
        if (maneuver != null) {
            val meters = g?.maneuverMeters ?: 0.0
            val locale = resources.configuration.locales[0]
            val distance = when {
                meters >= 1000 -> context.getString(R.string.v20_island_km, String.format(locale, "%.1f", meters / 1000))
                meters >= 100 -> context.getString(R.string.v20_island_meters, (meters / 10).roundToInt() * 10)
                else -> context.getString(R.string.v20_island_meters, (meters / 5).roundToInt() * 5)
            }
            val words = if (maneuver.kind == com.runline.app.core.TurnKind.ZIGZAG)
                context.getString(R.string.v20_turn_zigzag, (maneuver.lengthMeters / 10).roundToInt() * 10)
            else context.getString(when (maneuver.kind) {
                com.runline.app.core.TurnKind.BEAR_LEFT -> R.string.v20_turn_bear_left; com.runline.app.core.TurnKind.BEAR_RIGHT -> R.string.v20_turn_bear_right
                com.runline.app.core.TurnKind.LEFT -> R.string.v20_turn_left; com.runline.app.core.TurnKind.RIGHT -> R.string.v20_turn_right
                com.runline.app.core.TurnKind.SHARP_LEFT -> R.string.v20_turn_sharp_left; com.runline.app.core.TurnKind.SHARP_RIGHT -> R.string.v20_turn_sharp_right
                else -> R.string.v20_turn_around
            })
            val instruction = words.replaceFirstChar { it.titlecase(locale) }
            turnArrow.setImageResource(when (maneuver.kind) {
                com.runline.app.core.TurnKind.BEAR_LEFT -> R.drawable.ic_turn_bear_left; com.runline.app.core.TurnKind.BEAR_RIGHT -> R.drawable.ic_turn_bear_right
                com.runline.app.core.TurnKind.LEFT -> R.drawable.ic_turn_left; com.runline.app.core.TurnKind.RIGHT -> R.drawable.ic_turn_right
                com.runline.app.core.TurnKind.SHARP_LEFT -> R.drawable.ic_turn_sharp_left; com.runline.app.core.TurnKind.SHARP_RIGHT -> R.drawable.ic_turn_sharp_right
                com.runline.app.core.TurnKind.TURN_AROUND -> R.drawable.ic_turn_around; com.runline.app.core.TurnKind.ZIGZAG -> R.drawable.ic_turn_zigzag
            })
            change(turnDistance, distance); change(turnText, instruction)
            turnRow.contentDescription = context.getString(R.string.v20_island_maneuver_description, instruction, distance)
        }
        val model = route?.route
        ahead.visibility = if (onRoute && model != null && ahead.set(model, p!!.doneMeters)) VISIBLE else GONE
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

}
