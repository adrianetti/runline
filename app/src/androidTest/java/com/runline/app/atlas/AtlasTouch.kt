package com.runline.app.atlas

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent

internal class AtlasTouch(
    private val instrumentation: Instrumentation,
) {
    fun tap(
        x: Float,
        y: Float,
    ) {
        val down = SystemClock.uptimeMillis()
        send(down, down, MotionEvent.ACTION_DOWN, x, y)
        send(down, down + TAP_MS, MotionEvent.ACTION_UP, x, y)
    }

    fun swipeUp(environment: UiEnvironment) {
        val x = environment.widthPx / 2f
        val from = environment.heightPx * SWIPE_FROM
        val to = environment.heightPx * SWIPE_TO
        val down = SystemClock.uptimeMillis()
        send(down, down, MotionEvent.ACTION_DOWN, x, from)
        for (step in 1..SWIPE_STEPS) {
            send(down, down + step * STEP_MS, MotionEvent.ACTION_MOVE, x, from + (to - from) * step / SWIPE_STEPS)
        }
        send(down, down + (SWIPE_STEPS + HOLD_STEPS) * STEP_MS, MotionEvent.ACTION_MOVE, x, to)
        send(down, down + (SWIPE_STEPS + HOLD_STEPS + 1) * STEP_MS, MotionEvent.ACTION_UP, x, to)
    }

    private fun send(
        down: Long,
        time: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(down, time, action, x, y, 0)
        instrumentation.sendPointerSync(event)
        event.recycle()
    }

    private companion object {
        const val TAP_MS = 60L
        const val STEP_MS = 16L
        const val SWIPE_STEPS = 12
        const val HOLD_STEPS = 12
        const val SWIPE_FROM = 0.62f
        const val SWIPE_TO = 0.34f
    }
}
