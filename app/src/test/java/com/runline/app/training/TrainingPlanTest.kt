package com.runline.app.training

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrainingPlanTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val goal = TrainingGoal(21.1, today.plusWeeks(16), 3, 20.0)
    @Test fun localPlanIsBoundedAndRoundTrips() {
        val plan = TrainingPlanEngine.create(goal, today)
        assertTrue(plan.sessions.size in 40..48); assertEquals(plan.sessions.size, plan.sessions.map { it.date }.distinct().size)
        assertTrue(plan.sessions.all { it.distanceKm > 0 && it.distanceKm <= goal.distanceKm * .85 + .1 && it.date.isBefore(goal.raceDate) })
        assertEquals(plan, TrainingPlanEngine.parse(TrainingPlanEngine.json(plan)))
        assertFalse(TrainingPlanEngine.validateGoal(goal.copy(raceDate = today.plusDays(1)), today))
        assertFalse(TrainingPlanEngine.validateGoal(goal.copy(weeklyKm = Double.NaN), today))
    }
    @Test fun missedSessionsAreSkippedAndRunsLinkOnlyOnce() {
        val plan = TrainingPlanEngine.create(goal, today)
        val next = TrainingPlanEngine.reconcile(plan, today.plusDays(7), listOf(Triple("a", today, 5.0)))
        assertEquals(SessionState.DONE, next.sessions[0].state)
        assertEquals(1, next.sessions.count { it.activityId == "a" })
        assertTrue(next.sessions.filter { it.date.isBefore(today.plusDays(7)) }.all { it.state != SessionState.PLANNED })
        assertEquals(plan.sessions.map { it.date }, next.sessions.map { it.date })
    }
    @Test fun adviceCannotIncreaseLoadMoveDatesOrRewritePastStatus() {
        val base = TrainingPlanEngine.create(goal, today)
        val plan = base.copy(sessions = base.sessions.mapIndexed { i, s -> if (i == 0) s.copy(state = SessionState.DONE, activityId = "saved") else s })
        fun reply(multiplier: Double) = JSONObject().put("summary", "Keep the comfortable effort.").put("sessions", JSONArray().also { a -> plan.sessions.filter { it.state == SessionState.PLANNED }.forEach { s ->
            a.put(JSONObject().put("date", s.date.toString()).put("distance_km", s.distanceKm * multiplier).put("kind", "easy").put("note", "Comfortable effort.")) } }).toString()
        val updated = TrainingPlanEngine.applyAdvice(plan, reply(.9), today)
        assertEquals(plan.sessions.first(), updated.sessions.first()); assertEquals("anthropic", updated.source)
        try { TrainingPlanEngine.applyAdvice(plan, reply(2.0), today); fail() } catch (_: IllegalArgumentException) { }
        val broken = JSONObject(reply(.9)); broken.getJSONArray("sessions").getJSONObject(0).put("date", today.plusYears(1).toString())
        try { TrainingPlanEngine.applyAdvice(plan, broken.toString(), today); fail() } catch (_: Exception) { }
    }
    @Test fun duplicateDatesAndMissingSessionsAreRejected() {
        val plan = TrainingPlanEngine.create(goal, today)
        val empty = """{"summary":"A short summary","sessions":[]}"""
        try { TrainingPlanEngine.applyAdvice(plan, empty, today); fail() } catch (_: IllegalArgumentException) { }
        val json = TrainingPlanEngine.json(plan); json.getJSONArray("sessions").getJSONObject(1).put("date", today.toString())
        try { TrainingPlanEngine.parse(json); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun reducedSessionCannotJumpBackToOriginalEnvelope() {
        val base = TrainingPlanEngine.create(goal, today)
        val reduced = base.copy(sessions = base.sessions.map { it.copy(distanceKm = 1.0) })
        val response = JSONObject().put("summary", "Restore too much distance.").put("sessions", JSONArray().also { a -> base.sessions.forEach { s ->
            a.put(JSONObject().put("date", s.date.toString()).put("distance_km", s.distanceKm).put("kind", "easy").put("note", "Easy effort.")) } }).toString()
        try { TrainingPlanEngine.applyAdvice(reduced, response, today); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun twoDayLongRunIsAtLeastAsLongAsOtherRun() {
        val plan = TrainingPlanEngine.create(goal.copy(daysPerWeek = 2), today)
        assertTrue(plan.sessions[1].distanceKm >= plan.sessions[0].distanceKm)
        assertEquals("long", plan.sessions[1].kind)
    }

}
