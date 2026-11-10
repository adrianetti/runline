package com.runline.app.core

import java.text.NumberFormat
import java.util.Locale

/**
 * Names for imported routes. A useful name in the file wins; otherwise the suggestion reads like
 * "Granada, Los Pinos – Puente Romano loop 10.3K": town, start and far point, shape, distance.
 * Places come from waypoints in the GPX or from the phone's geocoder, and every part is optional.
 */
object RouteImportNaming {
    const val MAX_NAME = 120
    private const val MAX_SPOT = 48
    enum class Shape { ROUTE, LOOP, OUT_AND_BACK }

    /** What a geocoder knows about one point: its town and the most specific useful label. */
    data class Place(val town: String?, val spot: String?)

    /** One reverse-geocoding result, reduced to the fields that can name a place. */
    data class AddressParts(val feature: String? = null, val premises: String? = null, val street: String? = null,
                            val district: String? = null, val town: String? = null, val area: String? = null,
                            val region: String? = null, val country: String? = null)

    /** Shape words in the UI language: "loop", "out and back", and "Route" for a nameless one-way route. */
    data class Words(val loop: String, val outAndBack: String, val route: String)

    fun clean(value: String): String = value
        .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
        .replace(Regex("\\s+"), " ").trim().take(MAX_NAME).trim()

    // Provider IDs, timestamps, generic export names and default activity titles say nothing about the route.
    private val idLike = "(?=[0-9a-f:._-]*[0-9])[0-9a-f:._-]+"
    private val generic = listOf(
        Regex("(?i)(gpx|track|route|activity|recording|export|download|untitled|ruta|actividad|recorrido|grabaci[oó]n)([\\s._()-]*[0-9]+[\\s._()-]*)*"),
        Regex("(?i)((morning|afternoon|evening|night|lunch|midday)\\s+)?(run|running|ride|walk|walking|hike|hiking|jog|trail run)([\\s._()-]+$idLike)*[\\s._()-]*"),
        Regex("(?i)(carrera|caminata|senderismo|excursi[oó]n|paseo|marcha)(\\s+(matutina|vespertina|nocturna|(de|por) (la )?(ma[nñ]ana|tarde|noche|mediod[ií]a)|a la hora de comer))?([\\s._()-]+$idLike)*[\\s._()-]*"),
    )
    private fun isGeneric(name: String) = name.count { it.isLetter() } < 2 || Regex("(?i)[0-9a-f-]{20,}").matches(name) ||
        generic.any { it.matches(name) }

