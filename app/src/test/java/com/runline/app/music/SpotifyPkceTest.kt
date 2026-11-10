package com.runline.app.music

import org.junit.Assert.*
import org.junit.Test

class SpotifyPkceTest {
    @Test fun challengeMatchesTheRfc7636Example() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", SpotifyPkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun verifierIsLongRandomAndUrlSafe() {
        val first = SpotifyPkce.verifier(); val second = SpotifyPkce.verifier()
        assertEquals(64, first.length); assertNotEquals(first, second)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]{43,128}")))
        assertTrue(SpotifyPkce.state().matches(Regex("[A-Za-z0-9_-]{16,}")))
    }
    @Test fun authorizeUrlCarriesNoSecretAndOnlyPlaybackScopes() {
        val url = SpotifyPkce.authorizeUrl("0123456789abcdef0123456789abcdef", "challenge", "state-1")
        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("client_id=0123456789abcdef0123456789abcdef")); assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("redirect_uri=runline%3A%2F%2Fspotify-callback")); assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=challenge")); assertTrue(url.contains("state=state-1"))
        assertFalse(url.contains("secret")); assertFalse(url.contains("email")); assertFalse(url.contains("user-read-private"))
        assertTrue(url.contains("user-modify-playback-state"))
    }
    @Test fun clientIdMustBeThirtyTwoHexCharacters() {
        assertTrue(SpotifyPkce.validClientId("0123456789ABCDEF0123456789abcdef"))
        assertFalse(SpotifyPkce.validClientId("0123456789abcdef0123456789abcde")); assertFalse(SpotifyPkce.validClientId("g123456789abcdef0123456789abcdef"))
        assertThrows(IllegalArgumentException::class.java) { SpotifyPkce.authorizeUrl("not-an-id", "c", "s") }
    }
    @Test fun callbackNeedsTheExpectedStateBeforeItsCodeCounts() {
        assertEquals(SpotifyPkce.Callback.Code("AQD-code_1"), SpotifyPkce.parseCallback("runline://spotify-callback?code=AQD-code_1&state=abc", "abc"))
        assertEquals(SpotifyPkce.Callback.Mismatch, SpotifyPkce.parseCallback("runline://spotify-callback?code=AQD&state=other", "abc"))
        assertEquals(SpotifyPkce.Callback.Mismatch, SpotifyPkce.parseCallback("runline://spotify-callback?code=AQD", "abc"))
        assertEquals(SpotifyPkce.Callback.Mismatch, SpotifyPkce.parseCallback("runline://spotify-callback?code=AQD&state=", ""))
        assertEquals(SpotifyPkce.Callback.Denied("access_denied"), SpotifyPkce.parseCallback("runline://spotify-callback?error=access_denied&state=abc", "abc"))
        assertEquals(SpotifyPkce.Callback.Mismatch, SpotifyPkce.parseCallback("runline://spotify-callback?state=abc", "abc"))
    }
    @Test fun onlyTheExactRedirectIsACallback() {
        assertTrue(SpotifyPkce.isCallback("runline://spotify-callback")); assertTrue(SpotifyPkce.isCallback("runline://spotify-callback?code=1&state=2"))
        assertFalse(SpotifyPkce.isCallback("runline://spotify-callback-other?code=1")); assertFalse(SpotifyPkce.isCallback("https://spotify-callback?code=1"))
        assertFalse(SpotifyPkce.isCallback(null)); assertFalse(SpotifyPkce.isCallback("content://files/route.gpx"))
        assertNull(SpotifyPkce.parseCallback("runline://elsewhere?code=1&state=abc", "abc"))
    }
}
