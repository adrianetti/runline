package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

internal class LivePlan(
    run: JSONObject,
    context: JSONObject,
    spanish: Boolean,
) {
    private val goal: JSONObject? = context.optJSONObject("active_goal")
    private val facts = LivePlanRun(run, spanish, goal?.optString("clock") != "active")
    private val goalKm = goal?.amount("distance_goal_m")?.div(LivePlanRun.METRES_PER_KILOMETRE)
    private val deadline: LiveDeadline? =
        goalKm?.let { distance ->
            goal?.amount("distance_deadline_minutes")?.let { LiveDeadline(facts, it * LivePlanRun.SECONDS_PER_MINUTE, distance) }
        }
    private val target: LiveTarget? = bandOf(goal)?.takeIf { deadline == null }?.let { LiveTarget(facts, it) }
    val paced: Boolean = deadline != null || target != null
    private val intent = goal?.optString("intended_effort").orEmpty()
    private val gentle: LiveGentle? = if (paced) null else gentleLimit(intent, usualPace(run, context))?.let { LiveGentle(facts, it) }

    fun intention(numbers: MutableMap<String, String>): String = deadline?.intention(numbers) ?: target?.intention(numbers) ?: ""

    fun fits(point: LivePoint): Boolean = !paced || SELF_REFERENCED.none { point.id.startsWith(it) }

    fun moment(m: JSONObject): LivePoint? =
        when (m.optString("kind")) {
            "KILOMETRE" -> kilometre(m.optInt("number"), m.amount("split_seconds"))
            "HALFWAY" -> deadline?.halfway(m.optJSONObject("facts")?.optBoolean("past_halfway") == true)
            "ONE_KM_LEFT" -> deadline?.lastKilometre()
            "GOAL_REACHED" -> deadline?.finished(m.optJSONObject("facts"))
            else -> null
        }

    fun now(): LivePoint? = deadline?.now() ?: target?.now() ?: gentle?.now()

    private fun kilometre(
        n: Int,
        cueTime: Double?,
    ): LivePoint? {
        val time = if (facts.splits.size >= n) facts.splits.getOrNull(n - 1) else cueTime
        return time?.let { t ->
            val numbers = linkedMapOf("time_of_this_kilometre" to facts.say.time(t))
            val read = deadline?.kilometre(n, t, numbers) ?: target?.kilometre(n, t, numbers) ?: gentle?.kilometre(n, t)
            read?.let { (insight, hint) ->
                LivePoint("moment/km:$n", "Kilometre $n is done, $insight. Say both: its time, and what it shows.", numbers, hint)
            }
        }
    }

    private companion object {
        const val RECOVERY = "RECOVERY"
        const val MIN_USUAL_KM = 1.0
        val SELF_REFERENCED =
            listOf(
                "pace/now",
                "pace/early",
                "faster/pace",
                "slower/pace",
                "steady/back",
                "steady/pace",
                "steady/km",
                "faster/shape",
                "slower/shape",
                "history/previous_run",
                "history/km_first",
            )

        fun bandOf(goal: JSONObject?): ClosedFloatingPointRange<Double>? {
            val low = goal?.amount("pace_min_seconds_per_km")
            val high = goal?.amount("pace_max_seconds_per_km")
            val single = goal?.amount("target_pace_seconds_per_km")?.takeIf { goal.optString("type") == "PACE" }
            return when {
                low != null && high != null -> low..high
                single != null -> single..single
                else -> null
            }
        }

        fun usualPace(
            run: JSONObject,
            context: JSONObject,
        ): Double? {
            val type = run.optString("activity_type")
            val paces =
                context
                    .optJSONArray("recent_runs")
                    .members()
                    .filter { it.optString("activity_type") == type && (it.amount("distance_km") ?: 0.0) >= MIN_USUAL_KM }
                    .mapNotNull { it.amount("average_pace_seconds_per_km") }
                    .sorted()
            return paces.takeIf { it.isNotEmpty() }?.let { (it[(it.size - 1) / 2] + it[it.size / 2]) / 2 }
        }

        fun gentleLimit(
            intent: String,
            usual: Double?,
        ): Double? = usual?.takeIf { intent == RECOVERY }
    }
}

