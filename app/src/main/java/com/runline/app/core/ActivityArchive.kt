package com.runline.app.core

import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Portable analysis files; summary CSV deliberately contains no route coordinates. */
object ActivityArchive {
    data class Entry(val run: RunSession, val filename: String)

    fun entries(runs: List<RunSession>, locale: Locale, zone: ZoneId,
                activityName: (ActivityType) -> String): List<Entry> {
        val used = mutableSetOf<String>()
        return runs.sortedWith(compareByDescending<RunSession> { it.startedAtMs }.thenBy { it.id }).map { run ->
            val requested = ActivityExportFileName.format(activityName(run.activityType), run, locale, zone)
            val name = ActivityExportFileName.availableName(requested, used)
            used += name
            Entry(run, "activities/$name")
        }
    }

    fun writeZip(entries: List<Entry>, zone: ZoneId, output: OutputStream) {
        ZipOutputStream(output, Charsets.UTF_8).use { zip ->
            fun put(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("README.txt", README)
            put("summary.csv", summaryCsv(entries, zone))
            entries.forEach { put(it.filename, GpxExporter.export(it.run)) }
        }
    }

    /** Locale-independent headers, decimal points, ISO timestamps and RFC 4180 quoting. */
    fun summaryCsv(entries: List<Entry>, zone: ZoneId): String = buildString {
        append("activity_id,activity_type,started_at_utc,ended_at_utc,local_start,time_zone,active_seconds,elapsed_seconds,gps_observed_seconds,distance_km,average_pace_seconds_per_km,average_speed_kmh,ascent_m,descent_m,gps_point_count,gpx_segment_count,gpx_file\r\n")
        for ((run, filename) in entries) {
            val analysis = RunReplay(run).summary
            val distance = RunMetrics.distanceMeters(run.points)
            val pace = RunMetrics.paceSecondsPerKm(distance, run.activeDurationMs)
            val speed = if (run.activeDurationMs > 0 && distance > 0) distance * 3_600.0 / run.activeDurationMs else null
            val fields = listOf(
                run.id, run.activityType.storageValue,
                Instant.ofEpochMilli(run.startedAtMs).toString(), Instant.ofEpochMilli(run.endedAtMs).toString(),
                Instant.ofEpochMilli(run.startedAtMs).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), zone.id,
                number(run.activeDurationMs / 1000.0), number((run.endedAtMs - run.startedAtMs).coerceAtLeast(0) / 1000.0),
                number(analysis.observedDurationMs / 1000.0), number(distance / 1_000), number(pace), number(speed),
                number(analysis.elevationGainMeters), number(analysis.elevationLossMeters),
                run.points.size.toString(), continuousSegments(run.points).size.toString(), filename,
            )
            append(fields.joinToString(",", transform = ::csvCell)).append("\r\n")
        }
    }

    private fun number(value: Double?): String = value?.takeIf(Double::isFinite)?.let {
        String.format(Locale.ROOT, "%.3f", it)
    } ?: ""

    internal fun csvCell(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
        "\"${value.replace("\"", "\"\"")}\"" else value

    val README = """
        Runline activity export
        =======================
        summary.csv has one row per saved activity, with English column names, decimal points,
        UTC ISO 8601 timestamps and an additional local start/time-zone column. Empty cells
        mean unavailable data, not zero. Each gpx_file refers to a GPX in activities/ when this
        summary is inside the ZIP. A standalone CSV does not include those route files.

        Distance is horizontal GPS distance in kilometres. Pace is seconds per kilometre;
        lower pace means faster. Average speed is kilometres per hour. Average pace and speed
        use active time, which includes standing still and waiting for GPS but excludes explicit
        pauses. Elapsed time includes pauses. GPS observed time includes only continuous sample
        intervals, skipping segment boundaries and gaps longer than 15 seconds. It may be shorter
        than active time. GPX segment count describes the segments present in the exported GPX.

        Ascent/descent are metres estimated from GPS altitude with a 3-metre noise threshold;
        missing altitude and GPS gaps reset the altitude baseline. They are not terrain-corrected
        elevation or a measurement of slope. Old activities without a stored type default to run.
        Estimates can differ from other apps. The recorder does not measure heart rate or cadence.

        GPX files contain original accepted route coordinates, sample timestamps and available
        altitude. Display animation never adds samples. The CSV has no raw route coordinates.
        Share the ZIP only when you intend to disclose routes and precise times. Neither format
        is a complete restorable backup of Runline's private storage or recovery journal.

        For feedback: compare like activity types, inspect distance/time/elevation trends and
        distinguish missing values from zero. These observations do not establish GPS accuracy.
    """.trimIndent() + "\n"
}
