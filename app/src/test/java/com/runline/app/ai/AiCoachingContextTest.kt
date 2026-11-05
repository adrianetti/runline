package com.runline.app.ai

import com.runline.app.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AiCoachingContextTest {
    private val today = LocalDate.of(2026, 11, 2)
    private fun run(id: String = "active", at: Long = 1_000) = RunSession(id, at, at + 30_000, 30_000,
        (0..30).map { RunPoint(37.123456 + it * 2.0 / 111_195.0802, -3.123456, at + it * 1_000, 3f, 100.0 + it * .16) })
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
    @Test fun optionalAspirationIsBoundedAndNeverCreatesCalendarContext() {
        val context = AiCoachingContext.build(today, runnerGoal = RunnerGoal("Half marathon in April"))
        assertEquals("Half marathon in April", context.getJSONObject("runner_goal").getString("text"))
        assertFalse(context.has("training_plan")); assertFalse(context.toString().contains("race_date"))
        assertFalse(AiCoachingContext.build(today).has("runner_goal"))
        val long = AiCoachingContext.build(today, runnerGoal = RunnerGoal("g".repeat(900)))
        assertEquals(400, long.getJSONObject("runner_goal").getString("text").length)
    }
    @Test fun contextIsBoundedEvenWithLongHistoryAndConsentedNotes() {
        val data = AiCoachingContext.build(today, activeRun = run("active"), history = (0..100).map { run("history$it", it * 40_000L) },
            includeNotes = true, details = { ActivityDetails("t".repeat(120), "n".repeat(4000)) })
        assertEquals(8, data.getJSONArray("recent_runs").length())
        assertEquals(500, data.getJSONArray("recent_runs").getJSONObject(0).getString("user_notes").length)
        assertTrue(data.toString().length < 12_000)
    }
    @Test fun terrainUsesObservedWindowAndBecomesUnknownAfterGap() {
        val source = run().copy(elevationSource = com.runline.app.core.TerrainSource.TERRAIN_MAP)
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

    @Test fun completedLoadUsesCalendarWeekAndLatestActualEffortWithoutCountingWalksOrFutureRuns() {
        fun at(day: LocalDate, hour: Int = 8) = day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val runs = listOf(run("previous-week", at(today.minusDays(1))), run("morning", at(today)),
            run("evening", at(today, 18)), run("walk", at(today))).copyTypeForLast(ActivityType.WALK) + run("future", at(today.plusDays(1)))
        val data = AiCoachingContext.build(today, history = runs, details = {
            ActivityDetails(effort = if (it == "morning") 9 else null)
        }).getJSONObject("completed_training")
        assertEquals(today.toString(), data.getString("week_starts_on"))
        assertEquals(.12, data.getDouble("calendar_week_recorded_km"), .002)
        assertEquals(.18, data.getDouble("last_7_days_km"), .002)
        assertTrue(data.isNull("last_recorded_effort_1_to_10"))
        assertEquals(3, data.getInt("last_7_days_runs"))
    }

    @Test fun appliedGoalCarriesTimeAndPaceBandWithoutInventingCompletedMileage() {
        val goal = CoachConfig(enabled = true, goal = CoachGoal.TIME, targetMinutes = 25.0,
            paceMinSecondsPerKm = 390, paceMaxSecondsPerKm = 450, effortIntent = SmartRunEffort.RECOVERY)
        val context = AiCoachingContext.build(today, activeGoal = goal)
        val active = context.getJSONObject("active_goal")
        assertEquals("TIME", active.getString("type"))
        assertEquals(1500L, active.getLong("duration_goal_seconds"))
        assertEquals(390, active.getInt("pace_min_seconds_per_km"))
        assertEquals(450, active.getInt("pace_max_seconds_per_km"))
        assertEquals("RECOVERY", active.getString("intended_effort"))
        assertTrue(active.isNull("distance_goal_m"))
        assertEquals(0.0, context.getJSONObject("completed_training").getDouble("calendar_week_recorded_km"), 0.0)
    }

    @Test fun sparseHistoryDescribesRecordedWindowsWithoutManufacturingAWeeklyAllowance() {
        fun at(day: LocalDate) = day.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val recent = run("recent", at(today.minusDays(2)))
        val older = run("older", at(today.minusDays(9)))
        val data = AiCoachingContext.build(today, history = listOf(recent, recent, older),
            details = { ActivityDetails(effort = if (it == "older") 8 else null) }).getJSONObject("completed_training")
        assertEquals(2, data.getInt("recorded_runs_last_30_days"))
        assertEquals(2, data.getInt("recorded_days_last_30_days"))
        assertEquals(2, data.getInt("days_since_last_recorded_run"))
        assertTrue(data.isNull("last_recorded_effort_1_to_10"))
        val weeks = data.getJSONArray("recorded_7_day_windows")
        assertEquals(4, weeks.length())
        assertEquals(1, weeks.getJSONObject(0).getInt("recorded_runs"))
        assertEquals(0, weeks.getJSONObject(0).getInt("runs_with_reported_effort"))
        assertEquals(1, weeks.getJSONObject(1).getInt("runs_with_reported_effort"))
        assertEquals(0, weeks.getJSONObject(2).getInt("recorded_runs"))
        assertEquals(today.minusDays(6).toString(), weeks.getJSONObject(0).getString("from"))
        assertEquals(today.minusDays(7).toString(), weeks.getJSONObject(1).getString("through"))
        assertFalse(data.has("remaining_rolling_7_day_capacity_km"))
    }

    @Test fun noHistoryLeavesSessionAndGapEvidenceUnknown() {
        val data = AiCoachingContext.build(today).getJSONObject("completed_training")
        assertEquals(0, data.getInt("recorded_runs_last_30_days"))
        assertTrue(data.isNull("median_run_last_30_days_km"))
        assertTrue(data.isNull("longest_run_last_30_days_km"))
        assertTrue(data.isNull("days_since_last_recorded_run"))
    }

    private fun List<RunSession>.copyTypeForLast(type: ActivityType) = dropLast(1) + last().copy(activityType = type)
}
