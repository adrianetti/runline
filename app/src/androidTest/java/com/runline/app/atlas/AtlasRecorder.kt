package com.runline.app.atlas

import android.os.SystemClock
import com.runline.app.tracking.RecordingStatus
import com.runline.app.tracking.TrackingService

internal class AtlasRecorder(
    private val driver: AtlasDriver,
    private val fixtures: AtlasFixtures,
) {
    @Volatile private var feeding = false
    private var feeder: Thread? = null
    private var owned = false

    fun enter(state: FlowState): Boolean =
        when (state) {
            FlowState.IDLE -> status() == RecordingStatus.IDLE
            FlowState.RECORDING -> start()
            FlowState.PAUSED -> owned && command(TrackingService.ACTION_PAUSE, RecordingStatus.PAUSED)
        }

    fun finish() {
        if (!owned) return
        if (status() == RecordingStatus.RECORDING) command(TrackingService.ACTION_PAUSE, RecordingStatus.PAUSED)
        command(TrackingService.ACTION_DISCARD, RecordingStatus.IDLE)
        feeding = false
        feeder?.join(JOIN_MS)
        fixtures.endRecording()
        owned = false
    }

    private fun start(): Boolean {
        if (status() != RecordingStatus.IDLE || !fixtures.beginRecording()) return false
        owned = true
        feeding = true
        feeder =
            Thread {
                while (feeding) {
                    runCatching { fixtures.fix() }
                    SystemClock.sleep(FIX_MS)
                }
            }.also(Thread::start)
        val recording = command(TrackingService.ACTION_RESUME, RecordingStatus.RECORDING)
        SystemClock.sleep(WARM_UP_MS)
        return recording
    }

    private fun command(
        action: String,
        expected: RecordingStatus,
    ): Boolean {
        TrackingService.command(driver.context, action)
        val deadline = SystemClock.uptimeMillis() + COMMAND_TIMEOUT_MS
        while (status() != expected && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(POLL_MS)
        return status() == expected
    }

    private fun status() = TrackingService.state(driver.context).status

    companion object {
        private const val FIX_MS = 1_000L
        private const val WARM_UP_MS = 9_000L
        private const val COMMAND_TIMEOUT_MS = 8_000L
        private const val POLL_MS = 100L
        private const val JOIN_MS = 2_000L
    }
}
