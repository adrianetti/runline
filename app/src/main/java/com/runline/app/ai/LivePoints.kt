package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One thing worth saying, worked out on the phone. [point] states it plainly (in English, as
 * data for the model); [numbers] are the only figures the coach may speak, already written the
 * way they are said in the runner's language; [hint] is the coaching angle. [id] is
 * "theme/observation": an observation is offered once per activity, and a theme not twice running.
 */
data class LivePoint(val id: String, val point: String, val numbers: Map<String, String> = emptyMap(), val hint: String? = null) {
    val theme: String get() = themeOf(id)
    companion object { fun themeOf(id: String) = id.substringBefore('/') }
}

/**
 * What a local milestone cue knows, as data for [LivePoints]: the app's own numbers, so the
 * line the live coach speaks agrees with the screen and with the built-in announcement it replaces.
 */
object LiveMilestone {
    fun of(cue: com.runline.app.core.CoachCue): JSONObject? = when (cue) {
        is com.runline.app.core.CoachCue.Kilometre -> JSONObject().put("kind", "KILOMETRE").put("number", cue.completed)
            .putOpt("split_seconds", cue.splitPaceSeconds).putOpt("previous_split_seconds", cue.previousSplitPaceSeconds)
        is com.runline.app.core.CoachCue.LapCompleted -> JSONObject().put("kind", "LAP").put("number", cue.number)
            .putOpt("slower_quarter", cue.comparison?.slowerSector?.number)
        is com.runline.app.core.CoachCue.Encouragement -> when (cue.kind) {
            com.runline.app.core.EncouragementKind.HALFWAY -> JSONObject().put("kind", "HALFWAY").putOpt("facts", cue.facts?.let(::facts))
            com.runline.app.core.EncouragementKind.FINAL_STRETCH -> JSONObject().put("kind", "FINAL_STRETCH").putOpt("facts", cue.facts?.let(::facts))
            else -> null
        }
        com.runline.app.core.CoachCue.OneKilometreRemaining -> JSONObject().put("kind", "ONE_KM_LEFT")
        is com.runline.app.core.CoachCue.GoalReached -> JSONObject().put("kind", "GOAL_REACHED").putOpt("within_time", cue.withinTime).putOpt("facts", cue.facts?.let(::facts))
        else -> null
    }
    private fun facts(f: com.runline.app.core.CoachFacts) = JSONObject().put("distance_m", f.distanceMeters).put("moving_ms", f.movingMs)
        .putOpt("average_pace_seconds", f.averagePaceSeconds).putOpt("remaining_ms", f.remainingMs).putOpt("remaining_m", f.remainingMeters)
        .putOpt("projected_m", f.projectedMeters).putOpt("projected_ms", f.projectedMs).put("past_halfway", f.pastHalfway)
}

/** Figures as they are spoken: "7 minutos 41 por kilómetro", "2,2 kilómetros", "20 segundos". */
class LiveSpeech(private val spanish: Boolean) {
    fun pace(seconds: Double): String = time(seconds) + if (spanish) " por kilómetro" else " per kilometre"
    /** Minutes and seconds, as for a split or a lap. */
    fun time(seconds: Double): String {
        val total = seconds.roundToInt().coerceAtLeast(0)
        if (total >= 3600) return about(seconds)
        val m = total / 60; val s = total % 60
        return when {
            m == 0 -> seconds(s)
            s == 0 -> minutes(m)
            else -> minutes(m) + " " + s
        }
    }
    /** Rounded to whole minutes, as for time left. */
    fun about(seconds: Double): String {
        val total = (seconds / 60).roundToInt().coerceAtLeast(1)
        val h = total / 60; val m = total % 60
        return when {
            h == 0 -> minutes(m)
            m == 0 -> hours(h)
            else -> hours(h) + " " + minutes(m)
        }
    }
    fun seconds(value: Int): String = if (spanish) (if (value == 1) "1 segundo" else "$value segundos") else (if (value == 1) "1 second" else "$value seconds")
    private fun minutes(value: Int) = if (spanish) (if (value == 1) "1 minuto" else "$value minutos") else (if (value == 1) "1 minute" else "$value minutes")
    private fun hours(value: Int) = if (spanish) (if (value == 1) "1 hora" else "$value horas") else (if (value == 1) "1 hour" else "$value hours")
    /** Whole metres to the nearest 50 under a kilometre, one decimal above. */
    fun distance(km: Double): String {
        if (km < 0.95) { val metres = ((km * 1000 / 50).roundToInt() * 50).coerceAtLeast(50); return if (spanish) "$metres metros" else "$metres metres" }
        val tenths = (km * 10).roundToInt()
        val text = if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}${if (spanish) "," else "."}${tenths % 10}"
        return text + if (spanish) (if (tenths == 10) " kilómetro" else " kilómetros") else (if (tenths == 10) " kilometre" else " kilometres")
    }
    fun days(value: Long): String = if (spanish) (if (value == 1L) "1 día" else "$value días") else (if (value == 1L) "1 day" else "$value days")
    fun months(value: Int): String = if (spanish) (if (value == 1) "1 mes" else "$value meses") else (if (value == 1) "1 month" else "$value months")
    fun percent(value: Int): String = if (spanish) "$value por ciento" else "$value percent"
    fun perKilometre(value: String): String = value + if (spanish) " por kilómetro" else " per kilometre"
    fun ago(value: String): String = if (spanish) "hace $value" else "$value ago"
}

