package com.runline.app.ai

import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class LiveEventsTest {
    private val easy = JSONObject().put("type", "DISTANCE").put("distance_goal_m", 5000).put("intended_effort", "EASY")
    private val band = JSONObject().put("type", "PACE").put("pace_min_seconds_per_km", 300).put("pace_max_seconds_per_km", 310)
    private val cadence = LiveCall(LiveTrigger.CADENCE, null, LiveFocus.PACE_TREND, 3)
    private val kilometre = LiveCall(LiveTrigger.MOMENT, LiveMoment.KILOMETRE, LiveFocus.SPLIT_DETAIL, 3)

    private fun event(
        kind: String,
        number: Int = 1,
        ongoing: Boolean = false,
        endedAgo: Double = 40.0,
        duration: Double = 45.0,
    ) = JSONObject()
        .put("kind", kind)
        .put("number", number)
        .put("ongoing", ongoing)
        .put("ended_seconds_ago", if (ongoing) 0.0 else endedAgo)
        .put("started_seconds_ago", endedAgo + duration)
        .put("duration_seconds", duration)
        .put("kilometre", 2)
        .put("to_kilometre", 2)

    private fun run(
        vararg items: JSONObject,
        splits: List<Double> = listOf(340.0),
        laps: List<Double> = emptyList(),
        stoppedNow: Double? = null,
        counts: Map<String, Int> = items.groupingBy { it.getString("kind") }.eachCount(),
    ) = JSONObject()
        .put("activity_type", "run")
        .put("moving_seconds", 900.0)
        .put("distance_km", splits.size + .5)
        .put("average_pace_seconds_per_km", 900.0 / (splits.size + .5))
        .put("recent_pace_seconds_per_km", JSONObject.NULL)
        .put("recent_pace_comparison", JSONObject.NULL)
        .put(
            "splits",
            JSONArray(splits.mapIndexed { i, s -> JSONObject().put("number", i + 1).put(PACE, s).put("is_full_kilometre", true) }),
        ).put("estimated_repeated_laps", JSONArray(laps.map { JSONObject().put("duration_seconds", it).put("distance_m", 970) }))
        .put(
            AiRunEvents.KEY,
            JSONObject()
                .put("interrupted", items.any { it.getString("kind") in setOf("STOP", "WALK", "PAUSE") })
                .put("stopped_now_seconds", stoppedNow ?: JSONObject.NULL)
                .put("counts", JSONObject(counts))
                .put("items", JSONArray(items.toList())),
        )

    private fun context(goal: JSONObject? = null) =
        JSONObject()
            .put("today", "2026-11-10")
            .put("active_goal", goal ?: JSONObject.NULL)
            .put("recent_runs", JSONArray())

    private fun km(number: Int) = JSONObject().put("kind", "KILOMETRE").put("number", number)

    private fun pick(
        run: JSONObject,
        goal: JSONObject? = null,
        used: List<String> = emptyList(),
        call: LiveCall = cadence,
        milestone: JSONObject? = null,
    ) = LivePoints.pick(run, context(goal), call, true, used, milestone)

    @Test fun `a finished stop is news right after it and never while standing or once stale`() {
        val point = pick(run(event("STOP")))!!
        assertEquals("event/stop:1", point.id)
        assertEquals("45 segundos", point.numbers["standing_still_for"])
        assertTrue(point.hint!!.contains("not a slowdown"))
        assertFalse(pick(run(event("STOP", endedAgo = 300.0)))?.id.orEmpty().startsWith("event"))
        assertNull(pick(run(event("WALK", ongoing = true, duration = 60.0), stoppedNow = 12.0)))
        assertNull(pick(run(event("STOP", ongoing = true))))
        assertNotNull(pick(run(event("STOP", ongoing = true)), call = kilometre, milestone = km(1)))
    }

    @Test fun `a walk break is news while it lasts and when it ends, with hints for the day`() {
        val walking = pick(run(event("WALK", ongoing = true, duration = 63.0)), easy)!!
        assertEquals("event/walk:1", walking.id)
        assertEquals("1 minuto 5", walking.numbers["walking_for"])
        assertTrue(walking.hint!!.contains("normal part of building up"))
        assertTrue(pick(run(event("WALK", ongoing = true, duration = 63.0)), band)!!.hint!!.contains("matter-of-fact"))
        val back = pick(run(event("WALK", duration = 90.0).put(SINCE, 305.0)), easy)!!
        assertEquals("event/walk_end:1", back.id)
        assertEquals("1 minuto 30", back.numbers["the_walk_break_lasted"])
        assertFalse(back.numbers.containsKey("pace_since_the_walk"))
        val paced = pick(run(event("WALK", duration = 90.0).put(SINCE, 305.0)), band)!!
        assertEquals("5 minutos 5 por kilómetro", paced.numbers["pace_since_the_walk"])
    }

    @Test fun `signal loss and a paused recording are explained with their length`() {
        val signal = pick(run(event("GPS_GAP", duration = 121.0)))!!
        assertEquals("event/gps:1", signal.id)
        assertEquals("2 minutos", signal.numbers["signal_lost_for"])
        assertTrue(signal.point.contains("estimates"))
        val paused = pick(run(event("PAUSE", duration = 4_000.0)))!!
        assertEquals("event/pause:1", paused.id)
        assertEquals("1 hora 7 minutos", paused.numbers["paused_for"])
    }

    @Test fun `told events are not repeated and the latest event comes first`() {
        val both = run(event("STOP", endedAgo = 150.0), event("GPS_GAP", endedAgo = 20.0, duration = 40.0))
        assertEquals("event/gps:1", pick(both)!!.id)
        assertEquals("event/stop:1", pick(both, used = listOf("event/gps:1", "form/0:1", "moment/km:1"))!!.id)
        assertFalse(pick(run(event("STOP")), used = listOf("event/stop:1"))?.id.orEmpty().startsWith("event"))
    }

    @Test fun `a kilometre that included a stop, a walk or lost signal says so instead of comparing`() {
        val km2 = km(2)
        val splits = listOf(340.0, 368.0)
        val stop = pick(run(event("STOP"), splits = splits), call = kilometre, milestone = km2)!!
        assertEquals("moment/km:2", stop.id)
        assertTrue(stop.point.contains("included a stop"))
        assertEquals("6 minutos 8", stop.numbers["time_of_this_kilometre"])
        assertEquals("45 segundos", stop.numbers["standing_still_for"])
        assertEquals(listOf("event/stop:1"), stop.covers)
        val told = pick(run(event("STOP"), splits = splits), used = listOf("event/stop:1"), call = kilometre, milestone = km2)!!
        assertFalse(told.numbers.containsKey("standing_still_for"))
        assertTrue(told.hint!!.contains("mentioned a moment ago"))
        val walk = pick(run(event("WALK", duration = 90.0), splits = splits), call = kilometre, milestone = km2)!!
        assertEquals(listOf("event/walk:1", "event/walk_end:1"), walk.covers)
        assertTrue(walk.point.contains("walk break"))
        val gps = pick(run(event("GPS_GAP", duration = 120.0), splits = splits), call = kilometre, milestone = km2)!!
        assertTrue(gps.point.contains("only an estimate"))
        val earlier = event("STOP").put("kilometre", 1).put("to_kilometre", 1)
        val elsewhere = pick(run(earlier, splits = splits), call = kilometre, milestone = km2)!!
        assertFalse(elsewhere.point.contains("included"))
    }

    @Test fun `a lap that included a walk says so and an older walk changes nothing`() {
        val lap = LiveCall(LiveTrigger.MOMENT, LiveMoment.LAP, LiveFocus.SPLIT_DETAIL, 3)
        val lap2 = JSONObject().put("kind", "LAP").put("number", 2)
        val laps = listOf(560.0, 612.0)
        val walked = pick(run(event("WALK", endedAgo = 100.0, duration = 90.0), laps = laps), call = lap, milestone = lap2)!!
        assertEquals("moment/lap:2", walked.id)
        assertTrue(walked.point.contains("included a walk break"))
        assertEquals("10 minutos 12", walked.numbers["time_of_this_lap"])
        val older = pick(run(event("WALK", endedAgo = 900.0, duration = 90.0), laps = laps), call = lap, milestone = lap2)!!
        assertFalse(older.point.contains("included"))
    }

    @Test fun `the goal announcement counts walk breaks and stops and never claims an unbroken run`() {
        val goal = LiveCall(LiveTrigger.MOMENT, LiveMoment.GOAL_REACHED, LiveFocus.MORALE, 3)
        val reached = JSONObject().put("kind", "GOAL_REACHED")
        val point = pick(run(event("WALK"), counts = mapOf("WALK" to 1, "STOP" to 2)), easy, call = goal, milestone = reached)!!
        assertTrue(point.point, point.point.endsWith("Along the way there were 1 walk break and 2 stops."))
        assertTrue(point.hint!!.endsWith("Never say they ran it without stopping."))
        val once = pick(run(event("STOP"), counts = mapOf("STOP" to 1)), easy, call = goal, milestone = reached)!!
        assertTrue(once.point.endsWith("Along the way there was 1 stop."))
        assertFalse(pick(run(), easy, call = goal, milestone = reached)!!.point.contains("Along the way"))
    }

    @Test fun `unbroken claims are rejected only when the run had stops or walk breaks`() {
        val interrupted = run(event("WALK"))
        assertNotNull(LiveEvents.continuity("¡Tres kilómetros sin parar!", interrupted))
        assertNotNull(LiveEvents.continuity("Lo has hecho de principio a fin.", interrupted))
        assertNotNull(LiveEvents.continuity("Without stopping, brilliant work.", interrupted))
        assertNull(LiveEvents.continuity("Para un momento el reloj y respira.", interrupted))
        assertNull(LiveEvents.continuity("¡Tres kilómetros sin parar!", run(event("GPS_GAP"))))
        assertNull(LiveEvents.continuity("¡Tres kilómetros sin parar!", JSONObject()))
    }

    @Test fun `time on feet never says without stopping after a stop or walk`() {
        val morale = LiveCall(LiveTrigger.CADENCE, null, LiveFocus.MORALE, 3)
        val stopped = pick(run(event("STOP", endedAgo = 600.0)), call = morale)!!
        assertTrue(stopped.id.startsWith("banked/time"))
        assertTrue(stopped.point.contains("on the move"))
        assertTrue(stopped.hint!!.contains("Never say the running was unbroken"))
        assertTrue(pick(run(), call = morale)!!.point.contains("without stopping"))
    }

    @Test fun `two kilometres that lift or fade together are not called the same rhythm`() {
        val lifted = pick(run(splits = listOf(345.0, 344.0, 346.0, 345.0, 325.0, 324.0)), call = kilometre, milestone = km(6))!!
        assertTrue(lifted.point.contains("held the quicker pace"))
        assertTrue(lifted.hint!!.contains("Never call it the rhythm of the whole run"))
        val faded = pick(run(splits = listOf(300.0, 301.0, 299.0, 320.0, 321.0)), call = kilometre, milestone = km(5))!!
        assertTrue(faded.point.contains("both clearly slower"))
        val fadedEasy = pick(run(splits = listOf(300.0, 301.0, 299.0, 320.0, 321.0)), easy, call = kilometre, milestone = km(5))!!
        assertTrue(fadedEasy.hint!!.startsWith("Fine on an easy day"))
        val even = pick(run(splits = listOf(300.0, 301.0, 299.0, 300.0, 301.0)), call = kilometre, milestone = km(5))!!
        assertTrue(even.point.contains("almost exactly the same time"))
    }

    @Test fun `event lines take a warm or punchy style`() {
        fun styles(id: String) = (0..5).map { LiveAiAdvice.style(LivePoint(id, "p"), cadence.copy(number = it)) }.toSet()
        val styles = styles("event/stop:1")
        assertEquals(setOf(LiveStyle.WARM, LiveStyle.CALM, LiveStyle.LOOK_AHEAD), styles)
    }

    @Test fun `the run summary carries phone-detected events with their numbers`() {
        val points = mutableListOf<RunPoint>()
        var east = 0.0
        var clock = 0L

        fun fix() {
            points += RunPoint(LATITUDE, LONGITUDE + east / (METERS_PER_DEGREE * cos(Math.toRadians(LATITUDE))), START + clock, 5f)
        }

        fun move(seconds: Int) =
            repeat(seconds) {
                east += SPEED
                clock += MILLIS
                fix()
            }

        fun stand(seconds: Int) =
            repeat(seconds) {
                clock += MILLIS
                fix()
            }
        fix()
        move(300)
        stand(45)
        move(120)
        stand(40)
        move(60)
        val summary = AiRunSummary.of(RunSession("events", START, START + clock, clock, points.toList()))
        val events = summary.getJSONObject(AiRunEvents.KEY)
        assertTrue(events.getBoolean("interrupted"))
        assertEquals(2, events.getJSONObject("counts").getInt("STOP"))
        assertTrue(events.isNull("stopped_now_seconds"))
        val second = events.getJSONArray("items").getJSONObject(1)
        assertEquals("STOP", second.getString("kind"))
        assertEquals(2, second.getInt("number"))
        assertEquals(2, second.getInt("kilometre"))
        assertEquals(40.0, second.getDouble("duration_seconds"), 2.0)
        assertEquals(60.0, second.getDouble("ended_seconds_ago"), 2.0)
        assertTrue(second.isNull("pace_seconds_per_km"))
        stand(15)
        val waiting = AiRunSummary.of(RunSession("events", START, START + clock, clock, points.toList())).getJSONObject(AiRunEvents.KEY)
        assertEquals(15.0, waiting.getDouble("stopped_now_seconds"), 2.0)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val MILLIS = 1_000L
        const val LATITUDE = 37.0
        const val LONGITUDE = -3.6
        const val METERS_PER_DEGREE = 111_195.0802
        const val SPEED = 3.0
        const val PACE = "pace_seconds_per_km"
        const val SINCE = "pace_since_seconds_per_km"
    }
}
