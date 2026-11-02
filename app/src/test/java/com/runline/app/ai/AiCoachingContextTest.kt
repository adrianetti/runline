package com.runline.app.ai

import com.runline.app.core.*
import com.runline.app.training.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AiCoachingContextTest {
    private val today = LocalDate.of(2026, 11, 2)
    private fun run(id: String = "active", at: Long = 1_000) = RunSession(id, at, at + 30_000, 30_000,
        (0..30).map { RunPoint(37.123456 + it * 2.0 / 111_195.0802, -3.123456, at + it * 1_000, 3f, 100.0 + it * .16) })
    private fun plan(timing: TrainingTiming = TrainingTiming.TARGET_MONTH): TrainingPlan {
        val goal = if (timing == TrainingTiming.ROLLING) TrainingGoal(0.0, today.plusWeeks(8), 2, 8.0, TrainingGoalType.PERSONAL_PROGRESS, timing)
        else TrainingGoal(21.1, today.plusMonths(4).withDayOfMonth(31), 3, 12.0, timing = timing)
        return TrainingPlan(goal, today.minusWeeks(2), listOf(
            TrainingSession(today.minusDays(5), 4.0, "easy", "", SessionState.DONE),
            TrainingSession(today.minusDays(2), 5.0, "long", "", SessionState.SKIPPED),
            TrainingSession(today, 3.0, "recovery", "Comfortable effort"),
            TrainingSession(today.plusDays(3), 5.0, "long", "")), "", 1)
    }
    @Test fun profileAndNotesStayOutOfActualRequestByDefault() {
        val profile = RunnerProfile(RunningExperience.RETURNING, "PRIVATE PREFERENCE", "PRIVATE HEALTH")
        val context = AiCoachingContext.build(today, history = listOf(run()), profile = profile,
            details = { ActivityDetails("PRIVATE TITLE", "PRIVATE NOTES", 6, photoIds = listOf("PRIVATE PHOTO")) })
        var captured = ""
        val provider = AnthropicProvider(AiTransport { _, _, _, body, _ ->
            captured = body!!; AiHttpResponse(200, """{"stop_reason":"end_turn","content":[{"type":"text","text":"NONE"}]}""")
        })
        assertNull(LiveAiAdvice.request(provider, "fake-key", "claude-haiku-5-5", "English", context))
        assertFalse(captured.contains("PRIVATE")); assertFalse(captured.contains("37.123456")); assertFalse(captured.contains("-3.123456"))
        assertFalse(context.has("runner_profile")); assertFalse(context.toString().contains("latitude"))
        assertEquals(6, context.getJSONArray("recent_runs").getJSONObject(0).getInt("self_reported_effort_1_to_10"))
    }
    @Test fun explicitProfileConsentIsBoundedAndNeverInventsPersonalMeasurements() {
        val original = RunnerProfile(RunningExperience.STARTING, "p".repeat(900), "h".repeat(900), true)
        val restored = RunnerProfile.parse(original.json())
        val context = AiCoachingContext.build(today, profile = restored)
        val profile = context.getJSONObject("runner_profile")
        assertEquals(600, profile.getString("training_preferences").length)
        assertEquals(600, profile.getString("self_reported_health_notes").length)
        assertFalse(profile.has("age")); assertFalse(profile.has("heart_rate")); assertFalse(profile.has("diagnosis"))
        assertFalse(AiCoachingContext.build(today, profile = restored.copy(includeInAi = false)).has("runner_profile"))
        assertNull(RunnerProfile().forAi())
    }
    @Test fun planRetainsMonthPrecisionRecoveryIntentAndRecentAdherence() {
        val data = AiCoachingContext.build(today, plan = plan()).getJSONObject("training_plan")
        assertTrue(data.isNull("race_date")); assertEquals("2027-03", data.getString("target_month"))
        assertEquals("recovery", data.getJSONObject("today_session").getString("kind"))
        assertEquals(today.toString(), data.getJSONObject("next_planned_session").getString("date"))
        assertEquals(1, data.getJSONObject("recent_adherence_14_days").getInt("done"))
        assertEquals(1, data.getJSONObject("recent_adherence_14_days").getInt("skipped"))
        val rolling = AiCoachingContext.build(today, plan = plan(TrainingTiming.ROLLING)).getJSONObject("training_plan")
        assertTrue(rolling.isNull("race_date")); assertTrue(rolling.isNull("target_distance_km")); assertTrue(rolling.isNull("target_month"))
    }
    @Test fun contextIsBoundedEvenWithLongHistoryAndConsentedNotes() {
        val data = AiCoachingContext.build(today, activeRun = run("active"), history = (0..100).map { run("history$it", it * 40_000L) },
            includeNotes = true, details = { ActivityDetails("t".repeat(120), "n".repeat(4000)) })
        assertEquals(8, data.getJSONArray("recent_runs").length())
        assertEquals(500, data.getJSONArray("recent_runs").getJSONObject(0).getString("user_notes").length)
        assertTrue(data.toString().length < 12_000)
    }
    @Test fun terrainUsesObservedWindowAndBecomesUnknownAfterGap() {
        val source = run()
        val terrain = AiCoachingContext.build(today, activeRun = source).getJSONObject("recent_terrain")
        assertEquals("UPHILL", terrain.getString("direction")); assertEquals(8.0, terrain.getDouble("grade_percent"), .05)
        val gap = source.copy(points = source.points + source.points.last().copy(timestampMs = 90_000))
        val unknown = AiCoachingContext.build(today, activeRun = gap).getJSONObject("recent_terrain")
        assertEquals("UNKNOWN", unknown.getString("direction")); assertTrue(unknown.isNull("grade_percent"))
    }
    @Test fun speechHistoryContainsOnlyRecordedActualEventsAndExpires() {
        val id = "speech-context-test"; AiSpokenHistory.clear(id)
        assertTrue(AiSpokenHistory.recent(id, 100).isEmpty())
        AiSpokenHistory.record(id, "not spoken", "GENERATED", 100)
        assertTrue(AiSpokenHistory.recent(id, 100).isEmpty())
        repeat(9) { AiSpokenHistory.record(id, "Actual cue $it", if (it % 2 == 0) "LOCAL" else "AI", 100L + it) }
        val recent = AiSpokenHistory.recent(id, 200)
        assertEquals(6, recent.size); assertEquals("Actual cue 3", recent.first().text)
        val data = AiCoachingContext.build(today, spoken = recent, nowElapsedMs = 200).getJSONArray("recent_spoken_feedback")
        assertEquals(6, data.length()); assertEquals("AI", data.getJSONObject(0).getString("source"))
        assertTrue(AiSpokenHistory.recent(id, 1_000_000).isEmpty()); AiSpokenHistory.clear(id)
    }
    @Test fun liveResponseCanBeSilentAndRemainsOneBoundedUnstructuredThought() {
        assertNull(LiveAiAdvice.clean("  NONE  ")); assertNull(LiveAiAdvice.clean("none"))
        assertNull(LiveAiAdvice.clean("word ".repeat(27))); assertNull(LiveAiAdvice.clean("{\"speech\":\"slow down\"}"))
        assertEquals("Let this easy section stay comfortable.", LiveAiAdvice.clean("Let this easy section stay comfortable."))
    }
    @Test fun profileCodecDefaultsUnknownExperienceAndConsentToPrivate() {
        val value = RunnerProfile.parse(JSONObject("""{"version":1,"experience":"future","health_notes":"user note"}"""))
        assertEquals(RunningExperience.UNSPECIFIED, value.experience); assertFalse(value.includeInAi)
        assertEquals("user note", value.healthNotes); assertNull(value.forAi())
    }
    @Test fun privacyInvalidationClearsPreviouslySpokenProfileDerivedAdvice() {
        AiSpokenHistory.record("before-optout", "Private previous advice", "AI", 100)
        AiSpokenHistory.record("other-run", "Private previous advice", "AI", 100)
        AiSpokenHistory.clearAll()
        assertTrue(AiSpokenHistory.recent("before-optout", 200).isEmpty())
        assertTrue(AiSpokenHistory.recent("other-run", 200).isEmpty())
    }
}
