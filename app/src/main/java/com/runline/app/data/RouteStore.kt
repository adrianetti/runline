package com.runline.app.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.runline.app.core.ActivityType
import com.runline.app.core.CatalogRoute
import com.runline.app.core.GpxRouteReader
import com.runline.app.core.HikingRoute
import com.runline.app.core.RouteCatalog
import com.runline.app.core.RouteCatalogIndex
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** A route as listed in the Routes tab. */
data class RouteEntry(
    val id: String,
    val name: String,
    val region: String?,
    val summary: String?,
    val imported: Boolean,
    val activity: ActivityType,
    val distanceMeters: Double,
    val ascentMeters: Double?,
    val loop: Boolean,
)

/**
 * Routes to follow: the open catalog bundled with the app (refreshable from the public
 * repository, every file checked against the catalog's SHA-256) and GPX files the user
 * imported. All private storage; nothing here is a recording.
 */
class RouteStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "routes")
    private val downloaded = File(root, "catalog")
    private val imported = File(root, "imported")

    fun catalog(): RouteCatalogIndex = synchronized(LOCK) {
        runCatching { RouteCatalog.parse(File(downloaded, "index.json").readText()) }.getOrNull()
            ?: RouteCatalog.parse(app.assets.open("routes/index.json").bufferedReader().use { it.readText() })
    }

    fun list(language: String): List<RouteEntry> {
        val catalog = runCatching { catalog().routes }.getOrDefault(emptyList()).map {
            RouteEntry(it.id, it.name(language), it.region(language), it.summary(language), false, it.activity, it.distanceMeters, it.ascentMeters, it.loop)
        }
        val mine = (imported.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedByDescending { it.lastModified() }.mapNotNull { file ->
            runCatching {
                val o = JSONObject(file.readText()); require(o.getInt("version") == 1)
                RouteEntry(o.getString("id"), o.getString("name"), null, null, true, ActivityType.fromStorage(o.optString("activity", "hike")),
                    o.getDouble("distance_m"), if (o.isNull("ascent_m")) null else o.getDouble("ascent_m"), o.getBoolean("loop"))
            }.getOrNull()
        }
        return mine + catalog
    }

    fun entry(id: String, language: String) = list(language).firstOrNull { it.id == id }

    fun load(id: String): HikingRoute? = synchronized(LOCK) {
        cache[id]?.let { return it }
        val bytes = gpx(id) ?: return null
        val parsed = runCatching { GpxRouteReader.read(bytes) }.getOrNull() ?: return null
        val name = catalogEntry(id)?.name("en") ?: importedName(id) ?: parsed.name ?: id
        return runCatching { HikingRoute.of(id, name, parsed.points, parsed.waypoints, parsed.description) }.getOrNull()?.also { cache[id] = it }
    }

    /** The route's GPX bytes, exactly as published or imported. */
    fun gpx(id: String): ByteArray? {
        if (IMPORTED_ID.matches(id)) return runCatching { File(imported, "$id.gpx").readBytes() }.getOrNull()
        val entry = catalogEntry(id) ?: return null
        val local = File(downloaded, entry.file)
        val candidates = listOfNotNull(runCatching { local.readBytes() }.getOrNull(),
            runCatching { app.assets.open("routes/${entry.file}").use { it.readBytes() } }.getOrNull())
        return candidates.firstOrNull { sha256(it) == entry.sha256 }
    }

    fun fileName(id: String, language: String): String {
        val name = entry(id, language)?.name ?: "route"
        return name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "route" } + ".gpx"
    }

    private fun catalogEntry(id: String): CatalogRoute? = runCatching { catalog().routes.firstOrNull { it.id == id } }.getOrNull()
    private fun importedName(id: String) = runCatching { JSONObject(File(imported, "$id.json").readText()).getString("name") }.getOrNull()

    /** Reads a GPX file the user picked. Off the main thread. */
    fun import(uri: Uri, fallbackName: String): RouteEntry {
        val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(64 * 1024); var total = 0
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                total += n; if (total > GpxRouteReader.MAX_BYTES) throw GpxRouteReader.InvalidGpx("too large")
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } ?: throw IOException("unreadable")
        val parsed = GpxRouteReader.read(bytes)
        val id = "import-" + UUID.randomUUID().toString()
        val name = (parsed.name ?: fallbackName.substringBeforeLast('.')).filterNot { it.isISOControl() }.trim().take(120).ifBlank { "GPX" }
        val route = HikingRoute.of(id, name, parsed.points, parsed.waypoints, parsed.description)
        val stats = route.stats
        synchronized(LOCK) {
            check(imported.isDirectory || imported.mkdirs())
            writePrivateAtomic(AtomicFile(File(imported, "$id.gpx")), bytes)
            writePrivateAtomic(AtomicFile(File(imported, "$id.json")), JSONObject().put("version", 1).put("id", id).put("name", name)
                .put("activity", "hike").put("distance_m", stats.distanceMeters).put("ascent_m", stats.ascentMeters ?: JSONObject.NULL)
                .put("loop", stats.loop).put("imported_at", System.currentTimeMillis()).toString().toByteArray())
            cache[id] = route
        }
        return RouteEntry(id, name, null, null, true, ActivityType.HIKE, stats.distanceMeters, stats.ascentMeters, stats.loop)
    }

    fun delete(id: String) = synchronized(LOCK) {
        require(IMPORTED_ID.matches(id))
        File(imported, "$id.gpx").delete(); File(imported, "$id.json").delete(); cache.remove(id)
    }

    /**
     * Downloads the current catalog from the public repository. A file is kept only when it
     * matches the catalog's SHA-256; the catalog itself is replaced last. Off the main thread.
     * Returns the number of routes available afterwards.
     */
    fun refresh(): Int {
        val index = String(fetch(BASE_URL + "index.json", RouteCatalog.MAX_BYTES), Charsets.UTF_8)
        val catalog = RouteCatalog.parse(index)
        synchronized(LOCK) { check(downloaded.isDirectory || downloaded.mkdirs()) }
        catalog.routes.forEach { entry ->
            val local = File(downloaded, entry.file)
            val bundled = runCatching { app.assets.open("routes/${entry.file}").use { it.readBytes() } }.getOrNull()
            val have = runCatching { local.readBytes() }.getOrNull()
            if ((have != null && sha256(have) == entry.sha256) || (bundled != null && sha256(bundled) == entry.sha256)) return@forEach
            val bytes = fetch(BASE_URL + entry.file, GpxRouteReader.MAX_BYTES)
            if (sha256(bytes) != entry.sha256) throw IOException("checksum mismatch for ${entry.id}")
            GpxRouteReader.read(bytes)
            synchronized(LOCK) {
                check(local.parentFile!!.isDirectory || local.parentFile!!.mkdirs())
                writePrivateAtomic(AtomicFile(local), bytes)
            }
        }
        synchronized(LOCK) {
            writePrivateAtomic(AtomicFile(File(downloaded, "index.json")), index.toByteArray(Charsets.UTF_8))
            cache.clear()
        }
        return catalog.routes.size
    }

    private fun fetch(url: String, limit: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "Runline/0.20 (open routes)")
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(32 * 1024); var total = 0
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    total += n; if (total > limit) throw IOException("too large")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/adrianetti/runline/main/routes/"
        const val REPOSITORY_URL = "https://github.com/adrianetti/runline/tree/main/routes"
        val IMPORTED_ID = Regex("import-[0-9a-f-]{36}")
        private val LOCK = Any()
        private val cache = HashMap<String, HikingRoute>()
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
