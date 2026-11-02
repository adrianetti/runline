package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class CoachScenariosTest {
    private class Track(config: CoachConfig) {
        val points = mutableListOf(RunPoint(0.0, 0.0, 1000, 4f, 700.0))
        var active = 0L; var metres = 0.0
        val engine = CoachEngine(config, session())
        fun session() = RunSession("scenario", 1000, 1000 + active, active, points.toList())
        fun step(speed: Double, climb: Double = 0.0, fresh: Boolean = true): CoachCue? {
            active += 5000; metres += speed * 5
            points += RunPoint(0.0, metres / 111195.0802335329, 1000 + active, 4f, 700 + active / 1000.0 * climb)
            return engine.update(session(), true, fresh)
        }
    }
    @Test fun `encouragement is on within opt in and can be independently disabled`() {
        val a = Track(CoachConfig(enabled = true, kilometreAnnouncements = false))
        val cues = (1..90).mapNotNull { a.step(2.0) }
        assertTrue(cues.any { it is CoachCue.Encouragement })
        val b = Track(CoachConfig(enabled = true, kilometreAnnouncements = false, encouragement = false))
        assertTrue((1..90).mapNotNull { b.step(2.0) }.isEmpty())
    }
    @Test fun `sustained climb replaces speed up prompts and respects terrain switch`() {
        val config = CoachConfig(enabled = true, goal = CoachGoal.PACE, paceSecondsPerKm = 300,
            kilometreAnnouncements = false, encouragement = false)
        val a = Track(config)
        val cues = (1..80).mapNotNull { a.step(2.0, climb = .3) }
        assertTrue(cues.contains(CoachCue.Terrain(CoachTerrain.UPHILL)))
        assertFalse(cues.contains(CoachCue.SlowerThanTarget))
        val b = Track(config.copy(terrainFeedback = false))
        assertTrue((1..80).mapNotNull { b.step(2.0, climb = .3) }.isEmpty())
    }
    @Test fun `sustained descent never asks to accelerate to target`() {
        val t = Track(CoachConfig(enabled = true, goal = CoachGoal.PACE, paceSecondsPerKm = 300,
            kilometreAnnouncements = false, encouragement = false))
        val cues = (1..80).mapNotNull { t.step(2.0, climb = -.3) }
        assertTrue(cues.contains(CoachCue.Terrain(CoachTerrain.DOWNHILL)))
        assertFalse(cues.contains(CoachCue.SlowerThanTarget))
    }
    @Test fun `short pace oscillations do not alternate coaching bands`() {
        val t = Track(CoachConfig(enabled = true, goal = CoachGoal.PACE, paceSecondsPerKm = 330,
            kilometreAnnouncements = false, encouragement = false, terrainFeedback = false))
        val cues = (1..120).mapNotNull { t.step(if ((it / 3) % 2 == 0) 1000.0 / 300 else 1000.0 / 360) }
        assertTrue(cues.isEmpty())
    }
    @Test fun `weak GPS produces neither morale nor terrain feedback`() {
        val t = Track(CoachConfig(enabled = true, kilometreAnnouncements = false))
        assertTrue((1..100).mapNotNull { t.step(2.0, climb = .3, fresh = false) }.isEmpty())
    }
    @Test fun `pace guidance can be disabled without disabling the distance goal`() {
        val t = Track(CoachConfig(enabled = true, goal = CoachGoal.DISTANCE_TIME, distanceKm = .3,
            targetMinutes = 2.0, kilometreAnnouncements = false, paceFeedback = false, encouragement = false))
        val cues = (1..45).mapNotNull { t.step(2.0) }
        assertEquals(1, cues.filterIsInstance<CoachCue.GoalReached>().size)
        assertTrue(cues.all { it is CoachCue.GoalReached })
    }
}
