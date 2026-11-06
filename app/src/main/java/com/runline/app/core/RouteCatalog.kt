package com.runline.app.core

import org.json.JSONObject

/** One route listed in the open repository's `routes/index.json`. */
data class CatalogRoute(
    val id: String,
    val file: String,
    val sha256: String,
    val names: Map<String, String>,
    val regions: Map<String, String>,
    val summaries: Map<String, String>,
    val activity: ActivityType,
    val loop: Boolean,
    val distanceMeters: Double,
    val ascentMeters: Double?,
    val descentMeters: Double?,
    val startLatitude: Double,
    val startLongitude: Double,
) {
    fun name(language: String) = names[language] ?: names["en"] ?: names.values.first()
    fun region(language: String) = regions[language] ?: regions["en"] ?: regions.values.firstOrNull()
    fun summary(language: String) = summaries[language] ?: summaries["en"] ?: summaries.values.firstOrNull()
}

data class RouteCatalogIndex(val version: Int, val license: String, val attribution: String, val routes: List<CatalogRoute>)

/** Strict reader: a bad catalog is rejected whole, so a broken download never replaces a good one. */
object RouteCatalog {
    const val MAX_BYTES = 1_000_000
    const val MAX_ROUTES = 500
    private val ID = Regex("[a-z0-9][a-z0-9-]{2,79}")
    private val FILE = Regex("[a-z0-9][a-z0-9_-]*(/[a-z0-9][a-z0-9_-]*){0,4}\\.gpx")
    private val SHA = Regex("[0-9a-f]{64}")
    private val LANGUAGE = Regex("[a-z]{2}")

    fun parse(text: String): RouteCatalogIndex {
        require(text.length <= MAX_BYTES)
        val root = JSONObject(text)
        require(root.getInt("version") == 1)
        val license = root.getString("license").also { require(it == "ODbL-1.0") }
        val attribution = root.getString("attribution").also { require(it.length in 10..400 && it.contains("OpenStreetMap")) }
        val list = root.getJSONArray("routes"); require(list.length() <= MAX_ROUTES)
        val routes = (0 until list.length()).map { entry(list.getJSONObject(it)) }
        require(routes.map { it.id }.toSet().size == routes.size) { "duplicate route id" }
        return RouteCatalogIndex(1, license, attribution, routes)
    }

    private fun texts(o: JSONObject?, max: Int, required: Boolean): Map<String, String> {
        if (o == null) { require(!required); return emptyMap() }
        val out = LinkedHashMap<String, String>()
        o.keys().forEach { key ->
            require(LANGUAGE.matches(key))
            val value = o.getString(key).trim()
            require(value.length in 1..max && value.none { it.isISOControl() } && !value.contains('<'))
            out[key] = value
        }
        require(!required || out.isNotEmpty())
        return out
    }

    private fun entry(o: JSONObject): CatalogRoute {
        val id = o.getString("id").also { require(ID.matches(it)) }
        val file = o.getString("file").also { require(FILE.matches(it) && !it.contains("..")) }
        val sha = o.getString("sha256").also { require(SHA.matches(it)) }
        val activity = when (o.getString("activity")) { "hike" -> ActivityType.HIKE; "run" -> ActivityType.RUN; "walk" -> ActivityType.WALK; else -> error("activity") }
        val distance = o.getDouble("distance_m").also { require(it > 0 && it <= 500_000) }
        fun optional(key: String) = if (o.isNull(key) || !o.has(key)) null else o.getDouble(key).also { require(it in 0.0..20_000.0) }
        val start = o.getJSONArray("start")
        val lat = start.getDouble(0); val lon = start.getDouble(1)
        require(lat in -90.0..90.0 && lon in -180.0..180.0)
        return CatalogRoute(id, file, sha, texts(o.getJSONObject("name"), 120, true), texts(o.optJSONObject("region"), 80, false),
            texts(o.optJSONObject("summary"), 400, false), activity, o.optString("shape") == "loop", distance,
            optional("ascent_m"), optional("descent_m"), lat, lon)
    }
}
