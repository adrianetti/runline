package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class CoachStopsTest {
    private val points = mutableListOf<RunPoint>()
    private var time = 1_000L
    private var east = 0.0
    private fun session() = RunSession("coach-stops", 1_000, time, time - 1_000, points.toList())
    private fun fix() { points += RunPoint(0.0, east / 111195.0802335329, time, 4f) }
    private fun run(engine: CoachEngine, seconds: Int, speed: Double = 3.0, stopped: Long? = null): List<CoachCue> = (1..seconds).mapNotNull {
        time += 1_000; east += speed; fix(); engine.update(session(), true, true, stopped)
    }
    private fun stand(engine: CoachEngine, seconds: Int): List<CoachCue> {
        val since = time
        return (1..seconds).mapNotNull {
            time += 1_000
            if ((it % 10) == 0) { east += .5; fix() }
            engine.update(session(), true, true, since.takeIf { _ -> it >= 6 })
        }
    }
    private fun start(config: CoachConfig): CoachEngine { fix(); return CoachEngine(config, session()) }

    @Test fun `silent while stopped, situation ineligible, and no settling cue at a light`() {
        val engine = start(CoachConfig(enabled = true, kilometreAnnouncements = false))
        run(engine, 100)
        val during = stand(engine, 60)
        assertTrue(during.isEmpty())
        assertFalse(engine.situation.eligible)
        assertTrue(engine.situation.stopped)
        // Fresh movement first: nothing for the first 20 s after moving again.
        val after = run(engine, 15)
        assertTrue(after.none { it is CoachCue.Encouragement })
    }

    @Test fun `time goal counts moving time only`() {
        val engine = start(CoachConfig(enabled = true, goal = CoachGoal.TIME, targetMinutes = 3.0, kilometreAnnouncements = false, encouragement = false))
        run(engine, 120); stand(engine, 60)
        assertTrue(run(engine, 50).none { it is CoachCue.GoalReached })
        val reached = run(engine, 20).filterIsInstance<CoachCue.GoalReached>().single()
        assertEquals(180_000.0, reached.facts!!.movingMs.toDouble(), 5_000.0)
    }

    @Test fun `kilometre cue carries split and previous split in moving time`() {
        val engine = start(CoachConfig(enabled = true, encouragement = false))
        val first = run(engine, 340, 3.0).filterIsInstance<CoachCue.Kilometre>().single()
        assertEquals(1, first.completed)
        assertEquals(333.0, first.splitPaceSeconds!!, 3.0)
        assertNull(first.previousSplitPaceSeconds)
        stand(engine, 40)
        val second = run(engine, 270, 4.0).filterIsInstance<CoachCue.Kilometre>().single()
        // The standstill is excluded: 250 s for this kilometre, not ~290 s.
        assertEquals(250.0, second.splitPaceSeconds!!, 6.0)
        assertEquals(333.0, second.previousSplitPaceSeconds!!, 3.0)
        assertEquals(0, CoachNarration.splitChange(300.0, 303.0))
        assertEquals(-16, CoachNarration.splitChange(303.0, 319.0))
    }

    @Test fun `generic morale becomes a measured progress check`() {
        val engine = start(CoachConfig(enabled = true, kilometreAnnouncements = false))
        val cues = run(engine, 900, 2.8).filterIsInstance<CoachCue.Encouragement>()
        val progress = cues.filter { it.kind == EncouragementKind.PROGRESS }
        assertTrue(progress.isNotEmpty())
        assertTrue(progress.all { it.facts?.recentPaceSeconds != null && it.facts.averagePaceSeconds != null })
        assertTrue(cues.none { it.kind == EncouragementKind.STEADY })
        assertEquals(CoachNarration.Trend.FASTER, CoachNarration.trend(300.0, 320.0))
        assertEquals(CoachNarration.Trend.SLOWER, CoachNarration.trend(340.0, 320.0))
        assertEquals(CoachNarration.Trend.STEADY, CoachNarration.trend(325.0, 320.0))
    }

    @Test fun `auto pause off keeps active time`() {
        fix()
        val engine = CoachEngine(CoachConfig(enabled = true, goal = CoachGoal.TIME, targetMinutes = 3.0, kilometreAnnouncements = false,
            encouragement = false), session(), autoPause = false)
        run(engine, 120); stand(engine, 50)
        val reached = run(engine, 20).filterIsInstance<CoachCue.GoalReached>().single()
        assertEquals(180_000.0, reached.facts!!.movingMs.toDouble(), 2_000.0)
    }
}
