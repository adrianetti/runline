package com.runline.app.ui

import android.content.Context
import android.view.MotionEvent
import android.widget.LinearLayout
import kotlin.math.abs

/** Intentional horizontal gestures only; maps, sheets, vertical scroll and edge-back retain ownership. */
internal class SwipeTabs @JvmOverloads constructor(context: Context, private val enabled: () -> Boolean = { false }, private val swipe: (Int) -> Unit = {}) : LinearLayout(context) {
    private var startX = 0f; private var startY = 0f
    private var eligible = false; private var dragging = false
    private val density = resources.displayMetrics.density
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y; dragging = false
                eligible = enabled() && event.x >= 24 * density && event.x <= width - 24 * density
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> eligible = false
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX; val dy = event.y - startY
                if (abs(dy) > 20 * density && abs(dy) > abs(dx)) eligible = false
                if (eligible && abs(dx) > 48 * density && abs(dx) > abs(dy) * 1.8) {
                    dragging = true; return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }
    override fun performClick(): Boolean = super.performClick()
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                if (enabled() && abs(dx) >= 64 * density) swipe(if (dx < 0) 1 else -1)
                dragging = false; eligible = false; performClick()
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; eligible = false }
        }
        return true
    }
}
