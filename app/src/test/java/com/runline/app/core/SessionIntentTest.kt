package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class SessionIntentTest {
    @Test fun durableGoalIsIndependentOfSpeechPreferencesAndKeepsPaceBand() {
        val target = CoachConfig(goal = CoachGoal.DISTANCE, distanceKm = 7.0, paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 360, effortIntent = SmartRunEffort.EASY)
        val saved = SessionIntent.parse(SessionIntent(target, "smart", "daily-123").json().toString())
        for (voice in listOf(false, true)) {
            val actual = saved.applyTo(CoachConfig(enabled = voice, goal = CoachGoal.TIME, targetMinutes = 80.0))
            assertEquals(voice, actual.enabled); assertEquals(CoachGoal.DISTANCE, actual.goal)
            assertEquals(7.0, actual.distanceKm, 0.0); assertEquals(330, actual.paceMinSecondsPerKm)
            assertEquals(360, actual.paceMaxSecondsPerKm); assertEquals(SmartRunEffort.EASY, actual.effortIntent)
        }
        assertEquals("smart", saved.source); assertEquals("daily-123", saved.suggestionId)
    }
    @Test fun freeRunClearsPreviouslySavedGlobalGoal() {
        val result = SessionIntent.parse(SessionIntent.free().json().toString()).applyTo(
            CoachConfig(enabled = true, goal = CoachGoal.DISTANCE, distanceKm = 10.0, paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 360))
        assertEquals(CoachGoal.NONE, result.goal); assertTrue(result.enabled)
        assertNull(result.paceMinSecondsPerKm); assertNull(result.paceMaxSecondsPerKm)
    }
    @Test fun timeAndCombinedManualGoalsRoundTrip() {
        for (goal in listOf(CoachGoal.TIME, CoachGoal.DISTANCE_TIME, CoachGoal.PACE)) {
            val intent = SessionIntent(CoachConfig(goal = goal, distanceKm = 5.0, targetMinutes = 35.0, paceSecondsPerKm = 330), "manual")
            assertEquals(intent, SessionIntent.parse(intent.json().toString()))
        }
    }
    @Test fun invalidIntentCannotBecomeARecordingTarget() {
        for (json in listOf(SessionIntent.free().json().put("version", 2), SessionIntent.free().json().put("source", "unknown"),
            SessionIntent.free().json().put("goal", "DISTANCE").put("distance_km", -5), SessionIntent.free().json().put("suggestion_id", "x".repeat(101)))) {
            try { SessionIntent.parse(json.toString()); fail() } catch (_: Exception) { }
        }
    }
}
