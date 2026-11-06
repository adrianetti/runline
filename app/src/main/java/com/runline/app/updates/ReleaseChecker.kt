package com.runline.app.updates

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

data class ReleaseStatus(val checking: Boolean = false, val latest: String? = null, val failed: Boolean = false)

class ReleaseChecker private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("release_check", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    @Volatile var state = ReleaseStatus(latest = prefs.getString("latest", null))
        private set
    fun listen(callback: () -> Unit) { listeners += callback }
    fun unlisten(callback: () -> Unit) { listeners -= callback }
    private fun notifyChanged() { main.post { listeners.forEach { it() } } }
    @Synchronized fun check(force: Boolean = false) {
        val now = System.currentTimeMillis(); val checked = prefs.getLong("checked", 0)
        if (!ReleaseCheckPolicy.shouldCheck(now, checked, prefs.getLong("retry", 0), state.latest != null, force, state.checking)) return
        state = state.copy(checking = true, failed = false); notifyChanged()
        worker.execute {
            var retryAt = now + 60_000L
            val result = runCatching {
                val request = URL(API).openConnection() as HttpURLConnection
                try {
                    request.connectTimeout = 8_000; request.readTimeout = 8_000; request.instanceFollowRedirects = false
                    request.setRequestProperty("Accept", "application/vnd.github+json")
                    request.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                    request.setRequestProperty("User-Agent", "Runline/0.17")
                    prefs.getString("etag", null)?.let { request.setRequestProperty("If-None-Match", it) }
                    val code = request.responseCode
                    retryAt = ReleaseCheckPolicy.retryAt(now, code, request.getHeaderField("Retry-After"), request.getHeaderField("X-RateLimit-Reset"))
                    val latest = if (code == 304) requireNotNull(prefs.getString("latest", null)) else {
                        require(code == 200 && request.contentLengthLong <= 262144)
                        val deadline = android.os.SystemClock.elapsedRealtime() + 12_000
                        val bytes = java.io.ByteArrayOutputStream()
                        request.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                require(android.os.SystemClock.elapsedRealtime() < deadline)
                                val n = input.read(buffer); if (n < 0) break
                                require(bytes.size() + n <= 262144); bytes.write(buffer, 0, n)
                            }
                        }
                        requireNotNull(ReleaseVersion.publishedApk(JSONObject(bytes.toString("UTF-8"))))
                    }
                    kotlin.check(prefs.edit().putString("latest", latest).putLong("checked", now)
                        .putString("etag", request.getHeaderField("ETag") ?: if (code == 304) prefs.getString("etag", null) else null).commit())
                    latest
                } finally { request.disconnect() }
            }
            prefs.edit().putLong("retry", retryAt).apply()
            state = result.fold({ ReleaseStatus(latest = it) }, { ReleaseStatus(latest = state.latest, failed = true) })
            notifyChanged()
        }
    }
    companion object {
        const val RELEASES = "https://github.com/adrianetti/runline/releases/latest"
        const val REPOSITORY = "https://github.com/adrianetti/runline"
        private const val API = "https://api.github.com/repos/adrianetti/runline/releases/latest"
        @Volatile private var instance: ReleaseChecker? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: ReleaseChecker(context.applicationContext).also { instance = it } }
    }
}