internal class LivePlanRun(
    run: JSONObject,
    private val spanish: Boolean,
    movingClock: Boolean = true,
) {
    val say = LiveSpeech(spanish)
    val km = run.amount("distance_km") ?: 0.0
    val moving = run.amount("moving_seconds") ?: 0.0
    val clock = if (movingClock) moving else run.amount("active_seconds") ?: moving
    val recent = run.amount("recent_pace_seconds_per_km")
    val splits =
        run
            .optJSONArray("splits")
            .members()
            .filter { it.optBoolean("is_full_kilometre") }
            .mapNotNull { it.amount("pace_seconds_per_km") }

    fun block(minutes: Int) = (moving / SECONDS_PER_MINUTE / minutes).toInt()

    fun range(target: ClosedFloatingPointRange<Double>): String =
        if (target.start == target.endInclusive) {
            say.pace(target.start)
        } else {
            say.time(target.start) + (if (spanish) " a " else " to ") + say.pace(target.endInclusive)
        }

    fun exact(seconds: Double): String {
        val whole = seconds.roundToInt()
        val rest = whole % SECONDS_PER_MINUTE.toInt()
        return if (whole < SECONDS_PER_HOUR) {
            say.time(seconds)
        } else {
            say.about((whole - rest).toDouble()) + if (rest > 0) " " + say.seconds(rest) else ""
        }
    }

    companion object {
        const val METRES_PER_KILOMETRE = 1000.0
        const val SECONDS_PER_MINUTE = 60.0
        const val PACE_NOW = "pace_now"
        const val TARGET_PACE = "target_pace"
        const val SETTLE_SECONDS = 120.0
        const val OFF_TARGET_EVERY_MINUTES = 6
        private const val SECONDS_PER_HOUR = 3600
    }
}

private data class Schedule(
    val goalPace: Double,
    val margin: Double,
    val needed: Double?,
)

