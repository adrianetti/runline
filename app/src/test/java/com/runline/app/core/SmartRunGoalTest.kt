package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class SmartRunGoalTest {
    private fun distance(target: Double = 5.0) = SmartRunSuggestion(SmartRunGoalType.DISTANCE,
        targetDistanceKm = target, minDistanceKm = target, maxDistanceKm = target,
        rationale = "Choose a useful progression using recent training and effort.")

    @Test fun validGoalsAreNotClampedToAPreviousDistanceOrPercentage() {
        for (km in listOf(3.0, 5.0, 10.0, 21.1, 50.0)) {
            val suggestion = distance(km)
            assertTrue(SmartRunGoal.validate(suggestion))
            assertEquals(km, SmartRunGoal.toCoachConfig(suggestion, CoachConfig()).distanceKm, 0.0)
        }
    }
    @Test fun onlyExistingManualInputSupportLimitsAreEnforced() {
        for (km in listOf(CoachConfig.DISTANCE_KM_RANGE.start, CoachConfig.DISTANCE_KM_RANGE.endInclusive)) {
            assertTrue(SmartRunGoal.validate(distance(km)))
        }
        for (km in listOf(0.0, -1.0, 0.01, 500.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFalse(SmartRunGoal.validate(distance(km)))
        }
    }
    @Test fun distanceRangeMustBeCompleteFiniteOrderedAndContainItsTarget() {
        val valid = distance().copy(minDistanceKm = 4.0, maxDistanceKm = 6.0)
        assertTrue(SmartRunGoal.validate(valid))
        for (invalid in listOf(valid.copy(minDistanceKm = null), valid.copy(maxDistanceKm = Double.NaN),
            valid.copy(minDistanceKm = 6.0), valid.copy(maxDistanceKm = 4.0), valid.copy(targetDistanceKm = 7.0))) {
            assertFalse(SmartRunGoal.validate(invalid))
        }
    }
    @Test fun timeGoalsHaveNoDistanceAndUseTheManualDurationRange() {
        val time = SmartRunSuggestion(SmartRunGoalType.TIME, durationMinutes = 45.0, rationale = "Build endurance at a comfortable effort.")
        assertTrue(SmartRunGoal.validate(time))
        assertFalse(SmartRunGoal.validate(time.copy(targetDistanceKm = 5.0)))
        for (minutes in listOf(0.0, -5.0, 1441.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFalse(SmartRunGoal.validate(time.copy(durationMinutes = minutes)))
        }
        assertFalse(SmartRunGoal.validate(distance().copy(durationMinutes = 30.0)))
    }
    @Test fun paceBandsAreOptionalPairedOrderedAndRepresentable() {
        val valid = distance().copy(paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 390)
        assertTrue(SmartRunGoal.validate(valid))
        for (invalid in listOf(valid.copy(paceMinSecondsPerKm = null), valid.copy(paceMaxSecondsPerKm = null),
            valid.copy(paceMinSecondsPerKm = 59), valid.copy(paceMaxSecondsPerKm = 3601), valid.copy(paceMaxSecondsPerKm = 300))) {
            assertFalse(SmartRunGoal.validate(invalid))
        }
    }
    @Test fun distanceWithinTimeMustHaveOneCoherentRepresentablePace() {
        val mixed = distance().copy(goalType = SmartRunGoalType.DISTANCE_TIME, durationMinutes = 30.0)
        assertTrue(SmartRunGoal.validate(mixed))
        assertFalse(SmartRunGoal.validate(mixed.copy(durationMinutes = null)))
        assertFalse(SmartRunGoal.validate(mixed.copy(durationMinutes = 1.0)))
        assertFalse(SmartRunGoal.validate(mixed.copy(durationMinutes = 1440.0)))
        assertFalse(SmartRunGoal.validate(mixed.copy(paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 390)))
    }
    @Test fun restCannotSmuggleExecutableTargetsOrSteadyEffort() {
        val rest = SmartRunSuggestion(SmartRunGoalType.REST, rationale = "Recent reported effort favours rest.")
        assertTrue(SmartRunGoal.validate(rest))
        assertFalse(SmartRunGoal.validate(rest.copy(targetDistanceKm = 5.0)))
        assertFalse(SmartRunGoal.validate(rest.copy(durationMinutes = 30.0)))
        assertFalse(SmartRunGoal.validate(rest.copy(paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 390)))
        assertFalse(SmartRunGoal.validate(rest.copy(effort = SmartRunEffort.STEADY)))
        assertFalse(SmartRunGoal.validate(rest.copy(rationale = "")))
        assertFalse(SmartRunGoal.validate(rest.copy(rationale = "x".repeat(241))))
    }
    @Test fun mappingPreservesChosenGoalAndTogglesWithoutEnablingItImplicitly() {
        val base = CoachConfig(enabled = false, kilometreAnnouncements = false, encouragement = false)
        val config = SmartRunGoal.toCoachConfig(distance().copy(effort = SmartRunEffort.STEADY,
            paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 390), base)
        assertEquals(CoachGoal.DISTANCE, config.goal); assertEquals(5.0, config.distanceKm, 0.0)
        assertEquals(SmartRunEffort.STEADY, config.effortIntent)
        assertFalse(config.enabled); assertFalse(config.kilometreAnnouncements); assertFalse(config.encouragement)
        assertEquals(330, config.paceMinSecondsPerKm)
        val rest = SmartRunGoal.toCoachConfig(SmartRunSuggestion(SmartRunGoalType.REST, rationale = "Rest today."), config)
        assertEquals(CoachGoal.NONE, rest.goal); assertNull(rest.paceMinSecondsPerKm); assertNull(rest.effortIntent)
    }
    @Test fun everyAcceptedGoalCanBeAppliedWithoutClampingItsNumbers() {
        val examples = listOf(distance(5.0), distance(21.1).copy(effort = SmartRunEffort.STEADY),
            SmartRunSuggestion(SmartRunGoalType.TIME, durationMinutes = 60.0, rationale = "Comfortable endurance."),
            distance().copy(goalType = SmartRunGoalType.DISTANCE_TIME, durationMinutes = 30.0),
            SmartRunSuggestion(SmartRunGoalType.REST, rationale = "Recovery day."))
        for (example in examples) {
            assertTrue(SmartRunGoal.validate(example))
            assertTrue(SmartRunGoal.toCoachConfig(example, CoachConfig()).isValid())
        }
    }
}
