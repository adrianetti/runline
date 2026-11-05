package com.runline.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.runline.app.R
import com.runline.app.core.*
import com.runline.app.terrain.TerrainRepository
import kotlin.math.ceil

internal object TerrainUi {
    fun status(context: Context, raw: List<RunPoint>, mapped: List<RunPoint>, repository: TerrainRepository): String {
        if (!repository.enabled) return context.getString(R.string.terrain_maps_off)
        val count = mapped.count { it.altitudeMeters != null }
        if (count > 0) {
            val models = mapped.mapNotNull { it.terrainModel }.distinct()
            val source = context.getString(when {
                models.size > 1 -> R.string.terrain_source_mixed
                models.singleOrNull() == TerrainModel.MAPZEN -> R.string.terrain_source_world
                else -> R.string.terrain_map_source
            })
            return if (count == raw.size) source else context.getString(R.string.terrain_source_partial, source, (count * 100.0 / raw.size).toInt())
        }
        return context.getString(if (repository.isLoading(raw)) R.string.terrain_maps_loading else R.string.terrain_maps_unavailable)
    }
}

/** A secondary preparation task. Start remains available without downloading anything. */
internal class TerrainSettingsSheet(private val activity: AppCompatActivity, private val requestLocation: () -> Unit) : LocationListener {
    private val sheet = AiSheet(activity, R.string.terrain_offline_title)
    private val repository = TerrainRepository.get(activity)
    private val manager = activity.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val title = sheet.text("", 22, true)
    private val detail = sheet.text("")
    private val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal)
    private val primary = sheet.button("", R.drawable.ic_download, filled = true)
    private val retry = sheet.button(activity.getString(R.string.terrain_retry), R.drawable.ic_download)
    private val cancel = sheet.button(activity.getString(R.string.main_cancel), R.drawable.ic_close)
    private val locationSettings = sheet.button(activity.getString(R.string.main_location_settings), R.drawable.ic_settings)
    private val useMaps = MaterialSwitch(activity)
    private val automatic = MaterialSwitch(activity)
    private val wifi = MaterialSwitch(activity)
    private val cacheSize = sheet.text("")
    private val storageWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val measuring = java.util.concurrent.atomic.AtomicBoolean()
    private var candidate: TerrainDownloadArea? = null
    private var error: Int? = null
    private val changed: () -> Unit = { refresh() }
    private val timeout = Runnable { stopLocation(); error = R.string.terrain_location_failed; refresh() }
    private var sourceDialog: androidx.appcompat.app.AlertDialog? = null
    var locating = false
        private set
    val isShowing get() = sheet.isShowing
    init {
        automatic.apply { text = activity.getString(R.string.v16_auto_terrain); minHeight = sheet.dp(56); isChecked = repository.automatic
            setOnCheckedChangeListener { _, checked -> repository.setAutomatic(checked) } }
        wifi.apply { text = activity.getString(R.string.v16_wifi_only); minHeight = sheet.dp(56); isChecked = repository.wifiOnly
            setOnCheckedChangeListener { _, checked -> repository.setWifiOnly(checked) } }
        sheet.column.addView(automatic, sheet.row()); sheet.column.addView(wifi, sheet.row()); sheet.column.addView(cacheSize, sheet.row(0, 20))
        sheet.column.addView(title, sheet.row(0, 8))
        sheet.column.addView(detail, sheet.row(0, 16))
        sheet.column.addView(progress, sheet.row(0, 16))
        sheet.column.addView(primary, sheet.row(0, 8))
        sheet.column.addView(retry, sheet.row(0, 4))
        sheet.column.addView(cancel, sheet.row(0, 4))
        sheet.column.addView(locationSettings, sheet.row(0, 8))
        sheet.column.addView(sheet.text(activity.getString(R.string.terrain_offline_hint)), sheet.row(8, 16))
        sheet.column.addView(useMaps.apply {
            text = activity.getString(R.string.terrain_enabled); minHeight = sheet.dp(56); isChecked = repository.enabled
            setOnCheckedChangeListener { _, checked -> if (repository.enabled != checked) repository.setEnabled(checked) }
        }, sheet.row(0, 8))
        sheet.column.addView(sheet.button(activity.getString(R.string.terrain_maps_clear), R.drawable.ic_trash).apply {
            setOnClickListener { stopLocation(); candidate = null; error = null; repository.clearCache() }
        }, sheet.row())
        sheet.column.addView(sheet.text(activity.getString(R.string.terrain_download_privacy)), sheet.row(12, 8))
        sheet.column.addView(sheet.button(activity.getString(R.string.terrain_maps_source_link), R.drawable.ic_info).apply {
            setOnClickListener {
                sourceDialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.terrain_maps_source_link)
                    .setMessage(activity.assets.open("licenses/terrain.txt").bufferedReader().use { it.readText() })
                    .setPositiveButton(R.string.main_close, null).show()
            }
        }, sheet.row())
        primary.setOnClickListener {
            val area = candidate
            if (area == null) requestLocation() else {
                error = null
                if (repository.downloadArea(area)) candidate = null else error = R.string.terrain_storage_failed
                refresh()
            }
        }
        retry.setOnClickListener { error = null; repository.resumeArea(); refresh() }
        cancel.setOnClickListener { stopLocation(); candidate = null; repository.cancelDownloads(); refresh() }
        locationSettings.setOnClickListener { activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        sheet.dialog.setOnDismissListener { sheet.closed = true; repository.unlisten(changed); storageWorker.shutdownNow(); stopLocation(); sourceDialog?.dismiss() }
    }
    fun show() { repository.listen(changed); sheet.show(); refresh() }
    fun close() { sheet.close() }
    fun saveState() = Bundle().apply { candidate?.let { putDouble("latitude", it.latitude); putDouble("longitude", it.longitude) } }
    fun restoreState(state: Bundle) {
        candidate = if (state.containsKey("latitude")) runCatching { TerrainDownloadArea(state.getDouble("latitude"), state.getDouble("longitude")) }.getOrNull() else null
        refresh()
    }
    fun locate() {
        stopLocation(); candidate = null; error = null
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            error = R.string.terrain_location_permission; refresh(); return
        }
        locating = true; refresh()
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.filter(::usable).maxByOrNull { it.elapsedRealtimeNanos }
        if (cached != null) { onLocationChanged(cached); return }
        var started = false
        providers.forEach { provider -> if (runCatching { manager.requestLocationUpdates(provider, 1000L, 0f, this, Looper.getMainLooper()) }.isSuccess) started = true }
        if (started) handler.postDelayed(timeout, 20_000) else { stopLocation(); error = R.string.terrain_location_failed; refresh() }
    }
    fun permissionDenied() { error = R.string.terrain_location_permission; refresh() }
    private fun usable(location: Location): Boolean = location.hasAccuracy() && location.accuracy.isFinite() && location.accuracy in 0f..250f &&
        SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos in 0..120_000_000_000L &&
        location.latitude.isFinite() && location.longitude.isFinite()
    override fun onLocationChanged(location: Location) {
        if (!locating || !isShowing || !usable(location)) return
        stopLocation()
        candidate = runCatching { TerrainDownloadArea(location.latitude, location.longitude) }.getOrNull()
        if (candidate == null) error = R.string.terrain_polar_unavailable
        refresh()
    }
    @Deprecated("Legacy LocationListener callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
    private fun stopLocation() { locating = false; handler.removeCallbacks(timeout); runCatching { manager.removeUpdates(this) } }
    private fun refresh() {
        if (sheet.closed) return
        if (measuring.compareAndSet(false, true)) storageWorker.execute {
            val bytes = repository.cacheBytes()
            handler.post { measuring.set(false); if (!sheet.closed) cacheSize.text = activity.getString(R.string.v16_terrain_cache, android.text.format.Formatter.formatFileSize(activity, bytes)) }
        }
        val state = repository.areaStatus(); val area = candidate
        useMaps.isChecked = repository.enabled
        title.text = activity.getString(when {
            locating -> R.string.terrain_locating
            error != null -> R.string.terrain_not_ready
            area != null -> R.string.terrain_area_preview
            !repository.enabled -> R.string.terrain_maps_off
            state.loading -> R.string.terrain_downloading
            state.ready -> R.string.terrain_ready
            state.area != null -> R.string.terrain_incomplete
            else -> R.string.terrain_prepare
        })
        detail.text = when {
            error != null -> activity.getString(error!!)
            locating -> activity.getString(R.string.terrain_location_wait)
            area != null -> activity.getString(R.string.terrain_area_size, ceil(area.maxBytes / 1_000_000.0).toInt())
            state.area != null -> activity.getString(R.string.terrain_area_progress, state.completed, state.total, state.bytes / 1_000_000.0)
            else -> activity.getString(R.string.terrain_prepare_hint)
        }
        progress.visibility = if (locating || state.loading) View.VISIBLE else View.GONE
        progress.isIndeterminate = locating
        progress.max = state.total.coerceAtLeast(1); progress.progress = state.completed
        primary.text = activity.getString(if (area == null) R.string.terrain_use_location else R.string.terrain_download_area)
        primary.isEnabled = !locating && !state.loading
        retry.visibility = if (repository.enabled && state.area != null && !state.ready && !state.loading && !locating && area == null) View.VISIBLE else View.GONE
        cancel.visibility = if (locating || state.loading || area != null) View.VISIBLE else View.GONE
        locationSettings.visibility = if (error == R.string.terrain_location_failed) View.VISIBLE else View.GONE
    }
}