/**
 * Chooses what the live coach talks about. The model is good at sounding like a coach and poor
 * at arithmetic under time pressure, so every observation, comparison and projection is decided
 * here from the run summary and coaching context, and the model only gives it a voice.
 */
object LivePoints {
    /**
     * The observation for this call, or null when nothing is worth saying. [used] lists the
     * observations already voiced in this activity, oldest first. After a local milestone only a
     * real finding earns a follow-up; a routine check-in always finds something.
     */
    fun pick(run: JSONObject, context: JSONObject, call: LiveCall, spanish: Boolean, used: List<String> = emptyList(), milestone: JSONObject? = null): LivePoint? {
        val s = State(run, context, LiveSpeech(spanish))
        // The coach is announcing a milestone in the app's place: that announcement is the point.
        if (milestone != null) return s.milestone(milestone)
        val order: List<() -> LivePoint?> = when (call.trigger) {
            LiveTrigger.OPENING -> listOf(s::opening)
            LiveTrigger.MOMENT -> when (call.moment) {
                LiveMoment.KILOMETRE -> listOf(s::kmStreak, s::kmFastest, s::shape, s::kmSlowest, s::kmSteady, s::kmFirst, s::longest)
                LiveMoment.LAP -> listOf(s::lapFastest, s::lapChange, s::lapSteady)
                LiveMoment.HALFWAY -> listOf(s::longest, s::previousRun, s::week)
                LiveMoment.FINAL_STRETCH -> listOf(s::finalStretch)
                LiveMoment.ONE_KM_LEFT -> listOf(s::lastKilometre, s::finalStretch)
                LiveMoment.GOAL_REACHED -> listOf(s::goalBest)
                null -> emptyList()
            }
            // Nearly home: only the finish matters, or nothing.
            LiveTrigger.CADENCE -> if (s.nearlyThere) listOf(s::paceJumped, s::finalStretch)
            // Something that just changed outranks the rotating angle.
            else listOf(s::paceJumped, s::longest) + when (call.focus) {
                LiveFocus.PACE_TREND -> listOf(s::shape, s::paceMoved, s::paceSteady, s::paceNow)
                LiveFocus.TECHNIQUE -> listOf(s::technique)
                LiveFocus.HISTORY -> listOf(s::previousRun, s::week, s::comeback)
                LiveFocus.MORALE -> listOf(s::bestKilometre, s::kmSteady, s::timeOnFeet)
                LiveFocus.GOAL_PROJECTION -> listOf(s::stage, s::remaining, s::projection)
                LiveFocus.EFFORT_CHECK -> listOf(s::effortCheck)
                LiveFocus.SPLIT_DETAIL -> listOf(s::shape, s::kmSpread, s::lapSteady)
                LiveFocus.ASPIRATION -> listOf(s::aspiration, s::week)
            } + listOf(s::earlyPace, s::nextKilometre, s::shape, s::paceNow, s::technique)
        }
        val fresh = order.mapNotNull { it() }.filter { it.id !in used }
        val recent = used.takeLast(2).map(LivePoint::themeOf)
        return fresh.firstOrNull { it.theme !in recent } ?: fresh.firstOrNull().takeIf { call.trigger != LiveTrigger.MOMENT }
    }

