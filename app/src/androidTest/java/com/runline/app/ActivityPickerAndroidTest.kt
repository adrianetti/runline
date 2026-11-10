package com.runline.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.tabs.TabLayout
import com.runline.app.ui.TapDropdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Record activity picker opened by itself on a phone. It must open only on a tap, and still work. */
@RunWith(AndroidJUnit4::class)
class ActivityPickerAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun pickerStaysClosedOnReturnToRecordAndStillOpensOnTap() {
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            repeat(2) {
                for (position in listOf(1, 2, 0)) {
                    scenario.onActivity { find<TabLayout>(it.window.decorView)!!.getTabAt(position)!!.select() }
                    instrumentation.waitForIdleSync()
                }
                scenario.onActivity {
                    val picker = find<TapDropdown>(it.window.decorView)!!
                    // Whatever opened it on the phone ends in showDropDown() without a tap: that must do nothing.
                    picker.showDropDown()
                    assertFalse("Picker opened without a tap", picker.isPopupShowing)
                }
            }
            var center = IntArray(2)
            scenario.onActivity {
                val picker = find<TapDropdown>(it.window.decorView)!!
                picker.getLocationOnScreen(center)
                center = intArrayOf(center[0] + picker.width / 2, center[1] + picker.height / 2)
            }
            tap(center[0].toFloat(), center[1].toFloat())
            scenario.onActivity {
                val picker = find<TapDropdown>(it.window.decorView)!!
                assertTrue("A tap should open the picker", picker.isPopupShowing)
            }
            // Pick Walk from the open list the way a tap would.
            val walk = context.getString(R.string.main_type_walk)
            var row: AccessibilityNodeInfo? = null
            val until = SystemClock.uptimeMillis() + 5_000
            while (row == null && SystemClock.uptimeMillis() < until) {
                row = automation.windows.mapNotNull { it.root }.flatMap { it.findAccessibilityNodeInfosByText(walk) }
                    .firstOrNull { it.text?.toString() == walk }
                if (row == null) SystemClock.sleep(100)
            }
            assertTrue("Walk row not found in the open list", row != null)
            assertTrue(generateSequence(row!!) { it.parent }.any { it.isClickable && it.performAction(AccessibilityNodeInfo.ACTION_CLICK) })
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val picker = find<TapDropdown>(it.window.decorView)!!
                assertEquals(it.getString(R.string.main_type_walk), picker.text.toString())
                assertFalse(picker.isPopupShowing)
            }
        }
    }

    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 50, MotionEvent.ACTION_UP, x, y, 0))
        instrumentation.waitForIdleSync()
    }

    private inline fun <reified T : View> find(view: View): T? = all(view).firstNotNullOfOrNull { it as? T }
    private fun all(view: View): List<View> = listOf(view) + ((view as? ViewGroup)?.let { group ->
        (0 until group.childCount).flatMap { all(group.getChildAt(it)) } } ?: emptyList())
}
