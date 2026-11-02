package com.runline.app.training

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

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

    @Test fun legacyPlanMigratesWithoutChangingDatesStatusOrHistoryLinks() {
        val base = TrainingPlanEngine.create(goal, today)
        val original = base.copy(sessions = base.sessions.mapIndexed { index, session -> when (index) {
            0 -> session.copy(state = SessionState.DONE, activityId = "saved-run")
            1 -> session.copy(state = SessionState.SKIPPED)
            else -> session
        } })
        val legacy = TrainingPlanEngine.json(original).put("version", 1).put("race_date", goal.raceDate.toString())
        listOf("goal_type", "timing", "plan_end_date", "target_month").forEach(legacy::remove)
        val migrated = TrainingPlanEngine.parse(legacy)
        assertEquals(original, migrated)
        assertEquals(2, TrainingPlanEngine.json(migrated).getInt("version"))
        assertEquals(original.sessions, TrainingPlanEngine.parse(TrainingPlanEngine.json(migrated)).sessions)
    }

    @Test fun monthGoalKeepsMonthPrecisionWithoutInventingRaceDay() {
        val month = YearMonth.of(2027, 3)
        val monthly = goal.copy(raceDate = month.atEndOfMonth(), timing = TrainingTiming.TARGET_MONTH)
        val plan = TrainingPlanEngine.create(monthly, today)
        val json = TrainingPlanEngine.json(plan)
        assertEquals("2027-03", json.getString("target_month"))
        assertTrue(json.isNull("race_date"))
        assertEquals(plan, TrainingPlanEngine.parse(json))
        assertFalse(TrainingPlanEngine.validateGoal(monthly.copy(raceDate = month.atDay(15)), today))
        try { TrainingPlanEngine.parse(JSONObject(json.toString()).put("target_month", "2027-04")); fail() } catch (_: IllegalArgumentException) { }
        try { TrainingPlanEngine.parse(JSONObject(json.toString()).put("race_date", month.atDay(15).toString())); fail() } catch (_: IllegalArgumentException) { }
    }

    @Test fun personalProgressHasBoundedRollingScheduleWithoutRaceOrDistanceTarget() {
        val rolling = TrainingGoal(0.0, today.plusWeeks(8), 2, 5.0, TrainingGoalType.PERSONAL_PROGRESS, TrainingTiming.ROLLING)
        val plan = TrainingPlanEngine.create(rolling, today)
        assertEquals(16, plan.sessions.size)
        assertTrue(plan.sessions.all { it.date >= today && it.date < today.plusWeeks(8) && it.distanceKm in 0.5..50.0 })
        val json = TrainingPlanEngine.json(plan)
        assertTrue(json.isNull("race_date")); assertTrue(json.isNull("target_month"))
        assertEquals(plan, TrainingPlanEngine.parse(json))
        assertFalse(TrainingPlanEngine.validateGoal(rolling.copy(raceDate = today.plusYears(1)), today))
        assertFalse(TrainingPlanEngine.validateGoal(rolling.copy(distanceKm = 21.1), today))
        assertFalse(TrainingPlanEngine.validateGoal(rolling.copy(timing = TrainingTiming.EXACT_DAY), today))
    }

    @Test fun namedGoalsRejectMismatchedDistancesAndKeepOriginalBounds() {
        for ((type, km) in listOf(TrainingGoalType.FIVE_K to 5.0, TrainingGoalType.TEN_K to 10.0, TrainingGoalType.HALF_MARATHON to 21.1)) {
            val named = goal.copy(distanceKm = km, goalType = type)
            assertTrue(TrainingPlanEngine.validateGoal(named, today))
            assertFalse(TrainingPlanEngine.validateGoal(named.copy(distanceKm = km + 1), today))
        }
        assertTrue(TrainingPlanEngine.validateGoal(goal.copy(distanceKm = 7.5, goalType = TrainingGoalType.CUSTOM), today))
        assertFalse(TrainingPlanEngine.validateGoal(goal.copy(timing = TrainingTiming.ROLLING), today))
        assertFalse(TrainingPlanEngine.validateGoal(goal.copy(raceDate = today.plusDays(365)), today))
    }

    @Test fun sparseRunHistoryIsShownSeparatelyFromSuggestedStartingLoad() {
        val baseline = TrainingBaselineEstimator.infer(listOf(TrainingHistoryRun(today, 8.4)), today)
        assertEquals(8.4, baseline.observedWeeklyKm, .001)
        assertEquals(1, baseline.observedRuns); assertEquals(7, baseline.windowDays)
        assertFalse(baseline.established)
        assertEquals(5.0, baseline.weeklyKm, .001); assertEquals(2, baseline.daysPerWeek)
        val empty = TrainingBaselineEstimator.infer(emptyList(), today)
        assertEquals(0, empty.observedRuns); assertEquals(0.0, empty.observedWeeklyKm, .001)
        assertEquals(5.0, empty.weeklyKm, .001)
    }

    @Test fun establishedHistoryUsesFourWeekAverageAndIgnoresInvalidOrOutsideWindowRuns() {
        val runs = listOf(0L, 8L, 15L, 22L).map { TrainingHistoryRun(today.minusDays(it), 10.0) } + listOf(
            TrainingHistoryRun(today.plusDays(1), 100.0), TrainingHistoryRun(today.minusDays(28), 100.0),
            TrainingHistoryRun(today, Double.NaN), TrainingHistoryRun(today, 0.0))
        val baseline = TrainingBaselineEstimator.infer(runs, today)
        assertTrue(baseline.established); assertEquals(28, baseline.windowDays)
        assertEquals(4, baseline.observedRuns); assertEquals(10.0, baseline.observedWeeklyKm, .001)
        assertEquals(10.0, baseline.weeklyKm, .001)
        val sparseOlder = TrainingBaselineEstimator.infer(listOf(TrainingHistoryRun(today.minusDays(10), 8.0)), today)
        assertEquals(28, sparseOlder.windowDays); assertEquals(2.0, sparseOlder.observedWeeklyKm, .001)
        assertFalse(sparseOlder.established)
    }

    @Test fun rollingPlanReconciliationAndAdvicePreserveManualStatusesAndLinks() {
        val rolling = TrainingGoal(0.0, today.plusWeeks(8), 2, 5.0, TrainingGoalType.PERSONAL_PROGRESS, TrainingTiming.ROLLING)
        val base = TrainingPlanEngine.create(rolling, today)
        val plan = base.copy(sessions = base.sessions.mapIndexed { index, session -> when (index) {
            0 -> session.copy(state = SessionState.DONE, activityId = "manual")
            1 -> session.copy(state = SessionState.SKIPPED)
            else -> session
        } })
        val reconciled = TrainingPlanEngine.reconcile(plan, today.plusDays(7), emptyList())
        assertEquals(plan.sessions.take(2), reconciled.sessions.take(2))
        val response = JSONObject().put("summary", "Keep the comfortable schedule.").put("sessions", JSONArray().also { array ->
            reconciled.sessions.filter { it.state == SessionState.PLANNED && it.date >= today.plusDays(7) }.forEach { session ->
                array.put(JSONObject().put("date", session.date.toString()).put("distance_km", session.distanceKm).put("kind", session.kind).put("note", session.note))
            }
        }).toString()
        val advised = TrainingPlanEngine.applyAdvice(reconciled, response, today.plusDays(7))
        assertEquals(plan.sessions.take(2), advised.sessions.take(2))
        assertEquals(rolling, advised.goal)
    }

}
