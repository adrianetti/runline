package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class ReplayTrailTest {
    private fun run(): RunSession {
        var time = 0L
        val first = (0 until 30).map { RunPoint(37.17 + it * 2.7e-5, -3.6, time.also { time += 1_000 }, 4f) }
        time += 60_000
        val second = (0 until 30).map { RunPoint(37.18 + it * 2.7e-5, -3.6, time.also { time += 1_000 }, 4f, segment = 1) }
        return RunSession("trail", 0, time, time, first + second)
    }
    private fun route(replay: RunReplay) = replay.segments.flatMapIndexed { index, points -> points.map { it.copy(segment = index) } }

    @Test fun `the trail ends at the arrow and grows only behind it`() {
        val replay = RunReplay(run()); val route = route(replay)
        assertEquals(0, ReplayTrail.passed(route, replay.at(0)!!.point))
        val mid = replay.at(10_500)!!.point
        val passed = ReplayTrail.passed(route, mid)
        assertEquals(10, passed)
        val head = ReplayTrail.head(route, 4, passed, mid)
        assertEquals(route.subList(4, 11) + mid, head)
        assertSame(mid, head.last())
        assertEquals(route.lastIndex, ReplayTrail.passed(route, replay.at(replay.durationMs)!!.point))
        assertTrue(ReplayTrail.passed(route, replay.at(2_000)!!.point) < passed)
    }

    @Test fun `while playing the map trail stops 400 m behind the arrow, within the arrow's segment`() {
        val replay = RunReplay(run()); val route = route(replay)
        val mid = replay.at(25_000)!!.point
        val passed = ReplayTrail.passed(route, mid)
        assertEquals(-1, ReplayTrail.behind(route, passed, 400.0))
        assertEquals(passed - 4, ReplayTrail.behind(route, passed, 10.0))
        val later = ReplayTrail.passed(route, replay.at(replay.durationMs)!!.point)
        assertEquals(29, ReplayTrail.behind(route, later, 400.0))
        assertEquals(-1, ReplayTrail.behind(route, -1, 400.0))
    }

    @Test fun `the head never joins across a pause`() {
        val replay = RunReplay(run()); val route = route(replay)
        val after = replay.at(31_500)!!.point
        assertEquals(1, after.segment)
        val passed = ReplayTrail.passed(route, after)
        assertEquals(32, passed)
        val head = ReplayTrail.head(route, 20, passed, after)
        assertTrue(head.all { it.segment == 1 })
        assertTrue(ReplayTrail.head(route, 0, -1, after).isEmpty())
        assertEquals(-1, ReplayTrail.passed(route, null))
    }
}