    /** The GPX's own title, then a readable filename; null when neither says anything about the route. */
    fun suppliedName(gpxName: String?, fileName: String?): String? {
        clean(gpxName.orEmpty()).takeUnless(::isGeneric)?.let { return it }
        val file = fileName.orEmpty().substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("(?i)\\.gpx$"), "").replace('_', ' ')
            // Download copies ("Alhambra (1)") and Runline's own export suffix ("Caminata 8,42 km - 2026-11-01 10-30").
            .replace(Regex("\\s*\\(\\d+\\)$"), "")
            .replace(Regex("\\s+\\d+[.,]\\d+ km - \\d{4}-\\d{2}-\\d{2} \\d{2}-\\d{2}$"), "")
            // Provider IDs glued to the front, as in "1448280673GRANADAPINOSGENIL".
            .replace(Regex("^\\d{6,}[\\s.-]*"), "")
        // A long run of letters with no spaces ("GRANADAPINOSGENILGUEJAR") is not readable as a name.
        return clean(file).takeUnless { isGeneric(it) || (' ' !in it && it.length > 18) }
    }

    fun shape(route: HikingRoute): Shape {
        if (route.distanceMeters < 200) return Shape.ROUTE
        val first = route.points.first(); val last = route.points.last()
        val closure = (route.distanceMeters * .02).coerceIn(25.0, 100.0)
        if (distance(first, last) > closure) return Shape.ROUTE
        // Compare equal distances from opposite ends, independent of vertex spacing.
        // A shared start/end alone does not make a path an out-and-back.
        val pairs = (1..24).map { step ->
            val d = route.distanceMeters * step / 50.0
            distance(at(route, d), at(route, route.distanceMeters - d))
        }
        return if (pairs.count { it <= 25 } >= 23 && pairs.max() <= 60 &&
            distance(first, at(route, route.distanceMeters / 2)) >= 100) Shape.OUT_AND_BACK else Shape.LOOP
    }

    /** The two points that name a route: its start, and its finish or (loops, out-and-backs) the point farthest from the start. */
    fun namingPoints(route: HikingRoute, shape: Shape): List<RoutePoint> {
        val start = route.points.first()
        val far = if (shape == Shape.ROUTE) route.points.last() else route.points.maxBy { distance(start, it) }
        return listOf(start, far)
    }

    /** A place label worth showing, or null for house numbers, postcodes, plus codes and unnamed roads. */
    fun label(value: String?): String? {
        val text = clean(value.orEmpty())
        if (text.count { it.isLetter() } < 2 || text.substringBefore(' ').contains('+')) return null
        if (Regex("(?i)unnamed|sin nombre|^s/?n$").containsMatchIn(text)) return null
        return text.take(MAX_SPOT).trim()
    }

    /** Town plus the most specific label among a point's geocoder results (closest first). */
    fun place(results: List<AddressParts>): Place? {
        val town = results.firstNotNullOfOrNull { label(it.town) } ?: results.firstNotNullOfOrNull { label(it.area) }
        val broad = results.flatMap { listOf(it.town, it.area, it.region, it.country) }.mapNotNull(::label).map { it.lowercase() }.toSet()
        val spot = results.asSequence().flatMap { sequenceOf(it.feature, it.premises, it.street, it.district) }
            .mapNotNull(::label).firstOrNull { it.lowercase() !in broad }
        return if (town == null && spot == null) null else Place(town, spot)
    }

    /**
     * "Granada, Los Pinos – Puente Romano loop 10.3K", "Madrid, Gran Vía – Museo del Prado out and back 5K",
     * or "Loop 10.3K" with no places. Named waypoints near a point beat the geocoder's label for it.
     */
    fun suggestion(route: HikingRoute, shape: Shape, places: List<Place?>, words: Words, locale: Locale): String {
        val points = namingPoints(route, shape)
        val town = places.firstNotNullOfOrNull { it?.town }?.let(::label)
        val spots = points.mapIndexedNotNull { i, point -> label(waypointNear(route, point) ?: places.getOrNull(i)?.spot) }
            .filterNot { it.equals(town, true) }.distinctBy { it.lowercase(locale) }
        val head = listOfNotNull(town, spots.joinToString(" – ").ifEmpty { null }).joinToString(", ")
        val kind = when (shape) {
            Shape.LOOP -> words.loop
            Shape.OUT_AND_BACK -> words.outAndBack
            Shape.ROUTE -> words.route.takeIf { head.isEmpty() }
        }
        val name = listOfNotNull(head.ifEmpty { null }, kind, distance(route.distanceMeters, locale)).joinToString(" ")
        return clean(name.replaceFirstChar { it.titlecase(locale) })
    }

    /** "10.3K" or "10,3K"; whole kilometres drop the decimal ("5K"). */
    fun distance(meters: Double, locale: Locale): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0; maximumFractionDigits = 1; isGroupingUsed = false
    }.format(meters / 1000) + "K"

    /** Only waypoint labels within 100 m of a naming point describe it. */
    private fun waypointNear(route: HikingRoute, point: RoutePoint): String? = route.waypoints
        .filter { label(it.name) != null }
        .minByOrNull { RouteGeometry.meters(point.latitude, point.longitude, it.latitude, it.longitude) }
        ?.takeIf { RouteGeometry.meters(point.latitude, point.longitude, it.latitude, it.longitude) <= 100 }
        ?.name

    private fun distance(a: RoutePoint, b: RoutePoint) = RouteGeometry.meters(a.latitude, a.longitude, b.latitude, b.longitude)
    private fun at(route: HikingRoute, meters: Double): RoutePoint {
        val points = route.points
        var low = 1; var high = points.lastIndex
        while (low < high) { val middle = (low + high) / 2; if (points[middle].distanceMeters < meters) low = middle + 1 else high = middle }
        val a = points[low - 1]; val b = points[low]
        val span = b.distanceMeters - a.distanceMeters
        val fraction = if (span <= 0) 0.0 else ((meters - a.distanceMeters) / span).coerceIn(0.0, 1.0)
        // Interpolate across the antimeridian on the short arc.
        val delta = ((b.longitude - a.longitude + 540) % 360) - 180
        val lon = ((a.longitude + delta * fraction + 540) % 360) - 180
        return RoutePoint(a.latitude + (b.latitude - a.latitude) * fraction, lon, null, meters)
    }
}
