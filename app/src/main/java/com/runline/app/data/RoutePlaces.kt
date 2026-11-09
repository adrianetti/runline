package com.runline.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.runline.app.core.RouteImportNaming
import com.runline.app.core.RoutePoint
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Town and nearest named place for a route's naming points, from Android's built-in geocoder:
 * no API key or extra service. Offline, or on phones without a geocoder, every place is null and
 * the suggestion falls back to shape and distance. Call off the main thread.
 */
class RoutePlaces(context: Context) {
    private val app = context.applicationContext

    fun lookup(points: List<RoutePoint>, locale: Locale): List<RouteImportNaming.Place?> {
        if (!Geocoder.isPresent()) return points.map { null }
        val geocoder = Geocoder(app, locale)
        return points.map { point ->
            runCatching { RouteImportNaming.place(addresses(geocoder, point).map(::parts)) }.getOrNull()
        }
    }

    private fun addresses(geocoder: Geocoder, point: RoutePoint): List<Address> {
        if (Build.VERSION.SDK_INT < 33) {
            @Suppress("DEPRECATION")
            return geocoder.getFromLocation(point.latitude, point.longitude, RESULTS).orEmpty()
        }
        val result = AtomicReference<List<Address>>(emptyList())
        val done = CountDownLatch(1)
        geocoder.getFromLocation(point.latitude, point.longitude, RESULTS, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) { result.set(addresses.toList()); done.countDown() }
            override fun onError(errorMessage: String?) { done.countDown() }
        })
        done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return result.get()
    }

    private fun parts(a: Address) = RouteImportNaming.AddressParts(
        feature = a.featureName, premises = a.premises, street = a.thoroughfare, district = a.subLocality,
        town = a.locality, area = a.subAdminArea, region = a.adminArea, country = a.countryName)

    private companion object {
        const val RESULTS = 3
        const val TIMEOUT_SECONDS = 4L
    }
}
