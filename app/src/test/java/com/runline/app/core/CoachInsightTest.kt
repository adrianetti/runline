package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class CoachInsightTest {
    @Test fun encouragementRemainsTrueAtDeliveryInsteadOfBecomingStalePraise() {
        val situation = CoachSituation(eligible = true, paceSeconds = 330.0, targetPaceSeconds = 330.0,
            terrain = CoachTerrain.LEVEL, goalProgress = .55, steadyPace = true, activeMs = 180_000)
        assertTrue(situation.allows(CoachCue.Encouragement(EncouragementKind.HALFWAY)))
        assertFalse(situation.copy(goalProgress = .9).allows(CoachCue.Encouragement(EncouragementKind.HALFWAY)))
        assertTrue(situation.allows(CoachCue.Encouragement(EncouragementKind.CONSISTENT)))
        assertFalse(situation.copy(steadyPace = false).allows(CoachCue.Encouragement(EncouragementKind.CONSISTENT)))
        assertTrue(situation.allows(CoachCue.Encouragement(EncouragementKind.ON_TARGET)))
        assertFalse(situation.copy(paceSeconds = 400.0).allows(CoachCue.Encouragement(EncouragementKind.ON_TARGET)))
        assertFalse(situation.copy(terrain = CoachTerrain.UNKNOWN).allows(CoachCue.Encouragement(EncouragementKind.UPHILL)))
        assertFalse(situation.copy(goalReached = true).allows(CoachCue.Encouragement(EncouragementKind.FINAL_STRETCH)))
    }
    @Test fun timeGoalsGetProportionalEncouragementWithoutInventingDistance() {
        val config = CoachConfig(enabled = true, goal = CoachGoal.TIME, targetMinutes = 12.0,
            kilometreAnnouncements = false, paceFeedback = false, terrainFeedback = false)
        val points = mutableListOf<RunPoint>()
        fun session(i: Int) = RunSession("time", 1000, 1000 + i * 5000L, i * 5000L, points.toList())
        val engine = CoachEngine(config, session(0)); val cues = mutableListOf<CoachCue>()
        for (i in 0..140) {
            points += RunPoint(i * 10.0 / 111195.0, 0.0, 1000 + i * 5000L, 3f, 100.0)
            engine.update(session(i), true, true)?.let { cues += it }
        }
        val halfway = cues.filterIsInstance<CoachCue.Encouragement>().single { it.kind == EncouragementKind.HALFWAY }
        assertTrue(cues.any { it is CoachCue.Encouragement && it.kind == EncouragementKind.FINAL_STRETCH })
        // Measured facts: time remains, a projection from the real average, no invented distance goal.
        val facts = halfway.facts!!
        assertNull(facts.remainingMeters)
        assertTrue(facts.remainingMs!! in 0..6 * 60_000L)
        assertEquals(1440.0, facts.projectedMeters!!, 60.0)
    }
    @Test fun scheduleReportsContextExpiryAndReplacementWithoutSpeakingThem() {
        val dropped = mutableListOf<String>()
        val schedule = CoachSpeechSchedule { request, reason -> dropped += request.key + ":" + reason }
        schedule.finished(0)
        schedule.offer(CoachSpeechRequest("older", CoachSpeechPriority.MORALE), 1000)
        schedule.offer(CoachSpeechRequest("newer", CoachSpeechPriority.AI), 2000)
        assertEquals(listOf("older:superseded"), dropped)
        assertNull(schedule.takeReady(80_000, true))
        assertTrue(dropped.contains("newer:expired"))
        var valid = true
        schedule.offer(CoachSpeechRequest("hill", CoachSpeechPriority.TERRAIN, isValid = { valid }), 81_000)
        valid = false
        assertNull(schedule.takeReady(90_000, true))
        assertTrue(dropped.contains("hill:context_changed"))
    }
}