    private class State(val run: JSONObject, val context: JSONObject, val say: LiveSpeech) {
        val moving = run.number("moving_seconds") ?: 0.0
        val km = run.number("distance_km") ?: 0.0
        val average = run.number("average_pace_seconds_per_km")
        val recent = run.number("recent_pace_seconds_per_km")
        val splits = run.optJSONArray("splits").objects().filter { it.optBoolean("is_full_kilometre") }.mapNotNull { it.number("pace_seconds_per_km") }
        val laps = run.optJSONArray("estimated_repeated_laps").objects().mapNotNull { it.number("duration_seconds") }
        val goal: JSONObject? = context.optJSONObject("active_goal")
        val intent = goal?.optString("intended_effort").orEmpty()
        val easy = intent == "EASY" || intent == "RECOVERY"
        val distanceGoalKm = goal?.number("distance_goal_m")?.div(1000)
        val timeGoal = goal?.number("duration_goal_seconds")
        val progress = distanceGoalKm?.let { km / it } ?: timeGoal?.let { moving / it }
        val nearlyThere = progress?.let { it >= .9 && it < 1 } == true
        val today: LocalDate? = runCatching { LocalDate.parse(context.optString("today")) }.getOrNull()
        val earlier: List<Pair<LocalDate, JSONObject>> = context.optJSONArray("recent_runs").objects()
            .filter { it.optString("activity_type") == run.optString("activity_type") && (it.number("distance_km") ?: 0.0) >= .3 }
            .mapNotNull { r -> runCatching { LocalDate.parse(r.optString("date")) }.getOrNull()?.takeIf { today != null && it <= today }?.let { it to r } }
        val previousPace = earlier.firstOrNull()?.second?.number("average_pace_seconds_per_km")
        /** Coarse time buckets let a moving observation come back later in a long run, but not twice in a row. */
        private fun every(minutes: Int) = (moving / 60 / minutes).toInt()
        private val phase get() = (progress ?: (moving / 2400)).let { if (it < .25) 0 else if (it < .7) 1 else 2 }

        fun milestone(m: JSONObject): LivePoint? {
            val f = m.optJSONObject("facts")
            return when (m.optString("kind")) {
                "KILOMETRE" -> {
                    val n = m.optInt("number")
                    val time = m.number("split_seconds") ?: splits.getOrNull(n - 1)
                    val before = m.number("previous_split_seconds") ?: splits.getOrNull(n - 2)
                    val delta = if (time != null && before != null) (time - before).roundToInt() else null
                    val known = splits.take(n)
                    val numbers = linkedMapOf<String, String>()
                    time?.let { numbers["time_of_this_kilometre"] = say.time(it) }
                    val (insight, hint) = when {
                        known.size >= 3 && known.zipWithNext().all { (a, b) -> b < a - 2 } -> "and every kilometre so far has been faster than the one before it" to
                            (if (easy) "Admire the control; an easy day does not need to keep getting faster." else "A textbook progressive run; say so.")
                        n >= 2 && time != null && known.dropLast(1).minOrNull()?.let { time < it - 3 } == true -> "and it is the fastest kilometre of the run so far" to
                            (if (easy) "Be glad with them; no need to chase another one." else "Be glad with them, and point at the next kilometre.")
                        delta != null && delta >= 5 -> { numbers["it_took_this_much_longer_than_the_previous_kilometre"] = say.seconds(delta)
                            "and it took longer than the previous kilometre" to "Say that plainly, make it normal, then give this one cue: ${RESETS[n % RESETS.size]}" }
                        delta != null && delta <= -5 -> { numbers["it_was_this_much_quicker_than_the_previous_kilometre"] = say.seconds(-delta)
                            "and it was quicker than the previous kilometre" to "Be glad with them." }
                        delta != null -> "and it took almost exactly the same time as the previous kilometre" to "Even pacing is a skill; name it."
                        n == 1 && time != null && previousPace != null && abs(time - previousPace) >= 5 ->
                            "and it was ${if (time < previousPace) "quicker" else "gentler"} than the average pace of their previous run" to "A first kilometre sets the tone; say what this one sets up."
                        else -> "" to "Tell them what the next kilometre is for: settling into this rhythm."
                    }
                    LivePoint("moment/km:$n", "Kilometre $n is done" + (if (insight.isEmpty()) ". Announce it with its time." else ", $insight. Say both: its time, and what it shows."), numbers, hint)
                }
                "LAP" -> {
                    val n = m.optInt("number")
                    val numbers = linkedMapOf<String, String>()
                    laps.lastOrNull()?.let { numbers["time_of_this_lap"] = say.time(it) }
                    val delta = if (laps.size >= 2) (laps.last() - laps[laps.size - 2]).roundToInt() else null
                    val quarter = m.number("slower_quarter")?.toInt()
                    LivePoint("moment/lap:$n", "Lap $n of the same circuit is done." + when {
                        delta == null -> ""
                        delta <= -4 -> { numbers["faster_than_the_previous_lap_by"] = say.seconds(-delta); " It took less time than the lap before." }
                        delta >= 4 -> { numbers["slower_than_the_previous_lap_by"] = say.seconds(delta); " It took more time than the lap before." }
                        else -> " It took almost exactly the same time as the lap before."
                    } + (quarter?.let { " The ${QUARTERS[(it - 1).coerceIn(0, 3)]} of the circuit took longer than on the previous lap." } ?: ""), numbers,
                        "Announce the lap, then the one thing it shows.")
                }
                "HALFWAY" -> {
                    val numbers = linkedMapOf<String, String>()
                    if (timeGoal != null) { numbers["distance_so_far"] = say.distance(km); f?.number("projected_m")?.let { numbers["estimated_distance_by_the_end"] = say.distance(it / 1000) } }
                    else { numbers["time_so_far"] = say.about(moving); f?.number("projected_ms")?.let { numbers["estimated_finish_time"] = say.about(it / 1000) } }
                    LivePoint("moment/halfway", (if (f?.optBoolean("past_halfway") == true) "They have just passed the halfway point of today's goal." else "They are halfway through today's goal.") +
                        " Announce it with what is done and the estimate for the whole goal at this pace.", numbers, "The second half needs only the same rhythm.")
                }
                "FINAL_STRETCH" -> finalStretch()?.copy(id = "moment/final")
                "ONE_KM_LEFT" -> (lastKilometre() ?: LivePoint("goal/last_km", "One kilometre left to today's goal.", emptyMap(), "Bring it home.")).copy(id = "moment/last_km")
                "GOAL_REACHED" -> {
                    val numbers = linkedMapOf("distance" to say.distance((f?.number("distance_m") ?: (km * 1000)) / 1000), "time" to say.about((f?.number("moving_ms") ?: (moving * 1000)) / 1000))
                    (f?.number("average_pace_seconds") ?: average)?.let { numbers["average_pace"] = say.pace(it) }
                    val best = splits.takeIf { it.size >= 2 }?.let { it.indexOf(it.min()) + 1 }
                    LivePoint("moment/goal", "Today's goal is reached." + when {
                        m.opt("within_time") == true -> " Inside the time they set."
                        m.opt("within_time") == false -> " A little over the time they set."
                        else -> ""
                    } + when {
                        splits.size >= 3 && splits.zipWithNext().all { (a, b) -> b < a - 2 } -> " Every kilometre was faster than the one before."
                        best != null -> " Their quickest kilometre was kilometre $best."
                        else -> ""
                    }, numbers, "This is the moment of the run: celebrate properly, give the total, and tell them to ease down and breathe.")
                }
                else -> null
            }
        }

