package com.runline.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GpsCourseTest {
    private fun point(lat: Double = 0.0, lng: Double = 0.0, time: Long = 0L, accuracy: Float = 3f, segment: Int = 0) =
        RunPoint(lat, lng, time, accuracy, segment = segment)

    @Test fun derivesCardinalCoursesFromMovementInsteadOfAssumingNorthAtStart() {
        val destinations = listOf(0.0001 to 0.0, 0.0 to 0.0001, -0.0001 to 0.0, 0.0 to -0.0001)
        destinations.forEachIndexed { index, (lat, lng) ->
            val course = GpsCourse()
            course.update(point())
            assertNull(course.bearingDegrees)
            course.update(point(lat, lng, 5_000L))
            assertEquals(index * 90.0, course.bearingDegrees!!, 0.001)
        }
    }

    @Test fun combinesSeveralShortStepsIntoAUsefulBaseline() {
        val course = GpsCourse()
        repeat(4) { index -> course.update(point(lng = index * 0.00001, time = index * 1_000L)) }
        assertNull(course.bearingDegrees)
        repeat(4) { index -> course.update(point(lng = (index + 4) * 0.00001, time = (index + 4) * 1_000L)) }
        assertEquals(90.0, course.bearingDegrees!!, 0.001)
    }

    @Test fun accuracyDeadbandDoesNotTurnJitterIntoAnArrow() {
        val course = GpsCourse()
        course.update(point(accuracy = 12f))
        course.update(point(lng = 0.0001, time = 5_000L, accuracy = 12f))
        assertNull(course.bearingDegrees)
        course.update(point(lng = 0.0003, time = 10_000L, accuracy = 12f))
        assertEquals(90.0, course.bearingDegrees!!, 0.001)
    }

    @Test fun lastKnownCourseSurvivesStationaryJitterAndRepeatedUiUpdates() {
        val course = eastboundCourse()
        repeat(80) { index ->
            val fix = point(lat = if (index % 2 == 0) 0.000005 else -0.000005, lng = 0.0001, time = 6_000L + index * 1_000L)
            course.update(fix)
            course.update(fix)
        }
        assertEquals(90.0, course.bearingDegrees!!, 0.001)
    }

    @Test fun slowGpsWanderWhileStationaryDoesNotInferAHeading() {
        val course = GpsCourse()
        repeat(30) { index -> course.update(point(lng = index * 0.000003, time = index * 1_000L)) }
        assertNull(course.bearingDegrees)
    }

    @Test fun aTurnUsesTheNextMeaningfulBaseline() {
        val course = eastboundCourse()
        course.update(point(lat = 0.0001, lng = 0.0001, time = 10_000L))
        assertEquals(0.0, course.bearingDegrees!!, 0.001)
    }

    @Test fun segmentChangeAndGpsGapClearStaleDirection() {
        for (fix in listOf(point(lng = 0.001, time = 6_000L, segment = 1), point(lng = 0.001, time = 21_000L))) {
            val course = eastboundCourse()
            course.update(fix)
            assertNull(course.bearingDegrees)
            course.update(fix.copy(latitude = 0.0001, timestampMs = fix.timestampMs + 5_000L))
            assertEquals(0.0, course.bearingDegrees!!, 0.001)
        }
    }

    @Test fun invalidAccuracyCoordinatesTimeAndTeleportDoNotProduceFalseCourse() {
        for (fix in listOf(
            point(lng = Double.NaN, time = 6_000L), point(lng = 0.0001, time = 6_000L, accuracy = Float.NaN),
            point(lng = 0.0001, time = 6_000L, accuracy = 31f), point(lng = 0.0001, time = -1L),
            point(lng = 0.0002, time = 4_000L), point(lng = 0.0002, time = 5_000L),
            point(lng = 1.0, time = 6_000L),
        )) {
            val course = eastboundCourse()
            course.update(fix)
            assertNull(course.bearingDegrees)
        }
    }

    @Test fun datelineCrossingUsesShortEastboundCourse() {
        val course = GpsCourse()
        course.update(point(lng = 179.99995))
        course.update(point(lng = -179.99995, time = 5_000L))
        assertEquals(90.0, course.bearingDegrees!!, 0.001)
    }

    private fun eastboundCourse() = GpsCourse().apply {
        update(point())
        update(point(lng = 0.0001, time = 5_000L))
    }
}
