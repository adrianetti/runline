package com.runline.app.music

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SpotifyResponse(val status: Int, val body: String, val retryAfterSeconds: Int? = null)

/** [form] is the token endpoint's body; everything else is JSON or empty. */
fun interface SpotifyTransport { fun request(method: String, url: String, bearer: String?, body: String?, form: Boolean): SpotifyResponse }

class SpotifyFailure(val reason: Reason, val retryAfterSeconds: Int? = null) : Exception(reason.name) {
    enum class Reason {
        /** Not connected, or Spotify no longer accepts the saved session. */
        AUTH,
        /** Playback control is a Premium feature of Spotify's Web API. */
        PREMIUM,
        /** Spotify is not running anywhere this account can be controlled. */
        NO_DEVICE,
        /** A Development Mode app only serves the accounts its owner added. */
        NOT_ALLOWED,
        RATE_LIMIT, NETWORK, RESPONSE,
    }
}

data class SpotifyTokens(val access: String, val refresh: String, val expiresAtMs: Long)
data class SpotifyTrack(val uri: String, val name: String, val artist: String, val durationMs: Long,
                        /** The smallest album cover Spotify offers, shown beside the title as its terms ask. */
                        val coverUrl: String? = null)
data class SpotifyDevice(val id: String, val name: String, val type: String, val active: Boolean, val restricted: Boolean)
data class SpotifyPlaylist(val id: String, val name: String, val tracks: Int, val ownerId: String, val collaborative: Boolean)
data class SpotifyPlayback(val device: SpotifyDevice?, val playing: Boolean, val track: SpotifyTrack?, val contextUri: String?, val progressMs: Long) {
    val remainingMs: Long? get() = track?.let { (it.durationMs - progressMs).coerceAtLeast(0) }
}

/** Token exchange and refresh need only the public Client ID. */
class SpotifyAuthApi(private val transport: SpotifyTransport = HttpSpotifyTransport(), private val now: () -> Long = System::currentTimeMillis) {
    fun exchange(clientId: String, code: String, verifier: String): SpotifyTokens = token(mapOf(
        "grant_type" to "authorization_code", "code" to code, "redirect_uri" to SpotifyPkce.REDIRECT_URI,
        "client_id" to clientId, "code_verifier" to verifier), null)
    /** Spotify may or may not return a new refresh token; keep the old one when it does not. */
    fun refresh(clientId: String, refreshToken: String): SpotifyTokens = token(mapOf(
        "grant_type" to "refresh_token", "refresh_token" to refreshToken, "client_id" to clientId), refreshToken)
    private fun token(form: Map<String, String>, previousRefresh: String?): SpotifyTokens {
        val body = form.entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val response = transport.request("POST", TOKEN_URL, null, body, true)
        if (response.status == 429) throw SpotifyFailure(SpotifyFailure.Reason.RATE_LIMIT, response.retryAfterSeconds)
        if (response.status in 400..499) throw SpotifyFailure(SpotifyFailure.Reason.AUTH)
        if (response.status !in 200..299) throw SpotifyFailure(SpotifyFailure.Reason.NETWORK)
        return runCatching {
            val json = JSONObject(response.body)
            val access = json.getString("access_token"); require(validToken(access))
            val refresh = json.optString("refresh_token").ifEmpty { previousRefresh ?: "" }; require(validToken(refresh))
            SpotifyTokens(access, refresh, now() + json.getLong("expires_in").coerceIn(60, 86_400) * 1000)
        }.getOrElse { throw SpotifyFailure(SpotifyFailure.Reason.RESPONSE) }
    }
    companion object {
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        fun validToken(value: String) = value.length in 20..4000 && value.all { it.code in 33..126 }
    }
}

/** Supplies a current access token; [access] with `fresh` forces one refresh after a 401. */
fun interface SpotifyTokenSource { fun access(fresh: Boolean): String }

/**
 * Endpoints still open to Development Mode apps after Spotify's February 2026 changes: the
 * player and the list of the user's playlists. Nothing here needs a client secret, and nothing
 * read here is sent anywhere but back to Spotify.
 */