        fun opening(): LivePoint {
            val numbers = linkedMapOf<String, String>()
            val what = when {
                distanceGoalKm != null -> { numbers["todays_goal"] = say.distance(distanceGoalKm); "Today's plan is to run the goal distance." }
                timeGoal != null -> { numbers["todays_goal"] = say.about(timeGoal); "Today's plan is to run for the goal time." }
                else -> "No set target today: a run by feel."
            }
            val since = earlier.firstOrNull()?.let { ChronoUnit.DAYS.between(it.first, today) }
            val longest = earlier.filter { today != null && it.first >= today.minusDays(29) }.mapNotNull { it.second.number("distance_km") }.maxOrNull()
            val stretch = distanceGoalKm != null && longest != null && distanceGoalKm > longest + .3
            if (since != null && since >= 2) numbers["days_since_the_last_run"] = say.days(since)
            return LivePoint("start/opening", "The run has just started. $what" + when {
                since == null -> ""
                since >= 3 -> " It is the first run in a few days."
                since <= 1L -> " The previous run was very recent."
                else -> ""
            } + (if (stretch) " Today's goal is further than any run of theirs in the last 30 days." else ""), numbers, "Greet them and set the tone for " + (if (easy) "an easy, relaxed run" else "the run") + ". Do not mention or judge pace or distance: it has barely begun.")
        }
        fun earlyPace(): LivePoint? {
            val r = recent ?: return null
            if (moving !in 100.0..420.0 || splits.isNotEmpty()) return null
            val versus = previousPace?.let { (r - it).roundToInt() }
            return LivePoint("pace/early", "The run is a few minutes old and this is the pace they have opened at." + when {
                versus == null || abs(versus) < 8 -> ""
                versus > 0 -> " It is gentler than the average of their previous run."
                else -> " It is quicker than the average of their previous run."
            }, mapOf("opening_pace" to say.pace(r)), when {
                easy -> "A calm opening is exactly right today; if it is quicker than last time, suggest easing off kindly."
                else -> "The first minutes are for settling in, not for winning time."
            })
        }

