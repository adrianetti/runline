package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class CoachTimeAndBandTest {
    private fun run(active: Long = 0) = RunSession("time-goal", 1000, 1000 + active, active, emptyList())
    @Test fun timeUsesActiveClockAndCompletesOnceWithoutGps() {
        val engine = CoachEngine(CoachConfig(enabled = true, goal = CoachGoal.TIME, targetMinutes = 1.0), run())
        assertNull(engine.update(run(30_000), true, false))
        val cue = engine.update(run(60_000), true, false)
        assertEquals(CoachCue.GoalReached(null), cue)
        assertTrue(engine.situation.allows(CoachCue.GoalReached(null)))
        assertFalse(engine.situation.allows(CoachCue.Kilometre(1, 360.0)))
        assertFalse(engine.situation.allows(CoachCue.SlowerThanTarget))
        assertFalse(engine.situation.allows(CoachCue.Encouragement(EncouragementKind.STEADY)))
        assertNull(engine.update(run(90_000), true, false))
    }
    @Test fun pausedClockAndRecoveryNeverProduceOldTimeCompletion() {
        val config = CoachConfig(enabled = true, goal = CoachGoal.TIME, targetMinutes = 1.0)
        val engine = CoachEngine(config, run())
        assertNull(engine.update(run(45_000), true, false))
        engine.pause()
        assertNull(engine.update(run(45_000), false, false))
        assertNull(engine.update(run(55_000), true, false))
        assertEquals(CoachCue.GoalReached(null), engine.update(run(60_000), true, false))
        val restored = CoachEngine(config, run(120_000))
        assertNull(restored.update(run(125_000), true, false))
    }
    @Test fun distanceDeadlineStillNeedsDistanceRatherThanOnlyElapsedTime() {
        val config = CoachConfig(enabled = true, goal = CoachGoal.DISTANCE_TIME, distanceKm = 1.0, targetMinutes = 6.0)
        assertNull(config.durationGoalMs)
        assertEquals(360.0, config.targetPaceSeconds!!, .00001)
        val engine = CoachEngine(config, run())
        assertNull(engine.update(run(400_000), true, false))
        assertFalse(engine.situation.eligible)
    }
    @Test fun combinedDistanceOrTimeGoalsValidateWholePaceBand() {
        for (goal in listOf(CoachGoal.DISTANCE, CoachGoal.TIME, CoachGoal.PACE)) {
            val config = CoachConfig(goal = goal, paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 420)
            assertTrue(config.isValid())
            assertEquals(375.0, config.targetPaceSeconds!!, .000001)
            assertFalse(config.copy(paceMaxSecondsPerKm = null).isValid())
            assertFalse(config.copy(paceMaxSecondsPerKm = 329).isValid())
            assertFalse(config.copy(paceMinSecondsPerKm = 0).isValid())
        }
        assertFalse(CoachConfig(goal = CoachGoal.TIME, targetMinutes = Double.NaN).isValid())
        assertFalse(CoachConfig(goal = CoachGoal.DISTANCE_TIME, paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 420).isValid())
    }
    @Test fun rangeHysteresisDoesNotTreatInsideBandAsOutsideMidpointTarget() {
        assertEquals(0, CoachPaceBand.classify(400.0, 330.0..420.0))
        assertNull(CoachPaceBand.classify(425.0, 330.0..420.0))
        assertEquals(1, CoachPaceBand.classify(450.0, 330.0..420.0))
        assertNull(CoachPaceBand.classify(325.0, 330.0..420.0))
        assertEquals(-1, CoachPaceBand.classify(300.0, 330.0..420.0))
        val situation = CoachSituation(eligible = true, paceSeconds = 400.0, targetPaceSeconds = 375.0,
            paceMinSeconds = 330.0, paceMaxSeconds = 420.0)
        assertFalse(situation.allows(CoachCue.SlowerThanTarget))
        assertTrue(situation.allows(CoachCue.BackOnPace))
    }
    @Test fun sustainedPaceInsideWideBandDoesNotNagAndRecoveryNeverAsksToAccelerate() {
        fun cues(recovery: Boolean, speed: Double): List<CoachCue> {
            val points = mutableListOf(RunPoint(0.0, 0.0, 1000, 4f))
            val config = CoachConfig(enabled = true, goal = CoachGoal.DISTANCE, distanceKm = 100.0,
                paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 420, kilometreAnnouncements = false,
                encouragement = false, terrainFeedback = false, lapFeedback = false,
                effortIntent = if (recovery) SmartRunEffort.RECOVERY else SmartRunEffort.EASY)
            val initial = RunSession("range", 1000, 1000, 0, points.toList())
            val engine = CoachEngine(config, initial)
            return (1..100).mapNotNull { step ->
                val active = step * 5_000L
                points += RunPoint(0.0, speed * step * 5 / 111195.0802335329, 1000 + active, 4f)
                engine.update(initial.copy(endedAtMs = 1000 + active, activeDurationMs = active, points = points.toList()), true, true)
            }
        }
        assertTrue(cues(false, 2.5).isEmpty())
        assertTrue(cues(false, 2.0).contains(CoachCue.SlowerThanTarget))
        assertFalse(cues(true, 2.0).contains(CoachCue.SlowerThanTarget))
    }
}
