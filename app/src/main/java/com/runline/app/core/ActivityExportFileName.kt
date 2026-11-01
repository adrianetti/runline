package com.runline.app.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A portable, human-readable export name; it never requires geocoding or network access. */
object ActivityExportFileName {
    /** Reserve a readable name ourselves; some providers append collisions after .gpx. */
    fun availableName(desired: String, existingNames: Collection<String>): String {
        require(desired.endsWith(".gpx", ignoreCase = true))
        val taken = existingNames.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        if (desired.lowercase(Locale.ROOT) !in taken) return desired
        val stem = desired.dropLast(4)
        var copy = 2
        while (true) {
            val candidate = "$stem ($copy).gpx"
            if (candidate.lowercase(Locale.ROOT) !in taken) return candidate
            copy++
        }
    }

    fun format(activityName: String, run: RunSession, locale: Locale, zone: ZoneId): String {
        val safeName = activityName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "-")
            .replace(Regex("\\s+"), " ").trim(' ', '.').ifEmpty { "Activity" }
        val kilometers = RunMetrics.distanceMeters(run.points) / 1000.0
        val distance = String.format(locale, "%.2f", kilometers)
        val date = Instant.ofEpochMilli(run.startedAtMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm", Locale.ROOT))
        return "$safeName $distance km - $date.gpx"
    }
}
