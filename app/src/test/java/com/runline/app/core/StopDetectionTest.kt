package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class StopDetectionTest {
    private val metresPerDegree = 111195.0802335329

    /** Synthetic recorder output: moving fixes every second; standing still yields sparse 10 s fixes. */
    private class Track(private val start: Long = 1_000_000L) {
        val points = mutableListOf<RunPoint>()
        var time = start; var east = 0.0; var segment = 0
        fun move(seconds: Int, speed: Double, accuracy: Float = 4f) = repeat(seconds) {
            time += 1_000; east += speed; add(accuracy)
        }
        /** Recorder drops fixes within 2.5 m for 10 s, so a standstill keeps one fix per 10 s with GPS jitter. */
        fun stand(seconds: Int, jitter: Double = 1.0, accuracy: Float = 10f) {
            var waited = 0
            while (waited + 10 <= seconds) { waited += 10; time += 10_000; east += if (waited / 10 % 2 == 0) jitter else -jitter; add(accuracy) }
            time += (seconds - waited) * 1_000L
        }
        private fun add(accuracy: Float) { points += RunPoint(37.17, -3.6 + east / (111195.0802335329 * Math.cos(Math.toRadians(37.17))), time, accuracy, segment = segment) }
        fun session() = RunSession("stops", start, time, time - start, points.toList())
    }

    @Test fun `traffic light and shoelace stops are found and moving time excludes them`() {
        val t = Track()
        t.points += RunPoint(37.17, -3.6, t.time, 4f)
        t.move(120, 3.0); t.stand(35); t.move(200, 3.0); t.stand(60); t.move(60, 3.0)
        val stops = StopDetection.detect(t.points)
        assertEquals(2, stops.size)
        assertEquals(35_000.0, stops[0].durationMs.toDouble(), 6_000.0)
        assertEquals(60_000.0, stops[1].durationMs.toDouble(), 6_000.0)
        val run = t.session()
        assertEquals(run.activeDurationMs - StopDetection.totalMs(stops), StopDetection.movingDurationMs(run, stops))
    }

    @Test fun `continuous running, slow jogging and walking are never stops`() {
        val run = Track().apply { points += RunPoint(37.17, -3.6, time, 4f); move(300, 3.2); move(120, 1.4); move(300, 2.6) }
        assertTrue(StopDetection.detect(run.points).isEmpty())
        val walk = Track().apply { points += RunPoint(37.17, -3.6, time, 4f); move(600, .9) }
        assertTrue(StopDetection.detect(walk.points, ActivityType.WALK).isEmpty())
    }

    @Test fun `brief hesitations under five seconds do not count`() {
        val t = Track().apply { points += RunPoint(37.17, -3.6, time, 4f); move(60, 3.0); time += 3_000; move(60, 3.0) }
        assertTrue(StopDetection.detect(t.points).isEmpty())
    }

    @Test fun `stops never span a manual pause and never exceed active time`() {
        val t = Track()
        t.points += RunPoint(37.17, -3.6, t.time, 4f)
        t.move(30, 3.0); t.stand(20); t.segment = 1; t.time += 120_000; t.move(30, 3.0)
        val run = t.session().copy(activeDurationMs = 70_000)
        val stops = StopDetection.detect(run.points)
        assertTrue(stops.all { it.endMs <= run.points.filter { p -> p.segment == 0 }.last().timestampMs })
        assertTrue(StopDetection.movingDurationMs(run, stops) in 0..run.activeDurationMs)
        assertEquals(0L, StopDetection.movingDurationMs(run.copy(activeDurationMs = 1_000), stops))
    }

    @Test fun `overlapping intervals and an open live stop count once`() {
        assertEquals(30_000L, StopDetection.totalMs(listOf(StopInterval(0, 20_000), StopInterval(10_000, 30_000))))
        val run = RunSession("open", 0, 100_000, 100_000, emptyList())
        assertEquals(80_000L, StopDetection.movingDurationMs(run, emptyList(), openStopSinceMs = 80_000, nowMs = 100_000))
        // A just-started standstill shorter than the minimum is not subtracted yet.
        assertEquals(100_000L, StopDetection.movingDurationMs(run, emptyList(), openStopSinceMs = 97_000, nowMs = 100_000))
    }

    @Test fun `kilometre splits can exclude standstill time`() {
        val t = Track()
        t.points += RunPoint(37.17, -3.6, t.time, 4f)
        t.move(150, 4.0); t.stand(40); t.move(150, 4.0)
        val run = t.session()
        val stops = StopDetection.detect(run.points)
        val withStops = RunMetrics.splits(run)
        val moving = RunMetrics.splits(run, stops)
        assertEquals(withStops.size, moving.size)
        assertEquals(250_000.0, moving.first().durationMs.toDouble(), 3_000.0)
        assertTrue(withStops.first().durationMs - moving.first().durationMs >= 34_000)
    }

    @Test fun `live tracker uses Doppler speed quickly and position as a fallback`() {
        val tracker = StillnessTracker()
        var time = 0L
        repeat(4) { time += 1_000; assertNull(tracker.update(37.17, -3.6, time, 5f, .2f)) }
        repeat(3) { time += 1_000; tracker.update(37.17, -3.6, time, 5f, .2f) }
        assertEquals(1_000L, tracker.stoppedSinceMs)
        time += 1_000; assertNull(tracker.update(37.1701, -3.6, time, 5f, 3f))
        val fallback = StillnessTracker()
        repeat(8) { fallback.update(37.17, -3.6 + it * 1e-7, 10_000L + it * 1_000, 5f, null) }
        assertNotNull(fallback.stoppedSinceMs)
        // A provider that always reports zero speed (emulators, some chipsets) cannot hide movement.
        val zero = StillnessTracker()
        repeat(30) { zero.update(37.17, -3.6 + it * 3.0 / (metresPerDegree * .797), 10_000L + it * 1_000, 5f, 0f) }
        assertNull(zero.stoppedSinceMs)
        // Walking speed without Doppler drifts away from the anchor and is not a standstill.
        val walker = StillnessTracker(ActivityType.WALK)
        repeat(20) { walker.update(37.17, -3.6 + it * 1.3 / (metresPerDegree * .797), 10_000L + it * 1_000, 8f, null) }
        assertNull(walker.stoppedSinceMs)
    }
}
