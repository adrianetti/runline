package com.runline.app.sharing

import com.runline.app.core.ActivityDetails
import com.runline.app.core.GpxExporter
import com.runline.app.core.MarkerMotion
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunSession
import com.runline.app.core.StopDetection
import com.runline.app.core.TerrainTrend
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Everything one activity recorded, in one file the runner can hand to a person or an AI:
 * the original GPX, every fix with derived terrain/stop columns, the coach's spoken log,
 * the measured summary and any saved review. Photos and the runner profile are never included.
 */
object RunDataBundle {
    fun write(
        output: OutputStream,
        run: RunSession,
        terrainRun: RunSession,
        details: ActivityDetails,
        summary: JSONObject,
        coachEvents: List<JSONObject>,
        review: JSONObject?,
    ) {
        require(terrainRun.points.size == run.points.size)
        ZipOutputStream(output).use { zip ->
            fun put(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            put("README.txt", README)
            put("activity.gpx", GpxExporter.export(run, details))
            put("summary.json", summary.toString(2))
            put("points.csv", pointsCsv(run, terrainRun))
            put("stops.csv", stopsCsv(run))
            put("coach-log.json", JSONObject().put("coordinates_included", true).put("events", JSONArray(coachEvents)).toString(2))
            review?.let { put("review.json", it.toString(2)) }
        }
    }

    /** One row per recorded fix. Terrain columns are derived; GPS columns are as recorded. */
    fun pointsCsv(run: RunSession, terrainRun: RunSession): String = buildString {
        append("time_utc,elapsed_s,segment,latitude,longitude,horizontal_accuracy_m,vertical_accuracy_m,gps_altitude_m,")
        append("terrain_altitude_m,terrain_model,terrain_grade_percent,terrain_direction,distance_m,stopped\r\n")
        val stops = StopDetection.detect(run.points, run.activityType)
        val trend = TerrainTrend(terrainRun.elevationSource)
        val start = run.points.firstOrNull()?.timestampMs ?: 0L
        var distance = 0.0
        run.points.forEachIndexed { index, point ->
            val terrain = terrainRun.points[index]
            val estimate = trend.update(terrain)
            if (index > 0 && MarkerMotion.canInterpolate(run.points[index - 1], point))
                distance += RunMetrics.distanceMeters(listOf(run.points[index - 1], point))
            val stopped = stops.any { point.timestampMs in it.startMs..it.endMs }
            append(listOf(Instant.ofEpochMilli(point.timestampMs).toString(), number((point.timestampMs - start) / 1000.0, 0),
                point.segment.toString(), number(point.latitude, 7), number(point.longitude, 7), number(point.accuracyMeters.toDouble(), 1),
                point.verticalAccuracyMeters?.let { number(it.toDouble(), 1) } ?: "", point.altitudeMeters?.let { number(it, 1) } ?: "",
                terrain.altitudeMeters?.let { number(it, 1) } ?: "", terrain.terrainModel?.name ?: "",
                estimate.gradePercent?.let { number(it, 1) } ?: "", estimate.direction.name, number(distance, 1), if (stopped) "1" else "0",
            ).joinToString(",")).append("\r\n")
        }
    }

    fun stopsCsv(run: RunSession): String = buildString {
        append("start_utc,end_utc,duration_s,latitude,longitude\r\n")
        StopDetection.detect(run.points, run.activityType).forEach { stop ->
            val at = run.points.firstOrNull { it.timestampMs >= stop.startMs }
            append(listOf(Instant.ofEpochMilli(stop.startMs).toString(), Instant.ofEpochMilli(stop.endMs).toString(),
                number(stop.durationMs / 1000.0, 0), at?.let { number(it.latitude, 7) } ?: "", at?.let { number(it.longitude, 7) } ?: "")
                .joinToString(",")).append("\r\n")
        }
    }

    private fun number(value: Double, decimals: Int) = if (value.isFinite()) String.format(Locale.ROOT, "%.${decimals}f", value) else ""

    val README = """
        Runline activity data
        =====================
        This ZIP contains one activity's full recording, including its exact route. Share it only
        with people or services you trust. Photos and your runner profile are not included.

        activity.gpx    The original GPS fixes, as recorded.
        points.csv      One row per fix: GPS columns as recorded, plus derived columns. terrain_*
                        comes from the terrain map in use when this file was made; empty means
                        unknown (no data, or a bridge, underpass or embankment made it ambiguous).
                        stopped = 1 marks a detected standstill such as a traffic light.
        stops.csv       Detected standstills with start, end and duration.
        summary.json    Distance, moving and elapsed time, splits excluding stops, laps,
                        elevation totals and your title, notes and effort.
        coach-log.json  What the voice coach queued, said or dropped, with time and position.
        review.json     The saved AI review, when there is one.

        Times are UTC (ISO 8601). Distances in metres unless named _km. Pace in seconds per km.
    """.trimIndent() + "\n"
}
