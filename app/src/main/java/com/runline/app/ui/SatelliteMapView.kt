package com.runline.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.R
import com.runline.app.core.GpsCourse
import com.runline.app.core.FollowCamera
import com.runline.app.core.MarkerMotion
import com.runline.app.core.MarkerViewport
import com.runline.app.core.RunPoint
import com.runline.app.core.continuousSegments
import com.runline.app.core.hasValidCoordinates
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.tile.TileOperation
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.*

/**
 * A display-only map. It never requests GPS or writes to RunStore. Remote hosts receive
 * only the requested map tiles, not the session or its timestamps. Live recording is
 * independent of this view and of tile availability.
 *
 * Call onHostResume/onHostPause with the host; dispose before removing this view.
 * No whole-region/offline downloads. MapLibre's ambient cache is bounded at its 50 MB default.
 */
class SatelliteMapView(context: Context) : FrameLayout(context) {
    private val mapView: MapView
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var routePoints: List<RunPoint> = emptyList()
    private var marker: RunPoint? = null
    private var displayedMarker: RunPoint? = null
    private var displayedBearing: Double? = null
    private var motionAnimator: ValueAnimator? = null
    private var liveMotionEnabled = true
    private var locationWaitingMessage: String? = null
    private var tileMessage: String? = context.getString(R.string.map_loading_satellite)
    private var mapFailed = false
    private var initialCenter: LatLng? = null
    private var fitPending = false
    private var disposed = false
    private var resumed = false
    private var started = false
    private var following = false
    private var navigationMode = false
    private var replayMode = false
    private val course = GpsCourse()
    private val followCamera = FollowCamera()
    private var cameraCut = true
    private var overlayInsets = intArrayOf(0, 0, 0, 0)
    private var pnoaVisible = false
    private var pnoaUnavailable = false
    private var readyTiles = false
    private val loadingTimeout = Runnable {
        if (!disposed && !readyTiles) {
            tileMessage = context.getString(R.string.map_tiles_not_loaded)
            updateLocationPresentation()
        }
    }
    private val status = TextView(context).apply {
        text = context.getString(R.string.map_loading_satellite)
        textSize = 12f
        setTextColor(Color.BLACK)
        setPadding(dp(8), dp(5), dp(8), dp(5))
        setBackgroundColor(Color.WHITE)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val creditText = TextView(context).apply {
        text = context.getString(R.string.map_v8_attribution)
        textSize = 11f
        setTextColor(context.getColor(R.color.on_surface))
        background = GradientDrawable().apply { setColor(context.getColor(R.color.surface)); cornerRadius = dp(4).toFloat() }
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(6), dp(3), dp(6), dp(3))
        maxLines = 2
    }
    private val credits = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val fallback = RouteView(context).apply { visibility = GONE }
    // Follow uses the exact padded anchor; free camera projects this same native
    // overlay on motion frames. GeoJSON symbol uploads never delay the moving marker.
    private val followMarkerView = FollowMarkerView(context).apply {
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false; isFocusable = false
    }
    private val markerFrame = object : Runnable {
        override fun run() {
            if (disposed || !resumed) return
            updateMarkerPresentation()
        }
    }
    private val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val waitingSpinner = ProgressBar(context).apply {
        indeterminateTintList = ColorStateList.valueOf(context.getColor(R.color.primary))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val waitingText = TextView(context).apply {
        textSize = 18f; gravity = Gravity.CENTER
        setTextColor(context.getColor(R.color.on_surface))
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val waitingHint = TextView(context).apply {
        setText(R.string.map_location_hint); textSize = 14f; gravity = Gravity.CENTER
        setTextColor(context.getColor(R.color.on_surface_variant))
    }
    private val waitingContent = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(24), dp(16), dp(24), dp(16))
        addView(waitingSpinner, LinearLayout.LayoutParams(dp(32), dp(32)).apply { bottomMargin = dp(20) })
        addView(waitingText, LinearLayout.LayoutParams(-1, -2))
        addView(waitingHint, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }
    private val waitingPanel = FrameLayout(context).apply {
        isClickable = true // Do not let touches on loading content pan the hidden map.
        setBackgroundColor(context.getColor(R.color.surface))
        addView(waitingContent, LayoutParams(-1, -1))
    }

    val navigationBearingAvailable: Boolean get() = (navigationMode || replayMode) && displayedBearing != null

    init {
        setBackgroundColor(Color.rgb(44, 44, 44))
        MapLibre.getInstance(context.applicationContext)
        mapView = MapView(context, MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true).logoEnabled(false).attributionEnabled(false)
            .compassEnabled(false).rotateGesturesEnabled(false).tiltGesturesEnabled(false)
            .minZoomPreference(1.0).maxZoomPreference(19.0)
            .camera(CameraPosition.Builder().target(LatLng(15.0, 0.0)).zoom(1.0).build()))
        addView(mapView, LayoutParams(-1, -1))
        addView(fallback, LayoutParams(-1, -1))
        addView(followMarkerView, LayoutParams(dp(42), dp(42), Gravity.TOP or Gravity.LEFT))
        addView(waitingPanel, LayoutParams(-1, -1))
        addView(status, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(8), dp(8), dp(64), 0)
        })
        credits.addView(creditText, LinearLayout.LayoutParams(-2, -2))
        credits.addView(mapButton(R.drawable.ic_info, context.getString(R.string.map_credits_accessibility)) { showCredits() },
            LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        addView(credits, LayoutParams(-2, dp(48), Gravity.BOTTOM or Gravity.START))
        controls.addView(mapButton(R.drawable.ic_route, context.getString(R.string.map_fit_route)) { fitRoute() })
        controls.addView(mapButton(R.drawable.ic_location, context.getString(R.string.map_follow_marker)) { setFollowing(true) })
        addView(controls, LayoutParams(dp(48), -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(8), dp(8), 0)
        })
        mapView.contentDescription = context.getString(R.string.map_accessibility)
        var downX = 0f
        var downY = 0f
        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (hypot(event.x - downX, event.y - downY) > dp(8)) setFollowing(false)
                MotionEvent.ACTION_POINTER_DOWN -> setFollowing(false)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        mapView.onCreate(null)
        mapView.setMaximumFps(30)
        mapView.addOnDidFailLoadingMapListener {
            if (!disposed) {
                mapFailed = true
                tileMessage = context.getString(R.string.map_unavailable)
                fallback.points = routePoints
                updateLocationPresentation()
            }
        }
        mapView.addOnTileActionListener { operation, _, _, _, _, _, source ->
            if (!disposed && (source == "world" || source == "spain")) {
                when (operation) {
                    TileOperation.LoadFromNetwork, TileOperation.LoadFromCache -> {
                        readyTiles = true
                        tileMessage = null
                        updateLocationPresentation()
                    }
                    TileOperation.Error -> {
                        if (source == "spain") {
                            pnoaUnavailable = true
                            updateImageryCoverage()
                        }
                        tileMessage = context.getString(R.string.map_some_tiles_unavailable)
                        updateLocationPresentation()
                    }
                    else -> Unit
                }
            }
        }
        mapView.getMapAsync { loaded ->
            if (disposed) return@getMapAsync
            map = loaded
            loaded.setPrefetchZoomDelta(0)
            loaded.addOnCameraIdleListener { updateImageryCoverage() }
            loaded.addOnCameraMoveListener { updateMarkerPresentation(); requestMarkerFrame() }
            loaded.setStyle(Style.Builder().fromJson(BASE_STYLE)) { ready ->
                if (disposed) return@setStyle
                style = ready
                ready.addSource(GeoJsonSource("route"))
                ready.addLayer(LineLayer("route-outline", "route").withProperties(
                    lineColor(Color.BLACK), lineWidth(7f), lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_ROUND)))
                ready.addLayer(LineLayer("route-line", "route").withProperties(
                    lineColor(Color.WHITE), lineWidth(4f), lineJoin(Property.LINE_JOIN_ROUND), lineCap(Property.LINE_CAP_ROUND)))
                ready.addSource(GeoJsonSource("start"))
                ready.addLayer(CircleLayer("start-dot", "start").withProperties(
                    circleColor(Color.WHITE), circleRadius(5f), circleStrokeColor(Color.BLACK), circleStrokeWidth(2f)))
                updateRoute()
                updateMarker()
                applyCameraInsets()
                initialCenter?.let { loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(it, if (navigationMode || replayMode) 18.0 else 16.0)) }
                val keepFollowing = following
                if (fitPending || (!navigationMode && !replayMode && routePoints.isNotEmpty())) fitRoute()
                following = keepFollowing
                if (following) followMarker()
                updateImageryCoverage()
                updateLocationPresentation()
            }
        }
        updateLocationPresentation()
        postDelayed(loadingTimeout, 12_000)
    }

    fun setRoute(points: List<RunPoint>) {
        if (disposed || points === routePoints) return
        val firstRoute = routePoints.isEmpty() && points.isNotEmpty()
        if (navigationMode) {
            // Process only newly appended fixes during recording. A replaced/recovered
            // track rebuilds the course once, rather than rescanning every fix each second.
            val appended = points.size >= routePoints.size &&
                (routePoints.isEmpty() || points[routePoints.lastIndex] == routePoints.last())
            if (!appended) course.reset()
            val from = if (appended) routePoints.size else 0
            for (index in from until points.size) course.update(points[index])
        }
        routePoints = points
        fallback.points = points
        updateRoute()
        if (firstRoute && !navigationMode && !replayMode) {
            val keepFollowing = following
            fitRoute()
            following = keepFollowing
        }
    }

    /** Pass null while playback is in a pause or unavailable GPS interval. */
    fun setMarker(point: RunPoint?) {
        if (disposed) return
        val target = point?.takeIf { it.hasValidCoordinates() && abs(it.latitude) < 85.051129 }
        if (target == marker) return // A timer refresh must not restart an in-flight movement.
        val previous = marker
        val from = displayedMarker
        val fromBearing = displayedBearing
        marker = target
        val targetBearing = if (navigationMode) course.bearingDegrees else null
        cameraCut = !MarkerMotion.canInterpolate(previous, target)
        cancelMotion()
        if (navigationMode && liveMotionEnabled && ValueAnimator.areAnimatorsEnabled() && resumed && style != null && from != null && target != null &&
            MarkerMotion.canInterpolate(previous, target) && from.segment == target.segment) {
            var lastFrameMs = 0L
            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = MarkerMotion.durationMs(previous!!, target)
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    if (disposed || !resumed) return@addUpdateListener
                    val fraction = animation.animatedFraction.toDouble()
                    val now = SystemClock.uptimeMillis()
                    if (fraction < 1.0 && now - lastFrameMs < 30L) return@addUpdateListener
                    lastFrameMs = now
                    displayedMarker = MarkerMotion.interpolate(from, target, fraction)
                    displayedBearing = MarkerMotion.interpolateBearing(fromBearing, targetBearing, fraction)
                    updateMarker()
                    if (following) followMarker()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (motionAnimator === animation) motionAnimator = null
                    }
                })
            }
            motionAnimator = animator
            animator.start()
        } else {
            displayedMarker = target
            displayedBearing = targetBearing
            updateMarker()
            if (following) followMarker()
        }
        updateLocationPresentation()
    }

    /** Disable interpolation while paused/recovered. GPS files are never modified. */
    fun setLiveMotionEnabled(enabled: Boolean) {
        if (liveMotionEnabled == enabled) return
        liveMotionEnabled = enabled
        if (!enabled) {
            cancelMotion()
            displayedMarker = marker
            displayedBearing = if (navigationMode) course.bearingDegrees else if (replayMode) displayedBearing else null
            updateMarker()
            if (following) followMarker()
        }
        updateLocationPresentation()
    }

    /** Optional service status while awaiting the first fix; controls remain available. */
    fun setLocationWaitingMessage(message: String?) {
        if (locationWaitingMessage == message) return
        locationWaitingMessage = message
        updateLocationPresentation()
    }

    fun setCenter(latitude: Double, longitude: Double) {
        if (disposed || !latitude.isFinite() || !longitude.isFinite() || abs(latitude) >= 85.051129 || abs(longitude) > 180.0) return
        initialCenter = LatLng(latitude, longitude)
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(initialCenter!!, if (navigationMode || replayMode) 18.0 else 16.0))
        updateImageryCoverage()
    }

    fun setFollowing(enabled: Boolean) {
        if (disposed) return
        val changed = following != enabled
        following = enabled
        if (!enabled) { if (changed) updateMarker(); return }
        if (changed) { followCamera.reset(); cameraCut = true }
        if (displayedMarker != null) followMarker()
        else {
            if (navigationMode) updateLocationPresentation()
            else {
                status.setText(R.string.map_no_position)
                status.visibility = VISIBLE
            }
            announceForAccessibility(context.getString(R.string.map_no_position))
        }
        updateMarker()
    }

    /** Replay positions are already interpolated; do not apply live GPS lag a second time. */
    fun setReplayMode() {
        replayMode = true
        navigationMode = false
        following = true
        fitPending = false
        followCamera.reset(); cameraCut = true
        applyCameraInsets()
    }

    fun setReplayMarker(point: RunPoint?, bearing: Double?, smoothHeading: Boolean = true) {
        if (disposed) return
        val target = point?.takeIf { it.hasValidCoordinates() && abs(it.latitude) < 85.051129 }
        val sameSegment = marker?.segment == target?.segment
        cameraCut = !sameSegment || !smoothHeading || target == null || marker?.let { target.timestampMs < it.timestampMs } == true
        cancelMotion()
        marker = target
        displayedMarker = target
        displayedBearing = bearing?.takeIf(Double::isFinite)?.let(FollowCamera::normalize)
        updateMarker()
        if (following) followMarker()
        updateLocationPresentation()
    }

    /** Live GPS uses an arrow once movement establishes a course. */
    fun setNavigationMode(enabled: Boolean) {
        if (disposed || navigationMode == enabled) return
        navigationMode = enabled
        cancelMotion()
        course.reset()
        followCamera.reset()
        cameraCut = true
        if (enabled) {
            replayMode = false
            routePoints.forEach(course::update)
            following = true
            fitPending = false
        }
        displayedMarker = marker
        displayedBearing = if (enabled) course.bearingDegrees else null
        updateMarker()
        applyCameraInsets()
        if (following) followMarker()
        updateLocationPresentation()
    }

    /** Physical View pixels occupied by the host's floating controls and run island. */
    fun setOverlayInsets(leftPx: Int, topPx: Int, rightPx: Int, bottomPx: Int) {
        if (disposed) return
        val next = intArrayOf(leftPx, topPx, rightPx, bottomPx).map { it.coerceAtLeast(0) }.toIntArray()
        if (overlayInsets.contentEquals(next)) return
        overlayInsets = next
        (status.layoutParams as LayoutParams).apply {
            setMargins(next[0] + dp(8), next[1] + dp(8), next[2] + dp(64), 0)
            status.layoutParams = this
        }
        (credits.layoutParams as LayoutParams).apply {
            setMargins(next[0] + dp(8), 0, next[2] + dp(8), next[3] + dp(8))
            credits.layoutParams = this
        }
        creditText.maxWidth = (width - next[0] - next[2] - dp(80)).coerceAtLeast(dp(64))
        (controls.layoutParams as LayoutParams).apply {
            setMargins(0, next[1] + dp(8), next[2] + dp(8), 0)
            controls.layoutParams = this
        }
        (fallback.layoutParams as LayoutParams).apply {
            setMargins(next[0], next[1], next[2], next[3] + dp(56))
            fallback.layoutParams = this
        }
        (waitingContent.layoutParams as LayoutParams).apply {
            setMargins(next[0], next[1], next[2], next[3])
            waitingContent.layoutParams = this
        }
        applyCameraInsets()
        if (following) followMarker()
    }

    /** Fit handles routes crossing the date line without zooming out to the entire world. */
    fun fitRoute() {
        if (disposed) return
        val valid = routePoints.filter { it.hasValidCoordinates() && abs(it.latitude) < 85.051129 }
        if (valid.isEmpty()) {
            if (navigationMode) updateLocationPresentation()
            else { status.setText(R.string.route_no_gps); status.visibility = VISIBLE }
            announceForAccessibility(context.getString(R.string.route_no_gps))
            return
        }
        setFollowing(false)
        val currentMap = map
        if (currentMap == null || width == 0 || height == 0) {
            fitPending = true
            return
        }
        fitPending = false
        val origin = valid.first().longitude
        val lngs = valid.map { origin + ((it.longitude - origin + 540.0) % 360.0) - 180.0 }
        val ys = valid.map { mercatorY(it.latitude) }
        val spanX = max((lngs.max() - lngs.min()) / 360.0, 0.000002)
        val spanY = max(ys.max() - ys.min(), 0.000002)
        // MapLibre's native viewport is measured in density-independent pixels:
        // NativeMapView.resizeView divides Android's physical size by pixelRatio.
        // Additional route-fit margins leave room for the map controls. Camera padding
        // is passed in physical pixels; native MapLibre divides it by the same pixelRatio.
        val density = resources.displayMetrics.density.toDouble()
        val padding = cameraInsets()
        val usableWidth = max((width - padding[0] - padding[2]) / density - 128.0, 80.0)
        val usableHeight = max((height - padding[1] - padding[3]) / density - 104.0, 80.0)
        val zoom = min(log2(usableWidth / 512.0 / spanX), log2(usableHeight / 512.0 / spanY)).coerceIn(1.0, 17.0)
        val centerLng = ((lngs.max() + lngs.min()) / 2.0 + 540.0) % 360.0 - 180.0
        val centerY = (ys.max() + ys.min()) / 2.0
        val centerLat = Math.toDegrees(atan(sinh(Math.PI * (1.0 - 2.0 * centerY))))
        currentMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(currentMap.cameraPosition)
            .target(LatLng(centerLat, centerLng)).zoom(zoom).bearing(0.0).tilt(0.0).padding(padding).build()))
        updateImageryCoverage()
    }

    fun onHostResume() {
        if (disposed || resumed) return
        if (!started) { mapView.onStart(); started = true }
        mapView.onResume()
        resumed = true
        requestMarkerFrame()
        followCamera.reset(); cameraCut = true
        // Return directly to the last observed fix; never animate unseen background time.
        displayedMarker = marker
        displayedBearing = if (navigationMode) course.bearingDegrees else if (replayMode) displayedBearing else null
        updateMarker()
        if (following) followMarker()
    }

    fun onHostPause() {
        if (disposed) return
        cancelMotion()
        removeCallbacks(markerFrame)
        if (resumed) { mapView.onPause(); resumed = false }
        if (started) { mapView.onStop(); started = false }
        updateMarkerPresentation()
    }

    fun onLowMemory() { if (!disposed) mapView.onLowMemory() }

    fun dispose() {
        if (disposed) return
        onHostPause()
        disposed = true
        followMarkerView.visibility = GONE
        removeCallbacks(loadingTimeout)
        mapView.onDestroy()
        map = null
        style = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        creditText.maxWidth = (w - overlayInsets[0] - overlayInsets[2] - dp(80)).coerceAtLeast(dp(64))
        if (fitPending && w > 0 && h > 0) post {
            if (!disposed) {
                val keepFollowing = following
                fitRoute()
                following = keepFollowing
                if (following) followMarker()
            }
        }
        if ((navigationMode || replayMode) && following && w > 0 && h > 0) post {
            if (!disposed && following) { applyCameraInsets(); followMarker() }
        }
    }

    private fun updateRoute() {
        val ready = style ?: return
        val paths = continuousSegments(routePoints).map { segment ->
            segment.filter { abs(it.latitude) < 85.051129 }
        }.filter { it.size >= 2 }.map { segment ->
            Feature.fromGeometry(LineString.fromLngLats(segment.map { Point.fromLngLat(it.longitude, it.latitude) }))
        }
        ready.getSourceAs<GeoJsonSource>("route")?.setGeoJson(FeatureCollection.fromFeatures(paths))
        val start = routePoints.firstOrNull { it.hasValidCoordinates() && abs(it.latitude) < 85.051129 }
        ready.getSourceAs<GeoJsonSource>("start")?.setGeoJson(pointCollection(start))
    }

    private fun updateMarker() { updateMarkerPresentation(); requestMarkerFrame() }

    /** Coalesce to the next display frame after movement; paused maps never poll. */
    private fun requestMarkerFrame() {
        removeCallbacks(markerFrame)
        if (!disposed && resumed) postOnAnimation(markerFrame)
    }

    private fun updateMarkerPresentation() {
        val point = displayedMarker
        val loaded = map
        if (disposed || !resumed || mapFailed || style == null || point == null || loaded == null ||
            width < dp(48) || height < dp(48)) {
            followMarkerView.visibility = GONE
            return
        }
        val position = if ((navigationMode || replayMode) && following) {
            val padding = cameraInsets()
            MarkerViewport.place(((width + padding[0] - padding[2]) / 2).toFloat(),
                ((height + padding[1] - padding[3]) / 2).toFloat(), width, height, dp(42))
        } else {
            runCatching { loaded.projection.toScreenLocation(LatLng(point.latitude, point.longitude)) }.getOrNull()?.let {
                MarkerViewport.place(it.x, it.y, width, height, dp(42))
            }
        }
        followMarkerView.visibility = if (position != null) VISIBLE else GONE
        if (position == null) return
        followMarkerView.translationX = position.left
        followMarkerView.translationY = position.top
        followMarkerView.showBearing(FollowCamera.relativeBearing(displayedBearing, loaded.cameraPosition.bearing))
    }

    private fun followMarker() {
        val point = displayedMarker ?: return
        val loaded = map ?: return
        if (replayMode || navigationMode) {
            val pose = followCamera.update(displayedBearing, SystemClock.uptimeMillis(), cameraCut,
                reduceMotion = !ValueAnimator.areAnimatorsEnabled())
            cameraCut = false
            // Perspective over imagery, not terrain relief. The target remains the
            // displayed observed fix; only orientation is smoothed behind the arrow.
            loaded.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(loaded.cameraPosition)
                .target(LatLng(point.latitude, point.longitude)).zoom(18.0).bearing(pose.bearing).tilt(pose.tilt).padding(cameraInsets()).build()))
        }
        else if (loaded.cameraPosition.zoom < 13.0) loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0))
        else loaded.moveCamera(CameraUpdateFactory.newLatLng(LatLng(point.latitude, point.longitude)))
        updateMarkerPresentation()
        updateImageryCoverage()
    }

    private fun cancelMotion() {
        motionAnimator?.cancel()
        motionAnimator = null
    }

    private fun updateLocationPresentation() {
        if (disposed) return
        val waitingForPosition = navigationMode && marker == null
        val waiting = waitingForPosition || (style == null && !mapFailed)
        val message = if (waitingForPosition) locationWaitingMessage ?: context.getString(R.string.map_loading_location)
            else context.getString(R.string.map_loading_satellite)
        if (waitingText.text != message) waitingText.text = message
        waitingSpinner.visibility = if (!waitingForPosition || liveMotionEnabled) VISIBLE else GONE
        waitingHint.visibility = if (waitingForPosition && liveMotionEnabled) VISIBLE else GONE
        waitingPanel.visibility = if (waiting) VISIBLE else GONE
        // Keep the TextureView alive underneath the opaque loading panel. Making it
        // invisible can defer GL surface creation and prevent the style from loading.
        mapView.importantForAccessibility = if (waiting) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        fallback.visibility = if (mapFailed && !waiting) VISIBLE else GONE
        credits.visibility = if (waiting) GONE else VISIBLE
        status.text = tileMessage
        status.visibility = if (!waiting && tileMessage != null) VISIBLE else GONE
        updateMarkerPresentation()
    }

    private fun cameraInsets(): DoubleArray {
        if (!navigationMode && overlayInsets.all { it == 0 }) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        val padding = doubleArrayOf((overlayInsets[0] + dp(8)).toDouble(), (overlayInsets[1] + dp(8)).toDouble(),
            (overlayInsets[2] + dp(8)).toDouble(), (overlayInsets[3] + dp(56)).toDouble())
        if (following && (navigationMode || replayMode) && ValueAnimator.areAnimatorsEnabled()) {
            // A little more road ahead, while the arrow stays clear of the run island.
            padding[1] += (height - padding[1] - padding[3]).coerceAtLeast(0.0) * .16
        }
        return padding
    }

    private fun applyCameraInsets() {
        val loaded = map ?: return
        loaded.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(loaded.cameraPosition).padding(cameraInsets()).build()))
    }

    private fun updateImageryCoverage() {
        if (disposed) return
        val loaded = map ?: return
        val center = loaded.cameraPosition.target ?: return
        val show = !pnoaUnavailable && loaded.cameraPosition.zoom >= 11.0 && SpainCoverage.contains(context, center.latitude, center.longitude)
        if (show != pnoaVisible) {
            pnoaVisible = show
            style?.getLayer("spain-imagery")?.setProperties(visibility(if (show) Property.VISIBLE else Property.NONE))
        }
    }

    private fun pointCollection(point: RunPoint?): FeatureCollection = FeatureCollection.fromFeatures(
        if (point == null) emptyList() else listOf(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude))))

    private fun mapButton(icon: Int, label: String, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(context.getColor(R.color.on_surface))
        background = GradientDrawable().apply { setColor(context.getColor(R.color.surface)); cornerRadius = dp(12).toFloat() }
        contentDescription = label
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(8) }
    }

    private fun showCredits() {
        val message = TextView(context).apply {
            text = Html.fromHtml(context.getString(R.string.map_v8_credits_html), Html.FROM_HTML_MODE_LEGACY)
            setTextColor(context.getColor(R.color.on_surface))
            setLinkTextColor(context.getColor(R.color.on_surface))
            textSize = 14f
            setPadding(dp(24), dp(8), dp(24), dp(8))
            movementMethod = LinkMovementMethod.getInstance()
        }
        MaterialAlertDialogBuilder(context).setTitle(R.string.map_title)
            .setView(android.widget.ScrollView(context).apply { addView(message) }).setPositiveButton(R.string.map_close, null).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun mercatorY(latitude: Double) = (1.0 - asinh(tan(Math.toRadians(latitude))) / Math.PI) / 2.0

    companion object {
        private const val BASE_STYLE = """
        {"version":8,"sources":{
          "world":{"type":"raster","tiles":["https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2025_3857/default/g/{z}/{y}/{x}.jpg"],"tileSize":256,"minzoom":0,"maxzoom":14},
          "spain":{"type":"raster","tiles":["https://www.ign.es/wmts/pnoa-ma?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=OI.OrthoimageCoverage&STYLE=default&FORMAT=image/jpeg&TILEMATRIXSET=GoogleMapsCompatible&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"],"tileSize":256,"minzoom":0,"maxzoom":19,"bounds":[-19,27,5,44]}
        },"layers":[
          {"id":"background","type":"background","paint":{"background-color":"#303030"}},
          {"id":"world-imagery","type":"raster","source":"world","paint":{"raster-fade-duration":150}},
          {"id":"spain-imagery","type":"raster","source":"spain","layout":{"visibility":"none"},"paint":{"raster-fade-duration":150}}
        ]}
        """
    }
}

