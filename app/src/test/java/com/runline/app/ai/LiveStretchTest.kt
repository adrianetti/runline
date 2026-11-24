package com.runline.app.ai

import com.runline.app.core.CoachCue
import com.runline.app.core.CoachFacts
import com.runline.app.core.EncouragementKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStretchTest {
    private val kilometreCall = LiveCall(LiveTrigger.MOMENT, LiveMoment.KILOMETRE, LiveFocus.SPLIT_DETAIL, 3)

    private fun run(
        splits: List<Double>,
        extraKm: Double = .02,
        moving: Double = splits.sum() + extraKm * splits.last(),
    ) = JSONObject()
        .put("activity_type", "run")
        .put("moving_seconds", moving)
        .put("distance_km", splits.size + extraKm)
        .put("average_pace_seconds_per_km", splits.average())
        .put(
            "splits",
            JSONArray(
                splits.mapIndexed { i, pace ->
                    JSONObject()
                        .put("number", i + 1)
                        .put("distance_m", 1000)
                        .put("pace_seconds_per_km", pace)
                        .put("is_full_kilometre", true)
                },
            ),
        )

    private fun context(goal: JSONObject) =
        JSONObject()
            .put("today", "2026-11-10")
            .put("active_goal", goal)
            .put("recent_runs", JSONArray())

    private fun distance(
        km: Int,
        effort: String? = null,
    ) = JSONObject()
        .put("type", "DISTANCE")
        .put("distance_goal_m", km * 1000)
        .put("intended_effort", effort ?: JSONObject.NULL)

    private fun time(minutes: Int) = JSONObject().put("type", "TIME").put("duration_goal_seconds", minutes * 60)

    private fun even(count: Int) = List(count) { 340.0 + it % 2 }

    private fun kilometre(
        splits: List<Double>,
        goal: JSONObject,
        used: List<String> = emptyList(),
    ): LivePoint {
        val cue = CoachCue.Kilometre(splits.size, splits.average(), splits.last(), splits.getOrNull(splits.size - 2))
        return LivePoints.pick(run(splits), context(goal), kilometreCall, false, used, LiveMilestone.of(cue))!!
    }

    @Test fun `the stage follows the arc of the run, and settling in ends after the first minutes`() {
        assertEquals(LiveStage.SETTLE, LiveStretch(null, 600.0, false).stage)
        assertEquals(LiveStage.MIDDLE, LiveStretch(null, 1800.0, false).stage)
        assertEquals(LiveStage.SETTLE, LiveStretch(.1, 600.0, false).stage)
        assertEquals(LiveStage.MIDDLE, LiveStretch(.2, 1700.0, false).stage)
        assertEquals(LiveStage.MIDDLE, LiveStretch(.5, 1700.0, false).stage)
        assertEquals(LiveStage.CLOSE, LiveStretch(.75, 2400.0, false).stage)
        assertEquals(LiveStage.EXTRA, LiveStretch(1.05, 3000.0, false).stage)
    }

    @Test fun `only the closing part counts down, and only with a figure to give`() {
        val closing = linkedMapOf<String, String>()
        val close = LiveStretch(.8, 2400.0, false).next(0, closing, "2 kilometres")
        assertEquals(mapOf(LiveStretch.LEFT to "2 kilometres"), closing)
        assertTrue(close.contains("countdown"))
        val middling = linkedMapOf<String, String>()
        assertFalse(LiveStretch(.5, 1800.0, false).next(0, middling, "5 kilometres").contains("countdown"))
        assertTrue(middling.isEmpty())
        val unknown = linkedMapOf<String, String>()
        assertFalse(LiveStretch(.8, 2400.0, false).next(0, unknown, null).contains("countdown"))
        assertTrue(unknown.isEmpty())
    }

    @Test fun `the job for the next stretch moves on each kilometre, and easy days get their own`() {
        val jobs = (0..2).map { LiveStretch(.5, 1800.0, false).next(it, linkedMapOf(), null) }
        assertEquals(3, jobs.distinct().size)
        assertTrue(jobs.all { it.startsWith("Next:") })
        val easy = (0..2).map { LiveStretch(.5, 1800.0, true).next(it, linkedMapOf(), null) }
        assertTrue(easy.none { it in jobs })
        val extra = LiveStretch(1.1, 3000.0, false).next(0, linkedMapOf(), null)
        assertTrue(extra.contains("extra") || extra.contains("bonus"))
    }

    @Test fun `steadiness is named once, then never praised again`() {
        assertTrue(LiveStretch.steadiness(listOf("moment/km:2", "faster/km_fastest:2")).contains("once"))
        assertTrue(LiveStretch.steadiness(listOf("moment/km:3", "steady/km:3")).contains("already named"))
        assertTrue(LiveStretch.steadiness(listOf("steady/pace:2")).contains("already named"))
    }

    @Test fun `an unremarkable kilometre late in a distance goal counts down and looks ahead instead of praising`() {
        val late = kilometre(even(8), distance(10), listOf("moment/km:7", "steady/km:7"))
        assertEquals("moment/km:8", late.id)
        assertEquals(mapOf("time_of_this_kilometre" to "5 minutes 41", LiveStretch.LEFT to "2 kilometres"), late.numbers)
        assertTrue(late.point, late.point.contains("almost exactly the same time"))
        assertTrue(late.point, late.point.contains("next stretch"))
        assertTrue(late.hint!!, late.hint.contains("already named"))
        assertTrue(late.hint, late.hint.contains("countdown"))
        assertTrue("steady/km:8" in late.covers)
    }

    @Test fun `the middle of a run gets a job but no countdown, and the job changes from one kilometre to the next`() {
        val fourth = kilometre(even(4), distance(10))
        val fifth = kilometre(even(5), distance(10))
        assertEquals(setOf("time_of_this_kilometre"), fourth.numbers.keys)
        assertTrue(fourth.hint!!.contains("Next:"))
        assertNotEquals(fourth.hint, fifth.hint)
        val second = kilometre(even(2), distance(10))
        assertTrue(second.hint!!, second.hint.contains("once"))
    }

    @Test fun `a time goal counts down in minutes, and kilometres past the goal are extra`() {
        val timed = kilometre(List(4) { 420.0 + it % 2 }, time(36))
        assertEquals("8 minutes", timed.numbers[LiveStretch.LEFT])
        val beyond = kilometre(List(6) { 350.0 }, distance(5))
        assertEquals(setOf("time_of_this_kilometre"), beyond.numbers.keys)
        assertTrue(beyond.hint!!, beyond.hint.contains("extra") || beyond.hint.contains("bonus"))
    }

    @Test fun `a quicker kilometre is noted without gushing and still points ahead`() {
        val quicker = kilometre(listOf(335.0, 346.0, 338.0), distance(10, "EASY"))
        assertTrue(quicker.point, quicker.point.contains("quicker than the previous kilometre"))
        assertTrue(quicker.hint!!, quicker.hint.contains("no need for more speed"))
        assertTrue(quicker.hint, quicker.hint.contains("Next:"))
    }

    @Test fun `halfway on a distance goal says what is left and never estimates the finish`() {
        val facts = CoachFacts(5_020.0, 1_708_000, 340.6, 341.0, remainingMeters = 4_980.0)
        val half =
            LivePoints.pick(
                run(even(5)),
                context(distance(10)),
                LiveCall(LiveTrigger.MOMENT, LiveMoment.HALFWAY, LiveFocus.GOAL_PROJECTION, 5),
                false,
                milestone = LiveMilestone.of(CoachCue.Encouragement(EncouragementKind.HALFWAY, facts)),
            )!!
        assertEquals("moment/halfway", half.id)
        assertEquals(setOf("time_so_far", LiveStretch.LEFT), half.numbers.keys)
        assertEquals("5 kilometres", half.numbers[LiveStretch.LEFT])
        assertTrue(half.point, half.point.contains("Never estimate"))
    }

    @Test fun `final-stretch reminders change their angle within a run`() {
        fun angle(moving: Double) =
            LivePoints
                .pick(
                    run(List(8) { 300.0 }, extraKm = .5, moving = moving),
                    context(distance(10)),
                    LiveCall(LiveTrigger.MOMENT, LiveMoment.FINAL_STRETCH, LiveFocus.GOAL_PROJECTION, 7),
                    false,
                    milestone = JSONObject().put("kind", "FINAL_STRETCH"),
                )!!
                .hint
        assertNotEquals(angle(2550.0), angle(2730.0))
    }

    @Test fun `routine milestones look ahead instead of celebrating, while a fastest kilometre and the goal still cheer`() {
        val call = LiveCall(LiveTrigger.MOMENT, LiveMoment.KILOMETRE, LiveFocus.SPLIT_DETAIL, 0)

        fun styles(point: LivePoint) = (0..5).map { LiveAiAdvice.style(point, call.copy(number = it)) }.toSet()
        val routine = styles(LivePoint("moment/km:4", "", covers = listOf("steady/km:4")))
        assertEquals(setOf(LiveStyle.NUMBER_FIRST, LiveStyle.LOOK_AHEAD, LiveStyle.WARM), routine)
        assertFalse(LiveStyle.CELEBRATE in styles(LivePoint("moment/halfway", "")))
        assertTrue(LiveStyle.CELEBRATE in styles(LivePoint("moment/km:2", "", covers = listOf("best/km:2", "faster/km_fastest:2"))))
        assertEquals(setOf(LiveStyle.CELEBRATE), styles(LivePoint("moment/goal", "")))
        assertTrue(LiveAiAdvice.system("English").contains("end on that job"))
    }
}
