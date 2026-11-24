package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

internal class LiveEvents(
    run: JSONObject,
    private val say: LiveSpeech,
    easy: Boolean,
    relaxed: Boolean,
    paced: Boolean,
) {
    private val root = run.optJSONObject(AiRunEvents.KEY)
    private val items = root?.optJSONArray(ITEMS).entries()
    private val counts = root?.optJSONObject(COUNTS)
    private val news = LiveEventNews(say, easy, relaxed, paced)
    private val splits =
        run
            .optJSONArray("splits")
            .entries()
            .filter { it.optBoolean("is_full_kilometre") }
            .mapNotNull { it.figure(PACE) }
    private val laps = run.optJSONArray("estimated_repeated_laps").entries().mapNotNull { it.figure("duration_seconds") }
    val interrupted: Boolean = root?.optBoolean(INTERRUPTED) == true
    val stoppedNow: Boolean = root?.figure(STOPPED_NOW) != null || items.any { it.optString(KIND) == STOP && it.optBoolean(ONGOING) }

    fun news(used: List<String>): LivePoint? =
        items
            .asReversed()
            .asSequence()
            .mapNotNull(news::of)
            .firstOrNull { it.id !in used }

    fun moment(
        milestone: JSONObject,
        base: LivePoint?,
        used: List<String>,
    ): LivePoint? =
        when (milestone.optString(KIND)) {
            "KILOMETRE" -> kilometre(milestone, used) ?: base
            "LAP" -> lap(milestone.optInt(NUMBER), used) ?: base
            "GOAL_REACHED" -> base?.let(::along)
            else -> base
        }

    private fun kilometre(
        milestone: JSONObject,
        used: List<String>,
    ): LivePoint? {
        val n = milestone.optInt(NUMBER)
        val time = if (splits.size >= n) splits.getOrNull(n - 1) else milestone.figure("split_seconds")
        val event = items.lastOrNull { it.optString(KIND) in INCLUDED && n in it.optInt(FROM_KM)..it.optInt(TO_KM) } ?: return null
        val numbers = linkedMapOf<String, String>()
        time?.let { numbers["time_of_this_kilometre"] = say.time(it) }
        return included("Kilometre $n is done", "moment/km:$n", event, numbers, used)
    }

    private fun lap(
        n: Int,
        used: List<String>,
    ): LivePoint? {
        val time = laps.lastOrNull() ?: return null
        val event = items.lastOrNull { it.optString(KIND) in INCLUDED && (it.figure(ENDED_AGO) ?: Double.MAX_VALUE) <= time }
        val numbers = linkedMapOf("time_of_this_lap" to say.time(time))
        return event?.let { included("Lap $n of the same circuit is done", "moment/lap:$n", it, numbers, used) }
    }

    private fun included(
        opening: String,
        id: String,
        event: JSONObject,
        numbers: MutableMap<String, String>,
        used: List<String>,
    ): LivePoint {
        val ids = news.ids(event)
        val told = ids.any { it in used }
        val (what, key, hint) =
            when (event.optString(KIND)) {
                WALK -> Triple("it included a walk break", WALK_LASTED, "Give its time plainly and mention the walk in passing.")
                STOP -> Triple("it included a stop where they stood still", STILL_FOR, STOP_HINT)
                else -> Triple("the GPS signal dropped during it, so its time is only an estimate", SIGNAL_FOR, GPS_HINT)
            }
        if (!told) numbers[key] = news.duration(event)
        val repeat = if (told) " It was mentioned a moment ago, so a few words are enough." else ""
        return LivePoint(id, "$opening, and $what. Say both: its time, and what it included.", numbers, "$hint $NO_COMPARISON$repeat", ids)
    }

    private fun along(point: LivePoint): LivePoint {
        val walks = counts?.optInt(WALK) ?: 0
        val stops = counts?.optInt(STOP) ?: 0
        if (!interrupted || walks + stops == 0) return point
        val parts =
            listOfNotNull(
                walks.takeIf { it > 0 }?.let { if (it == 1) "1 walk break" else "$it walk breaks" },
                stops.takeIf { it > 0 }?.let { if (it == 1) "1 stop" else "$it stops" },
            )
        val verb = if (walks + stops == 1) "was" else "were"
        return point.copy(
            point = point.point + " Along the way there $verb " + parts.joinToString(" and ") + ".",
            hint = (point.hint?.let { "$it " } ?: "") + "Never say they ran it without stopping.",
        )
    }

    companion object {
        private const val ITEMS = "items"
        private const val COUNTS = "counts"
        private const val INTERRUPTED = "interrupted"
        private const val STOPPED_NOW = "stopped_now_seconds"
        const val KIND = "kind"
        const val NUMBER = "number"
        const val PACE = "pace_seconds_per_km"
        const val ONGOING = "ongoing"
        const val ENDED_AGO = "ended_seconds_ago"
        const val WALK = "WALK"
        const val STOP = "STOP"
        const val PAUSE = "PAUSE"
        const val GPS_GAP = "GPS_GAP"
        const val WALK_LASTED = "the_walk_break_lasted"
        const val STILL_FOR = "standing_still_for"
        const val SIGNAL_FOR = "signal_lost_for"
        private val INCLUDED = setOf(WALK, STOP, GPS_GAP)
        private const val FROM_KM = "kilometre"
        private const val TO_KM = "to_kilometre"
        private const val STOP_HINT = "A stop is not a slowdown: give its time and mention the stop in passing."
        private const val GPS_HINT = "Give its time as an estimate in a few calm words."
        private const val NO_COMPARISON = "Never compare it with others, call it slower or give a reset cue."
        private const val UNBROKEN_ES =
            "sin parar|sin pararte|sin detenerte|sin (?:una sola |ninguna )?pausa|sin pausas|sin descansos?|sin caminar|sin andar|" +
                "de principio a fin|del tir[oó]n|de un tir[oó]n|todo seguido|sin interrupci[oó]n|sin interrupciones|no has parado"
        private const val UNBROKEN_EN =
            "without stopping|without (?:a |one |any )?(?:single )?(?:stop|break|pause)s?|non-?stop|start to finish|without walking|" +
                "unbroken|never stopped|didn'?t stop|haven'?t stopped"
        private val UNBROKEN = Regex("\\b(?:$UNBROKEN_ES|$UNBROKEN_EN)\\b")

        fun continuity(
            line: String,
            run: JSONObject,
        ): String? {
            val interrupted = run.optJSONObject(AiRunEvents.KEY)?.optBoolean(INTERRUPTED) == true
            return if (interrupted && UNBROKEN.containsMatchIn(line.lowercase(Locale.ROOT))) {
                "It says they ran without stopping, but this run had stops or walk breaks. Do not claim the running was unbroken."
            } else {
                null
            }
        }
    }
}

