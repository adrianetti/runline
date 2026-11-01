package com.runline.app.core

/** One high-priority completion cue, never a queue of late pace or kilometre prompts. */
class PendingCoachGoal(private val maxAgeMs: Long = 20_000) {
    private var cue: CoachCue.GoalReached? = null
    private var offeredAtMs = 0L

    fun offer(value: CoachCue.GoalReached, nowMs: Long) { cue = value; offeredAtMs = nowMs }
    fun hasPending(nowMs: Long): Boolean {
        if (nowMs < offeredAtMs || nowMs - offeredAtMs >= maxAgeMs) clear()
        return cue != null
    }
    fun takeWhenReady(nowMs: Long, ready: Boolean): CoachCue.GoalReached? {
        if (!hasPending(nowMs) || !ready) return null
        val result = cue
        clear()
        return result
    }
    fun clear() { cue = null }
}
