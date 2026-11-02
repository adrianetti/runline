package com.runline.app.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class ActivityArchiveTest {
    private val start = Instant.parse("2026-11-01T15:30:00Z").toEpochMilli()
    private val zone = ZoneId.of("Europe/Madrid")
    private val points = listOf(
        RunPoint(37.0, -3.0, start, 4f, 100.0),
        RunPoint(37.0, -2.999, start + 10_000, 4f, 110.0),
        RunPoint(37.01, -2.99, start + 40_000, 4f, 150.0, segment = 1),
        RunPoint(37.01, -2.989, start + 50_000, 4f, 145.0, segment = 1),
    )
    private fun run(id: String = "walk") = RunSession(id, start, start + 50_000, 30_000, points, ActivityType.WALK)

    @Test fun zipContainsUniqueReadableGpxFilesAndCompleteAnalysisWithoutChangingSource() {
        val sessions = listOf(run("a"), run("b"))
        val originals = sessions.map { it.copy(points = it.points.toList()) }
        val entries = ActivityArchive.entries(sessions, Locale.forLanguageTag("es"), zone) { "Caminata" }
        assertEquals(2, entries.map { it.filename }.toSet().size)
        assertTrue(entries[1].filename.endsWith(" (2).gpx"))
        assertTrue(entries[0].filename.startsWith("activities/Caminata "))
        val output = ByteArrayOutputStream()
        ActivityArchive.writeZip(entries, zone, output)
        val files = readZip(output.toByteArray())
        assertEquals(setOf("README.txt", "summary.csv") + entries.map { it.filename }, files.keys)
        val xmlFactory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        for (entry in entries) {
            val xml = xmlFactory.newDocumentBuilder().parse(ByteArrayInputStream(files.getValue(entry.filename)))
            assertEquals(4, xml.getElementsByTagNameNS("*", "trkpt").length)
            assertEquals(2, xml.getElementsByTagNameNS("*", "trkseg").length)
            assertEquals("walking", xml.getElementsByTagNameNS("*", "type").item(0).textContent)
        }
        val csv = parseCsv(files.getValue("summary.csv").toString(Charsets.UTF_8))
        assertEquals(3, csv.size)
        assertEquals(entries[0].filename, csv[1][csv[0].indexOf("gpx_file")])
        assertEquals(originals, sessions)
    }

    @Test fun csvKeepsUnitsUnknownValuesAndEscapedIdsIndependentOfUiLocale() {
        val specialId = "walk,\"morning\"\nsecond line"
        val sessions = listOf(run(specialId), run("empty").copy(points = emptyList(), activeDurationMs = 0))
        val entries = ActivityArchive.entries(sessions, Locale.forLanguageTag("es"), zone) { "Caminata" }
        val rows = parseCsv(ActivityArchive.summaryCsv(entries, zone))
        val header = rows.first()
        val records = rows.drop(1).map { header.zip(it).toMap() }.associateBy { it.getValue("activity_id") }
        val walk = records.getValue(specialId)
        assertEquals("walk", walk["activity_type"])
        assertEquals("2026-11-01T15:30:00Z", walk["started_at_utc"])
        assertEquals("2026-11-01T16:30:00+01:00", walk["local_start"])
        assertEquals("30.000", walk["active_seconds"])
        assertEquals("50.000", walk["elapsed_seconds"])
        assertEquals("20.000", walk["gps_observed_seconds"])
        assertEquals("10.000", walk["ascent_m"])
        assertEquals("5.000", walk["descent_m"])
        assertEquals("2", walk["gpx_segment_count"])
        assertFalse(walk.getValue("distance_km").contains(','))
        assertEquals("", records.getValue("empty")["average_pace_seconds_per_km"])
        assertEquals("", records.getValue("empty")["ascent_m"])
        assertEquals("0.000", records.getValue("empty")["distance_km"])
        assertFalse(header.any { it.contains("latitude") || it.contains("longitude") })
        assertTrue(rows.all { it.size == header.size })
    }

    @Test fun csvRetainsGpsGapsAsUnobservedTimeAndUnknownAltitude() {
        val session = run().copy(activeDurationMs = 60_000, points = listOf(
            RunPoint(37.0, -3.0, start, 4f, 100.0),
            RunPoint(37.0, -2.999, start + 60_000, 4f, 150.0),
        ))
        val entries = ActivityArchive.entries(listOf(session), Locale.ENGLISH, zone) { "Walk" }
        val rows = parseCsv(ActivityArchive.summaryCsv(entries, zone))
        val result = rows[0].zip(rows[1]).toMap()
        assertEquals("0.000", result["gps_observed_seconds"])
        assertEquals("", result["ascent_m"])
        assertEquals("", result["descent_m"])
        assertTrue(ActivityArchive.README.contains("Empty cells"))
        assertTrue(ActivityArchive.README.contains("3-metre"))
    }

    @Test fun archivesAndCsvCopiesKeepTheirRecognizableExtensions() {
        assertEquals("Runline activities (2).zip", ActivityExportFileName.availableName("Runline activities.zip", listOf("Runline activities.zip")))
        assertEquals("Runline summary (3).csv", ActivityExportFileName.availableName("Runline summary.csv", listOf("Runline summary.csv", "Runline summary (2).csv")))
    }


    @Test fun editedMetadataAppearsInGpxAndCsvWithoutTurningTextIntoSpreadsheetFormulas() {
        val notes = ActivityDetails("=Three <laps> & \"friends\"", "@Warmup, then\nsteady <effort>.", 7, ActivityType.RUN, RoutineType.TEMPO)
        val entries = ActivityArchive.entries(listOf(run()), Locale.ENGLISH, zone, mapOf("walk" to notes)) { it.gpxName }
        val rows = parseCsv(ActivityArchive.summaryCsv(entries, zone))
        val row = rows[0].zip(rows[1]).toMap()
        assertEquals("'" + notes.title, row["title"])
        assertEquals("'" + notes.description, row["description"])
        assertEquals("7", row["effort"]); assertEquals("tempo", row["routine"]); assertEquals("run", row["activity_type"])
        val xml = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(ByteArrayInputStream(GpxExporter.export(entries.single().run, notes).toByteArray()))
        assertEquals(notes.title, xml.getElementsByTagNameNS("*", "name").item(0).textContent)
        assertEquals(notes.description, xml.getElementsByTagNameNS("*", "desc").item(0).textContent)
        assertEquals("running", xml.getElementsByTagNameNS("*", "type").item(0).textContent)
        assertEquals(points.size, xml.getElementsByTagNameNS("*", "trkpt").length)
        assertTrue(ActivityArchive.README.contains("Photos are private"))
    }

    @Test fun formulaTextGuardIncludesWhitespaceAndLeavesOrdinaryNotesReadable() {
        for (text in listOf("=2+2", "+SUM(A1)", "-1+2", "@lookup", "  =1", "\tplain", "\rline", "\nline"))
            assertEquals("'" + text, ActivityArchive.safeSpreadsheetText(text))
        assertEquals("Three park laps 🏃", ActivityArchive.safeSpreadsheetText("Three park laps 🏃"))
        assertEquals("", ActivityArchive.safeSpreadsheetText(""))
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes), Charsets.UTF_8).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                check(!entry.name.contains(".."))
                files[entry.name] = zip.readBytes()
                zip.closeEntry()
            }
        }
        return files
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val c = text[index++]
            when {
                c == '"' && quoted && index < text.length && text[index] == '"' -> { value.append('"'); index++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += value.toString(); value.setLength(0) }
                c == '\r' && !quoted -> Unit
                c == '\n' && !quoted -> { row += value.toString(); value.setLength(0); rows += row; row = mutableListOf() }
                else -> value.append(c)
            }
        }
        check(!quoted)
        return rows
    }
}
