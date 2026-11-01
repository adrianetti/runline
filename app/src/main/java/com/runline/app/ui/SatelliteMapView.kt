package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.R
import com.runline.app.core.GpsCourse
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
import org.maplibre.android.style.layers.SymbolLayer
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
    private var initialCenter: LatLng? = null
    private var fitPending = false
    private var disposed = false
    private var resumed = false
    private var started = false
    private var following = false
    private var navigationMode = false
    private val course = GpsCourse()
    private var overlayInsets = intArrayOf(0, 0, 0, 0)
    private var pnoaVisible = false
    private var pnoaUnavailable = false
    private var readyTiles = false
    private val loadingTimeout = Runnable {
        if (!disposed && !readyTiles) {
            status.text = "Map tiles not loaded · route still shown"
            status.visibility = VISIBLE
        }
    }
    private val status = TextView(context).apply {
        text = "Loading satellite…"
        textSize = 12f
        setTextColor(Color.BLACK)
        setPadding(dp(8), dp(5), dp(8), dp(5))
        setBackgroundColor(Color.WHITE)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val credits = TextView(context).apply {
        text = "© IGN · EOX · OpenStreetMap"
        textSize = 11f
        setTextColor(Color.BLACK)
        setBackgroundColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), 0, dp(8), 0)
        contentDescription = "Map credits and imagery coverage"
        setOnClickListener { showCredits() }
    }
    private val fallback = RouteView(context).apply { visibility = GONE }
    private val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    val navigationBearingAvailable: Boolean get() = navigationMode && course.bearingDegrees != null

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
        addView(status, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(8), dp(8), dp(64), 0)
        })
        addView(credits, LayoutParams(-2, dp(48), Gravity.BOTTOM or Gravity.START))
        controls.addView(mapButton(R.drawable.ic_route, "Fit entire route") { fitRoute() })
        controls.addView(mapButton(R.drawable.ic_location, "Follow moving marker") { setFollowing(true) })
        addView(controls, LayoutParams(dp(48), -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(8), dp(8), 0)
        })
        mapView.contentDescription = "Satellite map. Drag to pan and pinch to zoom."
        var downX = 0f
        var downY = 0f
        mapView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (hypot(event.x - downX, event.y - downY) > dp(8)) following = false
                MotionEvent.ACTION_POINTER_DOWN -> following = false
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        mapView.onCreate(null)
        mapView.setMaximumFps(30)
        mapView.addOnDidFailLoadingMapListener {
            if (!disposed) {
                status.text = "Map unavailable · route still shown"
                status.visibility = VISIBLE
                fallback.points = routePoints
                fallback.visibility = VISIBLE
            }
        }
        mapView.addOnTileActionListener { operation, _, _, _, _, _, source ->
            if (!disposed && (source == "world" || source == "spain")) {
                when (operation) {
                    TileOperation.LoadFromNetwork, TileOperation.LoadFromCache -> {
                        readyTiles = true
                        status.visibility = GONE
                    }
                    TileOperation.Error -> {
                        if (source == "spain") {
                            pnoaUnavailable = true
                            updateImageryCoverage()
                        }
                        status.text = "Some map tiles unavailable"
                        status.visibility = VISIBLE
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
                ready.addSource(GeoJsonSource("position"))
                ready.addLayer(CircleLayer("position-dot", "position").withProperties(
                    circleColor(Color.WHITE), circleRadius(8f), circleStrokeColor(Color.BLACK), circleStrokeWidth(3f)))
                ready.addImage("navigation-arrow", navigationImage())
                ready.addLayer(SymbolLayer("position-arrow", "position").withProperties(
                    iconImage("navigation-arrow"), iconAllowOverlap(true), iconIgnorePlacement(true),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP), iconRotate(0f),
                    visibility(Property.NONE)))
                updateRoute()
                updateMarker()
                applyCameraInsets()
                initialCenter?.let { loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(it, if (navigationMode) 18.0 else 16.0)) }
                val keepFollowing = following
                if (fitPending || (!navigationMode && routePoints.isNotEmpty())) fitRoute()
                following = keepFollowing
                if (following) followMarker()
                updateImageryCoverage()
            }
        }
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
        updateMarker()
        if (firstRoute && !navigationMode) {
            val keepFollowing = following
            fitRoute()
            following = keepFollowing
        }
    }

    /** Pass null while playback is in a pause or unavailable GPS interval. */
    fun setMarker(point: RunPoint?) {
        if (disposed) return
        marker = point?.takeIf { it.hasValidCoordinates() && abs(it.latitude) < 85.051129 }
        updateMarker()
        if (following) followMarker()
    }

    fun setCenter(latitude: Double, longitude: Double) {
        if (disposed || !latitude.isFinite() || !longitude.isFinite() || abs(latitude) >= 85.051129 || abs(longitude) > 180.0) return
        initialCenter = LatLng(latitude, longitude)
        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(initialCenter!!, if (navigationMode) 18.0 else 16.0))
        updateImageryCoverage()
    }

    fun setFollowing(enabled: Boolean) {
        following = enabled
        if (enabled) followMarker()
    }

    /** Live GPS uses an arrow once movement establishes a course; replay keeps its dot. */
    fun setNavigationMode(enabled: Boolean) {
        if (disposed || navigationMode == enabled) return
        navigationMode = enabled
        course.reset()
        if (enabled) {
            routePoints.forEach(course::update)
            following = true
            fitPending = false
        }
        updateMarker()
        applyCameraInsets()
        if (following) followMarker()
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
        (controls.layoutParams as LayoutParams).apply {
            setMargins(0, next[1] + dp(8), next[2] + dp(8), 0)
            controls.layoutParams = this
        }
        (fallback.layoutParams as LayoutParams).apply {
            setMargins(next[0], next[1], next[2], next[3] + dp(56))
            fallback.layoutParams = this
        }
        applyCameraInsets()
        if (following) followMarker()
    }

    /** Fit handles routes crossing the date line without zooming out to the entire world. */
    fun fitRoute() {
        if (disposed) return
        following = false
        val valid = routePoints.filter { it.hasValidCoordinates() && abs(it.latitude) < 85.051129 }
        val currentMap = map
        if (currentMap == null || width == 0 || height == 0) {
            fitPending = true
            return
        }
        fitPending = false
        if (valid.isEmpty()) return
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
        currentMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(centerLat, centerLng), zoom))
        updateImageryCoverage()
    }

    fun onHostResume() {
        if (disposed || resumed) return
        if (!started) { mapView.onStart(); started = true }
        mapView.onResume()
        resumed = true
    }

    fun onHostPause() {
        if (disposed) return
        if (resumed) { mapView.onPause(); resumed = false }
        if (started) { mapView.onStop(); started = false }
    }

    fun onLowMemory() { if (!disposed) mapView.onLowMemory() }

    fun dispose() {
        if (disposed) return
        onHostPause()
        disposed = true
        removeCallbacks(loadingTimeout)
        mapView.onDestroy()
        map = null
        style = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (fitPending && w > 0 && h > 0) post {
            if (!disposed) {
                val keepFollowing = following
                fitRoute()
                following = keepFollowing
                if (following) followMarker()
            }
        }
        if (navigationMode && following && w > 0 && h > 0) post {
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

    private fun updateMarker() {
        val ready = style ?: return
        ready.getSourceAs<GeoJsonSource>("position")?.setGeoJson(pointCollection(marker))
        val arrow = navigationBearingAvailable
        ready.getLayer("position-dot")?.setProperties(visibility(if (arrow) Property.NONE else Property.VISIBLE))
        ready.getLayer("position-arrow")?.setProperties(
            visibility(if (arrow) Property.VISIBLE else Property.NONE), iconRotate((course.bearingDegrees ?: 0.0).toFloat()))
    }

    private fun followMarker() {
        val point = marker ?: return
        val loaded = map ?: return
        if (navigationMode) loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 18.0))
        else if (loaded.cameraPosition.zoom < 13.0) loaded.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), 16.0))
        else loaded.moveCamera(CameraUpdateFactory.newLatLng(LatLng(point.latitude, point.longitude)))
        updateImageryCoverage()
    }

    private fun cameraInsets(): DoubleArray {
        if (!navigationMode && overlayInsets.all { it == 0 }) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        return doubleArrayOf((overlayInsets[0] + dp(8)).toDouble(), (overlayInsets[1] + dp(8)).toDouble(),
            (overlayInsets[2] + dp(8)).toDouble(), (overlayInsets[3] + dp(56)).toDouble())
    }

    private fun applyCameraInsets() {
        val loaded = map ?: return
        loaded.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder(loaded.cameraPosition).padding(cameraInsets()).build()))
    }

    private fun navigationImage(): Bitmap {
        val size = dp(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // MapLibre derives the icon's logical size from Bitmap.density.
        bitmap.density = resources.displayMetrics.densityDpi
        context.getDrawable(R.drawable.ic_navigation)!!.mutate().apply {
            setBounds(0, 0, size, size)
            draw(Canvas(bitmap))
        }
        return bitmap
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
        imageTintList = ColorStateList.valueOf(Color.BLACK)
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(4).toFloat() }
        contentDescription = label
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(4) }
    }

    private fun showCredits() {
        val message = TextView(context).apply {
            text = Html.fromHtml(CREDITS, Html.FROM_HTML_MODE_LEGACY)
            setTextColor(context.getColor(R.color.on_surface))
            setLinkTextColor(context.getColor(R.color.on_surface))
            textSize = 14f
            setPadding(dp(24), dp(8), dp(24), dp(8))
            movementMethod = LinkMovementMethod.getInstance()
        }
        MaterialAlertDialogBuilder(context).setTitle("Satellite map")
            .setView(message).setPositiveButton("Close", null).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun mercatorY(latitude: Double) = (1.0 - asinh(tan(Math.toRadians(latitude))) / Math.PI) / 2.0

    companion object {
        private const val CREDITS = "<p>Spain: <a href=\"https://www.ign.es/web/ide-area-nodo-ide-ign\">© IGN / PNOA</a>, <a href=\"https://creativecommons.org/licenses/by/4.0/\">CC BY 4.0</a>. Aerial detail is available when the map is centred in Spain.</p>" +
            "<p>Worldwide: <a href=\"https://maps.eox.at/\">EOxCloudless by EOX</a>. Contains modified Copernicus Sentinel data 2025. <a href=\"https://creativecommons.org/licenses/by-nc-sa/4.0/\">CC BY-NC-SA 4.0</a>. Worldwide imagery is about 10 metres per pixel, so close zooms can look soft.</p>" +
            "<p>Labels: © <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap contributors</a>; rendering © <a href=\"https://eox.at/\">EOX</a> and <a href=\"https://github.com/mapserver/basemaps\">MapServer</a>. Map display: <a href=\"https://maplibre.org/\">MapLibre</a>. Coverage outline: Natural Earth, public domain.</p>" +
            "<p>Imagery needs internet and may be cached. The providers receive tile-area requests and your network address. Your run file and timestamps stay on this phone. Recording works without map tiles.</p>"
        private const val BASE_STYLE = """
        {"version":8,"sources":{
          "world":{"type":"raster","tiles":["https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2025_3857/default/g/{z}/{y}/{x}.jpg"],"tileSize":256,"minzoom":0,"maxzoom":14},
          "spain":{"type":"raster","tiles":["https://www.ign.es/wmts/pnoa-ma?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=OI.OrthoimageCoverage&STYLE=default&FORMAT=image/jpeg&TILEMATRIXSET=GoogleMapsCompatible&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"],"tileSize":256,"minzoom":0,"maxzoom":19,"bounds":[-19,27,5,44]},
          "labels":{"type":"raster","tiles":["https://tiles.maps.eox.at/wmts/1.0.0/overlay_bright_3857/default/g/{z}/{y}/{x}.png"],"tileSize":256,"minzoom":0,"maxzoom":14}
        },"layers":[
          {"id":"background","type":"background","paint":{"background-color":"#303030"}},
          {"id":"world-imagery","type":"raster","source":"world","paint":{"raster-fade-duration":150}},
          {"id":"spain-imagery","type":"raster","source":"spain","layout":{"visibility":"none"},"paint":{"raster-fade-duration":150}},
          {"id":"map-labels","type":"raster","source":"labels","paint":{"raster-fade-duration":150}}
        ]}
        """
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
