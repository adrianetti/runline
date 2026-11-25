package com.runline.app

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityPickerAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun activitySwitchKeepsTheChoiceAcrossTabsAndRelaunch() {
        val context = instrumentation.targetContext
        val walk = context.getString(R.string.main_type_walk)
        val run = context.getString(R.string.main_type_run)
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            scenario.onActivity { segment(it.window.decorView, walk).performClick() }
            instrumentation.waitForIdleSync()
            for (position in listOf(1, 2, 3, 0)) {
                scenario.onActivity { find<TabLayout>(it.window.decorView)!!.getTabAt(position)!!.select() }
                instrumentation.waitForIdleSync()
            }
            scenario.onActivity { assertTrue("Walk should stay selected", segment(it.window.decorView, walk).isChecked) }
            scenario.recreate()
            scenario.onActivity {
                val group = find<MaterialButtonToggleGroup>(it.window.decorView)!!
                assertEquals(walk, group.findViewById<MaterialButton>(group.checkedButtonId).text.toString())
                segment(it.window.decorView, run).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertTrue("Run should be selected again", segment(it.window.decorView, run).isChecked) }
        }
    }

    private fun segment(
        root: View,
        label: String,
    ): MaterialButton {
        val group = find<MaterialButtonToggleGroup>(root)!!
        return (0 until group.childCount).map { group.getChildAt(it) as MaterialButton }.first { it.text.toString() == label }
    }

    private inline fun <reified T : View> find(view: View): T? = all(view).firstNotNullOfOrNull { it as? T }

    private fun all(view: View): List<View> =
        listOf(view) + ((view as? ViewGroup)?.let { group -> (0 until group.childCount).flatMap { all(group.getChildAt(it)) } } ?: emptyList())
}
