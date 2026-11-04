package com.runline.app.core

/** One slot per priority class, never a FIFO of late instructions. All times are monotonic. */
enum class CoachSpeechPriority(val rank: Int, val silenceMs: Long, val lifetimeMs: Long) {
    GOAL(100, 2_000, 90_000), MILESTONE(80, 30_000, 60_000),
    TERRAIN(50, 85_000, 60_000), AI(40, 85_000, 75_000),
    PACE(30, 85_000, 25_000), MORALE(20, 85_000, 60_000)
}

data class CoachSpeechRequest(
    val key: String,
    val priority: CoachSpeechPriority,
    val cue: CoachCue? = null,
    val advice: String? = null,
    val isValid: () -> Boolean = { true },
)

class CoachSpeechSchedule(private val onDrop: (CoachSpeechRequest, String) -> Unit = { _, _ -> }) {
    private data class Pending(val request: CoachSpeechRequest, val offeredAt: Long)
    private var goal: Pending? = null
    private var milestone: Pending? = null
    private var routine: Pending? = null
    private var lastFinishedAt: Long? = null
    private var lastObservedAt: Long? = null
    private var lastKilometre = 0
    private var goalSeen = false

    val pendingCount: Int get() = listOfNotNull(goal, milestone, routine).size

    fun offer(request: CoachSpeechRequest, nowMs: Long): Boolean {
        prune(nowMs)
        if (!valid(request)) return false
        val cue = request.cue
        if (cue is CoachCue.Kilometre) {
            if (cue.completed <= lastKilometre) return false
            lastKilometre = cue.completed
        }
        if (request.priority == CoachSpeechPriority.GOAL) {
            if (goalSeen) return false
            goalSeen = true
            goal = Pending(request, nowMs)
            dropped(milestone, "superseded"); dropped(routine, "superseded")
            milestone = null; routine = null
        } else if (request.priority == CoachSpeechPriority.MILESTONE) {
            if (goal != null) return false
            dropped(milestone, "superseded"); milestone = Pending(request, nowMs)
        } else {
            if (goal != null) return false
            if (routine != null && routine!!.request.priority.rank > request.priority.rank) return false
            dropped(routine, "superseded"); routine = Pending(request, nowMs)
        }
        return true
    }

    fun takeReady(nowMs: Long, outputAvailable: Boolean): CoachSpeechRequest? {
        prune(nowMs)
        if (!outputAvailable) return null
        val next = goal ?: milestone ?: routine ?: return null
        val last = lastFinishedAt
        if (last != null && nowMs - last < next.request.priority.silenceMs) return null
        when (next) { goal -> goal = null; milestone -> milestone = null; else -> routine = null }
        // A milestone already breaks the music. Do not follow it with an older routine cue.
        if (next.request.priority == CoachSpeechPriority.MILESTONE) { dropped(routine, "superseded"); routine = null }
        return next.request
    }

    /** Completion/error/stop, not generation or onStart, begins the quiet interval. */
    fun finished(nowMs: Long) {
        observeTime(nowMs)
        lastFinishedAt = nowMs
    }

    fun invalidate() { listOf(goal, milestone, routine).forEach { dropped(it, "invalidated") }; goal = null; milestone = null; routine = null }
    private fun dropped(value: Pending?, reason: String) { value?.let { runCatching { onDrop(it.request, reason) } } }
    fun reset() { invalidate(); lastKilometre = 0; goalSeen = false }

    private fun prune(nowMs: Long) {
        observeTime(nowMs)
        fun Pending?.fresh(): Pending? {
            if (this == null) return null
            val fresh = nowMs >= offeredAt && nowMs - offeredAt < request.priority.lifetimeMs
            if (!fresh || !valid(request)) { dropped(this, if (fresh) "context_changed" else "expired"); return null }
            return this
        }
        goal = goal.fresh(); milestone = milestone.fresh(); routine = routine.fresh()
    }
    private fun observeTime(nowMs: Long) {
        if (lastObservedAt?.let { nowMs < it } == true) { invalidate(); lastFinishedAt = nowMs }
        lastObservedAt = nowMs
    }
    private fun valid(request: CoachSpeechRequest) = runCatching(request.isValid).getOrDefault(false)
}

enum class CoachSpeechSource { LOCAL, AI }
data class SpokenCoachAdvice(val text: String, val source: CoachSpeechSource, val elapsedRealtimeMs: Long)

/** A queued/generated sentence is not speech history. Only the matching TTS start counts once. */
class SpokenCoachGate {
    private var id: String? = null
    private var text = ""
    private var source = CoachSpeechSource.LOCAL
    private var isValid: () -> Boolean = { true }
    fun expect(utteranceId: String, value: String, origin: CoachSpeechSource, valid: () -> Boolean = { true }) {
        id = utteranceId; text = value; source = origin; isValid = valid
    }
    fun started(utteranceId: String?, nowMs: Long): SpokenCoachAdvice? {
        if (utteranceId == null || utteranceId != id) return null
        id = null
        if (!runCatching(isValid).getOrDefault(false)) { clear(); return null }
        return SpokenCoachAdvice(text, source, nowMs)
    }
    fun clear() { id = null; text = ""; isValid = { true } }
}
