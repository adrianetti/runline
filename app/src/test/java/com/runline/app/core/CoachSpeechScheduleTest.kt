package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class CoachSpeechScheduleTest {
    private fun ai(text: String = "Keep this easy", valid: () -> Boolean = { true }) =
        CoachSpeechRequest("ai", CoachSpeechPriority.AI, advice = text, isValid = valid)
    private fun km(number: Int) = CoachSpeechRequest("km:$number", CoachSpeechPriority.MILESTONE,
        cue = CoachCue.Kilometre(number, 330.0))
    private fun goal() = CoachSpeechRequest("goal", CoachSpeechPriority.GOAL, cue = CoachCue.GoalReached(null))

    @Test fun `local and AI share silence measured from end of speech`() {
        val schedule = CoachSpeechSchedule()
        val local = CoachSpeechRequest("pace", CoachSpeechPriority.PACE, cue = CoachCue.SlowerThanTarget)
        schedule.offer(local, 0)
        assertEquals(local, schedule.takeReady(0, true))
        schedule.finished(8_000)
        schedule.offer(ai(), 25_000)
        assertNull(schedule.takeReady(85_000, true))
        assertNotNull(schedule.takeReady(93_000, true))
    }

    @Test fun `new AI replaces old advice without a FIFO backlog`() {
        val schedule = CoachSpeechSchedule()
        schedule.finished(0)
        repeat(50) { schedule.offer(ai("Advice $it"), 20_000 + it * 100L) }
        assertEquals(1, schedule.pendingCount)
        assertEquals("Advice 49", schedule.takeReady(85_000, true)?.advice)
        assertNull(schedule.takeReady(90_000, true))
    }

    @Test fun `expired AI never holds a slot or speaks late`() {
        val schedule = CoachSpeechSchedule()
        schedule.finished(10_000)
        schedule.offer(ai(), 20_000)
        assertNull(schedule.takeReady(95_000, true))
        assertEquals(0, schedule.pendingCount)
    }

    @Test fun `settings generation cancellation drops queued AI before it can speak`() {
        val schedule = CoachSpeechSchedule()
        var generation = 3
        schedule.finished(0)
        schedule.offer(ai { generation == 3 }, 30_000)
        generation++
        assertNull(schedule.takeReady(85_000, true))
        assertEquals(0, schedule.pendingCount)
    }

    @Test fun `pace direction change rejects an otherwise fresh deferred instruction`() {
        val schedule = CoachSpeechSchedule()
        var situation = CoachSituation(eligible = true, paceSeconds = 400.0, targetPaceSeconds = 330.0)
        val cue = CoachCue.SlowerThanTarget
        schedule.finished(0)
        schedule.offer(CoachSpeechRequest("pace", CoachSpeechPriority.PACE, cue = cue,
            isValid = { situation.allows(cue) }), 70_000)
        situation = situation.copy(paceSeconds = 310.0)
        assertNull(schedule.takeReady(85_000, true))
    }

    @Test fun `milestone waits briefly then discards an older routine cue`() {
        val schedule = CoachSpeechSchedule()
        schedule.finished(1_000)
        schedule.offer(ai(), 10_000)
        schedule.offer(km(1), 11_000)
        assertNull(schedule.takeReady(30_000, true))
        assertEquals(km(1).cue, schedule.takeReady(31_000, true)?.cue)
        assertEquals(0, schedule.pendingCount)
    }

    @Test fun `goal waits for current speech without interruption and supersedes lower priorities`() {
        val schedule = CoachSpeechSchedule()
        val first = ai()
        schedule.offer(first, 0)
        assertEquals(first, schedule.takeReady(0, true))
        schedule.offer(km(1), 1_000)
        schedule.offer(goal(), 2_000)
        assertEquals(1, schedule.pendingCount)
        assertNull(schedule.takeReady(4_000, false))
        schedule.finished(8_000)
        assertNull(schedule.takeReady(9_999, true))
        assertTrue(schedule.takeReady(10_000, true)?.cue is CoachCue.GoalReached)
        assertFalse(schedule.offer(goal(), 11_000))
    }

    @Test fun `km reports coalesce to newest crossing and duplicates cannot return after expiry`() {
        val schedule = CoachSpeechSchedule()
        schedule.offer(km(1), 0)
        schedule.offer(km(2), 10_000)
        assertEquals(1, schedule.pendingCount)
        assertEquals(2, (schedule.takeReady(11_000, true)?.cue as CoachCue.Kilometre).completed)
        assertFalse(schedule.offer(km(1), 20_000))
        assertFalse(schedule.offer(km(2), 20_000))
        schedule.offer(km(3), 30_000)
        assertNull(schedule.takeReady(90_000, true))
        assertFalse(schedule.offer(km(3), 91_000))
    }

    @Test fun `pause disable or stale GPS cancellation clears every pending priority`() {
        val schedule = CoachSpeechSchedule()
        schedule.offer(goal(), 0)
        schedule.invalidate()
        assertNull(schedule.takeReady(5_000, true))
        schedule.offer(ai(), 6_000)
        schedule.offer(km(1), 7_000)
        schedule.invalidate()
        assertEquals(0, schedule.pendingCount)
        assertNull(schedule.takeReady(100_000, true))
    }

    @Test fun `clock rollback invalidates pending advice instead of extending its age`() {
        val schedule = CoachSpeechSchedule()
        schedule.offer(ai(), 40_000)
        assertNull(schedule.takeReady(39_000, true))
        assertEquals(0, schedule.pendingCount)
    }

    @Test fun `configuration reset permits a new goal but retains music quiet interval`() {
        val schedule = CoachSpeechSchedule()
        schedule.offer(goal(), 0)
        schedule.takeReady(0, true)
        schedule.finished(5_000)
        schedule.reset()
        schedule.offer(ai(), 30_000)
        assertNull(schedule.takeReady(60_000, true))
        assertNotNull(schedule.takeReady(90_000, true))
        assertTrue(schedule.offer(goal(), 91_000))
    }

    @Test fun `broken validation fails closed and cannot block a newer valid cue`() {
        val schedule = CoachSpeechSchedule()
        assertFalse(schedule.offer(ai { error("changed state") }, 0))
        assertTrue(schedule.offer(km(1), 1_000))
        assertNotNull(schedule.takeReady(1_000, true))
    }

    @Test fun `even goal completion expires if output stays unavailable`() {
        val schedule = CoachSpeechSchedule()
        schedule.offer(goal(), 10_000)
        assertNull(schedule.takeReady(99_999, false))
        assertNull(schedule.takeReady(100_000, true))
        assertEquals(0, schedule.pendingCount)
    }

    @Test fun `revoked AI consent between TTS acceptance and start never enters spoken history`() {
        val history = SpokenCoachGate()
        var consent = true
        history.expect("accepted", "Optional AI advice", CoachSpeechSource.AI) { consent }
        consent = false
        assertNull(history.started("accepted", 5_000))
        consent = true
        assertNull(history.started("accepted", 6_000))
    }

    @Test fun `spoken history contains only matching actual starts exactly once`() {
        val history = SpokenCoachGate()
        assertNull(history.started("not-accepted", 0))
        history.expect("a", "A useful cue", CoachSpeechSource.LOCAL)
        assertNull(history.started("stale", 1))
        assertEquals(SpokenCoachAdvice("A useful cue", CoachSpeechSource.LOCAL, 2), history.started("a", 2))
        assertNull(history.started("a", 3))
        history.expect("b", "Unspoken AI", CoachSpeechSource.AI)
        history.clear()
        assertNull(history.started("b", 4))
        history.expect("c", "Current AI", CoachSpeechSource.AI)
        assertNull(history.started("b", 5))
        assertEquals("Current AI", history.started("c", 6)?.text)
    }
}
