package com.runline.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RouteRunEstimateTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val now = Instant.parse("2026-11-01T12:00:00Z").toEpochMilli()

    private fun stats(
        distanceMeters: Double = ROUTE_METERS,
        ascentMeters: Double? = ROUTE_ASCENT,
        walkingMinutes: Int = WALKING_MINUTES,
    ) = RouteStats(
        distanceMeters,
        ascentMeters,
        ascentMeters,
        ascentMeters?.let { BASE_HEIGHT },
        ascentMeters?.let { BASE_HEIGHT + it },
        walkingMinutes,
        distanceMeters / METERS_PER_KM + (ascentMeters ?: 0.0) / ASCENT_PER_EFFORT_KM,
        RouteDifficulty.MODERATE,
        true,
    )

    private fun activity(
        at: String,
        meters: Double,
        durationMs: Long,
        type: ActivityType = ActivityType.RUN,
    ) = HistoryActivity(
        at,
        type,
        Instant.parse(at).toEpochMilli(),
        durationMs,
        meters,
        RunMetrics.paceSecondsPerKm(meters, durationMs),
    )

    @Test fun aFlatRouteTakesItsDistanceAtTheGivenPace() {
        assertEquals(50, RouteRunEstimate.minutes(10_000.0, 0.0, 300.0))
        assertEquals(25, RouteRunEstimate.minutes(5_000.0, 0.0, 300.0))
        assertEquals(105, RouteRunEstimate.minutes(21_000.0, 0.0, 300.0))
    }

    @Test fun eachMetreOfAscentCountsAsScarfsFlatDistance() {
        assertEquals(7.92, RouteRunEstimate.FLAT_METERS_PER_ASCENT_METER, 0.0)
        assertEquals(87, RouteRunEstimate.minutes(ROUTE_METERS, ROUTE_ASCENT, RUN_PACE))
        assertEquals(90, RouteRunEstimate.minutes(10_000.0, 1_000.0, 300.0))
    }

    @Test fun moreAscentNeverShortensTheEstimate() {
        val estimates = listOf(0.0, 100.0, 400.0, 1_200.0).map { RouteRunEstimate.minutes(ROUTE_METERS, it, RUN_PACE)!! }
        assertEquals(estimates.sorted(), estimates)
        assertEquals(estimates.distinct(), estimates)
    }

    @Test fun unknownAscentUsesTheDistanceAlone() {
        assertEquals(75, RouteRunEstimate.minutes(ROUTE_METERS, null, RUN_PACE))
        assertEquals(75, RouteRunEstimate.minutes(ROUTE_METERS, Double.NaN, RUN_PACE))
        assertEquals(75, RouteRunEstimate.minutes(ROUTE_METERS, Double.POSITIVE_INFINITY, RUN_PACE))
        assertEquals(75, RouteRunEstimate.minutes(ROUTE_METERS, -40.0, RUN_PACE))
    }

    @Test fun theEstimateRoundsToTheNearestMinute() {
        assertEquals(5, RouteRunEstimate.minutes(1_000.0, null, 329.0))
        assertEquals(6, RouteRunEstimate.minutes(1_000.0, null, 331.0))
        assertEquals(0, RouteRunEstimate.minutes(50.0, null, 300.0))
    }

    @Test fun anUnusableDistanceGivesNoEstimate() {
        assertNull(RouteRunEstimate.minutes(0.0, ROUTE_ASCENT, RUN_PACE))
        assertNull(RouteRunEstimate.minutes(-1.0, ROUTE_ASCENT, RUN_PACE))
        assertNull(RouteRunEstimate.minutes(Double.NaN, ROUTE_ASCENT, RUN_PACE))
        assertNull(RouteRunEstimate.minutes(Double.POSITIVE_INFINITY, ROUTE_ASCENT, RUN_PACE))
    }

    @Test fun anUnusablePaceGivesNoEstimate() {
        assertNull(RouteRunEstimate.minutes(ROUTE_METERS, ROUTE_ASCENT, 0.0))
        assertNull(RouteRunEstimate.minutes(ROUTE_METERS, ROUTE_ASCENT, -300.0))
        assertNull(RouteRunEstimate.minutes(ROUTE_METERS, ROUTE_ASCENT, Double.NaN))
        assertNull(RouteRunEstimate.minutes(ROUTE_METERS, ROUTE_ASCENT, Double.POSITIVE_INFINITY))
    }

    @Test fun aRouteWithARunPaceShowsTheRunningEstimate() {
        assertEquals(87, RouteRunEstimate.forRoute(stats(), RUN_PACE))
        assertEquals(75, RouteRunEstimate.forRoute(stats(ascentMeters = null), RUN_PACE))
    }

    @Test fun aRouteWithoutARunPaceKeepsTheWalkingEstimate() {
        assertEquals(WALKING_MINUTES, RouteRunEstimate.forRoute(stats(), null))
        assertEquals(WALKING_MINUTES, RouteRunEstimate.forRoute(stats(), 0.0))
        assertEquals(WALKING_MINUTES, RouteRunEstimate.forRoute(stats(), Double.NaN))
    }

    @Test fun aRunningEstimateNeverExceedsTheWalkingEstimate() {
        assertEquals(WALKING_MINUTES, RouteRunEstimate.forRoute(stats(), 900.0))
        assertEquals(40, RouteRunEstimate.forRoute(stats(walkingMinutes = 40), RUN_PACE))
    }

    @Test fun aRunningEstimateLeavesTheRouteStatsAsTheyWere() {
        val before = stats()
        val copy = before.copy()
        RouteRunEstimate.forRoute(before, RUN_PACE)
        assertEquals(copy, before)
        assertEquals(WALKING_MINUTES, before.estimatedMinutes)
        assertEquals(RouteDifficulty.MODERATE, before.difficulty)
    }

    @Test fun aRealRouteProfileRunsFasterThanItsToblerWalk() {
        val climb = HikingRoute.of("climb", "Climb", (0..100).map { Triple(37.0 + it * 0.00045, -3.6, 700.0 + it * 5.0) })
        val running = RouteRunEstimate.forRoute(climb.stats, 300.0)
        assertEquals(45.0, running.toDouble(), 1.0)
        assertEquals(85.0, climb.stats.estimatedMinutes.toDouble(), 4.0)
        val flat = HikingRoute.of("flat", "Flat", (0..100).map { Triple(37.0 + it * 0.00045, -3.6, null) })
        assertNull(flat.stats.ascentMeters)
        assertEquals(25.0, RouteRunEstimate.forRoute(flat.stats, 300.0).toDouble(), 1.0)
    }

    @Test fun theRecentPaceIsTheDistanceWeightedPaceOfTheLastFourWeeksOfRuns() {
        val history =
            listOf(
                activity("2026-10-31T08:00:00Z", meters = 1_000.0, durationMs = 300_000),
                activity("2026-10-20T09:00:00Z", meters = 9_000.0, durationMs = 3_600_000),
                activity("2026-10-31T11:00:00Z", meters = 1_000.0, durationMs = 900_000, type = ActivityType.WALK),
                activity("2026-10-30T11:00:00Z", meters = 8_000.0, durationMs = 7_200_000, type = ActivityType.HIKE),
            )
        assertEquals(390.0, RouteRunEstimate.recentPace(ActivityType.RUN, history, now, zone)!!, 0.001)
    }

    @Test fun walksAndHikesNeverGetARunPace() {
        val history = listOf(activity("2026-10-31T08:00:00Z", meters = 1_000.0, durationMs = 300_000))
        assertNull(RouteRunEstimate.recentPace(ActivityType.WALK, history, now, zone))
        assertNull(RouteRunEstimate.recentPace(ActivityType.HIKE, history, now, zone))
    }

    @Test fun aRunnerWithoutRecentRunsHasNoRunPace() {
        assertNull(RouteRunEstimate.recentPace(ActivityType.RUN, emptyList(), now, zone))
        val old = listOf(activity("2026-09-01T08:00:00Z", meters = 5_000.0, durationMs = 1_500_000))
        assertNull(RouteRunEstimate.recentPace(ActivityType.RUN, old, now, zone))
        val untimed = listOf(activity("2026-10-31T08:00:00Z", meters = 0.0, durationMs = 600_000))
        assertNull(RouteRunEstimate.recentPace(ActivityType.RUN, untimed, now, zone))
        val walksOnly = listOf(activity("2026-10-31T08:00:00Z", meters = 3_000.0, durationMs = 2_400_000, type = ActivityType.WALK))
        assertNull(RouteRunEstimate.recentPace(ActivityType.RUN, walksOnly, now, zone))
    }

    @Test fun theSelectedActivityDecidesTheRouteEstimate() {
        val history = listOf(activity("2026-10-31T08:00:00Z", meters = 10_000.0, durationMs = 3_220_000))
        val shown =
            ActivityType.entries.associateWith { type ->
                RouteRunEstimate.forRoute(stats(), RouteRunEstimate.recentPace(type, history, now, zone))
            }
        assertEquals(87, shown.getValue(ActivityType.RUN))
        assertEquals(WALKING_MINUTES, shown.getValue(ActivityType.WALK))
        assertEquals(WALKING_MINUTES, shown.getValue(ActivityType.HIKE))
    }

    private companion object {
        const val ROUTE_METERS = 14_000.0
        const val ROUTE_ASCENT = 281.0
        const val RUN_PACE = 322.0
        const val WALKING_MINUTES = 178
        const val BASE_HEIGHT = 700.0
        const val METERS_PER_KM = 1_000.0
        const val ASCENT_PER_EFFORT_KM = 100.0
    }
}
