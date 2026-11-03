package com.runline.app.ai

import com.runline.app.training.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class RunnerGoalTest {
    private val day = LocalDate.of(2027, 4, 30)
    private fun legacy(timing: TrainingTiming) = TrainingPlan(
        TrainingGoal(if (timing == TrainingTiming.ROLLING) 0.0 else 21.1, day, 3, 12.0,
            if (timing == TrainingTiming.ROLLING) TrainingGoalType.PERSONAL_PROGRESS else TrainingGoalType.HALF_MARATHON, timing),
        day.minusMonths(4), emptyList(), "PRIVATE generated schedule notes", 1)

    @Test fun freeformGoalIsBoundedAndClearedChoiceRemainsConfigured() {
        val saved = RunnerGoal.parse(RunnerGoal("  " + "g".repeat(900) + "  ").json())
        assertEquals(400, saved.text.length)
        val cleared = RunnerGoal().json()
        assertTrue(cleared.getBoolean("configured")); assertEquals("", RunnerGoal.parse(cleared).text)
        assertEquals(RunnerGoal(), RunnerGoal.parse(cleared))
    }

    @Test fun migrationPreservesDatePrecisionWithoutScheduleOrPrivateText() {
        val exact = RunnerGoal.fromLegacy(legacy(TrainingTiming.EXACT_DAY)).text
        val month = RunnerGoal.fromLegacy(legacy(TrainingTiming.TARGET_MONTH)).text
        val rolling = RunnerGoal.fromLegacy(legacy(TrainingTiming.ROLLING)).text
        assertTrue(exact.contains("2027-04-30")); assertTrue(month.endsWith("2027-04"))
        assertFalse(month.contains("2027-04-30")); assertFalse(rolling.contains("2027"))
        assertFalse(exact.contains("PRIVATE")); assertFalse(month.contains("12.0"))
        assertEquals(RunnerGoal(), RunnerGoal.fromLegacy(null))
    }

    @Test fun malformedMarkerIsNotMistakenForAnExplicitGoal() {
        try { RunnerGoal.parse(JSONObject("""{"version":1,"text":"ambiguous"}""")); fail() }
        catch (_: org.json.JSONException) { }
    }
}