internal class LiveDeadline(
    private val facts: LivePlanRun,
    private val deadline: Double,
    private val goalKm: Double,
) {
    private val say = facts.say

    fun intention(numbers: MutableMap<String, String>): String {
        numbers["todays_time_goal"] = facts.exact(deadline)
        return " They want to finish it inside the time goal below."
    }

    fun kilometre(
        n: Int,
        time: Double,
        numbers: MutableMap<String, String>,
    ): Pair<String, String> {
        val s = schedule()
        margin(s, numbers)
        val slipped = time > s.goalPace + GOAL_PACE_SLACK_SECONDS
        val standing =
            when {
                s.margin >= ON_SCHEDULE_SECONDS -> "and they are ahead of the pace for their time goal"
                s.margin <= -ON_SCHEDULE_SECONDS -> "and they are behind the pace for their time goal"
                else -> "and they are right on the pace for their time goal"
            }
        val hint =
            when {
                s.margin < ON_SCHEDULE_SECONDS -> neededHint(s, numbers, NEEDED_FROM_HERE)
                slipped -> "Give the margin plainly: it is shrinking. The one job is back to goal pace now. No praise for steadiness."
                else -> AHEAD_HINTS[n % AHEAD_HINTS.size]
            }
        val insight = standing + if (slipped) ", although this kilometre was slower than that pace" else ""
        return insight to hint
    }

    fun halfway(passed: Boolean): LivePoint {
        val s = schedule()
        val numbers = linkedMapOf("time_so_far" to facts.exact(facts.clock))
        margin(s, numbers)
        return LivePoint(
            "moment/halfway",
            (if (passed) "They have just passed" else "They are at") +
                " the halfway point of today's goal. Announce it with the time so far and where they stand against their time goal.",
            numbers,
            if (s.margin < ON_SCHEDULE_SECONDS) {
                neededHint(s, numbers, NEEDED_FROM_HERE)
            } else {
                "The second half decides the time goal: hold goal pace."
            },
        )
    }

    fun lastKilometre(): LivePoint {
        val s = schedule()
        val numbers = linkedMapOf<String, String>()
        margin(s, numbers)
        return LivePoint(
            "moment/last_km",
            "One kilometre left, and their time goal is still to be decided by it.",
            numbers,
            neededHint(s, numbers, "pace_needed_for_the_last_kilometre"),
        )
    }

    fun finished(cue: JSONObject?): LivePoint {
        val time = cue?.amount("moving_ms")?.div(MILLIS_PER_SECOND) ?: facts.clock
        val over = (time - deadline).roundToInt()
        val distance = cue?.amount("distance_m")?.div(LivePlanRun.METRES_PER_KILOMETRE) ?: facts.km
        val numbers = linkedMapOf("distance" to say.distance(distance), "time" to facts.exact(time))
        if (over > 0) numbers["over_the_time_goal_by"] = say.time(over.toDouble())
        if (over < 0) numbers["inside_the_time_goal_by"] = say.time(-over.toDouble())
        return if (over > 0) {
            LivePoint(
                "moment/goal_over",
                "Today's distance is done, but over the time they set.",
                numbers,
                "Celebrate the distance warmly and honestly, give the exact time, and never say they made the time. " +
                    "Then tell them to ease down.",
            )
        } else {
            LivePoint(
                "moment/goal",
                "Today's distance is done inside the time they set.",
                numbers,
                "Celebrate properly with the exact time, then tell them to ease down.",
            )
        }
    }

    fun now(): LivePoint? {
        val s = schedule()
        val pace = facts.recent
        val needed = s.needed
        return when {
            pace == null || needed == null || facts.km < MIN_PLAN_KM -> null
            s.margin <= -ON_SCHEDULE_SECONDS || pace > needed + TARGET_MARGIN_SECONDS -> offPace(s, pace, slow = true)
            pace < needed - TARGET_MARGIN_SECONDS && facts.km < goalKm * BANKING_LATEST_SHARE -> offPace(s, pace, slow = false)
            else -> null
        }
    }

    private fun offPace(
        s: Schedule,
        pace: Double,
        slow: Boolean,
    ): LivePoint {
        val numbers = linkedMapOf(LivePlanRun.PACE_NOW to say.pace(pace))
        margin(s, numbers)
        val id =
            if (slow) {
                "plan/time:${facts.block(LivePlanRun.OFF_TARGET_EVERY_MINUTES)}"
            } else {
                "plan/bank:${facts.block(BANKING_EVERY_MINUTES)}"
            }
        return LivePoint(
            id,
            "Right now they are running ${if (slow) "slower" else "quicker"} than their time goal needs.",
            numbers,
            if (slow) {
                neededHint(s, numbers, NEEDED_FROM_HERE)
            } else {
                "Tell them to settle at goal pace: time banked early is usually paid back later. No praise for the speed."
            },
        )
    }

    private fun schedule(): Schedule {
        val goalPace = deadline / goalKm
        val left = goalKm - facts.km
        val needed = ((deadline - facts.clock) / left).takeIf { left >= MIN_LEFT_KM && it > 0 }
        return Schedule(goalPace, goalPace * facts.km - facts.clock, needed)
    }

    private fun margin(
        s: Schedule,
        numbers: MutableMap<String, String>,
    ) {
        if (s.margin >= ON_SCHEDULE_SECONDS) numbers["ahead_of_the_time_goal_by"] = say.time(s.margin)
        if (s.margin <= -ON_SCHEDULE_SECONDS) numbers["behind_the_time_goal_by"] = say.time(-s.margin)
    }

    private fun neededHint(
        s: Schedule,
        numbers: MutableMap<String, String>,
        key: String,
    ): String {
        val needed = s.needed?.takeIf { need -> facts.recent?.let { need >= it - REACHABLE_SECONDS } != false }
        needed?.let { numbers[key] = say.pace(it) }
        return if (needed != null) {
            "Say where they stand plainly, with no praise for steadiness, then make the pace they need the one job."
        } else {
            "The time goal is out of reach at this pace; say so kindly and honestly, and make the job a strong, even finish."
        }
    }

    private companion object {
        const val NEEDED_FROM_HERE = "pace_needed_from_here"
        const val MILLIS_PER_SECOND = 1000.0
        const val TARGET_MARGIN_SECONDS = 8.0
        const val GOAL_PACE_SLACK_SECONDS = 2.0
        const val ON_SCHEDULE_SECONDS = 5.0
        const val REACHABLE_SECONDS = 30.0
        const val MIN_PLAN_KM = 1.0
        const val MIN_LEFT_KM = .2
        const val BANKING_LATEST_SHARE = .5
        const val BANKING_EVERY_MINUTES = 15
        val AHEAD_HINTS =
            listOf(
                "Give the margin, then tell them to settle at goal pace: time banked early is usually paid back later.",
                "Give the margin in a few words; the one job is to hold goal pace, not to bank more.",
                "Name the cushion briefly and keep them patient: goal pace, no faster.",
            )
    }
}

