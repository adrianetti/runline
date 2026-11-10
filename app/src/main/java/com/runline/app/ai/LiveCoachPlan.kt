package com.runline.app.ai

/** Why the live coach is asked to speak now. */
enum class LiveTrigger { OPENING, MOMENT, CADENCE }

/** A local announcement the live coach can build on right after it is spoken. */
enum class LiveMoment { KILOMETRE, LAP, HALFWAY, FINAL_STRETCH, ONE_KM_LEFT, GOAL_REACHED;
    companion object {
        /** [cue] is the spoken cue's diagnostic name, e.g. "Kilometre(completed=2, ...)". */
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

/** How a line is delivered. Rotated on every call so the coach never falls into one sentence shape. */
enum class LiveStyle { PUNCHY, QUESTION, NUMBER_FIRST, CUE_FIRST, IMAGE, CELEBRATE, CALM, LOOK_AHEAD }

/** The angle of a routine check-in. Rotating it is what keeps consecutive lines different. */
enum class LiveFocus { PACE_TREND, TECHNIQUE, HISTORY, MORALE, GOAL_PROJECTION, EFFORT_CHECK, SPLIT_DETAIL, ASPIRATION }

data class LiveCall(val trigger: LiveTrigger, val moment: LiveMoment?, val focus: LiveFocus, val number: Int) {
    val style: LiveStyle get() = if (moment == LiveMoment.GOAL_REACHED) LiveStyle.CELEBRATE else LiveStyle.entries[(number * 3) % LiveStyle.entries.size]
}

/**
 * Decides when the live coach is asked for a line. All times are active recording time.
 *
 * It speaks early (a first line within the first minute), right after each local milestone
 * (kilometre, lap, halfway, goal) to add the meaning behind the number, and otherwise every
 * chosen interval since it last spoke. Requests are at least [MIN_GAP_MS] apart, back off
 * while the network fails, and stop at [MAX_ATTEMPTS] per activity as a cost ceiling.
 */
class LiveCoachPlan(initialAttempts: Int = 0) {
    var attempts = initialAttempts.coerceIn(0, MAX_ATTEMPTS)
        private set
    private var seen = false
    private var opened = false
    private var lastRequestAt: Long? = null
    private var nextCadenceAt = 0L
    private var lastSpeechAt: Long? = null
    private var pending: Pair<LiveMoment, Long>? = null
    private var rotation = 0
    private var failures = 0
    private var interval = 120_000L

    /** False after repeated failures or at the ceiling: the local coach fills in again. */
    val healthy: Boolean get() = failures < 2 && attempts < MAX_ATTEMPTS

    /** Any line that started playing. [moment] is set for a local milestone. */
    fun spoken(activeMs: Long, ai: Boolean, moment: LiveMoment?) {
        lastSpeechAt = activeMs
        if (ai) nextCadenceAt = activeMs + interval
        // A lap of about a kilometre ends seconds before the kilometre does. The lap waits a
        // moment so that one follow-up covers both; a kilometre or goal line replaces it.
        else if (moment != null && (moment != LiveMoment.LAP || pending == null)) pending = moment to activeMs
    }

    fun next(activeMs: Long, intervalMinutes: Int): LiveCall? {
        interval = intervalMinutes.coerceIn(1, 10) * 60_000L
        if (!seen) {
            seen = true
            // Resumed or recovered mid-run: no welcome ten minutes in.
            if (activeMs > OPENING_LATEST_MS || attempts > 0) { opened = true; nextCadenceAt = activeMs + 30_000 }
        }
        if (attempts >= MAX_ATTEMPTS) return null
        if (lastRequestAt?.let { activeMs - it < MIN_GAP_MS } == true) return null
        val moment = pending?.takeIf { activeMs - it.second in (if (it.first == LiveMoment.LAP) LAP_WAIT_MS else 0)..(if (it.first == LiveMoment.LAP) LAP_WAIT_MS else 0) + MOMENT_FRESH_MS }
        if (moment == null && pending?.let { activeMs - it.second > LAP_WAIT_MS + MOMENT_FRESH_MS } == true) pending = null
        val call = when {
            moment != null -> {
                pending = null; opened = true
                LiveCall(LiveTrigger.MOMENT, moment.first, when (moment.first) {
                    LiveMoment.KILOMETRE, LiveMoment.LAP -> LiveFocus.SPLIT_DETAIL
                    LiveMoment.GOAL_REACHED -> LiveFocus.MORALE
                    else -> LiveFocus.GOAL_PROJECTION
                }, attempts + 1)
            }
            !opened -> {
                if (activeMs < OPENING_EARLIEST_MS) return null
                opened = true
                LiveCall(LiveTrigger.OPENING, null, LiveFocus.MORALE, attempts + 1)
            }
            pending == null && activeMs >= nextCadenceAt && lastSpeechAt?.let { activeMs - it < QUIET_BEFORE_MS } != true ->
                LiveCall(LiveTrigger.CADENCE, null, LiveFocus.entries[rotation++ % LiveFocus.entries.size], attempts + 1)
            else -> return null
        }
        attempts++; lastRequestAt = activeMs; nextCadenceAt = activeMs + interval
        return call
    }

    /** The model answered, with a line or with silence. */
    fun answered(activeMs: Long, spoke: Boolean) {
        failures = 0
        if (!spoke) nextCadenceAt = minOf(nextCadenceAt, activeMs + SILENT_RETRY_MS)
    }

    /** Network, key or model failure: retry soon, then back off up to the normal interval. */
    fun failed(activeMs: Long) {
        failures++
        nextCadenceAt = activeMs + minOf(interval, FAILURE_RETRY_MS shl (failures - 1).coerceAtMost(3))
    }

    companion object {
        const val LAP_WAIT_MS = 40_000L
        const val MAX_ATTEMPTS = 80
        const val MIN_GAP_MS = 20_000L
        const val OPENING_EARLIEST_MS = 40_000L
        const val OPENING_LATEST_MS = 150_000L
        const val MOMENT_FRESH_MS = 20_000L
        const val QUIET_BEFORE_MS = 20_000L
        const val SILENT_RETRY_MS = 45_000L
        const val FAILURE_RETRY_MS = 30_000L
    }
}
