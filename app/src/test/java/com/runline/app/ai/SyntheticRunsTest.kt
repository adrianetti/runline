package com.runline.app.ai

import com.runline.app.core.RunMetrics
import com.runline.app.core.StopDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticRunsTest {
    private val catalog = SyntheticRuns.catalog().associateBy { it.getString("id") }

    private fun scenario(id: String) = catalog.getValue(id)

    @Test fun `every scenario reaches its goal with plausible GPS and a unique id`() {
        assertEquals(20, catalog.size)
        catalog.values.forEach { spec ->
            val run = SyntheticRuns.run(spec)
            val goal = spec.getJSONObject("goal")
            val km = RunMetrics.distanceMeters(run.points) / 1000
            if (goal.has("km")) assertTrue(spec.getString("id"), km >= goal.getDouble("km"))
            val minutes = goal.optDouble("minutes", 0.0)
            if (goal.getString("type") == "TIME") assertTrue(spec.getString("id"), run.activeDurationMs >= minutes * 60_000)
            assertTrue(spec.getString("id"), run.points.zipWithNext().all { (a, b) -> b.timestampMs > a.timestampMs })
            assertTrue(spec.getString("id"), run.points.all { it.accuracyMeters in 1f..30f && it.altitudeMeters != null })
        }
    }

    @Test fun `the same scenario always produces the same run`() {
        val spec = scenario("s03-hilly-loop-7k")
        assertEquals(SyntheticRuns.run(spec), SyntheticRuns.run(spec))
        assertEquals(SyntheticRuns.history(spec), SyntheticRuns.history(spec))
    }

    @Test fun `traffic lights become detected stops and a pause becomes a second segment`() {
        val city = SyntheticRuns.run(scenario("s07-city-traffic-lights"))
        assertEquals(4, StopDetection.detect(city.points, city.activityType).size)
        val paused = SyntheticRuns.run(scenario("s17-long-pause"))
        assertEquals(setOf(0, 1), paused.points.map { it.segment }.toSet())
        assertEquals(45.5 * 60_000, paused.activeDurationMs.toDouble(), 60_000.0)
    }

    @Test fun `a tunnel leaves a gap in the fixes and sparse GPS keeps its interval`() {
        val tunnel = SyntheticRuns.run(scenario("s10-gps-tunnel")).points
        assertTrue(tunnel.zipWithNext().any { (a, b) -> b.timestampMs - a.timestampMs >= 120_000 })
        val sparse = SyntheticRuns.run(scenario("s20-sparse-gps")).points
        assertEquals(
            3_000L,
            sparse
                .zipWithNext()
                .map { (a, b) -> b.timestampMs - a.timestampMs }
                .distinct()
                .single(),
        )
    }

    @Test fun `hills follow the profile and slow the runner going up`() {
        val run = SyntheticRuns.run(scenario("s13-downhill-out-uphill-back"))
        val heights = run.points.mapNotNull { it.altitudeMeters }
        assertEquals(100.0, heights.max() - heights.min(), 1.0)
        val half = run.points.indexOfFirst { RunMetrics.distanceMeters(run.points.subList(0, run.points.indexOf(it) + 1)) >= 3000 }
        val down = run.points[half].timestampMs - run.points.first().timestampMs
        val up = run.points.last().timestampMs - run.points[half].timestampMs
        assertTrue(up > down)
    }

    @Test fun `history runs come before the scenario and keep its place`() {
        val spec = scenario("s04-long-run-fade-18k")
        val history = SyntheticRuns.history(spec)
        assertEquals(3, history.size)
        val start = SyntheticRuns.run(spec).startedAtMs
        assertTrue(history.all { it.startedAtMs < start && it.id.startsWith("s04-long-run-fade-18k-h") })
        assertEquals(15.0, RunMetrics.distanceMeters(history.first().points) / 1000, 0.45)
    }
}
