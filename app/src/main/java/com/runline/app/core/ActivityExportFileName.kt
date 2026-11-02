package com.runline.app.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A portable, human-readable export name; it never requires geocoding or network access. */
object ActivityExportFileName {
    /** Reserve a readable name ourselves; some providers append collisions after an unknown extension. */
    fun availableName(desired: String, existingNames: Collection<String>): String {
        val extension = desired.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension in setOf("gpx", "zip", "csv"))
        val taken = existingNames.mapTo(HashSet()) { it.lowercase(Locale.ROOT) }
        val stem = desired.dropLast(extension.length + 1)
        val first = utf8Prefix(stem, MAX_BYTES - extension.length - 1) + ".$extension"
        if (first.lowercase(Locale.ROOT) !in taken) return first
        var copy = 2
        while (true) {
            val suffix = " ($copy).$extension"
            val candidate = utf8Prefix(stem, MAX_BYTES - suffix.toByteArray(Charsets.UTF_8).size) + suffix
            if (candidate.lowercase(Locale.ROOT) !in taken) return candidate
            copy++
        }
    }

    fun format(activityName: String, run: RunSession, locale: Locale, zone: ZoneId, title: String? = null): String {
        val requested = title?.takeIf { it.isNotBlank() } ?: activityName
        val safeName = requested.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}\\u202A-\\u202E\\u2066-\\u2069]"), "-")
            .replace(Regex("\\s+"), " ").trim(' ', '.').ifEmpty { "Activity" }
        val kilometers = RunMetrics.distanceMeters(run.points) / 1000.0
        val distance = String.format(locale, "%.2f", kilometers)
        val date = Instant.ofEpochMilli(run.startedAtMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm", Locale.ROOT))
        val suffix = " $distance km - $date.gpx"
        // Leave room for duplicate suffixes. Bound bytes as well as characters; emoji can be 4 bytes.
        val budget = (MAX_BYTES - 12 - suffix.toByteArray(Charsets.UTF_8).size).coerceAtLeast(8).coerceAtMost(160)
        val stem = utf8Prefix(safeName, budget, 80).trim(' ', '.').ifEmpty { "Activity" }
        return stem + suffix
    }

    private fun utf8Prefix(text: String, maxBytes: Int, maxCodePoints: Int = Int.MAX_VALUE): String = buildString {
        var offset = 0; var bytes = 0; var count = 0
        while (offset < text.length && count < maxCodePoints) {
            val point = text.codePointAt(offset); offset += Character.charCount(point)
            if (point in 0xD800..0xDFFF) continue
            val size = when { point <= 0x7F -> 1; point <= 0x7FF -> 2; point <= 0xFFFF -> 3; else -> 4 }
            if (bytes + size > maxBytes) break
            appendCodePoint(point); bytes += size; count++
        }
    }
    private const val MAX_BYTES = 240
}
