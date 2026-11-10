package com.runline.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast
import com.runline.app.R
import java.util.Locale

/**
 * Street directions to a route's start come from the phone's maps app: walking navigation in
 * Google Maps when it is installed, otherwise any app that opens a map point. Recording keeps
 * running in Runline meanwhile, and its own guidance takes over once you reach the route.
 */
object RouteDirections {
    fun open(context: Context, latitude: Double, longitude: Double) {
        // Coordinates always use a dot, whatever the phone's language.
        val point = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)
        val walking = Intent(Intent.ACTION_VIEW, "google.navigation:q=$point&mode=w".toUri())
            .setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val anyMap = Intent(Intent.ACTION_VIEW, "geo:$point?q=$point".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(walking) }
        catch (_: ActivityNotFoundException) {
            try { context.startActivity(anyMap) }
            catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.v21_directions_failed, Toast.LENGTH_LONG).show() }
        }
    }
}
