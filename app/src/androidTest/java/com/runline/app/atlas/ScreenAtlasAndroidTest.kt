package com.runline.app.atlas

import android.Manifest
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runline.app.i18n.AppLanguage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScreenAtlasAndroidTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val driver = AtlasDriver(instrumentation)
    private val fixtures by lazy { AtlasFixtures(driver.context, System.currentTimeMillis()) }
    private val recorder by lazy { AtlasRecorder(driver, fixtures) }
    private val output by lazy {
        File(
            arguments.getString("additionalTestOutputDir") ?: driver.context.externalMediaDirs
                .first()
                .path,
            "atlas",
        ).apply { mkdirs() }
    }

    @Before fun prepare() {
        val automation = instrumentation.uiAutomation
        val name = driver.context.packageName
        automation.grantRuntimePermission(name, Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(name, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= NOTIFICATION_API) automation.grantRuntimePermission(name, Manifest.permission.POST_NOTIFICATIONS)
        automation.executeShellCommand("appops set $name android:mock_location allow").close()
        fixtures.installHistory()
    }

    @After fun restore() {
        recorder.finish()
        driver.close()
        fixtures.removeHistory()
        instrumentation.runOnMainSync {
            AppLanguage.select(driver.context, "")
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    @Test fun everyScreenInTheFlowIsReachedAndAudited() {
        val looks = (arguments.getString("atlasLooks") ?: DEFAULT_LOOKS).split(',').map(AtlasLook::parse)
        val visits = looks.flatMap(::tour)
        File(output, "report.json").writeText(JSONArray(visits.map(AtlasVisit::json)).toString(JSON_INDENT))
        assertEquals("Screens the flow could not reach", emptyList<String>(), visits.filter { !it.reached }.map { "${it.look}/${it.screen}" })
    }

    private fun tour(look: AtlasLook): List<AtlasVisit> {
        driver.configure(look)
        val only = arguments.getString("atlasOnly")?.split(',')
        val visits =
            FlowState.entries.flatMap { state ->
                val wanted = AppFlow.screens.filter { it.state == state && (only == null || it.id in only) }
                val entered = wanted.isNotEmpty() && recorder.enter(state)
                wanted.map { if (entered) visit(look, it) else AtlasVisit(look.name, it.id, false, null) }
            }
        recorder.finish()
        return visits
    }

    private fun visit(
        look: AtlasLook,
        screen: FlowScreen,
    ): AtlasVisit {
        val wanted = arguments.getString("atlasShots")?.split(',').orEmpty()
        val pause = if (screen.id in wanted) arguments.getString("atlasSettleMs")?.toLongOrNull() ?: 0 else 0
        if (!driver.open(screen, pause)) return AtlasVisit(look.name, screen.id, false, null)
        val snapshot = driver.capture(screen.id)
        val exit = screen.exit?.let { AppLanguage.localizedContext(driver.context).getString(resource(it)) }
        File(output, "${look.name}/${screen.id}.json").apply { parentFile?.mkdirs() }.writeText(snapshot.json().toString())
        if (screen.id in wanted) driver.screenshot(File(output, "${look.name}/${screen.id}.png"))
        return AtlasVisit(look.name, screen.id, true, UiAudit.audit(snapshot, AuditPolicy(screen.reading, exit)))
    }

    private fun resource(name: String) = driver.context.resources.getIdentifier(name, "string", driver.context.packageName)

    private data class AtlasVisit(
        val look: String,
        val screen: String,
        val reached: Boolean,
        val result: AuditResult?,
    ) {
        fun json(): JSONObject = (result?.json() ?: JSONObject().put("screen", screen)).put("look", look).put("reached", reached)
    }

    companion object {
        private const val DEFAULT_LOOKS = "en-light,es-dark"
        private const val NOTIFICATION_API = 33
        private const val JSON_INDENT = 1
    }
}
