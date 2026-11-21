package com.runline.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.runline.app.R
import java.util.Locale

object RouteDirections {
    fun open(
        context: Context,
        latitude: Double,
        longitude: Double,
    ) {
        val point = String.format(Locale.ROOT, "%.6f,%.6f", latitude, longitude)
        val walking =
            Intent(Intent.ACTION_VIEW, "google.navigation:q=$point&mode=w".toUri())
                .setPackage("com.google.android.apps.maps")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val anyMap = Intent(Intent.ACTION_VIEW, "geo:$point?q=$point".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(walking)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(anyMap)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.v21_directions_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