class SpotifyApi(private val tokens: SpotifyTokenSource, private val transport: SpotifyTransport = HttpSpotifyTransport()) {
    fun displayName(): String = parse { JSONObject(call("GET", "/me").body).let { it.optString("display_name").ifBlank { it.optString("id") } }.take(80) }

    /** Null while nothing is playing or no device is active. */
    fun playback(): SpotifyPlayback? {
        val response = call("GET", "/me/player")
        if (response.status == 204 || response.body.isBlank()) return null
        return parse {
            val json = JSONObject(response.body)
            SpotifyPlayback(json.optJSONObject("device")?.let(::device), json.optBoolean("is_playing"),
                json.optJSONObject("item")?.let(::track), json.optJSONObject("context")?.optString("uri")?.ifBlank { null },
                json.optLong("progress_ms").coerceAtLeast(0))
        }
    }
    fun devices(): List<SpotifyDevice> = parse {
        val list = JSONObject(call("GET", "/me/player/devices").body).optJSONArray("devices") ?: JSONArray()
        (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(::device) }
    }
    /** With neither a context nor tracks this resumes what was playing. */
    fun play(deviceId: String? = null, contextUri: String? = null) {
        val body = contextUri?.let { require(PLAYLIST_URI.matches(it)); JSONObject().put("context_uri", it).toString() }
        call("PUT", "/me/player/play" + device(deviceId), body)
    }
    fun pause(deviceId: String? = null) { call("PUT", "/me/player/pause" + device(deviceId)) }
    fun next(deviceId: String? = null) { call("POST", "/me/player/next" + device(deviceId)) }
    fun shuffle(on: Boolean, deviceId: String? = null) { call("PUT", "/me/player/shuffle?state=$on" + device(deviceId, "&")) }

