package com.runline.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class RunEventsTest {
    private class Track(
        private val accuracy: Float = 5f,
    ) {
        val points = mutableListOf<RunPoint>()
        private var east = 0.0
        private var clock = 0L
        private var active = 0L
        private var segment = 0

        init {
            fix()
        }

        fun move(
            seconds: Int,
            speed: Double,
            every: Int = 1,
        ) = apply {
            repeat(seconds) { second ->
                east += speed
                tick()
                if ((second + 1) % every == 0) fix()
            }
        }

        fun stand(seconds: Int) = apply { repeat(seconds) { tick().also { fix() } } }

        fun silent(
            seconds: Int,
            speed: Double,
        ) = apply { repeat(seconds) { tick().also { east += speed } } }

        fun lost(
            seconds: Int,
            speed: Double,
        ) = apply {
            silent(seconds, speed)
            segment++
        }

        fun pause(seconds: Int) =
            apply {
                clock += seconds * MILLIS
                segment++
            }

        fun session(type: ActivityType = ActivityType.RUN) = RunSession("track", START, START + clock, active, points.toList(), type)

        private fun tick() {
            clock += MILLIS
            active += MILLIS
        }

        private fun fix() {
            val north = ((points.size % JITTER_STEPS) - 1) * JITTER_METERS
            points +=
                RunPoint(
                    LATITUDE + north / METERS_PER_DEGREE,
                    LONGITUDE + east / (METERS_PER_DEGREE * cos(Math.toRadians(LATITUDE))),
                    START + clock,
                    accuracy,
                    segment = segment,
                )
        }
    }

    private fun only(
        report: RunEventReport,
        kind: RunEventKind,
    ): RunEvent {
        assertEquals(report.events.toString(), listOf(kind), report.events.map { it.kind })
        return report.events.single()
    }

    @Test fun `a steady run has no events and a usual pace`() {
        val report = RunEvents.detect(Track().move(600, RUN).session())
        assertTrue(report.events.toString(), report.events.isEmpty())
        assertEquals(1000 / RUN, report.usualPaceSecondsPerKm!!, 8.0)
        assertNull(report.stillSinceMs)
    }

    @Test fun `a traffic light stop keeps its length, where it happened and the pace after it`() {
        val track = Track().move(300, RUN).stand(45).move(180, RUN)
        val stop = only(RunEvents.detect(track.session()), RunEventKind.STOP)
        assertEquals(45_000.0, stop.durationMs.toDouble(), 2_000.0)
        assertFalse(stop.ongoing)
        assertEquals(RunMetrics.distanceMeters(track.points.filter { it.timestampMs <= stop.startMs }), stop.startMeters, 0.001)
        assertEquals(1000 / RUN, stop.paceSinceSecondsPerKm!!, 8.0)
        assertNull(stop.paceSecondsPerKm)
    }

    @Test fun `standing still right now is ongoing and a short hesitation is not a stop`() {
        val waiting = RunEvents.detect(Track().move(300, RUN).stand(30).session())
        assertTrue(only(waiting, RunEventKind.STOP).ongoing)
        assertEquals(START + 300_000.0, waiting.stillSinceMs!!.toDouble(), 2_000.0)
        val hesitating = RunEvents.detect(Track().move(300, RUN).stand(12).session())
        assertTrue(hesitating.events.isEmpty())
        assertTrue(hesitating.stillSinceMs != null)
        val brief =
            RunEvents.detect(
                Track()
                    .move(300, RUN)
                    .stand(8)
                    .move(120, RUN)
                    .session(),
            )
        assertTrue(brief.events.isEmpty())
        assertNull(brief.stillSinceMs)
    }

    @Test fun `a walk break keeps its length and walking pace`() {
        val report =
            RunEvents.detect(
                Track()
                    .move(360, JOG)
                    .move(90, WALK)
                    .move(240, JOG)
                    .session(),
            )
        val walk = only(report, RunEventKind.WALK)
        assertEquals(90_000.0, walk.durationMs.toDouble(), 12_000.0)
        assertEquals(1000 / WALK, walk.paceSecondsPerKm!!, 120.0)
        assertFalse(walk.ongoing)
        assertEquals(1000 / JOG, walk.paceSinceSecondsPerKm!!, 10.0)
        assertEquals(1000 / JOG, report.usualPaceSecondsPerKm!!, 10.0)
    }

    @Test fun `walking right now is ongoing and has no pace since`() {
        val walk = only(RunEvents.detect(Track().move(360, RUN).move(70, WALK).session()), RunEventKind.WALK)
        assertTrue(walk.ongoing)
        assertNull(walk.paceSinceSecondsPerKm)
    }

    @Test fun `slow jogging, a short walk and a walking activity are never walk breaks`() {
        assertTrue(RunEvents.detect(Track().move(360, RUN).move(240, JOG).session()).events.isEmpty())
        assertTrue(
            RunEvents
                .detect(
                    Track()
                        .move(360, RUN)
                        .move(30, WALK)
                        .move(120, RUN)
                        .session(),
                ).events
                .isEmpty(),
        )
        val hike =
            RunEvents.detect(
                Track()
                    .move(360, WALK)
                    .move(120, SLOW_WALK)
                    .stand(40)
                    .move(60, WALK)
                    .session(ActivityType.WALK),
            )
        assertEquals(listOf(RunEventKind.STOP), hike.events.map { it.kind })
        assertNull(hike.usualPaceSecondsPerKm)
    }

    @Test fun `a signal gap and a paused recording are told apart`() {
        val tunnel =
            only(
                RunEvents.detect(
                    Track()
                        .move(300, RUN)
                        .silent(90, RUN)
                        .move(120, RUN)
                        .session(),
                ),
                RunEventKind.GPS_GAP,
            )
        assertEquals(91_000.0, tunnel.durationMs.toDouble(), 1_000.0)
        assertEquals(91 * RUN, tunnel.meters, 5.0)
        val paused =
            only(
                RunEvents.detect(
                    Track()
                        .move(300, RUN)
                        .pause(60)
                        .move(120, RUN)
                        .session(),
                ),
                RunEventKind.PAUSE,
            )
        assertEquals(61_000.0, paused.durationMs.toDouble(), 1_000.0)
        assertTrue(
            RunEvents
                .detect(
                    Track()
                        .move(300, RUN)
                        .silent(20, RUN)
                        .move(60, RUN)
                        .pause(10)
                        .move(120, RUN)
                        .session(),
                ).events
                .isEmpty(),
        )
    }

    @Test fun `a fast block is measured against the running before it`() {
        val report =
            RunEvents.detect(
                Track()
                    .move(360, RUN)
                    .move(120, FAST)
                    .move(180, RUN)
                    .session(),
            )
        val block = only(report, RunEventKind.FAST)
        assertEquals(120_000.0, block.durationMs.toDouble(), 12_000.0)
        assertEquals(1000 / FAST, block.paceSecondsPerKm!!, 8.0)
        assertEquals(1000 / RUN, block.referencePaceSecondsPerKm!!, 8.0)
        assertFalse(block.ongoing)
        assertEquals(1000 / RUN, block.paceSinceSecondsPerKm!!, 10.0)
    }

    @Test fun `a surge happening now is ongoing and every repeat of an interval session counts`() {
        assertTrue(only(RunEvents.detect(Track().move(360, RUN).move(90, FAST).session()), RunEventKind.FAST).ongoing)
        val session = Track().move(360, RUN)
        repeat(REPEATS) { session.move(90, FAST).move(120, RUN) }
        assertEquals(List(REPEATS) { RunEventKind.FAST }, RunEvents.detect(session.session()).events.map { it.kind })
    }

    @Test fun `a gentle lift, a fast start and sparse fixes never make a fast block`() {
        assertTrue(RunEvents.detect(Track().move(360, RUN).move(240, RUN * LIFT).session()).events.isEmpty())
        assertTrue(RunEvents.detect(Track().move(150, FAST).move(360, RUN).session()).events.isEmpty())
        val sparse = RunEvents.detect(Track(accuracy = 25f).move(600, RUN, every = 3).session())
        assertTrue(sparse.events.isEmpty())
    }

    @Test fun `a signal loss that starts a new segment is a gap, not a pause`() {
        val lost =
            RunEvents.detect(
                Track()
                    .move(300, RUN)
                    .lost(90, RUN)
                    .move(120, RUN)
                    .session(),
            )
        val gap = only(lost, RunEventKind.GPS_GAP)
        assertEquals(91_000.0, gap.durationMs.toDouble(), 1_000.0)
        assertTrue(lost.events.none { it.kind == RunEventKind.PAUSE })
        val both =
            RunEvents.detect(
                Track()
                    .move(300, RUN)
                    .lost(90, RUN)
                    .move(120, RUN)
                    .pause(60)
                    .move(120, RUN)
                    .session(),
            )
        assertEquals(listOf(RunEventKind.GPS_GAP, RunEventKind.PAUSE), both.events.map { it.kind })
    }

    @Test fun `a run with many pauses still counts each one as a pause`() {
        val track = Track().move(120, RUN)
        repeat(MANY_PAUSES) { track.pause(40).move(60, RUN) }
        val events = RunEvents.detect(track.session()).events
        assertEquals(List(MANY_PAUSES) { RunEventKind.PAUSE }, events.map { it.kind })
    }

    @Test fun `standing at the start before running is not a stop`() {
        assertTrue(RunEvents.detect(Track().stand(40).move(300, RUN).session()).events.isEmpty())
    }

    @Test fun `an empty run has no events`() {
        val report = RunEvents.detect(RunSession("empty", START, START, 0, emptyList()))
        assertTrue(report.events.isEmpty())
        assertNull(report.endMs)
    }

    private companion object {
        const val START = 1_700_000_000_000L
        const val MILLIS = 1_000L
        const val LATITUDE = 37.0
        const val LONGITUDE = -3.6
        const val METERS_PER_DEGREE = 111_195.0802
        const val JITTER_STEPS = 3
        const val JITTER_METERS = 1.0
        const val RUN = 3.0
        const val JOG = 2.0
        const val FAST = 3.8
        const val MANY_PAUSES = 13
        const val WALK = 1.1
        const val SLOW_WALK = 0.9
        const val LIFT = 1.06
        const val REPEATS = 3
    }
}