        fun kmStreak(): LivePoint? {
            if (splits.size < 3 || !splits.zipWithNext().all { (a, b) -> b < a - 2 }) return null
            return LivePoint("faster/km_streak:${splits.size}", "Each of the ${splits.size} kilometres so far has been faster than the one before it.",
                mapOf("first_kilometre_time" to say.time(splits.first()), "latest_kilometre_time" to say.time(splits.last())),
                if (easy) "Admire the control, and remind them an easy day does not need to keep getting faster." else "A textbook progressive run; say so.")
        }
        fun kmFastest(): LivePoint? {
            if (splits.size < 2 || splits.last() > splits.dropLast(1).min() - 3) return null
            return LivePoint("faster/km_fastest:${splits.size}", "The kilometre just completed, kilometre ${splits.size}, is the fastest kilometre of the run so far. The app has already announced its time.",
                emptyMap(),
                if (easy) "Be glad with them; no need to chase another one." else "Be glad with them, and point at the next kilometre.")
        }
        fun kmSteady(): LivePoint? {
            if (splits.size < 2) return null
            val spread = (splits.max() - splits.min()).roundToInt()
            if (spread > 8) return null
            return LivePoint("steady/km:${splits.size}", "All ${splits.size} kilometres so far took almost exactly the same time: very even pacing.",
                emptyMap(), "Even pacing is a skill; name it.")
        }
        fun kmFirst(): LivePoint? {
            val delta = previousPace?.takeIf { splits.size == 1 }?.let { (splits[0] - it).roundToInt() } ?: return null
            if (abs(delta) < 5) return null
            return LivePoint("history/km_first", "The first kilometre was ${if (delta < 0) "quicker" else "gentler"} than the average pace of their previous run.",
                mapOf("difference_per_kilometre" to say.seconds(abs(delta))), if (delta > 0 || easy) "A calm first kilometre is the right way in." else "A lively start; settle into it.")
        }
        fun kmSlowest(): LivePoint? {
            if (splits.size < 2 || splits.last() < splits.dropLast(1).max() + 5) return null
            return LivePoint("slower/km:${splits.size}", "The kilometre just completed was slower than the ones before it. The app has already announced its time.",
                emptyMap(), "Make it normal, not a problem. Give one reset: shoulders down, quick light steps. No pressure to win time back.")
        }
        fun kmSpread(): LivePoint? {
            if (splits.size < 3 || splits.max() - splits.min() <= 8) return kmSteady()
            val fast = splits.indexOf(splits.min()) + 1; val slow = splits.indexOf(splits.max()) + 1
            return LivePoint("splits/spread:${splits.size}", "Of the ${splits.size} kilometres so far, kilometre $fast was the fastest and kilometre $slow the slowest.",
                mapOf("fastest_kilometre_time" to say.time(splits.min()), "slowest_kilometre_time" to say.time(splits.max())),
                if (fast > slow) "They are running stronger now than at the start; say what that means." else "The start was the quick part; holding steady from here is the win.")
        }

        fun lapFastest(): LivePoint? {
            if (laps.size < 2 || laps.last() > laps.dropLast(1).min() - 3) return null
            return LivePoint("faster/lap_fastest:${laps.size}", "The lap just completed, lap ${laps.size} of the same circuit, is the fastest lap so far.",
                mapOf("quicker_than_the_previous_lap_by" to say.seconds((laps[laps.size - 2] - laps.last()).roundToInt().coerceAtLeast(1))), "Same circuit, less time.")
        }
        fun lapChange(): LivePoint? {
            if (laps.size < 2) return null
            val delta = (laps.last() - laps[laps.size - 2]).roundToInt()
            if (abs(delta) < 4) return null
            return LivePoint(if (delta < 0) "faster/lap:${laps.size}" else "slower/lap:${laps.size}", "Lap ${laps.size} of the same circuit took ${if (delta < 0) "less" else "more"} time than the lap before it.",
                mapOf("difference" to say.seconds(abs(delta))), if (delta > 0) "Make it normal; same circuit, so one small reset is enough." else null)
        }
        fun lapSteady(): LivePoint? {
            if (laps.size < 2) return null
            val spread = (laps.max() - laps.min()).roundToInt()
            if (spread > 10) return null
            return LivePoint("steady/lap:${laps.size}", "All ${laps.size} laps of the same circuit took almost exactly the same time.",
                mapOf("gap_between_the_fastest_and_slowest_lap" to say.seconds(spread.coerceAtLeast(1))), "Metronome running; name it.")
        }

