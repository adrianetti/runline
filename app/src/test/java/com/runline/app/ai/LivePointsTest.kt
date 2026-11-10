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
    private fun run(vararg splits: Double, moving: Double = splits.sum() + 120, extraKm: Double = .3, recent: Double? = null,
                    shift: Triple<String, Double, Double>? = null, laps: List<Double> = emptyList()) = JSONObject().put("activity_type", "run")
        .put("moving_seconds", moving).put("distance_km", splits.size + extraKm)
        .put("average_pace_seconds_per_km", moving / (splits.size + extraKm)).put("recent_pace_seconds_per_km", recent ?: JSONObject.NULL)
        .put("recent_pace_comparison", shift?.let { JSONObject().put("trend", it.first).put("recent_pace_seconds_per_km", it.second).put("previous_pace_seconds_per_km", it.third) } ?: JSONObject.NULL)
        .put("splits", JSONArray(splits.mapIndexed { i, s -> JSONObject().put("number", i + 1).put("distance_m", 1000).put("pace_seconds_per_km", s).put("is_full_kilometre", true) }))
        .put("estimated_repeated_laps", JSONArray(laps.map { JSONObject().put("duration_seconds", it).put("distance_m", 970) }))
    private fun context(goal: JSONObject? = null, aspiration: String? = null, vararg earlier: Triple<String, Double, Double>) = JSONObject().put("today", "2026-11-10")
        .put("active_goal", goal ?: JSONObject.NULL)
        .put("recent_runs", JSONArray(earlier.map { JSONObject().put("date", it.first).put("activity_type", "run").put("distance_km", it.second).put("average_pace_seconds_per_km", it.third) }))
        .apply { if (aspiration != null) put("runner_goal", JSONObject().put("text", aspiration)) }
    private fun timeGoal(minutes: Int, effort: String? = null) = JSONObject().put("type", "TIME").put("duration_goal_seconds", minutes * 60).put("intended_effort", effort ?: JSONObject.NULL)
    private fun distanceGoal(km: Int) = JSONObject().put("type", "DISTANCE").put("distance_goal_m", km * 1000)
    private fun cadence(focus: LiveFocus) = LiveCall(LiveTrigger.CADENCE, null, focus, 3)
    private val km = LiveCall(LiveTrigger.MOMENT, LiveMoment.KILOMETRE, LiveFocus.SPLIT_DETAIL, 3)

    @Test fun `figures are written the way they are spoken, in both languages`() {
        val es = LiveSpeech(true); val en = LiveSpeech(false)
        assertEquals("7 minutos 41 por kilómetro", es.pace(461.1)); assertEquals("7 minutes 41 per kilometre", en.pace(461.1))
        assertEquals("6 minutos", es.time(360.0)); assertEquals("45 segundos", es.time(45.0)); assertEquals("1 second", en.seconds(1))
        assertEquals("13 minutos", es.about(788.0)); assertEquals("1 hora 3 minutos", es.about(3765.0)); assertEquals("2 hours", en.about(7200.0))
        assertEquals("2,2 kilómetros", es.distance(2.21)); assertEquals("2.2 kilometres", en.distance(2.21)); assertEquals("1 kilómetro", es.distance(1.04))
        assertEquals("400 metros", es.distance(.41)); assertEquals("50 metres", en.distance(.01)); assertEquals("10 kilómetros", es.distance(10.0))
        assertEquals("hace 4 días", es.ago(es.days(4))); assertEquals("1 day ago", en.ago(en.days(1))); assertEquals("5 meses", es.months(5))
    }

    @Test fun `a kilometre is announced with the app's own time and what it shows`() {
        val cue = LiveMilestone.of(CoachCue.Kilometre(3, 455.0, splitPaceSeconds = 441.6, previousSplitPaceSeconds = 460.9))
        val streak = LivePoints.pick(run(466.3, 460.9, 441.6), context(), km, true, milestone = cue)!!
        assertEquals("moment/km:3", streak.id); assertEquals(mapOf("time_of_this_kilometre" to "7 minutos 22"), streak.numbers)
        assertTrue(streak.point.contains("every kilometre so far has been faster"))
        val fastest = LivePoints.pick(run(450.0, 466.0, 441.6), context(), km, true, milestone = cue)!!
        assertTrue(fastest.point.contains("fastest kilometre of the run so far"))
        val slower = LivePoints.pick(run(450.0, 440.0, 471.0), context(), km, false,
            milestone = LiveMilestone.of(CoachCue.Kilometre(3, 455.0, splitPaceSeconds = 471.0, previousSplitPaceSeconds = 440.0)))!!
        assertEquals("31 seconds", slower.numbers["it_took_this_much_longer_than_the_previous_kilometre"]); assertTrue(slower.hint!!.contains("normal"))
        val even = LivePoints.pick(run(461.0, 463.0), context(), km, true, milestone = LiveMilestone.of(CoachCue.Kilometre(2, 462.0, 463.0, 461.0)))!!
        assertTrue(even.point.contains("almost exactly the same time")); assertEquals(1, even.numbers.size)
        // The first kilometre has nothing to compare with unless there is a previous run.
        val first = LiveMilestone.of(CoachCue.Kilometre(1, 464.0, splitPaceSeconds = 464.0))
        assertFalse(LivePoints.pick(run(464.0), context(), km, true, milestone = first)!!.point.contains("previous"))
        assertTrue(LivePoints.pick(run(464.0), context(null, null, Triple("2026-11-06", 4.7, 380.0)), km, true, milestone = first)!!.point.contains("gentler than the average pace of their previous run"))
    }

    @Test fun `halfway and the goal carry the totals the built-in announcement would give`() {
        val facts = CoachFacts(2205.0, 1_012_000, 459.0, 440.0, remainingMs = 788_000, projectedMeters = 3_920.0)
        val half = LivePoints.pick(run(466.0, 461.0, moving = 1012.0, extraKm = .2), context(timeGoal(30)), LiveCall(LiveTrigger.MOMENT, LiveMoment.HALFWAY, LiveFocus.GOAL_PROJECTION, 5), true,
            milestone = LiveMilestone.of(CoachCue.Encouragement(EncouragementKind.HALFWAY, facts)))!!
        assertEquals(mapOf("distance_so_far" to "2,2 kilómetros", "estimated_distance_by_the_end" to "3,9 kilómetros"), half.numbers)
        assertTrue(half.point.startsWith("They have just passed the halfway point")); assertTrue(facts.pastHalfway)
        assertFalse(CoachFacts(1500.0, 900_000, remainingMs = 900_000).pastHalfway); assertTrue(CoachFacts(6000.0, 1, remainingMeters = 4000.0).pastHalfway)
        val goal = LivePoints.pick(run(371.0, 362.0, 354.0, moving = 3722.0, extraKm = 7.0), context(distanceGoal(10)), LiveCall(LiveTrigger.MOMENT, LiveMoment.GOAL_REACHED, LiveFocus.MORALE, 30), true,
            milestone = LiveMilestone.of(CoachCue.GoalReached(null, CoachFacts(10_000.0, 3_722_000, 372.2))))!!
        assertEquals("10 kilómetros", goal.numbers["distance"]); assertEquals("1 hora 2 minutos", goal.numbers["time"]); assertEquals("6 minutos 12 por kilómetro", goal.numbers["average_pace"])
        assertTrue(goal.point.contains("Every kilometre was faster"))
        assertNull(LiveMilestone.of(CoachCue.Encouragement(EncouragementKind.PROGRESS))); assertNull(LiveMilestone.of(CoachCue.SlowerThanTarget))
    }

    @Test fun `each observation is offered once and a theme never twice running`() {
        val data = run(461.0, 463.0, 462.0, recent = 462.0); val ctx = context(timeGoal(60))
        val first = LivePoints.pick(data, ctx, cadence(LiveFocus.MORALE), true)!!
        assertEquals("best/km:1", first.id)
        val second = LivePoints.pick(data, ctx, cadence(LiveFocus.MORALE), true, listOf(first.id))!!
        assertEquals("steady/km:3", second.id); assertTrue(second.numbers.isEmpty())
        // Steady again would be the same theme: the check-in moves on to something else.
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
        assertEquals("form", cue.theme); assertTrue(cue.numbers.isEmpty()); assertTrue(cue.hint!!.contains("you cannot see it"))
        val check = LivePoints.pick(run(461.0, moving = 600.0), context(), cadence(LiveFocus.EFFORT_CHECK), false)!!
        assertEquals("effort", check.theme); assertTrue(check.hint!!.contains("Never ask them to tell you"))
    }

    @Test fun `pace changes are mentioned with judgement, not every time they wobble`() {
        fun shifted(trend: String, now: Double, before: Double, ctx: JSONObject, moving: Double = 1500.0) =
            LivePoints.pick(run(450.0, 450.0, 450.0, moving = moving, recent = now, shift = Triple(trend, now, before)), ctx, cadence(LiveFocus.TECHNIQUE), true)!!
        // Easy day: slowing is never news, speeding up only when clearly faster than the run's average, and only once.
        val easy = context(timeGoal(60, "EASY"))
        assertEquals("form", shifted("SLOWER", 490.0, 440.0, easy).theme)
        assertEquals("form", shifted("FASTER", 440.0, 480.0, easy).theme)
        val nag = shifted("FASTER", 410.0, 450.0, easy)
        assertEquals("faster/pace_easy", nag.id); assertEquals("6 minutos 50 por kilómetro", nag.numbers["pace_now"])
        assertEquals("form", LivePoints.pick(run(450.0, 450.0, 450.0, moving = 1500.0, recent = 410.0, shift = Triple("FASTER", 410.0, 450.0)), easy, cadence(LiveFocus.TECHNIQUE), true, listOf(nag.id))!!.theme)
        // No effort set: a real fade gets one cue, coming back to the usual rhythm is good news, a late surge is cheered.
        val open = context(timeGoal(60))
        assertEquals("slower", shifted("SLOWER", 480.0, 440.0, open).theme)
        assertEquals("steady/back", shifted("FASTER", 452.0, 490.0, open).id.substringBefore(':'))
        assertEquals("faster/surge", shifted("FASTER", 420.0, 450.0, context(timeGoal(30)), moving = 1500.0).id)
        // A small wobble does not interrupt the rotation.
        assertEquals("form", shifted("FASTER", 430.0, 445.0, open).theme)
    }

    @Test fun `the shape of the run is named over kilometres and dropped once it is old news`() {
        val fading = LivePoints.pick(run(360.0, 362.0, 380.0, 384.0, recent = 385.0), context(distanceGoal(10)), cadence(LiveFocus.SPLIT_DETAIL), false)!!
        assertEquals("slower/shape:2", fading.id); assertEquals("6 minutes 22 per kilometre", fading.numbers["average_of_the_last_two_kilometres"])
        val building = LivePoints.pick(run(380.0, 384.0, 362.0, 360.0, recent = 360.0), context(distanceGoal(10)), cadence(LiveFocus.SPLIT_DETAIL), false)!!
        assertEquals("faster/shape:2", building.id)
        // Surging again: the earlier fade is not mentioned.
        assertNotEquals("slower", LivePoints.pick(run(360.0, 362.0, 380.0, 384.0, recent = 350.0), context(distanceGoal(10)), cadence(LiveFocus.SPLIT_DETAIL), false)!!.theme)
    }

    @Test fun `near the end only the finish is worth a word`() {
        val nearly = run(360.0, 360.0, 360.0, 360.0, 360.0, 360.0, 360.0, 360.0, 360.0, moving = 3300.0, extraKm = .5, recent = 360.0)
        val point = LivePoints.pick(nearly, context(distanceGoal(10)), cadence(LiveFocus.EFFORT_CHECK), true)!!
        assertEquals("goal", point.theme); assertEquals("500 metros", point.numbers["left_to_go"])
        assertNull(LivePoints.pick(nearly, context(distanceGoal(10)), cadence(LiveFocus.TECHNIQUE), true, listOf(point.id)))
    }

    @Test fun `history and the runner's own goal are used when they exist and never invented`() {
        val ctx = context(timeGoal(30, "EASY"), "Media maratón de Granada en abril de 2027", Triple("2026-11-06", 4.67, 374.0), Triple("2026-11-04", 10.0, 372.0))
        val data = run(464.0, 462.0, moving = 1012.0, extraKm = .2, recent = 450.0)
        val previous = LivePoints.pick(data, ctx, cadence(LiveFocus.HISTORY), true)!!
        assertEquals("history/previous_run", previous.id); assertEquals("hace 4 días", previous.numbers["their_previous_run_was"]); assertEquals("86 segundos", previous.numbers["difference_per_kilometre"])
        val week = LivePoints.pick(data, ctx, cadence(LiveFocus.HISTORY), true, listOf(previous.id, "form/0:0", "effort/0"))!!
        assertTrue(week.id.startsWith("history/week")); assertEquals("16,9 kilómetros", week.numbers["seven_day_total"]); assertTrue(week.point.contains("over 3 runs"))
        val goal = LivePoints.pick(data, ctx, cadence(LiveFocus.ASPIRATION), true)!!
        assertEquals("bigger", goal.theme); assertEquals("5 meses", goal.numbers["time_until_it"])
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
        assertEquals(1, LivePoints.monthsUntil("10K en diciembre", today)); assertEquals(11, LivePoints.monthsUntil("Carrera en octubre", today))
        assertNull(LivePoints.monthsUntil("I may run a marathon one day", today)); assertNull(LivePoints.monthsUntil("Correr más", today))
        assertNull(LivePoints.monthsUntil("Maratón en abril de 2031", today))
    }
}
