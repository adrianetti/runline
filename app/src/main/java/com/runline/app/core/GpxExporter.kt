package com.runline.app.core

import java.math.BigDecimal
import java.time.Instant

/** Standard GPX 1.1 for a portable copy of the recorded activity route. */
object GpxExporter {
    fun export(run: RunSession): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Runline\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <metadata><time>")
        append(Instant.ofEpochMilli(run.startedAtMs))
        append("</time></metadata>\n")
        append("  <trk><name>")
        append(escapeXml("${run.activityType.gpxName} ${run.id}"))
        append("</name><type>").append(run.activityType.gpxType).append("</type>\n")
        for (segment in continuousSegments(run.points)) {
            append("    <trkseg>\n")
            for (point in segment) {
                append("      <trkpt lat=\"")
                append(decimal(point.latitude))
                append("\" lon=\"")
                append(decimal(point.longitude))
                append("\">")
                point.altitudeMeters?.takeIf { it.isFinite() }?.let {
                    append("<ele>").append(decimal(it)).append("</ele>")
                }
                append("<time>").append(Instant.ofEpochMilli(point.timestampMs)).append("</time>")
                append("</trkpt>\n")
            }
            append("    </trkseg>\n")
        }
        append("  </trk>\n</gpx>\n")
    }

    private fun decimal(number: Double): String = BigDecimal.valueOf(number).stripTrailingZeros().toPlainString()

    private fun escapeXml(text: String): String = buildString {
        var offset = 0
        while (offset < text.length) {
            val codePoint = Character.codePointAt(text, offset)
            offset += Character.charCount(codePoint)
            when (codePoint) {
                '&'.code -> append("&amp;")
                '<'.code -> append("&lt;")
                '>'.code -> append("&gt;")
                '"'.code -> append("&quot;")
                '\''.code -> append("&apos;")
                // Exclude forbidden XML 1.0 controls and unpaired surrogate characters.
                else -> if (codePoint == 9 || codePoint == 10 || codePoint == 13 ||
                    codePoint in 0x20..0xD7FF || codePoint in 0xE000..0xFFFD ||
                    codePoint in 0x10000..0x10FFFF
                ) appendCodePoint(codePoint)
            }
        }
    }
}
