package com.runline.app.atlas

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import com.runline.app.MainActivity
import com.runline.app.R
import com.runline.app.i18n.AppLanguage
import java.io.File

internal data class AtlasLook(
    val locale: String,
    val night: Boolean,
) {
    val name get() = "$locale-${if (night) "dark" else "light"}"

    companion object {
        fun parse(value: String) = AtlasLook(value.substringBefore('-'), value.substringAfter('-') == "dark")
    }
}

internal class AtlasDriver(
    private val instrumentation: Instrumentation,
) {
    val context get() = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var activity: MainActivity? = null
    private var look = AtlasLook("en", false)
    private val touch = AtlasTouch(instrumentation)

    fun configure(next: AtlasLook) {
        close()
        look = next
        instrumentation.runOnMainSync {
            AppLanguage.select(context, next.locale)
            AppCompatDelegate.setDefaultNightMode(if (next.night) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    fun open(
        screen: FlowScreen,
        pauseMs: Long = 0,
    ): Boolean {
        close()
        scenario =
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).also { started ->
                started.onActivity { activity = it }
            }
        settle()
        val arrived = AppFlow.path(screen.id).orEmpty().all { tap(it.control) }
        if (!arrived || capture(screen.id).top.kind != screen.kind || !shows(screen.marker)) return false
        settle(pauseMs)
        screen.arrange.forEachIndexed { index, control ->
            if (index > 0) SystemClock.sleep(ARRANGE_GAP_MS)
            tap(control)
        }
        settle(pauseMs)
        return true
    }

    fun <T> onRoots(action: (List<android.view.View>) -> T): T? {
        val host = activity ?: return null
        var result: T? = null
        instrumentation.runOnMainSync {
            val roots =
                if (android.os.Build.VERSION.SDK_INT >= WINDOWS_API) {
                    android.view.inspector.WindowInspector
                        .getGlobalWindowViews()
                } else {
                    listOf(host.window.decorView)
                }
            result = action(roots)
        }
        return result
    }

    fun close() {
        activity = null
        scenario?.close()
        scenario = null
    }

    fun capture(id: String): UiScreen {
        val host = checkNotNull(activity) { "No activity for $id" }
        var windows = emptyList<UiWindow>()
        var environment: UiEnvironment? = null
        instrumentation.runOnMainSync {
            windows = ViewSnapshot.windows(host.window.decorView)
            val metrics = host.resources.displayMetrics
            val root = host.window.decorView
            val fontScale = host.resources.configuration.fontScale
            environment = UiEnvironment(look.locale, look.night, metrics.density, fontScale, root.width, root.height)
        }
        return UiScreen(id, checkNotNull(environment), windows)
    }

    fun find(control: String): UiNode? {
        val pattern = pattern(control)
        return capture(control)
            .top.root
            .walk()
            .filter { it.actionable && it.enabled && names(it).any(pattern::matches) }
            .minByOrNull { it.bounds.width.toLong() * it.bounds.height }
    }

    private fun names(node: UiNode): List<String> {
        val texts = node.walk().mapNotNull { it.text?.value }
        return (listOf(node.spokenName()) + texts).map(String::trim).filter(String::isNotBlank)
    }

    fun shows(marker: String): Boolean {
        val pattern = pattern(marker)
        return capture(marker).top.root.walk().any { node ->
            listOfNotNull(node.text?.value, node.label).any { pattern.matches(it.trim()) }
        }
    }

    fun tap(control: String): Boolean {
        var bounds = find(control)?.bounds
        var tries = 0
        while (bounds == null && tries < MAX_TRIES) {
            tries++
            val top = capture(control)
            if (tries > PATIENT_TRIES && top.top.kind in SCROLLABLE) touch.swipeUp(top.environment) else SystemClock.sleep(POLL_MS)
            settle()
            bounds = find(control)?.bounds
        }
        if (bounds == null) return false
        touch.tap((bounds.left + bounds.right) / 2f, (bounds.top + bounds.bottom) / 2f)
        settle()
        return true
    }

    fun settle(extraMs: Long = 0) {
        val idle = java.util.concurrent.CountDownLatch(1)
        instrumentation.waitForIdle { idle.countDown() }
        idle.await(IDLE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        var last = ""
        var stable = 0
        val deadline = SystemClock.uptimeMillis() + SETTLE_TIMEOUT_MS
        while (stable < STABLE_POLLS && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(POLL_MS)
            val now = activity?.let { capture("").windows.joinToString { window -> shape(window.root) } }.orEmpty()
            stable = if (now == last) stable + 1 else 0
            last = now
        }
        if (extraMs > 0) SystemClock.sleep(extraMs)
    }

    fun screenshot(file: File) {
        var bitmap: Bitmap? = null
        var calm = 0
        val deadline = SystemClock.uptimeMillis() + TICK_TIMEOUT_MS
        while (bitmap == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(TICK_POLL_MS)
            instrumentation.runOnMainSync {
                calm = if (rolling()) 0 else calm + 1
                if (calm >= CALM_POLLS) bitmap = instrumentation.uiAutomation.takeScreenshot()
            }
        }
        val shot = bitmap ?: instrumentation.uiAutomation.takeScreenshot() ?: return
        file.parentFile?.mkdirs()
        file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        shot.recycle()
    }

    private fun rolling(): Boolean {
        val pending = ArrayDeque<android.view.View>(listOfNotNull(activity?.window?.decorView))
        var found = false
        while (!found && pending.isNotEmpty()) {
            val view = pending.removeFirst()
            found = view.javaClass.simpleName == ROLLING && ROLLING_ANIMATOR.get(view) != null
            (view as? android.view.ViewGroup)?.let { group -> (0 until group.childCount).forEach { pending += group.getChildAt(it) } }
        }
        return found
    }

    fun stringId(name: String): Int = runCatching { R.string::class.java.getField(name).getInt(null) }.getOrDefault(0)

    private fun shape(node: UiNode): String = node.walk().joinToString(";") { "${it.type}${it.bounds}" }

    private fun pattern(name: String): Regex {
        val resources = AppLanguage.localizedContext(context).resources
        val id = stringId(name)
        check(id != 0) { "Unknown string $name" }
        val parts = resources.getString(id).split(FORMAT_ARGUMENT)
        return Regex(parts.joinToString(".*") { Regex.escape(it) }, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }

    companion object {
        private const val WINDOWS_API = 29
        private const val ARRANGE_GAP_MS = 3_000L
        private const val TICK_TIMEOUT_MS = 4_000L
        private const val TICK_POLL_MS = 40L
        private const val CALM_POLLS = 3
        private const val ROLLING = "RollingNumberView"
        private val ROLLING_ANIMATOR =
            Class.forName("com.runline.app.ui.RollingNumberView").getDeclaredField("animator").apply { isAccessible = true }
        private const val MAX_TRIES = 6
        private const val PATIENT_TRIES = 2
        private val SCROLLABLE = setOf(UiWindowKind.SCREEN, UiWindowKind.SHEET)
        private const val IDLE_TIMEOUT_MS = 800L
        private const val POLL_MS = 150L
        private const val STABLE_POLLS = 2
        private const val SETTLE_TIMEOUT_MS = 2_500L
        private const val PNG_QUALITY = 100
        private val FORMAT_ARGUMENT = Regex("%(\\d+\\$)?[sd]")
    }
}
