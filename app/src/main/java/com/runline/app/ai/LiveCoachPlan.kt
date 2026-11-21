package com.runline.app.ai

enum class LiveTrigger { OPENING, MOMENT, CADENCE }

enum class LiveMoment { KILOMETRE, LAP, HALFWAY, FINAL_STRETCH, ONE_KM_LEFT, GOAL_REACHED;
    companion object {
        fun of(cue: com.runline.app.core.CoachCue): LiveMoment? = of(cue.toString())
        fun of(cue: String): LiveMoment? = when {
            cue.startsWith("Kilometre(") -> KILOMETRE
            cue.startsWith("LapCompleted(") -> LAP
            cue.startsWith("Encouragement(kind=HALFWAY") -> HALFWAY
            cue.startsWith("Encouragement(kind=FINAL_STRETCH") -> FINAL_STRETCH
            cue.startsWith("OneKilometreRemaining") -> ONE_KM_LEFT
            cue.startsWith("GoalReached(") -> GOAL_REACHED
            else -> null
        }
    }
}

enum class LiveStyle { PUNCHY, WARM, NUMBER_FIRST, CUE_FIRST, CELEBRATE, CALM, LOOK_AHEAD }

enum class LiveFocus { PACE_TREND, TECHNIQUE, HISTORY, MORALE, GOAL_PROJECTION, EFFORT_CHECK, SPLIT_DETAIL, ASPIRATION }

data class LiveCall(val trigger: LiveTrigger, val moment: LiveMoment?, val focus: LiveFocus, val number: Int)

class LiveCoachPlan(initialAttempts: Int = 0) {
    var attempts = initialAttempts.coerceIn(0, MAX_ATTEMPTS)
        private set
    private var seen = false
    private var opened = false
    private var lastRequestAt: Long? = null
    private var nextCadenceAt = 0L
    private var lastSpeechAt: Long? = null
    private var rotation = 0
    private var failures = 0
    private var interval = 120_000L

    val healthy: Boolean get() = failures < 2 && attempts < MAX_ATTEMPTS

    fun spoken(activeMs: Long, ai: Boolean) {
        lastSpeechAt = activeMs
        if (ai) nextCadenceAt = activeMs + interval
    }

    fun milestone(activeMs: Long, moment: LiveMoment): LiveCall? {
        if (!healthy) return null
        opened = true
        attempts++; lastRequestAt = activeMs; nextCadenceAt = maxOf(nextCadenceAt, activeMs + SILENT_RETRY_MS)
        return LiveCall(LiveTrigger.MOMENT, moment, when (moment) {
            LiveMoment.KILOMETRE, LiveMoment.LAP -> LiveFocus.SPLIT_DETAIL
            LiveMoment.GOAL_REACHED -> LiveFocus.MORALE
            else -> LiveFocus.GOAL_PROJECTION
        }, attempts)
    }

    fun next(activeMs: Long, intervalMinutes: Int): LiveCall? {
        interval = intervalMinutes.coerceIn(1, 10) * 60_000L
        if (!seen) {
            seen = true
            if (activeMs > OPENING_LATEST_MS || attempts > 0) { opened = true; nextCadenceAt = activeMs + 30_000 }
        }
        if (attempts >= MAX_ATTEMPTS) return null
        if (lastRequestAt?.let { activeMs - it < MIN_GAP_MS } == true) return null
        val call = when {
            !opened -> {
                if (activeMs < OPENING_EARLIEST_MS) return null
                opened = true
                LiveCall(LiveTrigger.OPENING, null, LiveFocus.MORALE, attempts + 1)
            }
            activeMs >= nextCadenceAt && lastSpeechAt?.let { activeMs - it < QUIET_BEFORE_MS } != true ->
                LiveCall(LiveTrigger.CADENCE, null, LiveFocus.entries[rotation++ % LiveFocus.entries.size], attempts + 1)
            else -> return null
        }
        attempts++; lastRequestAt = activeMs; nextCadenceAt = activeMs + interval
        return call
    }

    fun answered(activeMs: Long, spoke: Boolean) {
        failures = 0
        if (!spoke) nextCadenceAt = minOf(nextCadenceAt, activeMs + SILENT_RETRY_MS)
    }

    fun failed(activeMs: Long) {
        failures++
        nextCadenceAt = activeMs + minOf(interval, FAILURE_RETRY_MS shl (failures - 1).coerceAtMost(3))
    }

    companion object {
        const val MAX_ATTEMPTS = 80
        const val MIN_GAP_MS = 20_000L
        const val OPENING_EARLIEST_MS = 40_000L
        const val OPENING_LATEST_MS = 150_000L
        const val QUIET_BEFORE_MS = 20_000L
        const val SILENT_RETRY_MS = 45_000L
        const val FAILURE_RETRY_MS = 30_000L
    }
}
