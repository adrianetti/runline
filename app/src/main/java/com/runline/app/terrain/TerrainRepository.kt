package com.runline.app.terrain

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import com.runline.app.core.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Optional downloads have their own bounded worker; recording never waits for network or disk. */
class TerrainRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("terrain_maps", Context.MODE_PRIVATE)
    private val folder = File(app.filesDir, "terrain-maps")
    private val worker = Executors.newSingleThreadExecutor()
    private val analysis = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val pending = mutableSetOf<TerrainTileKey>()
    private val failedUntil = mutableMapOf<TerrainTileKey, Long>()
    private val grids = linkedMapOf<TerrainTileKey, TerrainGrid>() // Worker-owned.
    private val generation = AtomicLong()
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile var snapshot = TerrainMapSnapshot(0, preferences.getBoolean("enabled", true), emptyMap())
        private set
    val enabled get() = preferences.getBoolean("enabled", true)
    fun listen(listener: () -> Unit) { listeners += listener }
    fun unlisten(listener: () -> Unit) { listeners -= listener }
    /** Projection and grade fitting can be substantial for a long activity; keep them off the UI thread. */
    fun replay(run: RunSession, complete: (Long, RunReplay, ReplayTerrain) -> Unit) {
        analysis.execute {
            val map = snapshot
            val replay = RunReplay(map.project(run)); val terrain = ReplayTerrain(replay)
            main.post { complete(map.revision, replay, terrain) }
        }
    }
    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean("enabled", value).apply()
        generation.incrementAndGet(); if (!value) connection?.disconnect()
        synchronized(this) { snapshot = TerrainMapSnapshot(snapshot.revision + 1, value, emptyMap()) }
        notifyListeners()
        worker.execute { grids.clear(); synchronized(pending) { failedUntil.clear() } }
    }
    fun clearCache() {
        generation.incrementAndGet(); connection?.disconnect()
        synchronized(this) { snapshot = TerrainMapSnapshot(snapshot.revision + 1, enabled, emptyMap()) }
        notifyListeners()
        worker.execute { grids.clear(); folder.listFiles().orEmpty().forEach { it.delete() }; synchronized(pending) { failedUntil.clear() }; publish() }
    }
    fun request(points: List<RunPoint>) {
        if (!enabled) return
        points.asReversed().mapNotNull(TerrainTileKey::at).distinct().take(48).forEach { key ->
            val token = generation.get()
            synchronized(pending) {
                if (pending.size >= 48 || key in pending || (failedUntil[key] ?: 0L) > android.os.SystemClock.elapsedRealtime()) return@forEach
                pending += key
            }
            worker.execute {
                try {
                    if (token != generation.get() || !enabled || key in grids) return@execute
                    folder.mkdirs()
                    val file = AtomicFile(File(folder, key.name + ".asc"))
                    val cached = runCatching {
                        val bytes = file.openRead().use { input ->
                            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (true) { val count = input.read(buffer); if (count < 0) break
                                require(output.size() + count <= TerrainGrid.MAX_BYTES); output.write(buffer, 0, count) }
                            output.toByteArray()
                        }
                        TerrainGrid.parse(bytes.toString(Charsets.US_ASCII)).also { require(it.matches(key)) }
                    }.getOrNull()
                    val grid = cached ?: download(key).let { text ->
                        val parsed = TerrainGrid.parse(text); require(parsed.matches(key))
                        if (token != generation.get() || !enabled) return@execute
                        val output = file.startWrite()
                        try { output.write(text.toByteArray(Charsets.US_ASCII)); file.finishWrite(output) }
                        catch (error: Exception) { file.failWrite(output); throw error }
                        parsed
                    }
                    if (token != generation.get() || !enabled) return@execute
                    grids[key] = grid
                    while (grids.size > 48) grids.remove(grids.keys.first())
                    // Keep at most 40 MiB and 64 areas on disk; completed recordings are elsewhere.
                    val files = folder.listFiles().orEmpty().filter { it.extension == "asc" }.sortedByDescending { it.lastModified() }
                    var bytes = 0L
                    files.forEachIndexed { index, entry -> bytes += entry.length(); if (index >= 64 || bytes > 40 * 1024 * 1024L) entry.delete() }
                    publish()
                } catch (_: Exception) {
                    synchronized(pending) {
                        failedUntil[key] = android.os.SystemClock.elapsedRealtime() + 60_000L
                        while (failedUntil.size > 128) failedUntil.remove(failedUntil.keys.first())
                    }
                    notifyListeners()
                } finally { synchronized(pending) { pending -= key }; notifyListeners() }
            }
        }
    }
    fun isLoading(points: List<RunPoint>): Boolean = synchronized(pending) { points.any { TerrainTileKey.at(it) in pending } }
    private fun publish() {
        synchronized(this) { snapshot = TerrainMapSnapshot(snapshot.revision + 1, enabled, if (enabled) grids.toMap() else emptyMap()) }
        notifyListeners()
    }
    private fun notifyListeners() { main.post { listeners.forEach { it() } } }
    private fun download(key: TerrainTileKey): String {
        val request = URL(key.url()).openConnection() as HttpURLConnection
        connection = request
        try {
            request.connectTimeout = 8_000; request.readTimeout = 12_000; request.instanceFollowRedirects = false
            request.setRequestProperty("Accept", "application/asc, multipart/mixed")
            request.setRequestProperty("User-Agent", "Runline/0.14 (terrain maps; IGN MDT05)")
            require(request.responseCode == 200)
            require(request.contentLengthLong <= TerrainGrid.MAX_BYTES)
            val bytes = request.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
                while (true) { require(android.os.SystemClock.elapsedRealtime() < deadline); val count = input.read(buffer); if (count < 0) break
                    require(output.size() + count <= TerrainGrid.MAX_BYTES); output.write(buffer, 0, count) }
                output.toByteArray()
            }
            require(bytes.size <= TerrainGrid.MAX_BYTES)
            return bytes.toString(Charsets.US_ASCII)
        } finally { request.disconnect(); connection = null }
    }
    companion object {
        @Volatile private var instance: TerrainRepository? = null
        fun get(context: Context): TerrainRepository = instance ?: synchronized(this) {
            instance ?: TerrainRepository(context).also { instance = it }
        }
    }
}