        private fun shift(): Triple<String, Double, Int>? {
            val c = run.optJSONObject("recent_pace_comparison") ?: return null
            val now = c.number("recent_pace_seconds_per_km") ?: return null; val before = c.number("previous_pace_seconds_per_km") ?: return null
            return Triple(c.optString("trend"), now, (now - before).roundToInt())
        }
        /**
         * A change of pace worth a word. A coach does not comment on every wobble: on an easy day
         * only real speeding counts; otherwise a late surge is cheered, a real fade gets one cue,
         * and coming back to the usual rhythm after a slow patch is simply good news.
         */
        private fun moved(minimum: Int): LivePoint? {
            val (trend, now, delta) = shift() ?: return null
            val a = average ?: return null
            if (abs(delta) < minimum) return null
            val pace = mapOf("pace_now" to say.pace(now))
            val late = (progress ?: 0.0) >= .8
            return when {
                trend == "FASTER" && easy -> if (now > a - 15) null else LivePoint("faster/pace_easy",
                    "Over the last couple of minutes they have sped up and are now clearly faster than their average for this run.", pace, "Today is an easy day: suggest easing back a touch, kindly, once.")
                trend == "FASTER" && late -> LivePoint("faster/surge", "They are speeding up as the end of today's goal gets close.", pace, "Cheer the surge and tell them to go with it.")
                trend == "FASTER" && now <= a - 10 -> LivePoint("faster/pace:${every(10)}", "Over the last couple of minutes they have sped up and are now faster than their average for this run.", pace,
                    "Be glad about it, and have them check in their own head that it feels sustainable.")
                trend == "FASTER" -> LivePoint("steady/back:${every(10)}", "After a slower patch they are back at their usual rhythm for this run.", pace, "Good news; say so.")
                trend == "SLOWER" && !easy && now >= a + 10 -> LivePoint("slower/pace:${every(10)}", "Over the last couple of minutes they have slowed and are now slower than their average for this run.", pace,
                    "Tell them it happens, and give this one cue: ${RESETS[every(10) % RESETS.size]}")
                else -> null
            }
        }
        fun paceJumped(): LivePoint? = moved(25)
        fun paceMoved(): LivePoint? = moved(12)
        /** How the run is developing over kilometres, not minutes: fading or building. */
        fun shape(): LivePoint? {
            if (splits.size < 4) return null
            val late = splits.takeLast(2).average(); val before = splits.dropLast(2).average()
            val delta = (late - before).roundToInt()
            if (abs(delta) < 10) return null
            // Old news once they are surging again or nearly home.
            if (delta > 0 && ((progress ?: 0.0) >= .9 || recent?.let { it < late - 10 } == true)) return null
            val numbers = mapOf("average_of_the_last_two_kilometres" to say.pace(late), "average_of_the_kilometres_before" to say.pace(before))
            return if (delta > 0) LivePoint("slower/shape:${splits.size / 2}", "Their last two kilometres were slower than the kilometres before them.", numbers,
                if (easy) "No problem today; ask only for good form." else "Say it plainly and kindly, then give one job for the next kilometre: win back the rhythm, not the time.")
            else LivePoint("faster/shape:${splits.size / 2}", "Their last two kilometres were faster than the kilometres before them: the run is building.", numbers,
                if (easy) "Be glad with them, and keep it comfortable." else "They are getting stronger as it goes; say what that sets up for the finish.")
        }
        fun bestKilometre(): LivePoint? {
            if (splits.size < 2) return null
            val best = splits.indexOf(splits.min()) + 1
            return LivePoint("best/km:$best", "Their fastest kilometre so far today is kilometre $best.", mapOf("time_of_that_kilometre" to say.time(splits.min())), "Remind them what they have already shown today.")
        }
        fun timeOnFeet(): LivePoint? {
            if (moving < 600) return null
            return LivePoint("banked/time:${every(10)}", "They have been running without stopping for the time below.", mapOf("running_time" to say.about(moving)), "Name it as something already achieved.")
        }
        fun stage(): LivePoint? {
            val p = progress ?: return null
            val left = distanceGoalKm?.let { say.distance(it - km) } ?: timeGoal?.let { say.about(it - moving) } ?: return null
            return when {
                p >= .25 && p < .45 -> LivePoint("goal/stage1", "The opening part of today's goal is done.", mapOf("done_so_far" to say.percent((p * 100).roundToInt())), "Settling in is over; the middle is for holding this rhythm.")
                p >= .62 && p < .78 -> LivePoint("goal/stage2", "The last third of today's goal has started.", mapOf("left_to_go" to left),
                    if (easy) "Bring it home the same relaxed way." else "This is the part that counts: hold the rhythm now and keep something for the last kilometre.")
                else -> null
            }
        }
        fun paceSteady(): LivePoint? {
            val (trend, now, _) = shift() ?: return null
            if (trend != "SIMILAR") return null
            return LivePoint("steady/pace:${every(6)}", "The pace of the last couple of minutes matches the minutes before it: locked in.", mapOf("pace_now" to say.pace(now)), "Steady is the goal; say so.")
        }
        fun paceNow(): LivePoint? {
            val r = recent ?: return null; val a = average ?: return null
            if (moving < 420) return null
            val delta = (r - a).roundToInt()
            return LivePoint("pace/now:${every(6)}", "Right now they are running " + when {
                delta <= -6 -> "faster than their average for this run."
                delta >= 6 -> "more gently than their average for this run."
                else -> "right on their average for this run."
            }, mapOf("current_pace_not_the_average" to say.pace(r)))
        }
        fun nextKilometre(): LivePoint? {
            val left = 1 - (km - kotlin.math.floor(km))
            if (left < .15 || left > .6 || km < .3) return null
            val next = kotlin.math.floor(km).toInt() + 1
            return LivePoint("goal/next_km:$next", "Kilometre $next is coming up soon.", mapOf("distance_left_to_kilometre_$next" to say.distance(left)), "Give them that small target to run to.")
        }
        fun remaining(): LivePoint? {
            val p = progress ?: return null
            if (p >= 1 || p < .3) return null
            val left = distanceGoalKm?.let { say.distance(it - km) } ?: timeGoal?.let { say.about(it - moving) } ?: return null
            return LivePoint("goal/remaining:${(p * 5).toInt()}", "What is left of today's goal.", mapOf("left_to_go" to left), when {
                p < .5 -> "More than half is still ahead: make it sound comfortably doable."
                p < .75 -> "Less is left than is done."
                else -> "Nearly there: bring it home with good form."
            })
        }
        fun projection(): LivePoint? {
            val a = average ?: return null
            if (km < .8 || (progress ?: return null) >= 1) return null
            return when {
                distanceGoalKm != null -> LivePoint("goal/projection:${every(12)}", "At their average pace so far, today's goal distance would be done in about the time below.",
                    mapOf("estimated_finish_time" to say.about(a * distanceGoalKm)), "An estimate, not a promise.")
                timeGoal != null -> LivePoint("goal/projection:${every(12)}", "At their average pace so far, today's goal time would add up to about the distance below.",
                    mapOf("estimated_distance_by_the_end" to say.distance(km / moving * timeGoal)), "An estimate, not a promise.")
                else -> null
            }
        }
        fun finalStretch(): LivePoint? {
            val left = distanceGoalKm?.takeIf { it - km > 0 }?.let { say.distance(it - km) } ?: timeGoal?.takeIf { it - moving > 0 }?.let { say.about(it - moving) } ?: return null
            return LivePoint("goal/final:${every(3)}", "The final stretch of today's goal.", mapOf("left_to_go" to left),
                if (easy) "Finish tall, light and smiling." else "Finish tall and strong; if it feels good they can lift the rhythm a little.")
        }
        fun lastKilometre(): LivePoint? {
            if (splits.isEmpty()) return null
            val best = splits.indexOf(splits.min()) + 1
            return LivePoint("goal/last_km", "One kilometre left. Their quickest kilometre today was kilometre $best.", mapOf("quickest_kilometre_time" to say.time(splits.min())),
                if (easy) "No need to beat it: glide this one in." else "Invite them to match it if the legs say yes.")
        }
        fun goalBest(): LivePoint {
            if (splits.size >= 3 && splits.zipWithNext().all { (a, b) -> b < a - 2 }) return LivePoint("goal/reached", "Goal reached, and every kilometre was faster than the one before.", emptyMap(), "Celebrate properly.")
            if (splits.size >= 2) {
                val best = splits.indexOf(splits.min()) + 1
                return LivePoint("goal/reached", "Goal reached. Their quickest kilometre was kilometre $best.", mapOf("quickest_kilometre_time" to say.time(splits.min())), "Celebrate properly, then tell them to ease down and breathe.")
            }
            return LivePoint("goal/reached", "Goal reached.", emptyMap(), "Celebrate properly.")
        }

