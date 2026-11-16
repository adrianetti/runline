package com.runline.app.music

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Authorization Code with PKCE. There is no client secret and nothing tied to a signing key, so
 * every build and fork connects with the Client ID of its own user's Spotify app.
 */
object SpotifyPkce {
    const val REDIRECT_URI = "runline://spotify-callback"
    const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
    const val DASHBOARD_URL = "https://developer.spotify.com/dashboard"
    /** Playback control and the user's own playlists; no profile, email or listening history. */
    val SCOPES = listOf("user-read-playback-state", "user-modify-playback-state", "playlist-read-private", "playlist-read-collaborative")

    sealed interface Callback {
        data class Code(val code: String) : Callback
        /** The user declined, or Spotify refused the request. */
        data class Denied(val error: String) : Callback
        /** Not the answer to the request this app started. */
        data object Mismatch : Callback
    }

    /** One sign-in attempt: what the Spotify app or the authorize page needs to answer it. Nothing here is secret. */
    data class SignIn(val clientId: String, val challenge: String, val state: String) {
        val url: String get() = authorizeUrl(clientId, challenge, state)
    }
    /** What the Spotify app handed back as an activity result. All null: the user left without agreeing. */
    data class AppReply(val code: String? = null, val state: String? = null, val error: String? = null)

    fun validClientId(value: String) = value.matches(Regex("[0-9a-fA-F]{32}"))
    fun verifier(random: SecureRandom = SecureRandom()): String = encode(ByteArray(48).also(random::nextBytes))
    fun state(random: SecureRandom = SecureRandom()): String = encode(ByteArray(16).also(random::nextBytes))
    fun challenge(verifier: String): String = encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun authorizeUrl(clientId: String, challenge: String, state: String): String {
        require(validClientId(clientId))
        return AUTHORIZE_URL + "?" + listOf(
            "client_id" to clientId, "response_type" to "code", "redirect_uri" to REDIRECT_URI,
            "code_challenge_method" to "S256", "code_challenge" to challenge, "state" to state,
            "scope" to SCOPES.joinToString(" "),
        ).joinToString("&") { (key, value) -> key + "=" + URLEncoder.encode(value, "UTF-8") }
    }

    fun isCallback(uri: String?): Boolean = uri != null && (uri == REDIRECT_URI || uri.startsWith("$REDIRECT_URI?"))

    fun parseCallback(uri: String, expectedState: String): Callback? {
        if (!isCallback(uri) || uri.length > 4000) return null
        val query = runCatching { URI(uri).rawQuery }.getOrNull().orEmpty().split('&').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else runCatching { part.substring(0, index) to URLDecoder.decode(part.substring(index + 1), "UTF-8") }.getOrNull()
        }.toMap()
        if (expectedState.isEmpty() || query["state"] != expectedState) return Callback.Mismatch
        query["error"]?.let { return Callback.Denied(it.take(80)) }
        val code = query["code"]?.takeIf { it.isNotEmpty() && it.length <= 2000 } ?: return Callback.Mismatch
        return Callback.Code(code)
    }

    /**
     * An activity result can only be set by the app that was started, and that app was checked to
     * be Spotify's. So unlike the open runline:// link, a reply without a state is accepted, as
     * Spotify's own library accepts it; a different state is still refused.
     */
    fun parseAppReply(reply: AppReply, expectedState: String): Callback {
        if (expectedState.isEmpty() || (reply.state != null && reply.state != expectedState)) return Callback.Mismatch
        reply.error?.let { return Callback.Denied(it.take(80)) }
        val code = reply.code?.takeIf { it.isNotEmpty() && it.length <= 2000 } ?: return Callback.Denied("cancelled")
        return Callback.Code(code)
    }

    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