internal class LiveEventNews(
    private val say: LiveSpeech,
    private val easy: Boolean,
    private val relaxed: Boolean,
    private val paced: Boolean,
) {
    fun of(event: JSONObject): LivePoint? {
        val ongoing = event.optBoolean(LiveEvents.ONGOING)
        if (!ongoing && (event.figure(LiveEvents.ENDED_AGO) ?: Double.MAX_VALUE) > FRESH_SECONDS) return null
        val number = event.optInt(LiveEvents.NUMBER)
        return when (event.optString(LiveEvents.KIND)) {
            LiveEvents.WALK -> if (ongoing) walking(event, number) else walked(event, number)
            LiveEvents.STOP -> if (ongoing) null else stopped(event, number)
            LiveEvents.PAUSE -> resumed(event, number)
            LiveEvents.GPS_GAP -> signal(event, number)
            else -> null
        }
    }

    fun ids(event: JSONObject): List<String> {
        val number = event.optInt(LiveEvents.NUMBER)
        return when (event.optString(LiveEvents.KIND)) {
            LiveEvents.WALK -> listOf("event/walk:$number", "event/walk_end:$number")
            LiveEvents.STOP -> listOf("event/stop:$number")
            else -> listOf("event/gps:$number")
        }
    }

    fun duration(event: JSONObject): String {
        val seconds = event.figure("duration_seconds") ?: 0.0
        val rounded = ((seconds / ROUNDING).roundToInt() * ROUNDING).coerceAtLeast(ROUNDING)
        return if (seconds >= LONG_SECONDS) say.about(seconds) else say.time(rounded.toDouble())
    }

    private fun walking(
        event: JSONObject,
        number: Int,
    ) = LivePoint(
        "event/walk:$number",
        "They are walking right now: a walk break that has lasted the time below so far.",
        mapOf("walking_for" to duration(event)),
        if (relaxed) {
            "Reassure them warmly: walking is a normal part of building up. They can jog again gently when ready. No pace talk."
        } else {
            "Kind and matter-of-fact: a short walk is fine. When they start again, ease in before thinking about the plan. No pace talk."
        },
    )

    private fun walked(
        event: JSONObject,
        number: Int,
    ): LivePoint {
        val numbers = linkedMapOf(LiveEvents.WALK_LASTED to duration(event))
        val since = event.figure(SINCE)?.takeIf { paced }
        since?.let { numbers["pace_since_the_walk"] = say.pace(it) }
        val after = if (since != null) " Since then they have been running at the pace below." else ""
        return LivePoint(
            "event/walk_end:$number",
            "They have started running again after a walk break.$after",
            numbers,
            if (paced) {
                "Welcome them back to running; settle into a rhythm first, then the plan. Nothing to make up."
            } else {
                "Welcome them back to running, warmly. Ease in gently; there is nothing to make up."
            },
        )
    }

    private fun stopped(
        event: JSONObject,
        number: Int,
    ) = LivePoint(
        "event/stop:$number",
        "They are moving again after standing still for the time below.",
        mapOf(LiveEvents.STILL_FOR to duration(event)),
        "Welcome them back in a few words and give one cue to find their rhythm again. " +
            "A stop is not a slowdown: no pace judgement, nothing about time lost.",
    )

    private fun resumed(
        event: JSONObject,
        number: Int,
    ) = LivePoint(
        "event/pause:$number",
        "They have just restarted the recording after a pause.",
        mapOf("paused_for" to duration(event)),
        "Welcome them back out; the first minutes after a break are for settling in, not for winning time.",
    )

    private fun signal(
        event: JSONObject,
        number: Int,
    ) = LivePoint(
        "event/gps:$number",
        "The GPS signal dropped for the time below and is back now. Distance and pace over that stretch are only estimates.",
        mapOf(LiveEvents.SIGNAL_FOR to duration(event)),
        "Tell them calmly that the signal is back and nothing changes for them. Give no pace, split or distance for that stretch.",
    )

    private companion object {
        const val FRESH_SECONDS = 180.0
        const val LONG_SECONDS = 600.0
        const val ROUNDING = 5
        const val SINCE = "pace_since_seconds_per_km"
    }
}

internal fun JSONObject.figure(name: String): Double? = (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }

internal fun JSONArray?.entries(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
