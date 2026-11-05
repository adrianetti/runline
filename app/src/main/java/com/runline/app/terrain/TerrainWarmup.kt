package com.runline.app.terrain

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.runline.app.core.RunPoint

/** Short foreground-only preparation; the recorder owns all live GPS requests. */
class TerrainWarmup(private val activity: Activity) : LocationListener {
    private val manager = activity.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stop() }
    private var listening = false
    fun start() {
        stop()
        val repo = TerrainRepository.get(activity)
        if (!repo.enabled || !repo.automatic || activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        listening = true
        val latest = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }.filter(::usable).maxByOrNull { it.elapsedRealtimeNanos }
        if (latest != null) { onLocationChanged(latest); return }
        providers.forEach { runCatching { manager.requestLocationUpdates(it, 1000, 0f, this, Looper.getMainLooper()) } }
        handler.postDelayed(timeout, 20_000)
    }
    fun stop() { listening = false; handler.removeCallbacks(timeout); runCatching { manager.removeUpdates(this) } }
    private fun usable(p: Location) = p.hasAccuracy() && p.accuracy.isFinite() && p.accuracy in 0f..100f &&
        p.latitude.isFinite() && p.longitude.isFinite() && SystemClock.elapsedRealtimeNanos() - p.elapsedRealtimeNanos in 0..120_000_000_000L
    override fun onLocationChanged(location: Location) {
        if (!listening || !usable(location)) return
        stop(); TerrainRepository.get(activity).request(listOf(RunPoint(location.latitude, location.longitude, location.time, location.accuracy)))
    }
}
