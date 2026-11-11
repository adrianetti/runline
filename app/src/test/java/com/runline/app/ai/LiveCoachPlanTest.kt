package com.runline.app.ai

import org.junit.Assert.*
import org.junit.Test

class LiveCoachPlanTest {
    private fun tick(plan: LiveCoachPlan, fromMs: Long, toMs: Long, interval: Int = 2): List<Pair<Long, LiveCall>> =
        (fromMs..toMs step 1_000).mapNotNull { at -> plan.next(at, interval)?.let { at to it } }

    @Test fun `first line comes within the first minute, not after a full interval`() {
        val plan = LiveCoachPlan()
        assertNull(plan.next(10_000, 5))
        val calls = tick(plan, 11_000, 60_000, 5)
        assertEquals(1, calls.size); assertEquals(LiveTrigger.OPENING, calls[0].second.trigger); assertEquals(40_000, calls[0].first)
    }

    @Test fun `a recovered recording skips the welcome`() {
        val plan = LiveCoachPlan()
        val calls = tick(plan, 600_000, 700_000)
        assertEquals(LiveTrigger.CADENCE, calls.first().second.trigger)
        assertTrue(calls.none { it.second.trigger == LiveTrigger.OPENING })
    }

    @Test fun `the live coach takes over a milestone at once, even right after a check-in`() {
        val plan = LiveCoachPlan()
        assertNotNull(plan.next(40_000, 2))
        val call = plan.milestone(45_000, LiveMoment.KILOMETRE)!!
        assertEquals(LiveTrigger.MOMENT, call.trigger); assertEquals(LiveMoment.KILOMETRE, call.moment); assertEquals(2, plan.attempts)
        assertEquals(LiveMoment.GOAL_REACHED, plan.milestone(46_000, LiveMoment.GOAL_REACHED)?.moment)
        // The next check-in keeps its distance from the milestone.
        assertNull(plan.next(60_000, 2))
    }

    @Test fun `while the provider is failing or the ceiling is reached, milestones stay with the built-in coach`() {
        val failing = LiveCoachPlan()
        failing.next(40_000, 2); failing.failed(41_000); failing.failed(42_000)
        assertFalse(failing.healthy); assertNull(failing.milestone(100_000, LiveMoment.KILOMETRE))
        failing.next(200_000, 2); failing.answered(201_000, true)
        assertNotNull(failing.milestone(210_000, LiveMoment.LAP))
        val spent = LiveCoachPlan(LiveCoachPlan.MAX_ATTEMPTS)
        assertNull(spent.milestone(1_000, LiveMoment.KILOMETRE)); assertEquals(LiveCoachPlan.MAX_ATTEMPTS, spent.attempts)
    }

    @Test fun `check-ins follow the chosen quiet time after the coach last spoke and rotate their angle`() {
        val plan = LiveCoachPlan()
        var at = 40_000L
        val focuses = mutableListOf<LiveFocus>()
        plan.next(at, 2); plan.answered(at + 1_000, true); plan.spoken(at + 9_000, ai = true)
        repeat(8) {
            val spokeAt = at + 9_000
            assertNull(plan.next(spokeAt + 119_000, 2))
            at = spokeAt + 120_000
            val call = plan.next(at, 2)!!
            assertEquals(LiveTrigger.CADENCE, call.trigger); focuses += call.focus
            plan.answered(at + 1_000, true); plan.spoken(at + 9_000, ai = true)
        }
        assertEquals(LiveFocus.entries.toList(), focuses)
    }

    @Test fun `a check-in does not start while something was just said`() {
        val plan = LiveCoachPlan()
        plan.next(40_000, 1); plan.answered(41_000, true); plan.spoken(48_000, ai = true)
        plan.spoken(100_000, ai = false)
        assertNull(plan.next(110_000, 1))
        assertNotNull(plan.next(120_000, 1))
    }

    @Test fun `silence is retried soon, failures back off, and the built-in coach takes over`() {
        val plan = LiveCoachPlan()
        plan.next(40_000, 5); plan.answered(41_000, spoke = false)
        assertNull(plan.next(80_000, 5)); assertNotNull(plan.next(86_000, 5))
        assertTrue(plan.healthy)
        plan.failed(87_000); assertTrue(plan.healthy)
        assertNull(plan.next(110_000, 5)); assertNotNull(plan.next(117_000, 5))
        plan.failed(118_000); assertFalse(plan.healthy)
        assertNull(plan.next(170_000, 5)); assertNotNull(plan.next(178_000, 5))
        plan.answered(179_000, true); assertTrue(plan.healthy)
    }

    @Test fun `requests stop at the per-activity ceiling`() {
        val plan = LiveCoachPlan(LiveCoachPlan.MAX_ATTEMPTS - 1)
        assertEquals(1, tick(plan, 0, 3_600_000, 1).size)
        assertFalse(plan.healthy)
        assertEquals(LiveCoachPlan.MAX_ATTEMPTS, LiveCoachPlan(10_000).attempts)
    }

    @Test fun `a 23 minute run with a kilometre every seven and a half minutes hears about a dozen live lines`() {
        val plan = LiveCoachPlan(); var lines = 0
        (0..1_397_000L step 1_000).forEach { at ->
            val call = if (at > 0 && at % 450_000 == 0L) plan.milestone(at, LiveMoment.KILOMETRE) else plan.next(at, 2)
            if (call != null) { plan.answered(at + 1_000, true); plan.spoken(at + 3_000, ai = true); lines++ }
        }
        assertTrue("$lines", lines in 11..14)
    }

    @Test fun `milestones are recognised from the cue`() {
        assertEquals(LiveMoment.KILOMETRE, LiveMoment.of(com.runline.app.core.CoachCue.Kilometre(2, 460.0)))
        assertEquals(LiveMoment.LAP, LiveMoment.of("LapCompleted(number=2, comparison=null)"))
        assertEquals(LiveMoment.HALFWAY, LiveMoment.of("Encouragement(kind=HALFWAY, facts=CoachFacts(distanceMeters=2205.6))"))
        assertEquals(LiveMoment.GOAL_REACHED, LiveMoment.of("GoalReached(withinTime=null, facts=null)"))
        assertEquals(LiveMoment.ONE_KM_LEFT, LiveMoment.of(com.runline.app.core.CoachCue.OneKilometreRemaining))
        assertNull(LiveMoment.of("Encouragement(kind=PROGRESS, facts=null)")); assertNull(LiveMoment.of(com.runline.app.core.CoachCue.SlowerThanTarget))
    }
}
