package com.runline.app.core

/** What a GPX file offers as a route to follow. */
data class GpxRoute(
    val name: String?,
    val description: String?,
    val points: List<Triple<Double, Double, Double?>>,
    val waypoints: List<RouteWaypoint>,
)

/**
 * A small, strict GPX reader for route import. It never resolves DTDs or entities, so a
 * file cannot make it read other files or expand into memory; it reads track points (or
 * route points when there is no track) and waypoints, and ignores every extension.
 */
object GpxRouteReader {
    const val MAX_BYTES = 15 * 1024 * 1024
    private const val MAX_WAYPOINTS = 500
    private const val MAX_TEXT = 500
    private val BOM = 0xFEFF.toChar().toString()

    class InvalidGpx(message: String) : IllegalArgumentException(message)

    fun read(bytes: ByteArray): GpxRoute {
        if (bytes.size > MAX_BYTES) throw InvalidGpx("too large")
        return read(String(bytes, Charsets.UTF_8))
    }

    fun read(source: String): GpxRoute {
        if (source.length > MAX_BYTES) throw InvalidGpx("too large")
        val text = source.removePrefix(BOM)
        if (text.contains("<!DOCTYPE", ignoreCase = true) || text.contains("<!ENTITY", ignoreCase = true)) throw InvalidGpx("DOCTYPE is not allowed")
        val path = ArrayList<String>()
        val track = ArrayList<Triple<Double, Double, Double?>>()
        val route = ArrayList<Triple<Double, Double, Double?>>()
        val waypoints = ArrayList<RouteWaypoint>()
        var metaName: String? = null; var metaDesc: String? = null; var trackName: String? = null; var routeName: String? = null
        var sawGpx = false
        // The point or waypoint being read.
        var lat = 0.0; var lon = 0.0; var ele: Double? = null; var wName: String? = null; var wDesc: String? = null
        val textBuffer = StringBuilder()
        var i = 0
        val n = text.length
        fun parent(offset: Int = 1) = path.getOrNull(path.size - 1 - offset)
        // A malformed point is skipped later (non-finite coordinates), not fatal for the file.
        fun startPoint(attrs: Map<String, String>) {
            lat = attrs["lat"]?.trim()?.toDoubleOrNull() ?: Double.NaN
            lon = attrs["lon"]?.trim()?.toDoubleOrNull() ?: Double.NaN
            ele = null; wName = null; wDesc = null
        }
        while (i < n) {
            val lt = text.indexOf('<', i)
            if (lt < 0) break
            if (lt > i) textBuffer.append(text, i, lt).also { if (textBuffer.length > MAX_TEXT * 4) textBuffer.setLength(MAX_TEXT * 4) }
            when {
                text.startsWith("<!--", lt) -> { i = text.indexOf("-->", lt + 4).takeIf { it >= 0 }?.plus(3) ?: throw InvalidGpx("open comment") }
                text.startsWith("<![CDATA[", lt) -> {
                    val end = text.indexOf("]]>", lt + 9).takeIf { it >= 0 } ?: throw InvalidGpx("open CDATA")
                    if (textBuffer.length < MAX_TEXT * 4) textBuffer.append(text, lt + 9, minOf(end, lt + 9 + MAX_TEXT * 4))
                    i = end + 3; continue
                }
                text.startsWith("<?", lt) -> { i = text.indexOf("?>", lt + 2).takeIf { it >= 0 }?.plus(2) ?: throw InvalidGpx("open instruction") }
                text.startsWith("<!", lt) -> throw InvalidGpx("declaration is not allowed")
                text.startsWith("</", lt) -> {
                    val end = text.indexOf('>', lt).takeIf { it >= 0 } ?: throw InvalidGpx("open tag")
                    val name = local(text.substring(lt + 2, end).trim())
                    if (path.lastOrNull() != name) throw InvalidGpx("mismatched </$name>")
                    val value = decode(textBuffer.toString()).replace(Regex("\\s+"), " ").filterNot { it.isISOControl() }.trim().take(MAX_TEXT)
                    val up = parent()
                    when (name) {
                        "ele" -> if (up == "trkpt" || up == "rtept" || up == "wpt") ele = value.toDoubleOrNull()
                        "name" -> when (up) {
                            "metadata" -> metaName = value.ifEmpty { null }
                            "trk" -> if (trackName == null) trackName = value.ifEmpty { null }
                            "rte" -> if (routeName == null) routeName = value.ifEmpty { null }
                            "wpt" -> wName = value.ifEmpty { null }
                            "gpx" -> if (metaName == null) metaName = value.ifEmpty { null }
                        }
                        "desc" -> when (up) { "metadata" -> metaDesc = value.ifEmpty { null }; "wpt" -> wDesc = value.ifEmpty { null } }
                        "trkpt" -> { track += Triple(lat, lon, ele); if (track.size > HikingRoute.MAX_POINTS) throw InvalidGpx("too many points") }
                        "rtept" -> { route += Triple(lat, lon, ele); if (route.size > HikingRoute.MAX_POINTS) throw InvalidGpx("too many points") }
                        "wpt" -> if (waypoints.size < MAX_WAYPOINTS && lat in -90.0..90.0 && lon in -180.0..180.0)
                            waypoints += RouteWaypoint(lat, lon, wName ?: "", wDesc, ele)
                    }
                    path.removeAt(path.size - 1); textBuffer.setLength(0)
                    i = end + 1
                }
                else -> {
                    val end = tagEnd(text, lt)
                    val selfClosing = text[end - 1] == '/'
                    val body = text.substring(lt + 1, if (selfClosing) end - 1 else end)
                    val nameEnd = body.indexOfFirst { it.isWhitespace() }.let { if (it < 0) body.length else it }
                    val name = local(body.substring(0, nameEnd))
                    if (name.isEmpty()) throw InvalidGpx("empty tag")
                    val attrs = attributes(body.substring(nameEnd))
                    if (path.isEmpty()) { if (name != "gpx") throw InvalidGpx("not a GPX file"); sawGpx = true }
                    if (path.size > 32) throw InvalidGpx("too deep")
                    if (name == "trkpt" || name == "rtept" || name == "wpt") startPoint(attrs)
                    textBuffer.setLength(0)
                    if (selfClosing) {
                        when (name) {
                            "trkpt" -> track += Triple(lat, lon, null)
                            "rtept" -> route += Triple(lat, lon, null)
                            "wpt" -> if (waypoints.size < MAX_WAYPOINTS && lat in -90.0..90.0 && lon in -180.0..180.0)
                                waypoints += RouteWaypoint(lat, lon, "", null, null)
                        }
                    } else path += name
                    i = end + 1
                }
            }
        }
        if (!sawGpx) throw InvalidGpx("not a GPX file")
        if (path.isNotEmpty()) throw InvalidGpx("unclosed <${path.last()}>")
        val valid = { p: Triple<Double, Double, Double?> -> p.first in -90.0..90.0 && p.second in -180.0..180.0 }
        val points = (if (track.count(valid) >= 2) track else route).filter(valid)
        if (points.size < 2) throw InvalidGpx("no track")
        return GpxRoute(metaName ?: trackName ?: routeName, metaDesc, points, waypoints)
    }

