package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class FollowCameraTest {
    @Test fun crossesNorthByTheShortTurnWithArrowRelativeToCamera() {
        val camera = FollowCamera()
        camera.update(359.0, 0)
        val pose = camera.update(1.0, 120)
        assertTrue(pose.bearing > 359.0 || pose.bearing < 1.0)
        assertTrue(pose.markerRotation!! in 0.0..2.0)
        assertEquals(46.0, pose.tilt, .001)
        assertEquals(-2.0, FollowCamera.relativeBearing(359.0, 1.0)!!, .001)
    }

    @Test fun steeringIsIndependentOfFrameRate() {
        fun sample(step: Long): FollowCamera.Pose {
            val camera = FollowCamera()
            var pose = camera.update(0.0, 0)
            for (time in step..480L step step) pose = camera.update(90.0, time)
            return pose
        }
        assertEquals(sample(16).bearing, sample(32).bearing, .000001)
        assertEquals(sample(16).markerRotation!!, sample(32).markerRotation!!, .000001)
    }

    @Test fun cutsSeeksAndBackgroundReturnDoNotAnimateUnobservedTurns() {
        val camera = FollowCamera()
        camera.update(30.0, 0)
        assertEquals(240.0, camera.update(240.0, 33, cut = true).bearing, .0)
        assertEquals(90.0, camera.update(90.0, 4_000).bearing, .0)
        assertEquals(15.0, camera.update(15.0, 1).bearing, .0)
        camera.reset()
        assertEquals(200.0, camera.update(200.0, 20).bearing, .0)
    }

    @Test fun unknownCourseIsNeutralAndNeverInventsAnArrow() {
        val camera = FollowCamera()
        val first = camera.update(null, 0)
        assertEquals(0.0, first.bearing, .0)
        assertEquals(0.0, first.tilt, .0)
        assertNull(first.markerRotation)
        assertEquals(0.0, camera.update(null, 33).tilt, .0)
        camera.update(80.0, 100, cut = true)
        val missing = camera.update(Double.NaN, 200)
        assertEquals(80.0, missing.bearing, .0)
        assertNull(missing.markerRotation)
        assertNull(FollowCamera.relativeBearing(Double.POSITIVE_INFINITY, 0.0))
        val gap = camera.update(null, 300, cut = true)
        assertEquals(0.0, gap.bearing, .0)
        assertEquals(0.0, gap.tilt, .0)
    }

    @Test fun removeAnimationsUsesFlatNorthUpWhilePreservingObservedArrowDirection() {
        val camera = FollowCamera()
        camera.update(180.0, 0)
        val pose = camera.update(270.0, 33, reduceMotion = true)
        assertEquals(0.0, pose.bearing, .0)
        assertEquals(0.0, pose.tilt, .0)
        assertEquals(-90.0, pose.markerRotation!!, .0)
    }

    @Test fun duplicateFramesDoNotKeepTurningAndAnglesStayFinite() {
        val camera = FollowCamera()
        camera.update(-10.0, 100)
        val once = camera.update(720.0, 133)
        val twice = camera.update(720.0, 133)
        assertEquals(once, twice)
        assertTrue(twice.bearing in 0.0..360.0)
        assertTrue(twice.tilt in 0.0..46.0)
    }
}
