package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class CoachEngineTest {
    private val points = mutableListOf<RunPoint>()
    private var elapsed = 0L
    private var metres = 0.0
    private var segment = 0
    private fun session() = RunSession("coach-test", 1000, 1000 + elapsed, elapsed, points.toList())
    private fun advance(engine: CoachEngine, speed: Double, seconds: Int = 5, fresh: Boolean = true): CoachCue? {
        elapsed += seconds * 1000L; metres += speed * seconds
        points += RunPoint(0.0, metres / 111195.0802335329, 1000 + elapsed, 4f, segment = segment)
        return engine.update(session(), recording = true, gpsFresh = fresh)
    }
    private fun initial(config: CoachConfig): CoachEngine {
        points += RunPoint(0.0, 0.0, 1000, 4f)
        return CoachEngine(config, session())
    }

    @Test fun `off by default and invalid goals never coach`() {
        val engine = initial(CoachConfig())
        repeat(60) { assertNull(advance(engine, 10.0)) }
        assertFalse(CoachConfig(goal = CoachGoal.DISTANCE, distanceKm = Double.NaN).isValid())
        assertFalse(CoachConfig(goal = CoachGoal.DISTANCE_TIME, distanceKm = 5.0, targetMinutes = 1.0).isValid())
        assertTrue(CoachConfig(goal = CoachGoal.DISTANCE_TIME, distanceKm = 5.0, targetMinutes = 30.0).isValid())
        assertEquals(360.0, CoachConfig(goal = CoachGoal.DISTANCE_TIME).targetPaceSeconds!!, 0.001)
    }

    @Test fun `distance priority gives remaining kilometre and reaches goal once`() {
        val engine = initial(CoachConfig(enabled = true, goal = CoachGoal.DISTANCE, distanceKm = 2.0))
        val cues = (1..50).mapNotNull { advance(engine, 10.0) }
        assertEquals(1, cues.count { it == CoachCue.OneKilometreRemaining })
        assertEquals(1, cues.count { it is CoachCue.GoalReached })
        assertTrue(cues.none { it is CoachCue.Kilometre })
    }

    @Test fun `enable midway and process recovery never announce old milestones`() {
        val old = initial(CoachConfig())
        repeat(45) { advance(old, 10.0) }
        val engine = CoachEngine(CoachConfig(enabled = true, goal = CoachGoal.DISTANCE, distanceKm = 2.0), session())
        assertNull(engine.update(session(), true, true))
        repeat(10) { assertNull(advance(engine, 5.0)) }
    }

    @Test fun `pace must settle and is rate limited while repeated reminders remain possible`() {
        val engine = initial(CoachConfig(enabled = true, goal = CoachGoal.PACE, paceSecondsPerKm = 300, kilometreAnnouncements = false))
        val events = mutableListOf<Pair<Long, CoachCue>>()
        repeat(70) { advance(engine, 2.5)?.let { events += elapsed to it } }
        assertTrue(events.isNotEmpty())
        assertTrue(events.first().first >= 75_000)
        assertTrue(events.all { it.second == CoachCue.SlowerThanTarget })
        assertTrue(events.zipWithNext().all { (a, b) -> b.first - a.first >= 120_000 })
    }

    @Test fun `GPS loss and pause discard stale pace windows`() {
        val engine = initial(CoachConfig(enabled = true, goal = CoachGoal.PACE, paceSecondsPerKm = 300, kilometreAnnouncements = false))
        repeat(14) { advance(engine, 2.0) }
        repeat(20) { assertNull(advance(engine, 2.0, fresh = false)) }
        engine.pause(); segment++
        repeat(5) { assertNull(advance(engine, 2.0)) }
        val after = (1..12).mapNotNull { advance(engine, 2.0) }
        assertTrue(after.any { it == CoachCue.SlowerThanTarget })
    }

    @Test fun `goal time uses active duration and does not claim a late finish was in time`() {
        val engine = initial(CoachConfig(enabled = true, goal = CoachGoal.DISTANCE_TIME,
            distanceKm = 0.2, targetMinutes = 1.0, kilometreAnnouncements = false))
        val goal = (1..30).mapNotNull { advance(engine, 2.0) }.filterIsInstance<CoachCue.GoalReached>().single()
        assertEquals(false, goal.withinTime)
    }

    @Test fun `GPS gaps do not invent distance toward a goal`() {
        val engine = initial(CoachConfig(enabled = true, goal = CoachGoal.DISTANCE, distanceKm = 0.5))
        assertNull(advance(engine, 100.0, seconds = 20))
        repeat(10) { assertNull(advance(engine, 2.0)) }
    }

    @Test fun `turning off kilometre announcements keeps distance goals available`() {
        val engine = initial(CoachConfig(enabled = true, goal = CoachGoal.DISTANCE,
            distanceKm = 1.5, kilometreAnnouncements = false))
        val cues = (1..35).mapNotNull { advance(engine, 10.0) }
        assertTrue(cues.none { it is CoachCue.Kilometre })
        assertEquals(1, cues.filterIsInstance<CoachCue.GoalReached>().size)
    }
}