/** One display marker, using the pinned Tabler arrow in follow and free-camera modes. */
private class FollowMarkerView(context: Context) : View(context) {
    private val arrow = context.getDrawable(R.drawable.ic_navigation)!!.mutate()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bearing: Double? = null
    fun showBearing(value: Double?) {
        val safe = value?.takeIf(Double::isFinite)
        if (safe == bearing) return
        bearing = safe; invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val direction = bearing
        if (direction != null) {
            val checkpoint = canvas.save()
            canvas.rotate(direction.toFloat(), width / 2f, height / 2f)
            arrow.setBounds(0, 0, width, height); arrow.draw(canvas)
            canvas.restoreToCount(checkpoint)
        } else {
            val scale = resources.displayMetrics.density
            paint.color = Color.BLACK
            canvas.drawCircle(width / 2f, height / 2f, 11f * scale, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(width / 2f, height / 2f, 8f * scale, paint)
        }
    }
}

/** Natural Earth public-domain outline keeps PNOA off Portugal, France and other countries. */
private object SpainCoverage {
    private var rings: List<List<Pair<Double, Double>>>? = null
    fun contains(context: Context, latitude: Double, longitude: Double): Boolean {
        if (latitude !in 27.0..44.0 || longitude !in -19.0..5.0) return false
        val polygons = rings ?: runCatching {
            val geometry = JSONObject(context.assets.open("maps/spain-coverage.geojson").bufferedReader().use { it.readText() }).getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")
            (0 until coordinates.length()).map { polygonIndex ->
                val ring = coordinates.getJSONArray(polygonIndex).getJSONArray(0)
                (0 until ring.length()).map { index -> ring.getJSONArray(index).let { it.getDouble(0) to it.getDouble(1) } }
            }
        }.getOrElse { emptyList() }.also { rings = it }
        return polygons.any { ring ->
            var inside = false
            var previous = ring.lastOrNull() ?: return@any false
            for (point in ring) {
                if ((point.second > latitude) != (previous.second > latitude) &&
                    longitude < (previous.first - point.first) * (latitude - point.second) / (previous.second - point.second) + point.first) inside = !inside
                previous = point
            }
            inside
        }
    }
}
