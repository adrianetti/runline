package com.runline.app.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityExportFileNameTest {
    private val started = Instant.parse("2026-11-01T15:30:00Z").toEpochMilli()
    private val run = RunSession("walk", started, started + 60_000, 60_000, listOf(
        RunPoint(0.0, 0.0, started, 5f),
        RunPoint(0.0, 0.01, started + 20_000, 5f),
        RunPoint(0.0, 1.0, started + 40_000, 5f, segment = 1),
        RunPoint(0.0, 1.01, started + 60_000, 5f, segment = 1),
    ), ActivityType.WALK)

    @Test fun humanReadableNameUsesLanguageDecimalAndLocalStartTimeWithoutBridgingPauses() {
        assertEquals("Caminata 2,22 km - 2026-11-01 16-30.gpx",
            ActivityExportFileName.format("Caminata", run, Locale.forLanguageTag("es-ES"), ZoneId.of("Europe/Madrid")))
        assertEquals("Walk 2.22 km - 2026-11-01 15-30.gpx",
            ActivityExportFileName.format("Walk", run, Locale.ENGLISH, ZoneId.of("UTC")))
    }

    @Test fun emptyRouteStillExportsWithAFileSystemSafeReadableName() {
        val name = ActivityExportFileName.format("Walk / Hike:", run.copy(points = emptyList()), Locale.ENGLISH, ZoneId.of("UTC"))
        assertEquals("Walk - Hike- 0.00 km - 2026-11-01 15-30.gpx", name)
        assertFalse(name.any { it in "\\/:*?\"<>|" })
    }

    @Test fun repeatedExportsKeepTheGpxExtensionAndLeaveExistingNamesUntouched() {
        val original = "Caminata 8,37 km - 2026-11-01 18-48.gpx"
        val names = mutableListOf<String>()
        repeat(3) { names.add(ActivityExportFileName.availableName(original, names)) }
        assertEquals(listOf(original,
            "Caminata 8,37 km - 2026-11-01 18-48 (2).gpx",
            "Caminata 8,37 km - 2026-11-01 18-48 (3).gpx"), names)
    }

    @Test fun collisionsAreCaseInsensitiveAndCoexistWithOldProviderSuffixes() {
        val original = "Walk 2.22 km - 2026-11-01 15-30.gpx"
        val existing = listOf(original.uppercase(Locale.ROOT),
            "Walk 2.22 km - 2026-11-01 15-30 (2).GPX", "$original (1)")
        assertEquals("Walk 2.22 km - 2026-11-01 15-30 (3).gpx",
            ActivityExportFileName.availableName(original, existing))
    }

    @Test fun userTitleReplacesGenericTypeAndPreservesDistanceAndTime() {
        val name = ActivityExportFileName.format("Walk", run, Locale.ENGLISH, ZoneId.of("UTC"), "Park / three laps")
        assertEquals("Park - three laps 2.22 km - 2026-11-01 15-30.gpx", name)
        assertEquals(ActivityExportFileName.format("Walk", run, Locale.ENGLISH, ZoneId.of("UTC")),
            ActivityExportFileName.format("Walk", run, Locale.ENGLISH, ZoneId.of("UTC"), "  "))
    }

    @Test fun unicodeNamesAndCollisionsStayWithinPortableByteLimitsWithoutSplittingEmoji() {
        val name = ActivityExportFileName.format("Run", run, Locale.ENGLISH, ZoneId.of("UTC"), "🏃".repeat(120))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 240)
        assertFalse(name.contains('�')); assertFalse(name.contains('?'))
        assertTrue(name.endsWith("2.22 km - 2026-11-01 15-30.gpx"))
        val duplicate = ActivityExportFileName.availableName(name, listOf(name))
        assertTrue(duplicate.endsWith(" (2).gpx")); assertTrue(duplicate.toByteArray(Charsets.UTF_8).size <= 240)
        val huge = ActivityExportFileName.availableName("路".repeat(200) + ".gpx", emptyList())
        assertTrue(huge.toByteArray(Charsets.UTF_8).size <= 240)
        assertTrue(ActivityExportFileName.availableName(huge, listOf(huge)).toByteArray(Charsets.UTF_8).size <= 240)
    }

}
