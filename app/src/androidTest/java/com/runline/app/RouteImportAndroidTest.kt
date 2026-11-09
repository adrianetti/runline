package com.runline.app

import android.content.Intent
import android.net.Uri
import android.widget.EditText
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runline.app.data.RouteStore
import com.runline.app.ui.RouteImportDialog
import com.runline.app.ui.RouteImportIntents
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RouteImportAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun uri(name: String = "track.gpx") = Uri.parse("content://com.runline.app.test.gpx/$name")
    private fun intent(name: String = "track.gpx") = Intent(Intent.ACTION_VIEW, uri(name), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test fun prepareDoesNotSaveAndCommitPreservesOriginalBytesWithStableId() {
        val store = RouteStore(context); val id = "import-${UUID.randomUUID()}"
        try {
            val draft = store.prepareImport(uri(), id)
            assertNull(store.entry(id, "en")); assertNull(store.gpx(id))
            val saved = store.commitImport(draft, "My route")
            assertEquals("My route", store.load(saved.id)!!.name)
            assertArrayEquals(GpxFixtureProvider.GPX.toByteArray(), store.gpx(id))
            assertEquals(saved.id, store.commitImport(draft, "Duplicate tap").id)
            assertEquals("My route", store.entry(id, "en")!!.name)
            assertEquals(1, store.list("en").count { it.id == id })
        } finally { store.delete(id) }
    }

    @Test fun intentsOnlyAcceptSingleContentFiles() {
        assertEquals(uri(), RouteImportIntents.uri(intent()))
        assertEquals(uri(), RouteImportIntents.uri(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri())))
        listOf("file:///data/data/com.runline.app/files/private.gpx", "https://example.com/route.gpx").forEach {
            assertNull(RouteImportIntents.uri(Intent(Intent.ACTION_VIEW, Uri.parse(it))))
        }
        assertNull(RouteImportIntents.uri(Intent(Intent.ACTION_SEND_MULTIPLE).putExtra(Intent.EXTRA_STREAM, uri())))
        assertNull(RouteImportIntents.uri(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, "not a Uri")))
    }

    @Test fun openWithMatchesGpxTypesAndGenericFilesWithGpxSuffix() {
        fun matches(path: String, type: String): Boolean {
            val request = Intent(Intent.ACTION_VIEW).setDataAndType(uri(path), type).setPackage(context.packageName)
            return context.packageManager.queryIntentActivities(request, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo.name == MainActivity::class.java.name }
        }
        assertTrue(matches("opaque-document-id", "application/gpx+xml"))
        assertTrue(matches("route.gpx", "application/octet-stream"))
        assertTrue(matches("morning.walk.gpx", "application/xml"))
        assertTrue(matches("ROUTE.GPX", "application/octet-stream"))
        assertFalse(matches("invoice.pdf", "application/pdf"))
    }

    @Test fun sharedGpxOpensNamingStepInAnAlreadyRunningApp() {
        val launchIntent = Intent(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            scenario.onActivity {
                it.startActivity(Intent(it, MainActivity::class.java).setAction(Intent.ACTION_SEND)
                    .putExtra(Intent.EXTRA_STREAM, uri("Afternoon.gpx")).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            ready(scenario)
            scenario.onActivity {
                val dialog = dialog(it)!!
                assertEquals("Afternoon", field(dialog).text.toString())
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                // Scenario matches lifecycle events by Intent; MainActivity adopts the incoming SEND.
                // Restore only the test launch intent so teardown can observe DESTROYED.
                it.intent = launchIntent
                it.finish()
            }
        }
    }

    @Test fun rotationRetainsEditedNameAndCancelDoesNotImport() {
        val before = RouteStore(context).list("en").map { it.id }.toSet()
        ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
            ready(scenario)
            scenario.onActivity { field(dialog(it)!!).setText("Edited before rotation") }
            scenario.recreate()
            ready(scenario)
            scenario.onActivity {
                val dialog = dialog(it)!!
                assertEquals("Edited before rotation", field(dialog).text.toString())
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
        }
        assertEquals(before, RouteStore(context).list("en").map { it.id }.toSet())
    }

    @Test fun emptyNameUsesSuggestionAndDoubleTapAddsOneRoute() {
        val store = RouteStore(context); val before = store.list("en").map { it.id }.toSet()
        try {
            ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
                ready(scenario)
                var expected = ""
                scenario.onActivity {
                    val d = dialog(it)!!; expected = field(d).text.toString(); assertTrue(expected.isNotBlank())
                    field(d).setText("")
                    d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                    d.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                }
                waitUntil { store.list("en").any { it.id !in before } }
                val added = store.list("en").filter { it.id !in before }
                assertEquals(1, added.size); assertEquals(expected, added.single().name)
            }
        } finally { store.list("en").filter { it.id !in before && it.imported }.forEach { store.delete(it.id) } }
    }

    @Test fun invalidGpxCannotBeAdded() {
        val before = RouteStore(context).list("en").map { it.id }.toSet()
        ActivityScenario.launch<MainActivity>(intent("invalid.gpx")).use { scenario ->
            waitUntil {
                var done = false
                scenario.onActivity { activity -> dialog(activity)?.let { d ->
                    done = d.getButton(AlertDialog.BUTTON_NEGATIVE).text.toString() == activity.getString(R.string.main_close)
                    if (done) assertFalse(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                } }; done
            }
        }
        assertEquals(before, RouteStore(context).list("en").map { it.id }.toSet())
    }

    private fun ready(scenario: ActivityScenario<MainActivity>) = waitUntil {
        var ready = false
        scenario.onActivity { ready = dialog(it)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled == true }
        ready
    }
    private fun dialog(activity: MainActivity) = (activity.supportFragmentManager.findFragmentByTag(RouteImportDialog.TAG) as? RouteImportDialog)?.dialog as? AlertDialog
    private fun field(dialog: AlertDialog): EditText {
        fun find(view: View): EditText? = if (view is EditText) view else if (view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
        return find(dialog.window!!.decorView)!!
    }
    private fun waitUntil(test: () -> Boolean) {
        val until = System.currentTimeMillis() + 60_000
        while (!test()) { if (System.currentTimeMillis() > until) fail("Timed out waiting for import"); Thread.sleep(100) }
    }
}
