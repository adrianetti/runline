package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class PendingCoachGoalTest {
    @Test fun `goal crossing during another prompt is spoken once after that prompt finishes`() {
        var currentText: String? = null
        var releases = 0
        val lease = SpeechLease(object : SpeechOutput {
            override fun requestFocus() = true
            override fun abandonFocus() { releases++ }
            override fun speak(text: String, id: String): Boolean { currentText = text; return true }
            override fun stopSpeech() { currentText = null }
        })
        val pending = PendingCoachGoal()
        assertTrue(lease.begin("one kilometre", "km"))
        pending.offer(CoachCue.GoalReached(true), 1_000)
        assertNull(pending.takeWhenReady(2_000, !lease.isBusy))
        assertEquals("one kilometre", currentText)
        lease.finish("km")
        val goal = pending.takeWhenReady(4_000, !lease.isBusy)
        assertEquals(CoachCue.GoalReached(true), goal)
        assertTrue(lease.begin("goal reached", "goal"))
        assertNull(pending.takeWhenReady(5_000, true))
        lease.finish("goal")
        assertEquals(2, releases)
    }

    @Test fun `another speaker can hold focus without losing or overlapping the pending goal`() {
        val pending = PendingCoachGoal()
        pending.offer(CoachCue.GoalReached(null), 5_000)
        assertNull(pending.takeWhenReady(10_000, ready = false))
        assertEquals(CoachCue.GoalReached(null), pending.takeWhenReady(12_000, ready = true))
    }

    @Test fun `pause focus loss and speech failure cancellation cannot speak an old goal later`() {
        val pending = PendingCoachGoal()
        pending.offer(CoachCue.GoalReached(false), 1_000)
        pending.clear()
        assertNull(pending.takeWhenReady(2_000, true))
        assertFalse(pending.hasPending(2_000))
    }

    @Test fun `stale goals expire and only the newest completion can occupy the slot`() {
        val pending = PendingCoachGoal()
        pending.offer(CoachCue.GoalReached(true), 1_000)
        pending.offer(CoachCue.GoalReached(false), 2_000)
        assertEquals(CoachCue.GoalReached(false), pending.takeWhenReady(3_000, true))
        pending.offer(CoachCue.GoalReached(null), 5_000)
        assertNull(pending.takeWhenReady(25_000, true))
        pending.offer(CoachCue.GoalReached(null), 30_000)
        assertNull(pending.takeWhenReady(29_000, true))
    }
}
