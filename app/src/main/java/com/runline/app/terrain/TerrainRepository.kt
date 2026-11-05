package com.runline.app.terrain

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AtomicFile
import com.runline.app.core.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

data class TerrainAreaStatus(val area: TerrainDownloadArea?, val completed: Int, val total: Int,
                             val ready: Boolean, val loading: Boolean, val bytes: Long)

/** Bounded public-data downloads; GPS persistence never waits for this worker. */
class TerrainRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("terrain_maps", Context.MODE_PRIVATE)
    private val folder = File(app.filesDir, "terrain-maps")
    private val worker = Executors.newSingleThreadExecutor()
    private val analysis = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private data class Entry(val grid: Any, val bytes: Long)
    private val entries = linkedMapOf<TerrainDownloadKey, Entry>() // Worker-owned.
    private val pending = mutableMapOf<TerrainDownloadKey, Long>()
    private val failedUntil = mutableMapOf<TerrainDownloadKey, Long>()
    @Volatile private var generation = 0L
    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var available = emptyMap<TerrainDownloadKey, Long>()
    @Volatile var area: TerrainDownloadArea? = runCatching {
        preferences.getString("area", null)?.split(',')?.takeIf { it.size == 2 }?.let { TerrainDownloadArea(it[0].toDouble(), it[1].toDouble()) }
    }.getOrNull()
        private set
    @Volatile var snapshot = TerrainMapSnapshot(0, preferences.getBoolean("enabled", true), emptyMap())
        private set
    val enabled get() = preferences.getBoolean("enabled", true)
    init { area?.let { enqueue(it.keys, network = false) } }
    fun listen(listener: () -> Unit) { listeners += listener }
    fun unlisten(listener: () -> Unit) { listeners -= listener }
    fun areaStatus(): TerrainAreaStatus {
        val saved = area; val keys = saved?.keys.orEmpty(); val loaded = available
        return TerrainAreaStatus(saved, keys.count { it in loaded }, keys.size,
            enabled && saved != null && saved.requiredKeys.all { it in loaded },
            synchronized(this) { keys.any { it in pending } }, keys.sumOf { loaded[it] ?: 0L })
    }
    fun downloadArea(next: TerrainDownloadArea): Boolean {
        if (!preferences.edit().putString("area", "${next.latitude},${next.longitude}").commit()) return false
        cancelDownloads(); area = next
        if (!enabled) { setEnabled(true); cancelDownloads() }
        enqueue(next.keys, network = true, retry = true); notifyListeners(); return true
    }
    fun resumeArea() { area?.let { cancelDownloads(); enqueue(it.keys, network = true, retry = true) } }
    fun cancelDownloads() {
        synchronized(this) { generation++; pending.clear() }
        connection?.disconnect(); notifyListeners()
    }
    fun replay(run: RunSession, complete: (Long, RunReplay, ReplayTerrain) -> Unit) {
        analysis.execute {
            val map = snapshot; val replay = RunReplay(map.project(run)); val terrain = ReplayTerrain(replay)
            main.post { complete(map.revision, replay, terrain) }
        }
    }
    fun setEnabled(value: Boolean) {
        preferences.edit().putBoolean("enabled", value).apply(); cancelDownloads()
        synchronized(this) { snapshot = TerrainMapSnapshot(snapshot.revision + 1, value, emptyMap()); available = emptyMap() }
        worker.execute { entries.clear(); synchronized(this) { failedUntil.clear() } }
        if (value) area?.let { enqueue(it.keys, network = false) }
        notifyListeners()
    }
    fun clearCache() {
        cancelDownloads(); area = null; preferences.edit().remove("area").apply()
        synchronized(this) { snapshot = TerrainMapSnapshot(snapshot.revision + 1, enabled, emptyMap()); available = emptyMap() }
        worker.execute { entries.clear(); folder.listFiles().orEmpty().forEach { it.delete() }; synchronized(this) { failedUntil.clear() }; notifyListeners() }
        notifyListeners()
    }
    fun request(points: List<RunPoint>) {
        if (!enabled) return
        enqueue(points.asReversed().flatMap { TerrainDownloadKey.forPoint(it) }.distinct().take(64), network = true)
    }
    fun isLoading(points: List<RunPoint>): Boolean = synchronized(this) {
        points.any { point -> TerrainDownloadKey.forPoint(point).any { it in pending } }
    }
    private fun enqueue(keys: Collection<TerrainDownloadKey>, network: Boolean, retry: Boolean = false) {
        if (!enabled) return
        keys.forEach { key ->
            val token = synchronized(this) {
                if (retry) failedUntil.remove(key)
                if (key in available || key in pending || pending.size >= 96 || (network && (failedUntil[key] ?: 0L) > SystemClock.elapsedRealtime())) return@forEach
                generation.also { pending[key] = it }
            }
            worker.execute {
                try {
                    if (token != generation || !enabled) return@execute
                    folder.mkdirs(); val file = AtomicFile(File(folder, key.name))
                    val cached = runCatching { file.openRead().use { input -> readBounded(input, key.maxBytes) }.let { bytes -> Entry(decode(key, bytes), bytes.size.toLong()) } }.getOrNull()
                    val entry = cached ?: if (network) {
                        val bytes = download(key); val grid = decode(key, bytes)
                        if (token != generation || !enabled) return@execute
                        val output = file.startWrite()
                        try { output.write(bytes); file.finishWrite(output) }
                        catch (error: Exception) { file.failWrite(output); throw error }
                        Entry(grid, bytes.size.toLong())
                    } else return@execute
                    synchronized(this) {
                        if (token != generation || !enabled) return@execute
                        entries[key] = entry
                        val pinned = area?.keys.orEmpty()
                        while (entries.size > 64) entries.remove(entries.keys.firstOrNull { it !in pinned } ?: entries.keys.first())
                        available = entries.mapValues { it.value.bytes }
                        snapshot = TerrainMapSnapshot(snapshot.revision + 1, true,
                            entries.filterKeys { it is TerrainDownloadKey.Spain }.map { (k, v) -> (k as TerrainDownloadKey.Spain).key to v.grid as TerrainGrid }.toMap(),
                            entries.filterKeys { it is TerrainDownloadKey.World }.map { (k, v) -> (k as TerrainDownloadKey.World).key to v.grid as WorldTerrainGrid }.toMap())
                    }
                    trimCache()
                } catch (_: Exception) {
                    synchronized(this) {
                        if (token == generation) failedUntil[key] = SystemClock.elapsedRealtime() + 60_000L
                        while (failedUntil.size > 192) failedUntil.remove(failedUntil.keys.first())
                    }
                } finally {
                    synchronized(this) { if (pending[key] == token) pending.remove(key) }
                    notifyListeners()
                }
            }
        }
        notifyListeners()
    }
    private fun trimCache() {
        val pinned = area?.keys.orEmpty().map { it.name }.toSet()
        val files = folder.listFiles().orEmpty().filter { it.extension in setOf("asc", "png") }
            .sortedWith(compareByDescending<File> { it.name in pinned }.thenByDescending { it.lastModified() })
        var bytes = 0L
        files.forEachIndexed { index, file -> bytes += file.length(); if (index >= 96 || bytes > 64 * 1024 * 1024L) file.delete() }
    }
    private fun decode(key: TerrainDownloadKey, bytes: ByteArray): Any = when (key) {
        is TerrainDownloadKey.Spain -> TerrainGrid.parse(bytes.toString(Charsets.US_ASCII)).also { require(it.matches(key.key)) }
        is TerrainDownloadKey.World -> {
            require(bytes.size >= 24 && bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map(Int::toByte))
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth == 256 && bounds.outHeight == 256 && bounds.outMimeType == "image/png")
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inScaled = false; inPremultiplied = false }))
            try {
                require(bitmap.width == 256 && bitmap.height == 256)
                val pixels = IntArray(256 * 256); bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256)
                WorldTerrainGrid.fromArgb(pixels)
            } finally { bitmap.recycle() }
        }
    }
    private fun readBounded(input: java.io.InputStream, maximum: Int, deadline: Long = Long.MAX_VALUE): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            require(SystemClock.elapsedRealtime() < deadline)
            val count = input.read(buffer); if (count < 0) break
            require(output.size() + count <= maximum); output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private fun download(key: TerrainDownloadKey): ByteArray {
        val request = URL(key.url).openConnection() as HttpURLConnection; connection = request
        try {
            request.connectTimeout = 8_000; request.readTimeout = 12_000; request.instanceFollowRedirects = false
            request.setRequestProperty("User-Agent", "Runline/0.15 (offline terrain)")
            require(request.responseCode == 200 && request.contentLengthLong <= key.maxBytes)
            return request.inputStream.use { readBounded(it, key.maxBytes, SystemClock.elapsedRealtime() + 20_000) }
        } finally { request.disconnect(); connection = null }
    }
    private fun notifyListeners() { main.post { listeners.forEach { it() } } }
    companion object {
        @Volatile private var instance: TerrainRepository? = null
        fun get(context: Context): TerrainRepository = instance ?: synchronized(this) {
            instance ?: TerrainRepository(context).also { instance = it }
        }
    }
}