        fun previousRun(): LivePoint? {
            val (date, _) = earlier.firstOrNull() ?: return null
            val pace = previousPace ?: return null; val a = average ?: return null
            if (km < .8) return null
            val delta = (a - pace).roundToInt()
            val numbers = linkedMapOf("their_previous_run_was" to say.ago(say.days(ChronoUnit.DAYS.between(date, today))))
            if (abs(delta) >= 5) numbers["difference_per_kilometre"] = say.seconds(abs(delta))
            return LivePoint("history/previous_run", "Compared with their previous run, today's average pace so far is " + when {
                delta <= -5 -> "faster."; delta >= 5 -> "gentler."; else -> "almost identical."
            }, numbers, when {
                easy && delta >= 5 -> "Gentler is right for today."
                else -> null
            })
        }
        fun longest(): LivePoint? {
            val longest = earlier.filter { today != null && it.first >= today.minusDays(29) }.mapNotNull { it.second.number("distance_km") }.maxOrNull() ?: return null
            return when {
                km > longest + .05 -> LivePoint("history/longest_passed", "This run has just become their longest of the last 30 days.", mapOf("the_longest_until_today" to say.distance(longest)), "New ground: every step from here is a bonus.")
                longest - km in .1..1.0 -> LivePoint("history/longest_near", "A little further and this becomes their longest run of the last 30 days.", mapOf("distance_left_for_that" to say.distance(longest - km)),
                    if (easy) "Mention it lightly; no need to chase it on an easy day." else "A small target to reach for.")
                else -> null
            }
        }
        fun week(): LivePoint? {
            val t = today ?: return null
            val week = earlier.filter { it.first >= t.minusDays(6) }
            if (week.isEmpty() || km < .5) return null
            val total = week.sumOf { it.second.number("distance_km") ?: 0.0 } + km
            return LivePoint("history/week:${every(20)}", "Counting today, their last seven days add up to the distance below, over ${week.size + 1} runs.", mapOf("seven_day_total" to say.distance(total)),
                "Consistency is what builds a runner; that total is it.")
        }
        fun comeback(): LivePoint? {
            val days = earlier.firstOrNull()?.let { ChronoUnit.DAYS.between(it.first, today) } ?: return null
            if (days < 3) return null
            return LivePoint("history/comeback", "This is their first run in several days.", mapOf("days_since_the_last_run" to say.days(days)), "Glad they are back out.")
        }
        fun aspiration(): LivePoint? {
            val text = context.optJSONObject("runner_goal")?.optString("text").orEmpty().trim()
            if (text.isEmpty() || km < .5) return null
            val numbers = linkedMapOf<String, String>()
            monthsUntil(text, today)?.let { numbers["time_until_it"] = say.months(it) }
            return LivePoint("bigger/aspiration:${every(25)}", "Their own bigger goal, in their words: \"${text.take(160)}\". Today's run is one of the building blocks for it.", numbers,
                "Connect today to it in one warm sentence. No predictions about that day.")
        }

