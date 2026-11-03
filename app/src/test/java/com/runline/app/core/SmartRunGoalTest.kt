package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SmartRunGoalTest {
    private val today = LocalDate.of(2026, 11, 3)
    private fun history(effort: Int? = 3) = listOf(1L to 6.0, 8L to 8.0, 10L to 10.0, 15L to 8.0,
        17L to 10.0, 22L to 8.0, 24L to 10.0).map { (age, km) ->
        SmartRunHistoryRun(today.minusDays(age), km, "run-$age", effort, (km * 360_000).toLong())
    }
    private fun distance(target: Double = 6.0) = SmartRunSuggestion(SmartRunGoalType.DISTANCE,
        targetDistanceKm = target, minDistanceKm = target, maxDistanceKm = target, rationale = "Use recent recorded distances.")

    @Test fun noHistoryUsesBriefTimeWithoutInventingPaceOrFitness() {
        val suggestion = SmartRunGoal.suggest(emptyList(), today)
        assertEquals(SmartRunGoalType.TIME, suggestion.goalType)
        assertNull(suggestion.paceMinSecondsPerKm)
        assertNull(suggestion.targetDistanceKm)
        assertTrue(SmartRunGoal.validate(suggestion, emptyList(), today))
        assertFalse(SmartRunGoal.validate(distance(10.0), emptyList(), today))
    }
    @Test fun sparseEightKmRunSupportsUsefulNextSessionWithoutFiftyKmWeeklyAssumption() {
        val history = listOf(SmartRunHistoryRun(today.minusDays(1), 8.4, "one"))
        val limits = SmartRunGoal.limits(history, today)
        val suggestion = SmartRunGoal.suggest(history, today)
        assertTrue(suggestion.targetDistanceKm!! in 4.0..8.4)
        assertTrue(limits.availableWeeklyKm < 10.0)
        assertNull(suggestion.paceMinSecondsPerKm)
        assertTrue(SmartRunGoal.validate(suggestion, history, today))
    }
    @Test fun twoRunsConsumeHeadroomAndRepeatedSparseRunsCannotGrowTheirOwnUnlimitedBudget() {
        val two = listOf(1L, 2L).map { SmartRunHistoryRun(today.minusDays(it), 8.4, "$it") }
        val limit = SmartRunGoal.limits(two, today)
        assertEquals(16.8, limit.observedWeeklyKm, .001)
        assertTrue(limit.maxDistanceKm in 3.0..6.0)
        val seven = (0L..6L).map { SmartRunHistoryRun(today.minusDays(it), 8.4, "$it") }
        val exhausted = SmartRunGoal.limits(seven, today)
        assertEquals(0.0, exhausted.maxDistanceKm, .001)
        assertEquals(SmartRunGoalType.REST, SmartRunGoal.suggest(seven, today).goalType)
        assertFalse(SmartRunGoal.validate(distance(1.0), seven, today))
    }
    @Test fun hardRecentEffortRecommendsRestAndOnlyAllowsRecoveryAlternatives() {
        val history = listOf(SmartRunHistoryRun(today.minusDays(1), 8.4, "hard", 8))
        assertEquals(SmartRunGoalType.REST, SmartRunGoal.suggest(history, today).goalType)
        val limits = SmartRunGoal.limits(history, today)
        val short = distance(limits.maxDistanceKm)
        assertFalse(SmartRunGoal.validate(short, history, today))
        assertTrue(SmartRunGoal.validate(short.copy(effort = SmartRunEffort.RECOVERY), history, today))
        assertTrue(limits.maxDistanceKm <= 8.4 * .65)
    }
    @Test fun sameDayOrderUsesStartTimeAndTodayDoesNotTriggerAnotherAutomaticRun() {
        val runs = listOf(SmartRunHistoryRun(today, 4.0, "z", 3, startedAtMs = 100),
            SmartRunHistoryRun(today, 4.0, "a", 9, startedAtMs = 200))
        assertEquals(SmartRunGoal.limits(runs, today), SmartRunGoal.limits(runs.reversed(), today))
        assertEquals(SmartRunGoalType.REST, SmartRunGoal.suggest(runs, today).goalType)
    }
    @Test fun missingEffortDoesNotInventComfortablePaceFromGpsSpeed() {
        val runs = history(effort = null)
        val limit = SmartRunGoal.limits(runs, today)
        assertNull(limit.fastestPaceSecondsPerKm)
        assertNull(limit.slowestPaceSecondsPerKm)
        assertFalse(limit.allowSteady)
        assertFalse(SmartRunGoal.validate(distance().copy(paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 390), runs, today))
    }
    @Test fun establishedComfortableEvidencePermitsOnlyObservedPaceRange() {
        val runs = history()
        val limits = SmartRunGoal.limits(runs, today)
        assertTrue(limits.allowSteady)
        assertEquals(360, limits.fastestPaceSecondsPerKm)
        val valid = distance().copy(paceMinSecondsPerKm = 365, paceMaxSecondsPerKm = 420)
        assertTrue(SmartRunGoal.validate(valid, runs, today))
        assertFalse(SmartRunGoal.validate(valid.copy(paceMinSecondsPerKm = 240), runs, today))
        assertFalse(SmartRunGoal.validate(valid.copy(paceMaxSecondsPerKm = 350), runs, today))
        assertFalse(SmartRunGoal.validate(valid.copy(paceMaxSecondsPerKm = null), runs, today))
    }
    @Test fun gapReducesNextDistanceWithoutCatchupDebt() {
        val runs = listOf(SmartRunHistoryRun(today.minusDays(14), 8.4, "older", 3))
        val suggestion = SmartRunGoal.suggest(runs, today)
        assertEquals(SmartRunEffort.RECOVERY, suggestion.effort)
        assertTrue(suggestion.maxDistanceKm!! <= 8.4 * .6)
        assertTrue(SmartRunGoal.validate(suggestion, runs, today))
    }
    @Test fun timeOnlyCannotBypassDistanceHeadroomWithASlowAssumedPace() {
        val runs = history() + SmartRunHistoryRun(today.minusDays(11), 2.0, "fast", 8, 6 * 60_000)
        val limits = SmartRunGoal.limits(runs, today)
        assertTrue(limits.maxTimeOnlyDurationMinutes < limits.maxDurationMinutes)
        val time = SmartRunSuggestion(SmartRunGoalType.TIME, durationMinutes = limits.maxTimeOnlyDurationMinutes + 1,
            rationale = "Use a bounded duration.")
        assertFalse(SmartRunGoal.validate(time, runs, today))
        val mixed = distance().copy(goalType = SmartRunGoalType.DISTANCE_TIME, durationMinutes = 36.0)
        assertTrue(SmartRunGoal.validate(mixed, runs, today))
        assertFalse(SmartRunGoal.validate(mixed.copy(durationMinutes = 15.0), runs, today))
        assertFalse(SmartRunGoal.validate(mixed.copy(paceMinSecondsPerKm = 360, paceMaxSecondsPerKm = 420), runs, today))
    }
    @Test fun nonfiniteUnorderedAndIrrelevantTargetsAreRejected() {
        val runs = history()
        assertFalse(SmartRunGoal.validate(distance().copy(targetDistanceKm = Double.NaN), runs, today))
        assertFalse(SmartRunGoal.validate(distance().copy(maxDistanceKm = Double.POSITIVE_INFINITY), runs, today))
        assertFalse(SmartRunGoal.validate(distance().copy(minDistanceKm = 7.0), runs, today))
        assertFalse(SmartRunGoal.validate(distance().copy(durationMinutes = 30.0), runs, today))
        assertFalse(SmartRunGoal.validate(distance().copy(goalType = SmartRunGoalType.REST), runs, today))
        assertFalse(SmartRunGoal.validate(distance().copy(rationale = ""), runs, today))
    }
    @Test fun duplicatesFutureRunsAndInvalidRecordsDoNotInflateEvidence() {
        val run = SmartRunHistoryRun(today.minusDays(1), 8.4, "one")
        val clean = SmartRunGoal.limits(listOf(run), today)
        val noisy = listOf(run, run, run.copy(id = "future", date = today.plusDays(1)), run.copy(id = "bad", distanceKm = Double.NaN))
        assertEquals(clean, SmartRunGoal.limits(noisy, today))
    }
    @Test fun mappingIsConcretePreservesTogglesAndDoesNotEnableBeforeUse() {
        val base = CoachConfig(enabled = false, kilometreAnnouncements = false, encouragement = false)
        val config = SmartRunGoal.toCoachConfig(distance().copy(effort = SmartRunEffort.RECOVERY,
            paceMinSecondsPerKm = 365, paceMaxSecondsPerKm = 420), base)
        assertEquals(CoachGoal.DISTANCE, config.goal)
        assertEquals(SmartRunEffort.RECOVERY, config.effortIntent)
        assertFalse(config.enabled); assertFalse(config.kilometreAnnouncements); assertFalse(config.encouragement)
        assertEquals(365, config.paceMinSecondsPerKm)
        val rest = SmartRunGoal.toCoachConfig(SmartRunSuggestion(SmartRunGoalType.REST, rationale = "Rest today."), config)
        assertEquals(CoachGoal.NONE, rest.goal); assertNull(rest.paceMinSecondsPerKm); assertNull(rest.effortIntent)
    }
    @Test fun localSpanishFallbackAndEveryLocalBranchPassTheSameValidator() {
        val cases = listOf(emptyList(), history(), history(null), listOf(SmartRunHistoryRun(today, 8.4, "today", 8)),
            listOf(SmartRunHistoryRun(today.minusDays(14), 8.4, "returning")))
        for (runs in cases) {
            val suggestion = SmartRunGoal.suggest(runs, today, spanish = true)
            assertTrue(suggestion.rationale.isNotBlank())
            assertTrue(SmartRunGoal.validate(suggestion, runs, today))
        }
        assertTrue(SmartRunGoal.suggest(emptyList(), today, spanish = true).rationale.contains("carreras"))
    }
}
