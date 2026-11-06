package com.runline.app.sharing

import com.runline.app.core.ActivityDetails
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import com.runline.app.core.TerrainLevel
import com.runline.app.core.TerrainModel
import com.runline.app.core.TerrainSource
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class RunDataBundleTest {
    private fun run(): RunSession {
        val points = mutableListOf<RunPoint>()
        var time = 1_000_000L; var east = 0.0
        fun add() { points += RunPoint(37.17, -3.6 + east / 88_600.0, time, 4f, 700.0 + east / 100, verticalAccuracyMeters = 3f) }
        add(); repeat(60) { time += 1_000; east += 3.0; add() }
        repeat(3) { time += 10_000; east += .5; add() }
        repeat(60) { time += 1_000; east += 3.0; add() }
        return RunSession("bundle", 1_000_000L, time, time - 1_000_000L, points)
    }

    private fun unzip(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes().toString(Charsets.UTF_8)) }
        }
    }

    @Test fun `bundle holds route, every fix, stops, coach log and review but no photos`() {
        val original = run()
        val terrain = original.copy(points = original.points.mapIndexed { index, point ->
            if (index == 10) point.copy(altitudeMeters = null, verticalAccuracyMeters = null, terrainModel = TerrainModel.STRUCTURE)
            else if (index == 20) point.copy(altitudeMeters = 651.0, verticalAccuracyMeters = null, terrainModel = TerrainModel.IGN_MDT05, terrainLevel = TerrainLevel.ABOVE)
            else point.copy(altitudeMeters = 650.0, verticalAccuracyMeters = null, terrainModel = TerrainModel.IGN_MDT05)
        }, elevationSource = TerrainSource.TERRAIN_MAP)
        val output = ByteArrayOutputStream()
        RunDataBundle.write(output, original, terrain, ActivityDetails(title = "Buscando mi ritmo", photoIds = listOf("photo1")),
            JSONObject().put("moving_seconds", 120), listOf(JSONObject().put("event", "started").put("text", "Hola")), JSONObject().put("text", "Bien"))
        val files = unzip(output.toByteArray())
        assertEquals(setOf("README.txt", "activity.gpx", "summary.json", "points.csv", "stops.csv", "coach-log.json", "review.json"), files.keys)
        val rows = files.getValue("points.csv").trim().lines()
        assertEquals(original.points.size + 1, rows.size)
        assertTrue(rows[0].startsWith("time_utc,elapsed_s,segment,latitude,longitude"))
        // GPS columns stay as recorded; the structure point has no derived terrain height.
        assertTrue(rows[1].contains(",700.0,650.0,IGN_MDT05,"))
        assertTrue(rows[11].contains(",700.") && rows[11].contains(",,STRUCTURE,"))
        assertTrue(rows[0].endsWith(",stopped,terrain_level"))
        assertTrue(rows.any { it.endsWith(",1,") })
        assertTrue(rows[21].endsWith(",above"))
        assertEquals(2, files.getValue("stops.csv").trim().lines().size)
        assertEquals("Hola", JSONObject(files.getValue("coach-log.json")).getJSONArray("events").getJSONObject(0).getString("text"))
        assertTrue(files.getValue("activity.gpx").contains("Buscando mi ritmo"))
        assertFalse(files.keys.any { it.contains("photo", ignoreCase = true) })
    }

    @Test fun `review is optional and mismatched terrain projections are rejected`() {
        val original = run()
        val output = ByteArrayOutputStream()
        RunDataBundle.write(output, original, original, ActivityDetails(), JSONObject(), emptyList(), null)
        assertFalse("review.json" in unzip(output.toByteArray()))
        assertThrows(IllegalArgumentException::class.java) {
            RunDataBundle.write(ByteArrayOutputStream(), original, original.copy(points = original.points.drop(1)), ActivityDetails(), JSONObject(), emptyList(), null)
        }
    }
}