    fun playlists(limit: Int = 200): List<SpotifyPlaylist> = parse {
        val result = mutableListOf<SpotifyPlaylist>()
        var offset = 0
        while (result.size < limit) {
            val page = JSONObject(call("GET", "/me/playlists?limit=50&offset=$offset").body)
            val items = page.optJSONArray("items") ?: break
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id"); if (!PLAYLIST_ID.matches(id)) continue
                // February 2026 renamed the count's holder from `tracks` to `items`; accept both.
                val total = (item.optJSONObject("items") ?: item.optJSONObject("tracks"))?.optInt("total") ?: 0
                result += SpotifyPlaylist(id, item.optString("name").ifBlank { id }.take(120), total,
                    item.optJSONObject("owner")?.optString("id").orEmpty(), item.optBoolean("collaborative"))
            }
            offset += 50
            if (items.length() < 50 || page.isNull("next")) break
        }
        result.take(limit)
    }
    private fun device(id: String?, separator: String = "?") = id?.let { require(DEVICE_ID.matches(it)); separator + "device_id=" + it }.orEmpty()
    private fun device(json: JSONObject): SpotifyDevice? = json.optString("id").takeIf(DEVICE_ID::matches)?.let {
        SpotifyDevice(it, json.optString("name").take(80), json.optString("type"), json.optBoolean("is_active"), json.optBoolean("is_restricted"))
    }
    /** Podcast episodes and local files are shown by Spotify itself; only catalogue tracks are named here. */
    private fun track(json: JSONObject): SpotifyTrack? {
        val uri = json.optString("uri"); if (!TRACK_URI.matches(uri) || json.optBoolean("is_local")) return null
        val artists = json.optJSONArray("artists")
        val artist = (0 until (artists?.length() ?: 0)).mapNotNull { artists?.optJSONObject(it)?.optString("name")?.ifBlank { null } }.take(3).joinToString(", ")
        val covers = json.optJSONObject("album")?.optJSONArray("images")
        val cover = (0 until (covers?.length() ?: 0)).mapNotNull { covers?.optJSONObject(it) }
            .filter { it.optString("url").startsWith(COVER_ORIGIN) && it.optString("url").length <= 300 }
            .minByOrNull { it.optInt("height", Int.MAX_VALUE) }?.optString("url")
        return SpotifyTrack(uri, json.optString("name").take(160), artist.take(160), json.optLong("duration_ms").coerceAtLeast(0), cover)
    }
    private fun <T> parse(block: () -> T): T = try { block() } catch (error: SpotifyFailure) { throw error }
        catch (_: Exception) { throw SpotifyFailure(SpotifyFailure.Reason.RESPONSE) }

    private fun call(method: String, path: String, body: String? = null): SpotifyResponse {
        var response = transport.request(method, API + path, tokens.access(false), body, false)
        // One refresh: an access token can expire early or be revoked between two requests.
        if (response.status == 401) response = transport.request(method, API + path, tokens.access(true), body, false)
        if (response.status in 200..299) return response
        val reason = runCatching { JSONObject(response.body).optJSONObject("error")?.optString("reason") }.getOrNull().orEmpty()
        val message = runCatching { JSONObject(response.body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
        throw SpotifyFailure(when {
            response.status == 401 -> SpotifyFailure.Reason.AUTH
            response.status == 429 -> SpotifyFailure.Reason.RATE_LIMIT
            reason == "PREMIUM_REQUIRED" -> SpotifyFailure.Reason.PREMIUM
            reason == "NO_ACTIVE_DEVICE" || (response.status == 404 && path.startsWith("/me/player")) -> SpotifyFailure.Reason.NO_DEVICE
            // Spotify answers an account outside the app's user list with a plain-text 403. Any other
            // 403 is one refused command (pausing what is already paused), not a broken connection.
            response.status == 403 && (response.body.contains("not registered", true) || reason.isEmpty() && message.isEmpty()) -> SpotifyFailure.Reason.NOT_ALLOWED
            response.status >= 500 -> SpotifyFailure.Reason.NETWORK
            else -> SpotifyFailure.Reason.RESPONSE
        }, response.retryAfterSeconds)
    }

    companion object {
        const val API = "https://api.spotify.com/v1"
        const val COVER_ORIGIN = "https://i.scdn.co/image/"
        val TRACK_URI = Regex("spotify:track:[A-Za-z0-9]{10,40}")
        val PLAYLIST_URI = Regex("spotify:playlist:[A-Za-z0-9]{10,40}")
        val PLAYLIST_ID = Regex("[A-Za-z0-9]{10,40}")
        private val DEVICE_ID = Regex("[A-Za-z0-9_-]{8,120}")
    }
}

/** Two fixed HTTPS origins, no redirects, bounded bodies and timeouts; never logs tokens or bodies. */
class HttpSpotifyTransport : SpotifyTransport {
    override fun request(method: String, url: String, bearer: String?, body: String?, form: Boolean): SpotifyResponse {
        require(url == SpotifyAuthApi.TOKEN_URL || url.startsWith(SpotifyApi.API + "/"))
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method; connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000; connection.readTimeout = 12_000
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (method != "GET") {
                // Spotify rejects a PUT or POST without a length, even when it carries nothing.
                val bytes = body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0); require(bytes.size <= 60_000)
                connection.setRequestProperty("Content-Type", if (form) "application/x-www-form-urlencoded" else "application/json")
                connection.doOutput = true; connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val source = if (status in 200..299) connection.inputStream else connection.errorStream
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            source?.use { stream -> while (true) { val size = stream.read(buffer); if (size < 0) break; if (out.size() + size > 2_000_000) throw SpotifyFailure(SpotifyFailure.Reason.RESPONSE); out.write(buffer, 0, size) } }
            return SpotifyResponse(status, out.toString("UTF-8"), connection.getHeaderField("Retry-After")?.toIntOrNull()?.coerceIn(1, 3600))
        } catch (error: SpotifyFailure) { throw error }
        catch (_: Exception) { throw SpotifyFailure(SpotifyFailure.Reason.NETWORK) }
        finally { connection.disconnect() }
    }
}
