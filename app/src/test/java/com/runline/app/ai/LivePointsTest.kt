package com.runline.app.ai

import com.runline.app.core.CoachCue
import com.runline.app.core.CoachFacts
import com.runline.app.core.EncouragementKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LivePointsTest {
    private fun run(
        vararg splits: Double,
        moving: Double = splits.sum() + 120,
        extraKm: Double = .3,
        recent: Double? = null,
        shift: Triple<String, Double, Double>? = null,
        laps: List<Double> = emptyList(),
    ) = JSONObject()
        .put("activity_type", "run")
        .put("moving_seconds", moving)
        .put("distance_km", splits.size + extraKm)
        .put("average_pace_seconds_per_km", moving / (splits.size + extraKm))
        .put("recent_pace_seconds_per_km", recent ?: JSONObject.NULL)
        .put("recent_pace_comparison", shift?.let { JSONObject().put("trend", it.first).put("recent_pace_seconds_per_km", it.second).put("previous_pace_seconds_per_km", it.third) } ?: JSONObject.NULL)
        .put(
            "splits",
            JSONArray(
                splits.mapIndexed { i, s ->
                    JSONObject()
                        .put("number", i + 1)
                        .put("distance_m", 1000)
                        .put("pace_seconds_per_km", s)
                        .put("is_full_kilometre", true)
                },
            ),
        ).put("estimated_repeated_laps", JSONArray(laps.map { JSONObject().put("duration_seconds", it).put("distance_m", 970) }))

    private fun context(
        goal: JSONObject? = null,
        aspiration: String? = null,
        vararg earlier: Triple<String, Double, Double>,
    ) = JSONObject()
        .put("today", "2026-11-10")
        .put("active_goal", goal ?: JSONObject.NULL)
        .put(
            "recent_runs",
            JSONArray(
                earlier.map {
                    JSONObject()
                        .put("date", it.first)
                        .put("activity_type", "run")
                        .put("distance_km", it.second)
                        .put("average_pace_seconds_per_km", it.third)
                },
            ),
        ).apply { if (aspiration != null) put("runner_goal", JSONObject().put("text", aspiration)) }

    private fun timeGoal(
        minutes: Int,
        effort: String? = null,
    ) = JSONObject().put("type", "TIME").put("duration_goal_seconds", minutes * 60).put("intended_effort", effort ?: JSONObject.NULL)

    private fun distanceGoal(km: Int) = JSONObject().put("type", "DISTANCE").put("distance_goal_m", km * 1000)

    private fun cadence(focus: LiveFocus) = LiveCall(LiveTrigger.CADENCE, null, focus, 3)

    private val km = LiveCall(LiveTrigger.MOMENT, LiveMoment.KILOMETRE, LiveFocus.SPLIT_DETAIL, 3)

    @Test fun `figures are written the way they are spoken, in both languages`() {
        val es = LiveSpeech(true)
        val en = LiveSpeech(false)
        assertEquals("7 minutos 41 por kilómetro", es.pace(461.1))
        assertEquals("7 minutes 41 per kilometre", en.pace(461.1))
        assertEquals("6 minutos", es.time(360.0))
        assertEquals("45 segundos", es.time(45.0))
        assertEquals("1 second", en.seconds(1))
        assertEquals("13 minutos", es.about(788.0))
        assertEquals("1 hora 3 minutos", es.about(3765.0))
        assertEquals("2 hours", en.about(7200.0))
        assertEquals("2,2 kilómetros", es.distance(2.21))
        assertEquals("2.2 kilometres", en.distance(2.21))
        assertEquals("1 kilómetro", es.distance(1.04))
        assertEquals("400 metros", es.distance(.41))
        assertEquals("50 metres", en.distance(.01))
        assertEquals("10 kilómetros", es.distance(10.0))
        assertEquals("hace 4 días", es.ago(es.days(4)))
        assertEquals("1 day ago", en.ago(en.days(1)))
        assertEquals("5 meses", es.months(5))
    }

    @Test fun `a kilometre is announced with the app's own time and what it shows`() {
        val cue = LiveMilestone.of(CoachCue.Kilometre(3, 455.0, splitPaceSeconds = 441.6, previousSplitPaceSeconds = 460.9))
        val streak = LivePoints.pick(run(466.3, 460.9, 441.6), context(), km, true, milestone = cue)!!
        assertEquals("moment/km:3", streak.id)
        assertEquals(mapOf("time_of_this_kilometre" to "7 minutos 22"), streak.numbers)
        assertTrue(streak.point.contains("every kilometre so far has been faster"))
        val fastest = LivePoints.pick(run(450.0, 466.0, 441.6), context(), km, true, milestone = cue)!!
        assertTrue(fastest.point.contains("fastest kilometre of the run so far"))
        val slower =
            LivePoints.pick(
                run(450.0, 440.0, 471.0),
                context(),
                km,
                false,
                milestone = LiveMilestone.of(CoachCue.Kilometre(3, 455.0, splitPaceSeconds = 471.0, previousSplitPaceSeconds = 440.0)),
            )!!
        assertEquals("31 seconds", slower.numbers["it_took_this_much_longer_than_the_previous_kilometre"])
        assertTrue(slower.hint!!.contains("normal"))
        val even = LivePoints.pick(run(461.0, 463.0), context(), km, true, milestone = LiveMilestone.of(CoachCue.Kilometre(2, 462.0, 463.0, 461.0)))!!
        assertTrue(even.point.contains("almost exactly the same time"))
        assertEquals(1, even.numbers.size)
        val first = LiveMilestone.of(CoachCue.Kilometre(1, 464.0, splitPaceSeconds = 464.0))
        assertFalse(LivePoints.pick(run(464.0), context(), km, true, milestone = first)!!.point.contains("previous"))
        assertTrue(LivePoints.pick(run(464.0), context(null, null, Triple("2026-11-06", 4.7, 380.0)), km, true, milestone = first)!!.point.contains("gentler than the average pace of their previous run"))
    }

    @Test fun `halfway and the goal carry the totals the built-in announcement would give`() {
        val facts = CoachFacts(2205.0, 1_012_000, 459.0, 440.0, remainingMs = 788_000, projectedMeters = 3_920.0)
        val half =
            LivePoints.pick(
                run(466.0, 461.0, moving = 1012.0, extraKm = .2),
                context(timeGoal(30)),
                LiveCall(LiveTrigger.MOMENT, LiveMoment.HALFWAY, LiveFocus.GOAL_PROJECTION, 5),
                true,
                milestone = LiveMilestone.of(CoachCue.Encouragement(EncouragementKind.HALFWAY, facts)),
            )!!
        assertEquals(mapOf("distance_so_far" to "2,2 kilómetros", "estimated_distance_by_the_end" to "3,9 kilómetros"), half.numbers)
        assertTrue(half.point.startsWith("They have just passed the halfway point"))
        assertTrue(facts.pastHalfway)
        assertFalse(CoachFacts(1500.0, 900_000, remainingMs = 900_000).pastHalfway)
        assertTrue(CoachFacts(6000.0, 1, remainingMeters = 4000.0).pastHalfway)
        val goal =
            LivePoints.pick(
                run(371.0, 362.0, 354.0, moving = 3722.0, extraKm = 7.0),
                context(distanceGoal(10)),
                LiveCall(LiveTrigger.MOMENT, LiveMoment.GOAL_REACHED, LiveFocus.MORALE, 30),
                true,
                milestone = LiveMilestone.of(CoachCue.GoalReached(null, CoachFacts(10_000.0, 3_722_000, 372.2))),
            )!!
        assertEquals("10 kilómetros", goal.numbers["distance"])
        assertEquals("1 hora 2 minutos", goal.numbers["time"])
        assertEquals("6 minutos 12 por kilómetro", goal.numbers["average_pace"])
        assertTrue(goal.point.contains("Every kilometre was faster"))
        assertNull(LiveMilestone.of(CoachCue.Encouragement(EncouragementKind.PROGRESS)))
        assertNull(LiveMilestone.of(CoachCue.SlowerThanTarget))
    }

    @Test fun `a kilometre or lap back to the usual rhythm after a burst is good news, not a big drop`() {
        val splits = doubleArrayOf(433.6, 418.5, 397.0, 386.4, 332.4, 415.8)
        val cue = LiveMilestone.of(CoachCue.Kilometre(6, 400.0, splitPaceSeconds = 415.8, previousSplitPaceSeconds = 332.4))
        val easy = context(distanceGoal(5).put("intended_effort", "EASY"))
        val eased = LivePoints.pick(run(*splits), easy, km, true, milestone = cue)!!
        assertTrue(eased.point, eased.point.contains("unusually quick kilometre, and it is slower than their kilometres before"))
        assertEquals(mapOf("time_of_this_kilometre" to "6 minutos 56", "slower_than_their_recent_kilometres_by" to "19 segundos"), eased.numbers)
        assertTrue(eased.hint!!.contains("never with the quick one"))
        val back =
            LivePoints.pick(
                run(400.0, 402.0, 398.0, 401.0, 340.0, 405.0),
                easy,
                km,
                true,
                milestone = LiveMilestone.of(CoachCue.Kilometre(6, 392.0, splitPaceSeconds = 405.0, previousSplitPaceSeconds = 340.0)),
            )!!
        assertTrue(back.point, back.point.contains("back at their recent rhythm"))
        assertEquals(setOf("time_of_this_kilometre"), back.numbers.keys)
        assertTrue(back.hint!!.contains("easy"))
        val held =
            LivePoints.pick(
                run(398.0, 360.0, 358.0, 409.0),
                context(),
                km,
                true,
                milestone = LiveMilestone.of(CoachCue.Kilometre(4, 381.0, splitPaceSeconds = 409.0, previousSplitPaceSeconds = 358.0)),
            )!!
        assertEquals("51 segundos", held.numbers["it_took_this_much_longer_than_the_previous_kilometre"])
        val drop =
            LivePoints.pick(
                run(400.0, 402.0, 398.0, 401.0, 440.0),
                context(),
                km,
                true,
                milestone = LiveMilestone.of(CoachCue.Kilometre(5, 408.0, splitPaceSeconds = 440.0, previousSplitPaceSeconds = 401.0)),
            )!!
        assertEquals("39 segundos", drop.numbers["it_took_this_much_longer_than_the_previous_kilometre"])
        assertTrue(drop.hint!!.contains("without calling the difference small"))
        val lap =
            LivePoints.pick(
                run(*splits, laps = listOf(416.6, 433.0, 386.0, 340.0, 419.0)),
                context(),
                LiveCall(LiveTrigger.MOMENT, LiveMoment.LAP, LiveFocus.SPLIT_DETAIL, 4),
                true,
                milestone = JSONObject().put("kind", "LAP").put("number", 5).put("slower_quarter", 3),
            )!!
        assertTrue(lap.point, lap.point.contains("back at their recent lap time"))
        assertFalse(lap.point.contains("quarter"))
        assertEquals(setOf("time_of_this_lap"), lap.numbers.keys)
    }

    @Test fun `past the goal the coach says what the extra running is for`() {
        val easy = context(distanceGoal(5).put("intended_effort", "EASY"))
        val beyond = LivePoints.pick(run(433.0, 418.0, 397.0, 386.0, 332.0, extraKm = .6), easy, cadence(LiveFocus.MORALE), true)!!
        assertEquals("goal/beyond:4", beyond.id)
        assertEquals(mapOf("beyond_the_goal" to "600 metros"), beyond.numbers)
        assertTrue(beyond.hint!!.contains("cool-down"))
        assertFalse(LivePoints.pick(run(433.0, 418.0, 397.0, 386.0, extraKm = .5), easy, cadence(LiveFocus.MORALE), true)!!.id.startsWith("goal/beyond"))
    }

    @Test fun `a fact one line already gave is not given again by another`() {
        val splits = doubleArrayOf(433.6, 418.5, 397.0, 386.4)
        val last =
            LivePoints.pick(
                run(*splits),
                context(distanceGoal(5)),
                LiveCall(LiveTrigger.MOMENT, LiveMoment.ONE_KM_LEFT, LiveFocus.GOAL_PROJECTION, 5),
                true,
                milestone = JSONObject().put("kind", "ONE_KM_LEFT"),
            )!!
        assertEquals(listOf("best/km:4"), last.covers)
        val line = LiveLine("¡Último kilómetro!", last)
        assertEquals(listOf("moment/last_km", "best/km:4"), line.pointIds)
        val next = LivePoints.pick(run(*splits, extraKm = .5), context(distanceGoal(5)), cadence(LiveFocus.MORALE), true, line.pointIds)
        assertNotEquals("best/km:4", next?.id)
    }

    @Test fun `each observation is offered once and a theme never twice running`() {
        val data = run(461.0, 463.0, 462.0, recent = 462.0)
        val ctx = context(timeGoal(60))
        val first = LivePoints.pick(data, ctx, cadence(LiveFocus.MORALE), true)!!
        assertEquals("best/km:1", first.id)
        val second = LivePoints.pick(data, ctx, cadence(LiveFocus.MORALE), true, listOf(first.id))!!
        assertEquals("steady/km:3", second.id)
        assertTrue(second.numbers.isEmpty())
        val third = LivePoints.pick(data, ctx, cadence(LiveFocus.PACE_TREND), true, listOf(first.id, second.id))!!
        assertNotEquals("steady", third.theme)
        assertEquals("steady", LivePoint.themeOf("steady/pace:3"))
    }

    @Test fun `a routine check-in always has something to say and a form cue never claims how the body feels`() {
        LiveFocus.entries.forEach { focus ->
            val point = LivePoints.pick(run(moving = 200.0, extraKm = .4), context(), cadence(focus), true)
            assertNotNull(focus.name, point)
        }
        val cue = LivePoints.pick(run(moving = 200.0, extraKm = .4), context(), cadence(LiveFocus.TECHNIQUE), false)!!
        assertEquals("form", cue.theme)
        assertTrue(cue.numbers.isEmpty())
        assertTrue(cue.hint!!.contains("you cannot see it"))
        val check = LivePoints.pick(run(461.0, moving = 600.0), context(), cadence(LiveFocus.EFFORT_CHECK), false)!!
        assertEquals("effort", check.theme)
        assertTrue(check.hint!!.contains("Never ask them to tell you"))
    }

    @Test fun `pace changes are mentioned with judgement, not every time they wobble`() {
        fun shifted(
            trend: String,
            now: Double,
            before: Double,
            ctx: JSONObject,
            moving: Double = 1500.0,
        ) = LivePoints.pick(run(450.0, 450.0, 450.0, moving = moving, recent = now, shift = Triple(trend, now, before)), ctx, cadence(LiveFocus.TECHNIQUE), true)!!
        val easy = context(timeGoal(60, "EASY"))
        assertEquals("form", shifted("SLOWER", 490.0, 440.0, easy).theme)
        assertEquals("form", shifted("FASTER", 440.0, 480.0, easy).theme)
        val nag = shifted("FASTER", 410.0, 450.0, easy)
        assertEquals("faster/pace_easy:2", nag.id)
        assertEquals("6 minutos 50 por kilómetro", nag.numbers["pace_now"])
        assertEquals("form", LivePoints.pick(run(450.0, 450.0, 450.0, moving = 1500.0, recent = 410.0, shift = Triple("FASTER", 410.0, 450.0)), easy, cadence(LiveFocus.TECHNIQUE), true, listOf(nag.id))!!.theme)
        assertEquals("faster/pace_easy:3", shifted("FASTER", 410.0, 450.0, easy, moving = 2100.0).id)
        val open = context(timeGoal(60))
        assertEquals("slower", shifted("SLOWER", 480.0, 440.0, open).theme)
        assertEquals("steady/back", shifted("FASTER", 452.0, 490.0, open).id.substringBefore(':'))
        assertEquals("faster/surge", shifted("FASTER", 420.0, 450.0, context(timeGoal(30)), moving = 1500.0).id)
        assertEquals("form", shifted("FASTER", 430.0, 445.0, open).theme)
    }

    @Test fun `the shape of the run is named over kilometres and dropped once it is old news`() {
        val fading = LivePoints.pick(run(360.0, 362.0, 380.0, 384.0, recent = 385.0), context(distanceGoal(10)), cadence(LiveFocus.SPLIT_DETAIL), false)!!
        assertEquals("slower/shape:2", fading.id)
        assertEquals("6 minutes 22 per kilometre", fading.numbers["average_of_the_last_two_kilometres"])
        val building = LivePoints.pick(run(380.0, 384.0, 362.0, 360.0, recent = 360.0), context(distanceGoal(10)), cadence(LiveFocus.SPLIT_DETAIL), false)!!
        assertEquals("faster/shape:2", building.id)
        assertNotEquals("slower", LivePoints.pick(run(360.0, 362.0, 380.0, 384.0, recent = 350.0), context(distanceGoal(10)), cadence(LiveFocus.SPLIT_DETAIL), false)!!.theme)
    }

    @Test fun `near the end only the finish is worth a word`() {
        val nearly = run(360.0, 360.0, 360.0, 360.0, 360.0, 360.0, 360.0, 360.0, 360.0, moving = 3300.0, extraKm = .5, recent = 360.0)
        val point = LivePoints.pick(nearly, context(distanceGoal(10)), cadence(LiveFocus.EFFORT_CHECK), true)!!
        assertEquals("goal", point.theme)
        assertEquals("500 metros", point.numbers["left_to_go"])
        assertNull(LivePoints.pick(nearly, context(distanceGoal(10)), cadence(LiveFocus.TECHNIQUE), true, listOf(point.id)))
    }

    @Test fun `history and the runner's own goal are used when they exist and never invented`() {
        val ctx = context(timeGoal(30, "EASY"), "Media maratón de Granada en abril de 2027", Triple("2026-11-06", 4.67, 374.0), Triple("2026-11-04", 10.0, 372.0))
        val data = run(464.0, 462.0, moving = 1012.0, extraKm = .2, recent = 450.0)
        val previous = LivePoints.pick(data, ctx, cadence(LiveFocus.HISTORY), true)!!
        assertEquals("history/previous_run", previous.id)
        assertEquals("hace 4 días", previous.numbers["their_previous_run_was"])
        assertEquals("86 segundos", previous.numbers["difference_per_kilometre"])
        val week = LivePoints.pick(data, ctx, cadence(LiveFocus.HISTORY), true, listOf(previous.id, "form/0:0", "effort/0"))!!
        assertTrue(week.id.startsWith("history/week"))
        assertEquals("16,9 kilómetros", week.numbers["seven_day_total"])
        assertTrue(week.point.contains("over 3 runs"))
        val goal = LivePoints.pick(data, ctx, cadence(LiveFocus.ASPIRATION), true)!!
        assertEquals("bigger", goal.theme)
        assertEquals("5 meses", goal.numbers["time_until_it"])
        assertNotEquals("history", LivePoints.pick(data, context(timeGoal(30)), cadence(LiveFocus.HISTORY), true)!!.theme)
        assertNotEquals("bigger", LivePoints.pick(data, context(timeGoal(30)), cadence(LiveFocus.ASPIRATION), true)!!.theme)
        val opening = LivePoints.pick(run(moving = 40.0, extraKm = .09), context(distanceGoal(12), null, Triple("2026-11-06", 4.67, 374.0)), LiveCall(LiveTrigger.OPENING, null, LiveFocus.MORALE, 1), false)!!
        assertEquals(mapOf("todays_goal" to "12 kilometres", "days_since_the_last_run" to "4 days"), opening.numbers)
        assertTrue(opening.point.contains("further than any run of theirs in the last 30 days"))
    }

    @Test fun `a date in the runner's goal is read only when it is one`() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(5, LivePoints.monthsUntil("Media maratón de Granada en abril de 2027", today))
        assertEquals(6, LivePoints.monthsUntil("Half marathon, May 2027", today))
        assertEquals(1, LivePoints.monthsUntil("10K en diciembre", today))
        assertEquals(11, LivePoints.monthsUntil("Carrera en octubre", today))
        assertNull(LivePoints.monthsUntil("I may run a marathon one day", today))
        assertNull(LivePoints.monthsUntil("Correr más", today))
        assertNull(LivePoints.monthsUntil("Maratón en abril de 2031", today))
    }

    @Test fun `the same minute of two different runs gets a different form cue, self-check and stock hint`() {
        fun seeded(
            focus: LiveFocus,
            variety: Int,
        ) = LiveCall(LiveTrigger.CADENCE, null, focus, 3, variety)
        val minute = run(moving = 290.0, extraKm = .7)
        val forms = (0..3).map { LivePoints.pick(minute, context(), seeded(LiveFocus.TECHNIQUE, it), true)!!.point }
        assertEquals(4, forms.distinct().size)
        val effort = run(461.0, moving = 600.0)
        val checks = (0..2).map { LivePoints.pick(effort, context(), seeded(LiveFocus.EFFORT_CHECK, it), false)!!.point }
        assertEquals(3, checks.distinct().size)
        val locked = run(450.0, 450.0, moving = 1100.0, recent = 450.0, shift = Triple("SIMILAR", 450.0, 452.0))
        val steady = (0..2).map { LivePoints.pick(locked, context(), seeded(LiveFocus.PACE_TREND, it), true)!! }
        assertTrue(steady.all { it.theme == "steady" })
        assertEquals(3, steady.map { it.hint }.distinct().size)
        assertEquals("Steady is the goal; say so.", steady[0].hint)
        val again = seeded(LiveFocus.TECHNIQUE, 5)
        assertEquals(LivePoints.pick(minute, context(), again, true), LivePoints.pick(minute, context(), again, true))
    }

    private fun band() =
        context(
            JSONObject()
                .put("type", "PACE")
                .put("target_pace_seconds_per_km", 300)
                .put("pace_min_seconds_per_km", 290)
                .put("pace_max_seconds_per_km", 310)
                .put("intended_effort", "STEADY"),
            null,
            Triple("2026-11-07", 8.0, 343.0),
        )

    private fun race() =
        context(
            JSONObject()
                .put("type", "DISTANCE_TIME")
                .put("distance_goal_m", 10_000)
                .put("distance_deadline_minutes", 50)
                .put("target_pace_seconds_per_km", 300),
        )

    private fun raceSplits(count: Int): DoubleArray {
        val all = doubleArrayOf(288.0, 291.0, 292.0, 291.0, 288.0, 303.0, 307.0, 310.0, 321.0, 323.0)
        return all.copyOfRange(0, count)
    }

    private fun opening() = LiveCall(LiveTrigger.OPENING, null, LiveFocus.MORALE, 1)

    private fun finish() = LiveCall(LiveTrigger.MOMENT, LiveMoment.GOAL_REACHED, LiveFocus.MORALE, 31)

    private fun kilometre(
        number: Int,
        split: Double,
        before: Double? = null,
    ) = LiveMilestone.of(CoachCue.Kilometre(number, split, split, before))

    @Test fun `with a target pace, kilometres and check-ins are judged against the target, not the run's own average`() {
        val start = LivePoints.pick(run(moving = 40.0, extraKm = .09), band(), opening(), false)!!
        assertFalse(start.point, start.point.contains("by feel"))
        assertEquals("4 minutes 50 to 5 minutes 10 per kilometre", start.numbers["todays_target_pace"])
        val warm = LivePoints.pick(run(384.0), band(), km, false, milestone = kilometre(1, 384.0))!!
        assertTrue(warm.point, warm.point.contains("fine while warming up"))
        assertEquals("4 minutes 50 to 5 minutes 10 per kilometre", warm.numbers["target_pace"])
        val on = LivePoints.pick(run(384.0, 368.0, 292.0), band(), km, false, milestone = kilometre(3, 292.0, 368.0))!!
        assertTrue(on.point, on.point.contains("right on their target pace"))
        assertEquals(mapOf("time_of_this_kilometre" to "4 minutes 52"), on.numbers)
        val drifting = run(384.0, 368.0, 292.0, 301.0, 313.0, 305.0, 388.0)
        val drift = LivePoints.pick(drifting, band(), km, false, milestone = kilometre(7, 388.0, 305.0))!!
        assertTrue(drift.point, drift.point.contains("slower than their target pace"))
        assertEquals("78 seconds", drift.numbers["slower_than_the_target_by"])
        assertFalse(drift.hint!!.contains("normal"))
        val slow = LivePoints.pick(run(292.0, 301.0, moving = 1500.0, recent = 396.0), band(), cadence(LiveFocus.MORALE), false)!!
        assertEquals("plan/slow:4", slow.id)
        assertEquals("6 minutes 36 per kilometre", slow.numbers["pace_now"])
        val onTarget = run(292.0, 301.0, moving = 1500.0, recent = 296.0, shift = Triple("FASTER", 296.0, 330.0))
        assertEquals("plan/on", LivePoints.pick(onTarget, band(), cadence(LiveFocus.PACE_TREND), false)!!.id)
        val selfReferenced = listOf("pace/now", "faster/pace", "history/previous_run")
        listOf(LiveFocus.PACE_TREND, LiveFocus.HISTORY, LiveFocus.SPLIT_DETAIL).forEach { focus ->
            val other = LivePoints.pick(onTarget, band(), cadence(focus), false, listOf("plan/on"))?.id.orEmpty()
            assertTrue(other, selfReferenced.none { other.startsWith(it) })
        }
    }

    @Test fun `a time goal is coached against the clock, and a miss is never called a success`() {
        val start = LivePoints.pick(run(moving = 40.0, extraKm = .09), race(), opening(), false)!!
        assertEquals(mapOf("todays_goal" to "10 kilometres", "todays_time_goal" to "50 minutes"), start.numbers)
        val seven = run(*raceSplits(7), moving = 2060.0, extraKm = 0.0)
        val seventh = LivePoints.pick(seven, race(), km, false, milestone = kilometre(7, 307.0, 303.0))!!
        assertEquals("40 seconds", seventh.numbers["ahead_of_the_time_goal_by"])
        assertTrue(seventh.point, seventh.point.contains("slower than that pace"))
        assertFalse(seventh.hint!!.contains("skill"))
        val early = run(288.0, 291.0, moving = 665.0, extraKm = .3, recent = 284.0)
        val banking = LivePoints.pick(early, race(), cadence(LiveFocus.MORALE), false)!!
        assertEquals("plan/bank:0", banking.id)
        assertEquals("25 seconds", banking.numbers["ahead_of_the_time_goal_by"])
        assertTrue(banking.hint!!.contains("settle at goal pace"))
        val late = run(*raceSplits(8), moving = 2506.0, extraKm = .4, recent = 320.0)
        val slipping = LivePoints.pick(late, race(), cadence(LiveFocus.HISTORY), false)!!
        assertEquals("plan", slipping.theme)
        assertEquals("5 minutes 9 per kilometre", slipping.numbers["pace_needed_from_here"])
        val last =
            LivePoints.pick(
                run(*raceSplits(9), moving = 2691.0, extraKm = 0.0),
                race(),
                LiveCall(LiveTrigger.MOMENT, LiveMoment.ONE_KM_LEFT, LiveFocus.GOAL_PROJECTION, 30),
                false,
                milestone = JSONObject().put("kind", "ONE_KM_LEFT"),
            )!!
        assertEquals("moment/last_km", last.id)
        assertEquals("9 seconds", last.numbers["ahead_of_the_time_goal_by"])
        assertEquals("5 minutes 9 per kilometre", last.numbers["pace_needed_for_the_last_kilometre"])
        val over = LiveMilestone.of(CoachCue.GoalReached(false, CoachFacts(10_000.0, 3_014_000, 301.4)))
        val missed = LivePoints.pick(run(*raceSplits(10), moving = 3014.0, extraKm = 0.0), race(), finish(), false, milestone = over)!!
        assertEquals("50 minutes 14", missed.numbers["time"])
        assertEquals("14 seconds", missed.numbers["over_the_time_goal_by"])
        assertFalse(missed.point.contains("reached"))
        assertTrue(missed.hint!!.contains("never say they made the time"))
        val inside = LiveMilestone.of(CoachCue.GoalReached(true, CoachFacts(10_000.0, 2_990_000, 299.0)))
        val made = LivePoints.pick(run(*raceSplits(10), moving = 2990.0, extraKm = 0.0), race(), finish(), true, milestone = inside)!!
        assertEquals("moment/goal", made.id)
        assertEquals("10 segundos", made.numbers["inside_the_time_goal_by"])
    }

    @Test fun `a recovery day flags a start quicker than the runner's usual pace, and praises easing back`() {
        val recovery =
            context(
                distanceGoal(4).put("intended_effort", "RECOVERY"),
                null,
                Triple("2026-11-09", 12.0, 329.0),
                Triple("2026-11-06", 6.0, 360.0),
            )
        val quick = LivePoints.pick(run(moving = 165.0, extraKm = .5, recent = 323.0), recovery, cadence(LiveFocus.MORALE), true)!!
        assertEquals("plan/gentle:0", quick.id)
        assertTrue(quick.point, quick.point.contains("too quick for a recovery day"))
        assertEquals("5 minutos 23 por kilómetro", quick.numbers["pace_now"])
        val calm = LivePoints.pick(run(moving = 165.0, extraKm = .4, recent = 380.0), recovery, cadence(LiveFocus.MORALE), true)
        assertNotEquals("plan", calm?.theme)
        val first = LivePoints.pick(run(327.0), recovery, km, true, milestone = kilometre(1, 327.0))!!
        assertTrue(first.point, first.point.contains("too quick for a recovery day"))
        val eased = LivePoints.pick(run(327.0, 395.0), recovery, km, true, milestone = kilometre(2, 395.0, 327.0))!!
        assertTrue(eased.point, eased.point.contains("eased back to a gentle pace"))
        val later = LivePoints.pick(run(327.0, 395.0, 380.0, moving = 1300.0, recent = 320.0), recovery, cadence(LiveFocus.MORALE), true)
        assertNotEquals("plan", later?.theme)
        val easy = context(distanceGoal(5).put("intended_effort", "EASY"), null, Triple("2026-11-07", 5.0, 357.0))
        val hurried = LivePoints.pick(run(moving = 300.0, extraKm = .9, recent = 320.0), easy, cadence(LiveFocus.MORALE), false)
        assertNotEquals("plan", hurried?.theme)
    }

    @Test fun `halfway and a slipping time goal are said straight, and an impossible pace is never asked for`() {
        val half = LiveCall(LiveTrigger.MOMENT, LiveMoment.HALFWAY, LiveFocus.GOAL_PROJECTION, 12)
        val passed = JSONObject().put("kind", "HALFWAY").put("facts", JSONObject().put("past_halfway", true))
        val halfway = LivePoints.pick(run(*raceSplits(5), moving = 1460.0, extraKm = .1), race(), half, false, milestone = passed)!!
        assertEquals(mapOf("time_so_far" to "24 minutes 20", "ahead_of_the_time_goal_by" to "1 minute 10"), halfway.numbers)
        assertTrue(halfway.point, halfway.point.contains("against their time goal"))
        val slow = DoubleArray(9) { 330.0 }
        val lagging = run(*slow, moving = 2970.0, extraKm = 0.0, recent = 330.0)
        val behind = LivePoints.pick(lagging, race(), km, false, milestone = kilometre(9, 330.0, 330.0))!!
        assertEquals("4 minutes 30", behind.numbers["behind_the_time_goal_by"])
        assertFalse(behind.numbers.containsKey("pace_needed_from_here"))
        assertTrue(behind.hint!!.contains("out of reach"))
        val warm = LivePoints.pick(run(moving = 300.0, extraKm = .8, recent = 380.0), band(), cadence(LiveFocus.MORALE), false)!!
        assertEquals("plan/warm", warm.id)
        val quick = LivePoints.pick(run(292.0, 301.0, moving = 1500.0, recent = 270.0), band(), cadence(LiveFocus.MORALE), false)!!
        assertEquals("plan/quick:4", quick.id)
        val fast = LivePoints.pick(run(292.0, 301.0, 270.0), band(), km, false, milestone = kilometre(3, 270.0, 301.0))!!
        assertEquals("20 seconds", fast.numbers["quicker_than_the_target_by"])
    }

    @Test fun `a check-in without news waits for a quiet stretch, and news never waits`() {
        val data = run(461.0, 463.0, 462.0, recent = 462.0)
        val ctx = context(timeGoal(60))
        val tooSoon = LiveCall(LiveTrigger.CADENCE, null, LiveFocus.MORALE, 3, room = false)
        assertNull(LivePoints.pick(data, ctx, tooSoon, true))
        assertEquals("best/km:1", LivePoints.pick(data, ctx, tooSoon.copy(room = true), true)!!.id)
        val easy = context(distanceGoal(5).put("intended_effort", "EASY"))
        val beyond = LivePoints.pick(run(400.0, 402.0, 398.0, 401.0, 399.0, extraKm = .6), easy, tooSoon, true)!!
        assertEquals("goal/beyond:4", beyond.id)
        val later = run(400.0, 402.0, 398.0, 401.0, 399.0, moving = 2700.0, extraKm = 1.4)
        val heard = listOf(beyond.id, "form/1:0", "effort/0")
        assertNull(LivePoints.pick(later, easy, tooSoon, true, heard))
        assertEquals("goal/beyond:5", LivePoints.pick(later, easy, tooSoon.copy(room = true), true, heard)!!.id)
    }

    @Test fun `an aside waits while a kilometre is about to be announced`() {
        val close = run(461.0, 463.0, 462.0, extraKm = .92, recent = 462.0)
        assertNull(LivePoints.pick(close, context(timeGoal(60)), cadence(LiveFocus.TECHNIQUE), false))
        val silentKilometres = context(timeGoal(60).put("local_milestones_enabled", false))
        assertEquals("form", LivePoints.pick(close, silentKilometres, cadence(LiveFocus.TECHNIQUE), false)!!.theme)
        val early = run(461.0, 463.0, 462.0, extraKm = .3, recent = 462.0)
        assertEquals("form", LivePoints.pick(early, context(timeGoal(60)), cadence(LiveFocus.TECHNIQUE), false)!!.theme)
    }

    @Test fun `a low-value aside is said once a run, even when its figure changes`() {
        val heard = listOf("best/km:1", "form/0:1", "effort/1")
        val faster = run(461.0, 463.0, 440.0, recent = 440.0)
        val newBest = LivePoints.pick(faster, context(timeGoal(60)), cadence(LiveFocus.MORALE), true, heard)!!
        assertNotEquals("best", newBest.theme)
        val week = listOf("history/week:0", "form/0:1", "effort/1")
        val ctx = context(timeGoal(30, "EASY"), "A first half marathon next spring", Triple("2026-11-06", 4.67, 374.0))
        val middle = run(464.0, 462.0, moving = 1500.0, extraKm = .2, recent = 460.0)
        val again = LivePoints.pick(middle, ctx, cadence(LiveFocus.ASPIRATION), true, week)!!
        assertEquals("bigger", again.theme)
        val twice = listOf("bigger/aspiration:0", "history/week:0", "form/0:1", "effort/1")
        val later = run(464.0, 462.0, moving = 1600.0, extraKm = .2, recent = 460.0)
        val after = LivePoints.pick(later, ctx, cadence(LiveFocus.ASPIRATION), true, twice)
        assertFalse(after?.id.orEmpty(), after?.theme in setOf("bigger", "history"))
    }

    @Test fun `a time goal runs on the same clock as the app, leaving stops out when auto-pause is on`() {
        val half = LiveCall(LiveTrigger.MOMENT, LiveMoment.HALFWAY, LiveFocus.GOAL_PROJECTION, 12)
        val passed = JSONObject().put("kind", "HALFWAY").put("facts", JSONObject().put("past_halfway", true))
        val stopped = run(*raceSplits(5), moving = 1460.0, extraKm = .1).put("active_seconds", 1580.0)
        val moving = LivePoints.pick(stopped, race(), half, false, milestone = passed)!!
        assertEquals("24 minutes 20", moving.numbers["time_so_far"])
        assertEquals("1 minute 10", moving.numbers["ahead_of_the_time_goal_by"])
        val wall = race().apply { getJSONObject("active_goal").put("clock", "active") }
        val active = LivePoints.pick(stopped, wall, half, false, milestone = passed)!!
        assertEquals("26 minutes 20", active.numbers["time_so_far"])
        assertEquals("50 seconds", active.numbers["behind_the_time_goal_by"])
    }
}
