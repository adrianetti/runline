package com.runline.app.music

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpotifyApiTest {
    private data class Call(val method: String, val url: String, val bearer: String?, val body: String?, val form: Boolean)
    private class Fake(private val answers: MutableList<SpotifyResponse>) : SpotifyTransport {
        val calls = mutableListOf<Call>()
        override fun request(method: String, url: String, bearer: String?, body: String?, form: Boolean): SpotifyResponse {
            calls += Call(method, url, bearer, body, form); return answers.removeAt(0)
        }
    }
    private fun api(vararg answers: SpotifyResponse): Pair<SpotifyApi, Fake> {
        val fake = Fake(answers.toMutableList())
        return SpotifyApi({ fresh -> if (fresh) "fresh-token-0123456789" else "saved-token-0123456789" }, fake) to fake
    }
    private val track = """{"uri":"spotify:track:4uLU6hMCjMI75M1A2tKUQC","name":"Song","duration_ms":200000,"artists":[{"name":"One"},{"name":"Two"}],
        "album":{"images":[{"url":"https://i.scdn.co/image/large","height":640},{"url":"https://i.scdn.co/image/small","height":64},{"url":"https://evil.example/x.png","height":1}]}}"""

    @Test fun exchangeSendsVerifierAndNoSecret() {
        val fake = Fake(mutableListOf(SpotifyResponse(200, """{"access_token":"access-token-0123456789","refresh_token":"refresh-token-0123456789","expires_in":3600}""")))
        val tokens = SpotifyAuthApi(fake) { 1_000 }.exchange("0123456789abcdef0123456789abcdef", "the-code", "the-verifier")
        assertEquals(SpotifyTokens("access-token-0123456789", "refresh-token-0123456789", 3_601_000), tokens)
        val call = fake.calls.single()
        assertEquals("POST", call.method); assertEquals("https://accounts.spotify.com/api/token", call.url); assertTrue(call.form); assertNull(call.bearer)
        assertTrue(call.body!!.contains("grant_type=authorization_code")); assertTrue(call.body.contains("code_verifier=the-verifier"))
        assertTrue(call.body.contains("redirect_uri=runline%3A%2F%2Fspotify-callback")); assertFalse(call.body.contains("secret"))
    }
    @Test fun refreshKeepsTheOldRefreshTokenWhenSpotifySendsNone() {
        val fake = Fake(mutableListOf(SpotifyResponse(200, """{"access_token":"access-token-9876543210","expires_in":3600}""")))
        val tokens = SpotifyAuthApi(fake) { 0 }.refresh("0123456789abcdef0123456789abcdef", "refresh-token-0123456789")
        assertEquals("refresh-token-0123456789", tokens.refresh); assertEquals("access-token-9876543210", tokens.access)
        assertTrue(fake.calls.single().body!!.contains("grant_type=refresh_token"))
    }
    @Test fun refusedTokenRequestIsAnAuthFailureAndGarbageIsNotASession() {
        val refused = Fake(mutableListOf(SpotifyResponse(400, """{"error":"invalid_grant"}""")))
        assertEquals(SpotifyFailure.Reason.AUTH, assertThrows(SpotifyFailure::class.java) { SpotifyAuthApi(refused).refresh("0123456789abcdef0123456789abcdef", "refresh-token-0123456789") }.reason)
        val garbage = Fake(mutableListOf(SpotifyResponse(200, """{"access_token":"x"}""")))
        assertEquals(SpotifyFailure.Reason.RESPONSE, assertThrows(SpotifyFailure::class.java) { SpotifyAuthApi(garbage).exchange("0123456789abcdef0123456789abcdef", "c", "v") }.reason)
    }
    @Test fun expiredAccessTokenIsRefreshedOnceThenReported() {
        val (api, fake) = api(SpotifyResponse(401, ""), SpotifyResponse(204, ""))
        assertNull(api.playback())
        assertEquals(listOf("saved-token-0123456789", "fresh-token-0123456789"), fake.calls.map { it.bearer })
        val (again, _) = api(SpotifyResponse(401, ""), SpotifyResponse(401, ""))
        assertEquals(SpotifyFailure.Reason.AUTH, assertThrows(SpotifyFailure::class.java) { again.playback() }.reason)
    }
    @Test fun playbackReadsDeviceTrackContextAndRemainingTime() {
        val (api, fake) = api(SpotifyResponse(200, """{"device":{"id":"device-id-1234","name":"Phone","type":"Smartphone","is_active":true,"is_restricted":false},
            "is_playing":true,"progress_ms":150000,"context":{"uri":"spotify:playlist:37i9dQZF1DX76Wlfdnj7AP"},"item":$track}"""))
        val playback = api.playback()!!
        assertEquals("https://api.spotify.com/v1/me/player", fake.calls.single().url)
        assertTrue(playback.playing); assertEquals("device-id-1234", playback.device!!.id); assertTrue(playback.device!!.active)
        // The smallest cover from Spotify's own image host; any other origin is never fetched.
        assertEquals(SpotifyTrack("spotify:track:4uLU6hMCjMI75M1A2tKUQC", "Song", "One, Two", 200_000, "https://i.scdn.co/image/small"), playback.track)
        assertEquals("spotify:playlist:37i9dQZF1DX76Wlfdnj7AP", playback.contextUri); assertEquals(50_000L, playback.remainingMs)
    }
    @Test fun playSendsContextToTheChosenDeviceAndResumeSendsNothing() {
        val (api, fake) = api(SpotifyResponse(204, ""), SpotifyResponse(204, ""), SpotifyResponse(204, ""), SpotifyResponse(204, ""))
        api.play("device-id-1234", contextUri = "spotify:playlist:37i9dQZF1DX76Wlfdnj7AP")
        api.play()
        api.next()
        api.shuffle(true, "device-id-1234")
        assertEquals("PUT", fake.calls[0].method); assertEquals("https://api.spotify.com/v1/me/player/play?device_id=device-id-1234", fake.calls[0].url)
        assertEquals("spotify:playlist:37i9dQZF1DX76Wlfdnj7AP", JSONObject(fake.calls[0].body!!).getString("context_uri"))
        assertEquals("https://api.spotify.com/v1/me/player/play", fake.calls[1].url); assertNull(fake.calls[1].body)
        assertEquals("POST", fake.calls[2].method); assertEquals("https://api.spotify.com/v1/me/player/next", fake.calls[2].url)
        assertEquals("https://api.spotify.com/v1/me/player/shuffle?state=true&device_id=device-id-1234", fake.calls[3].url)
    }
    @Test fun onlySpotifyUrisAndIdsReachARequest() {
        val (api, fake) = api()
        assertThrows(IllegalArgumentException::class.java) { api.play(contextUri = "https://example.com/playlist") }
        assertThrows(IllegalArgumentException::class.java) { api.play(contextUri = "spotify:playlist:abc&device_id=other") }
        assertThrows(IllegalArgumentException::class.java) { api.play("bad device") }
        assertTrue(fake.calls.isEmpty())
    }
    @Test fun playlistsPageAndAcceptBothCountShapes() {
        val first = (1..50).joinToString(",") { """{"id":"playlist${1000 + it}abc","name":"List $it","items":{"total":$it},"owner":{"id":"me"}}""" }
        val (api, fake) = api(SpotifyResponse(200, """{"items":[$first],"next":"https://api.spotify.com/v1/me/playlists?offset=50"}"""),
            SpotifyResponse(200, """{"items":[{"id":"playlistOLD12345","name":"","tracks":{"total":7},"collaborative":true},{"id":"bad id","name":"x"}],"next":null}"""))
        val playlists = api.playlists()
        assertEquals(51, playlists.size); assertEquals(1, playlists.first().tracks); assertEquals("me", playlists.first().ownerId)
        assertEquals(SpotifyPlaylist("playlistOLD12345", "playlistOLD12345", 7, "", true), playlists.last())
        assertEquals("https://api.spotify.com/v1/me/playlists?limit=50&offset=50", fake.calls[1].url)
    }
    @Test fun episodesAndLocalFilesAreNotNamedAsTracks() {
        val (api, _) = api(SpotifyResponse(200, """{"is_playing":true,"item":{"uri":"spotify:episode:512ojhOuo1ktJprKbVcKyQ","name":"Podcast"}}"""),
            SpotifyResponse(200, """{"is_playing":true,"item":{"uri":"spotify:track:4uLU6hMCjMI75M1A2tKUQC","name":"File","is_local":true}}"""))
        assertNull(api.playback()!!.track); assertNull(api.playback()!!.track)
    }
    @Test fun failuresAreNamedSoTheAppCanExplainThem() {
        fun reason(response: SpotifyResponse, block: (SpotifyApi) -> Unit = { it.play() }): SpotifyFailure {
            val (api, _) = api(response); return assertThrows(SpotifyFailure::class.java) { block(api) }
        }
        assertEquals(SpotifyFailure.Reason.PREMIUM, reason(SpotifyResponse(403, """{"error":{"status":403,"message":"Player command failed: Premium required","reason":"PREMIUM_REQUIRED"}}""")).reason)
        assertEquals(SpotifyFailure.Reason.NO_DEVICE, reason(SpotifyResponse(404, """{"error":{"status":404,"message":"Player command failed: No active device found","reason":"NO_ACTIVE_DEVICE"}}""")).reason)
        assertEquals(SpotifyFailure.Reason.NOT_ALLOWED, reason(SpotifyResponse(403, "User not registered in the Developer Dashboard")).reason)
        // Pausing what is already paused is one refused command, not a lost account.
        assertEquals(SpotifyFailure.Reason.RESPONSE, reason(SpotifyResponse(403, """{"error":{"status":403,"message":"Player command failed: Restriction violated","reason":"UNKNOWN"}}""")).reason)
        val limited = reason(SpotifyResponse(429, "", 12))
        assertEquals(SpotifyFailure.Reason.RATE_LIMIT, limited.reason); assertEquals(12, limited.retryAfterSeconds)
        assertEquals(SpotifyFailure.Reason.NETWORK, reason(SpotifyResponse(503, "")).reason)
        assertEquals(SpotifyFailure.Reason.RESPONSE, reason(SpotifyResponse(200, "not json")) { it.devices() }.reason)
    }
    @Test fun transportRefusesAnyOtherOrigin() {
        assertThrows(IllegalArgumentException::class.java) { HttpSpotifyTransport().request("GET", "https://api.spotify.com.evil.example/v1/me", "t", null, false) }
        assertThrows(IllegalArgumentException::class.java) { HttpSpotifyTransport().request("POST", "http://accounts.spotify.com/api/token", null, "", true) }
    }
}
