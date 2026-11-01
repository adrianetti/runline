package com.runline.app.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunNotificationActionsTest {
    @Test fun controlsFromPreviousRunCannotMutateTheNextRun() {
        for (action in listOf(TrackingService.ACTION_PAUSE, TrackingService.ACTION_RESUME, TrackingService.ACTION_FINISH)) {
            for (status in RecordingStatus.entries) {
                assertFalse(RunNotificationActions.isAllowed(action, "old-run", "new-run", status))
            }
        }
    }

    @Test fun finishRequiresAPausedRunEvenIfAnOldPausedNotificationWasStillVisible() {
        assertTrue(RunNotificationActions.isAllowed(TrackingService.ACTION_FINISH, "run", "run", RecordingStatus.PAUSED))
        assertFalse(RunNotificationActions.isAllowed(TrackingService.ACTION_FINISH, "run", "run", RecordingStatus.RECORDING))
    }

    @Test fun pausedAndRecoveredRunsCanResumeOrFinish() {
        for (status in listOf(RecordingStatus.PAUSED, RecordingStatus.INTERRUPTED)) {
            assertTrue(RunNotificationActions.isAllowed(TrackingService.ACTION_RESUME, "run", "run", status))
            assertTrue(RunNotificationActions.isAllowed(TrackingService.ACTION_FINISH, "run", "run", status))
            assertFalse(RunNotificationActions.isAllowed(TrackingService.ACTION_PAUSE, "run", "run", status))
        }
    }

    @Test fun missingRunCannotBeCreatedByAStaleNotification() {
        for (id in listOf(null, "", "run")) {
            assertFalse(RunNotificationActions.isAllowed(TrackingService.ACTION_RESUME, id, null, RecordingStatus.IDLE))
        }
        assertFalse(RunNotificationActions.isAllowed(TrackingService.ACTION_START, "run", "run", RecordingStatus.PAUSED))
    }

    @Test fun activeRunCanOnlyBePausedByItsOwnControl() {
        assertTrue(RunNotificationActions.isAllowed(TrackingService.ACTION_PAUSE, "run", "run", RecordingStatus.RECORDING))
        assertFalse(RunNotificationActions.isAllowed(TrackingService.ACTION_RESUME, "run", "run", RecordingStatus.RECORDING))
        assertFalse(RunNotificationActions.isAllowed(TrackingService.ACTION_DISCARD, "run", "run", RecordingStatus.RECORDING))
    }

    @Test fun damagedDraftCannotBeMutatedByNotificationControls() {
        for (action in listOf(TrackingService.ACTION_PAUSE, TrackingService.ACTION_RESUME, TrackingService.ACTION_FINISH)) {
            assertFalse(RunNotificationActions.isAllowed(action, "run", "run", RecordingStatus.RECOVERY_ERROR))
        }
    }
}
