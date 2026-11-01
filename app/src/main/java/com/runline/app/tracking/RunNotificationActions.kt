package com.runline.app.tracking

/** Recheck controls against the live recorder, including after process recovery. */
internal object RunNotificationActions {
    fun isAllowed(action: String?, expectedRunId: String?, currentRunId: String?, status: RecordingStatus): Boolean {
        if (expectedRunId.isNullOrBlank() || expectedRunId != currentRunId) return false
        return when (action) {
            TrackingService.ACTION_PAUSE -> status == RecordingStatus.RECORDING
            TrackingService.ACTION_RESUME, TrackingService.ACTION_FINISH ->
                status == RecordingStatus.PAUSED || status == RecordingStatus.INTERRUPTED
            else -> false
        }
    }
}
