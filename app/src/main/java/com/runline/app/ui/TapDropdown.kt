package com.runline.app.ui

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

/**
 * A read-only exposed dropdown that opens only when someone taps it. On some phones a rebuilt screen
 * opened the Record activity picker by itself (seen on a Samsung, not reproducible on the emulator),
 * so any open request that does not follow a tap or accessibility click is ignored.
 */
class TapDropdown(context: Context) : MaterialAutoCompleteTextView(context) {
    private var requestedAt = Long.MIN_VALUE / 2

    /** Allow the next open request; the field's own tap and the dropdown arrow both call this. */
    fun request() { requestedAt = SystemClock.uptimeMillis() }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) request()
        return super.dispatchTouchEvent(event)
    }

    override fun performClick(): Boolean { request(); return super.performClick() }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) request()
        return super.performAccessibilityAction(action, arguments)
    }

    override fun showDropDown() {
        if (SystemClock.uptimeMillis() - requestedAt <= REQUEST_WINDOW_MS) super.showDropDown()
    }

    companion object {
        private const val REQUEST_WINDOW_MS = 1_000L

        /** The outlined field around [TapDropdown]; a tap anywhere on it, arrow included, counts as a request. */
        fun field(context: Context, choice: () -> TapDropdown?): TextInputLayout =
            object : TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_UP) choice()?.request()
                    return super.dispatchTouchEvent(event)
                }
            }
    }
}