    private fun local(name: String) = name.substringAfter(':')

    private fun tagEnd(text: String, start: Int): Int {
        var quote: Char? = null
        var k = start + 1
        while (k < text.length) {
            val c = text[k]
            if (quote != null) { if (c == quote) quote = null }
            else if (c == '"' || c == '\'') quote = c
            else if (c == '>') return k
            else if (c == '<') throw InvalidGpx("broken tag")
            k++
        }
        throw InvalidGpx("open tag")
    }

    private fun attributes(source: String): Map<String, String> {
        val out = HashMap<String, String>()
        Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*("([^"]*)"|'([^']*)')""").findAll(source).forEach { m ->
            out[local(m.groupValues[1])] = decode(m.groupValues[3].ifEmpty { m.groupValues[4] })
        }
        return out
    }

    /** Only XML's predefined and numeric references; anything else stays literal text. */
    private fun decode(value: String): String {
        if (!value.contains('&')) return value
        return Regex("&(#x[0-9A-Fa-f]{1,6}|#[0-9]{1,7}|amp|lt|gt|quot|apos);").replace(value) { m ->
            val v = m.groupValues[1]
            when {
                v == "amp" -> "&"; v == "lt" -> "<"; v == "gt" -> ">"; v == "quot" -> "\""; v == "apos" -> "'"
                v.startsWith("#x") -> v.substring(2).toInt(16).takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: ""
                else -> v.substring(1).toInt().takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: ""
            }
        }
    }
}