internal class LiveTarget(
    private val facts: LivePlanRun,
    private val band: ClosedFloatingPointRange<Double>,
) {
    private val say = facts.say

    fun intention(numbers: MutableMap<String, String>): String {
        numbers["todays_target_pace"] = facts.range(band)
        return " Today's job is to hold the target pace below."
    }

    fun kilometre(
        n: Int,
        time: Double,
        numbers: MutableMap<String, String>,
    ): Pair<String, String> {
        val off = offBand(time)
        if (off != 0.0) numbers[LivePlanRun.TARGET_PACE] = facts.range(band)
        return when {
            off == 0.0 -> {
                "and it is right on their target pace" to ON_TARGET_HINTS[n % ON_TARGET_HINTS.size]
            }

            off > WARM_UP_GAP_SECONDS && n <= WARM_UP_KILOMETRES -> {
                "and it is still well slower than their target pace, which is fine while warming up" to
                    "Treat it as the warm-up: give the target pace as the job once they are warm, without pressure."
            }

            off > 0 -> {
                numbers["slower_than_the_target_by"] = say.seconds(off.roundToInt())
                "and it was slower than their target pace" to
                    "Say it plainly, with no praise for steadiness, then one cue to bring the pace back to target: quicker, lighter steps."
            }

            else -> {
                numbers["quicker_than_the_target_by"] = say.seconds(-off.roundToInt())
                "and it was quicker than their target pace" to
                    "Quicker than planned now costs later: ease back to the target pace. No praise for speed."
            }
        }
    }

    fun now(): LivePoint? = facts.recent?.takeIf { facts.moving >= LivePlanRun.SETTLE_SECONDS }?.let(::placed)

    private fun placed(pace: Double): LivePoint {
        val off = offBand(pace)
        val numbers = linkedMapOf(LivePlanRun.PACE_NOW to say.pace(pace), LivePlanRun.TARGET_PACE to facts.range(band))
        val every = facts.block(LivePlanRun.OFF_TARGET_EVERY_MINUTES)
        return when {
            off > WARM_UP_GAP_SECONDS && facts.moving < WARM_UP_SECONDS -> {
                LivePoint(
                    "plan/warm",
                    "They are still well slower than their target pace, which is fine while warming up.",
                    mapOf(LivePlanRun.TARGET_PACE to facts.range(band)),
                    "No pressure: once they are warm, the target pace is the job.",
                )
            }

            off > 0 -> {
                LivePoint(
                    "plan/slow:$every",
                    "Right now they are slower than their target pace.",
                    numbers,
                    "Plain and kind, no praise for steadiness: one cue to bring it back to target, such as quicker, lighter steps.",
                )
            }

            off < 0 -> {
                LivePoint(
                    "plan/quick:$every",
                    "Right now they are quicker than their target pace.",
                    numbers,
                    "Ease back to the target pace: quicker now costs later. No praise for the speed.",
                )
            }

            else -> {
                LivePoint(
                    "plan/on",
                    "Right now they are running right on their target pace.",
                    mapOf(LivePlanRun.PACE_NOW to say.pace(pace)),
                    "On target: confirm it in a few words and ask for exactly this.",
                )
            }
        }
    }

    private fun offBand(pace: Double): Double =
        when {
            pace > band.endInclusive + TARGET_MARGIN_SECONDS -> pace - band.endInclusive
            pace < band.start - TARGET_MARGIN_SECONDS -> pace - band.start
            else -> 0.0
        }

    private companion object {
        const val TARGET_MARGIN_SECONDS = 8.0
        const val WARM_UP_GAP_SECONDS = 30.0
        const val WARM_UP_KILOMETRES = 2
        const val WARM_UP_SECONDS = 900.0
        val ON_TARGET_HINTS =
            listOf(
                "On target: say so in a few words and ask for exactly the same again.",
                "Another one on target: name the rhythm as theirs now, with nothing to change.",
                "On target again: keep it brief and hold it.",
            )
    }
}

internal class LiveGentle(
    private val facts: LivePlanRun,
    private val below: Double,
) {
    fun kilometre(
        n: Int,
        time: Double,
    ): Pair<String, String>? {
        val before = facts.splits.getOrNull(n - 2)
        return when {
            n <= OPENING_KILOMETRES && time < below -> {
                "and it was quicker than their usual running pace, too quick for a recovery day" to
                    "Kindly and clearly: ease off for the next one. On a day like this, slower is the job."
            }

            n <= OPENING_KILOMETRES + 1 && before != null && before < below && time >= below -> {
                "and it has eased back to a gentle pace after a quick one" to
                    "Exactly right for today; say so warmly. Do not give the difference."
            }

            else -> {
                null
            }
        }
    }

    fun now(): LivePoint? =
        facts.recent?.takeIf { it < below && facts.moving in LivePlanRun.SETTLE_SECONDS..OPENING_SECONDS }?.let { pace ->
            LivePoint(
                "plan/gentle:${facts.block(LivePlanRun.OFF_TARGET_EVERY_MINUTES)}",
                "Right now they are running quicker than their usual pace, which is too quick for a recovery day.",
                mapOf(LivePlanRun.PACE_NOW to facts.say.pace(pace)),
                "Kindly ask them to ease off now; on a day like this, slower is the job.",
            )
        }

    private companion object {
        const val OPENING_KILOMETRES = 2
        const val OPENING_SECONDS = 900.0
    }
}

internal fun JSONObject.amount(name: String): Double? = (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }

internal fun JSONArray?.members(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
