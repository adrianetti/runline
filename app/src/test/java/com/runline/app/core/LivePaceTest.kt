package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class LivePaceTest {
    private fun points(speed: Double = 1000.0 / 330, count: Int = 21) = (0 until count).map { i ->
        RunPoint(37.0 + i * speed / 111195.08, -3.0, 1_000L + i * 1_000L, 4f, null, 0)
    }

    @Test fun showsRecentFiveThirtyPaceInsteadOfWholeRunAverage() {
        val fast = points()
        assertEquals(330.0, LivePace.secondsPerKm(fast, fast.last().timestampMs)!!, .01)
        val extended = fast + (1..10).map { i -> fast.last().copy(latitude = fast.last().latitude + i * 2.0 / 111195.08,
            timestampMs = fast.last().timestampMs + i * 1_000L) }
        assertEquals(500.0, LivePace.secondsPerKm(extended, extended.last().timestampMs)!!, .01)
    }

    @Test fun unavailableWhileAcquiringOrAfterGpsGoesStale() {
        assertNull(LivePace.secondsPerKm(emptyList(), 0))
        assertNull(LivePace.secondsPerKm(points(count = 5), 5_000))
        val sample = points()
        assertNull(LivePace.secondsPerKm(sample, sample.last().timestampMs + 5_001))
        assertNull(LivePace.secondsPerKm(sample, sample.last().timestampMs - 1))
    }

    @Test fun pausesGapsAndBadFixesCannotBorrowEarlierPace() {
        val sample = points()
        val last = sample.last()
        for (next in listOf(last.copy(timestampMs = last.timestampMs + 1_000, segment = 1),
            last.copy(timestampMs = last.timestampMs + 16_000),
            last.copy(timestampMs = last.timestampMs + 1_000, accuracyMeters = 40f),
            last.copy(timestampMs = last.timestampMs + 1_000, latitude = Double.NaN),
            last.copy(timestampMs = last.timestampMs + 1_000, latitude = 38.0), last)) {
            assertNull(LivePace.secondsPerKm(sample + next, next.timestampMs))
        }
    }

    @Test fun stoppingDoesNotLeaveAnOldMovingPaceVisible() {
        val sample = points()
        val stopped = sample + (1..10).map { sample.last().copy(timestampMs = sample.last().timestampMs + it * 1_000) }
        assertNull(LivePace.secondsPerKm(stopped, stopped.last().timestampMs))
        assertNull(LivePace.secondsPerKm(points(speed = .1), 21_000))
    }

    @Test fun windowBoundaryIsWeightedWithoutChangingOriginalSamples() {
        val sample = points(count = 7).mapIndexed { i, point -> point.copy(timestampMs = 1_000L + i * 2_000L) }
        val before = sample.toList()
        assertEquals(660.0, LivePace.secondsPerKm(sample, sample.last().timestampMs)!!, .01)
        assertEquals(before, sample)
    }
}
