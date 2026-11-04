package com.runline.app.diagnostics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.io.File

class DiagnosticLogTest {
    @Test fun tornTailDoesNotLosePastOrNextEventsAndExportRedactsCoordinates() {
        val folder = Files.createTempDirectory("diagnostic").toFile()
        try {
            val log = DiagnosticLog(folder)
            log.append("run", JSONObject().put("event", "started").put("text", "Good rhythm").put("latitude", 37.0).put("longitude", -3.0).put("at_ms", 1000))
            File(folder, "run.jsonl").appendText("{\"torn\":")
            log.append("run", JSONObject().put("event", "completed"))
            assertEquals(2, log.read("run").size)
            assertFalse(log.export("run", false).contains("latitude")); assertFalse(log.export("run", false).contains("longitude"))
            assertTrue(log.export("run", true).contains("latitude")); assertTrue(log.export("run", false).contains("Good rhythm"))
            assertThrows(IllegalArgumentException::class.java) { log.read("../outside") }
        } finally { folder.deleteRecursively() }
    }
    @Test fun separatorsDoNotHideRecentEventsBeforeByteRetentionLimit() {
        val folder = Files.createTempDirectory("diagnostic-small").toFile()
        try {
            val log = DiagnosticLog(folder)
            repeat(2100) { log.append("usage", JSONObject().put("at_ms", it)) }
            assertEquals(2100, log.read("usage").size)
            assertEquals(2099, log.read("usage").last().getInt("at_ms"))
        } finally { folder.deleteRecursively() }
    }
    @Test fun logsAreBoundedAndDeletingThemDoesNotDeleteSiblingRecordings() {
        val root = Files.createTempDirectory("diagnostic").toFile()
        try {
            File(root, "recording.json").writeText("keep")
            val folder = File(root, "diagnostics"); val log = DiagnosticLog(folder)
            repeat(800) { log.append("run", JSONObject().put("text", "x".repeat(1000)).put("at_ms", it)) }
            assertTrue(File(folder, "run.jsonl").length() < 520_000)
            assertEquals(799, log.read("run").last().getInt("at_ms"))
            log.clear(); assertEquals("keep", File(root, "recording.json").readText()); assertTrue(log.ids().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