        fun technique(): LivePoint {
            val topics = TECHNIQUE[phase]
            val index = every(2) % topics.size
            val numbers = if (moving >= 480) mapOf("time_they_have_been_running" to say.about(moving)) else emptyMap()
            return LivePoint("form/$phase:$index", "A form cue to give now: ${topics[index]}", numbers,
                "One cue only, as a friendly instruction" + (if (numbers.isEmpty()) "." else ", tied to how long they have been running.") + " Do not say how their body is doing: you cannot see it.")
        }
        fun effortCheck(): LivePoint {
            val checks = if (easy || goal == null) EASY_CHECKS else STEADY_CHECKS
            val index = every(6) % checks.size
            return LivePoint("effort/$index", "Invite a quick self-check: ${checks[index]}", emptyMap(),
                "Phrase it as a question or a 'check that...' they settle in their own head. You do not know the answer, so never state it. Never ask them to tell you.")
        }
    }

    private val TECHNIQUE = listOf(
        listOf("drop the shoulders away from the ears and keep the hands soft.", "breathe low and steady, letting the breath follow the steps.", "run tall, chest open, eyes on the path ahead rather than on the feet.", "let the arms swing forward and back, close to the body."),
        listOf("short, quick, light steps instead of long strides.", "land with each foot under the hips, not out in front.", "relax the jaw and the face; the rest of the body follows.", "drive the arms straight back, elbows bent, hands loose.", "stay tall through the hips, as if a string lifted the crown of the head."),
        listOf("late in a run posture tends to sag: hips forward, run tall.", "land softly and quietly, as if not wanting to be heard.", "shake out the hands and drop the shoulders once more.", "keep the steps quick and light all the way in."),
    )
    private val QUARTERS = listOf("first quarter", "second quarter", "third quarter", "last quarter")
    private val RESETS = listOf("quick, light steps.", "drop the shoulders and let the arms set the rhythm.", "run tall and look ahead.", "breathe out fully and settle.")
    private val EASY_CHECKS = listOf("could they say a full sentence out loud right now without gasping?", "could they breathe through the nose for a few steps?", "does this feel like a pace they could hold for a long time yet?")
    private val STEADY_CHECKS = listOf("is the breathing strong but still under control?", "are the shoulders and hands still relaxed at this effort?", "does this effort feel like one they can hold to the end?")

    /** "abril de 2027" or "April 2027" in the runner's aspiration: whole months from [today], when it can be read. */
    internal fun monthsUntil(text: String, today: LocalDate?): Int? {
        val now = today ?: return null
        val lower = text.lowercase(java.util.Locale.ROOT)
        // A month counts only next to a year or after "en/in/de/of", so the verb "may" is not a date.
        val month = MONTHS.entries.firstOrNull { (name, _) ->
            Regex("\\b(?:en|in|de|of)\\s+$name\\b|\\b$name\\b(?:\\s+de)?\\s+20\\d{2}").containsMatchIn(lower) }?.value ?: return null
        val year = Regex("\\b(20\\d{2})\\b").find(lower)?.groupValues?.get(1)?.toInt() ?: (if (month >= now.monthValue) now.year else now.year + 1)
        val months = (year - now.year) * 12 + (month - now.monthValue)
        return months.takeIf { it in 1..36 }
    }
    private val MONTHS = listOf(listOf("enero", "january"), listOf("febrero", "february"), listOf("marzo", "march"), listOf("abril", "april"), listOf("mayo", "may"), listOf("junio", "june"),
        listOf("julio", "july"), listOf("agosto", "august"), listOf("septiembre", "september", "setiembre"), listOf("octubre", "october"), listOf("noviembre", "november"), listOf("diciembre", "december"))
        .flatMapIndexed { index, names -> names.map { it to index + 1 } }.toMap()

    private fun JSONObject.number(name: String): Double? = (opt(name) as? Number)?.toDouble()?.takeIf { it.isFinite() }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
