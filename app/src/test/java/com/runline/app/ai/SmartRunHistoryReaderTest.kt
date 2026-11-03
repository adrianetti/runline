package com.runline.app.ai

import com.runline.app.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SmartRunHistoryReaderTest {
    private val start = Instant.parse("2026-11-02T23:30:00Z").toEpochMilli()
    private fun run(id: String, type: ActivityType = ActivityType.RUN) = RunSession(id, start, start + 60_000,
        60_000, (0..60).map { i -> RunPoint(40.0 + i * .00005, -3.0, start + i * 1000, 5f, 600.0 + i * .1) }, type)

    @Test fun editedTypeAndEffortOverrideOriginalRecordingWithoutInventingEffort() {
        val readings = SmartRunAiInput.history(listOf(run("retyped"), run("walk", ActivityType.WALK), run("unrated")), {
            when (it) {
                "retyped" -> ActivityDetails(effort = 9, activityType = ActivityType.WALK)
                "walk" -> ActivityDetails(effort = 7, activityType = ActivityType.RUN)
                else -> ActivityDetails()
            }
        }, ZoneId.of("Europe/Madrid"))
        assertEquals(listOf("unrated", "walk"), readings.map { it.id })
        assertNull(readings.first().effort)
        assertEquals(7, readings.last().effort)
        assertEquals(LocalDate.of(2026, 11, 3), readings.last().date)
        assertTrue(readings.last().distanceKm > .3)
        assertEquals(60_000L, readings.last().activeDurationMs)
        assertNotNull(readings.last().elevationGainMeters)
    }

    @Test fun duplicatesNoGpsAndInvalidDurationsDoNotInflateTrainingVolume() {
        val valid = run("valid")
        val history = SmartRunAiInput.history(listOf(valid, valid,
            run("empty").copy(points = emptyList()), run("invalid").copy(activeDurationMs = 0)), { ActivityDetails() })
        assertEquals(1, history.size)
        assertEquals("valid", history.single().id)
    }

    @Test fun annotationReadFailureDoesNotSilentlyProduceAnIncompleteBaseline() {
        try {
            SmartRunAiInput.history(listOf(run("broken")), { error("Cannot read annotation") })
            fail("A broken history must surface to the caller")
        } catch (_: IllegalStateException) { }
    }
}
