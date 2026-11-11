package com.runline.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runline.app.music.SpotifyAccount
import com.runline.app.music.SpotifyAppSignIn
import com.runline.app.music.SpotifyPkce
import com.runline.app.ui.MusicSheet
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The parts of the Spotify sign-in that need real intents and the package manager. No network, no Spotify. */
@RunWith(AndroidJUnit4::class)
class SpotifySignInAndroidTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val signIn = SpotifyPkce.SignIn("0123456789abcdef0123456789abcdef", "challenge", "state-1")
    private fun answer(vararg values: Pair<String, String>) =
        Intent().putExtra("REPLY", Bundle().apply { values.forEach { (key, value) -> putString(key, value) } })

    @Test fun spotifysResultCarriesTheCodeAndState() {
        val data = answer("RESPONSE_TYPE" to "code", "AUTHORIZATION_CODE" to "AQD-code_1", "STATE" to "state-1")
        assertEquals(SpotifyPkce.AppReply("AQD-code_1", "state-1"), SpotifyAppSignIn.reply(Activity.RESULT_OK, data))
        assertEquals(SpotifyPkce.AppReply("AQD-code_1"), SpotifyAppSignIn.reply(Activity.RESULT_OK, answer("RESPONSE_TYPE" to "code", "AUTHORIZATION_CODE" to "AQD-code_1")))
    }
    @Test fun aResultWithoutACodeIsAnError() {
        assertEquals("no_code", SpotifyAppSignIn.reply(Activity.RESULT_OK, null).error)
        assertEquals("no_code", SpotifyAppSignIn.reply(Activity.RESULT_OK, Intent()).error)
        // This app never asks for a token, so one is not accepted in place of a code.
        assertEquals("no_code", SpotifyAppSignIn.reply(Activity.RESULT_OK, answer("RESPONSE_TYPE" to "token", "ACCESS_TOKEN" to "BQD")).error)
        assertEquals(SpotifyPkce.AppReply(error = "INVALID_APP_ID"), SpotifyAppSignIn.reply(-2, Intent().putExtra("ERROR", "INVALID_APP_ID")))
        assertEquals(SpotifyPkce.AppReply(error = "unknown"), SpotifyAppSignIn.reply(-2, null))
    }
    @Test fun leavingSpotifyIsNeitherACodeNorAnError() {
        assertEquals(SpotifyPkce.AppReply(), SpotifyAppSignIn.reply(Activity.RESULT_CANCELED, null))
        assertEquals(SpotifyPkce.AppReply(), SpotifyAppSignIn.reply(7, Intent().putExtra("ERROR", "ignored")))
    }
    @Test fun withoutSpotifyTheBrowserSignsInInItsOwnTask() {
        // The test device has no Spotify, so there is nothing to hand the sign-in to.
        assertNull(SpotifyAppSignIn.intent(context, signIn))
        val browser = SpotifyAppSignIn.browser(context, signIn.url)
        assertEquals(Intent.ACTION_VIEW, browser.action); assertEquals(signIn.url, browser.dataString)
        assertTrue(browser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNotEquals(SpotifyAppSignIn.PACKAGE, browser.`package`); assertNotEquals("android", browser.`package`)
    }
    /** The first thing a user without a registered fingerprint sees: it must name the way out. */
    @Test fun aRefusalFromSpotifyShowsItsErrorTheFingerprintAndTheBrowser() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun shown(text: String) = automation.windows.any { window -> window.root?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true }
        SpotifyAccount.saveClientId(context, signIn.clientId); SpotifyAccount.begin(context)
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { MusicSheet(it, appReply = SpotifyPkce.AppReply(error = "INVALID_APP_ID")) { }.show() }
                val browser = context.getString(R.string.music_browser)
                val deadline = SystemClock.uptimeMillis() + 10_000
                while (!shown(browser) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100)
                assertTrue("No browser offer", shown(browser))
                assertTrue("Spotify's error is hidden", shown("INVALID_APP_ID"))
                assertTrue("Guide stayed closed", shown(context.packageName) && shown(SpotifyAppSignIn.ownFingerprint(context)!!))
                assertFalse(SpotifyAccount.isConnected(context))
            }
        } finally { SpotifyAccount.disconnect(context) }
    }
    @Test fun thisBuildsFingerprintHasTheDashboardForm() {
        assertTrue(SpotifyAppSignIn.ownFingerprint(context).orEmpty().matches(Regex("([0-9A-F]{2}:){19}[0-9A-F]{2}")))
    }
}
