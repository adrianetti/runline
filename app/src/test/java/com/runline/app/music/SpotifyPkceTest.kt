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
    @Test fun signInOpensTheSameAuthorizePageTheBrowserFlowUsed() {
        val signIn = SpotifyPkce.SignIn("0123456789abcdef0123456789abcdef", "challenge", "state-1")
        assertEquals(SpotifyPkce.authorizeUrl("0123456789abcdef0123456789abcdef", "challenge", "state-1"), signIn.url)
    }
    @Test fun spotifyAppReplyGivesACodeWithTheSameStateOrWithNone() {
        assertEquals(SpotifyPkce.Callback.Code("AQD-code_1"), SpotifyPkce.parseAppReply(SpotifyPkce.AppReply("AQD-code_1", "abc"), "abc"))
        assertEquals(SpotifyPkce.Callback.Code("AQD-code_1"), SpotifyPkce.parseAppReply(SpotifyPkce.AppReply("AQD-code_1"), "abc"))
        assertEquals(SpotifyPkce.Callback.Mismatch, SpotifyPkce.parseAppReply(SpotifyPkce.AppReply("AQD", "other"), "abc"))
        assertEquals(SpotifyPkce.Callback.Mismatch, SpotifyPkce.parseAppReply(SpotifyPkce.AppReply("AQD", "abc"), ""))
    }
    @Test fun spotifyAppReplyWithoutACodeNeverConnects() {
        assertEquals(SpotifyPkce.Callback.Denied("cancelled"), SpotifyPkce.parseAppReply(SpotifyPkce.AppReply(), "abc"))
        assertEquals(SpotifyPkce.Callback.Denied("cancelled"), SpotifyPkce.parseAppReply(SpotifyPkce.AppReply(code = ""), "abc"))
        assertEquals(SpotifyPkce.Callback.Denied("INVALID_APP_ID"), SpotifyPkce.parseAppReply(SpotifyPkce.AppReply(error = "INVALID_APP_ID"), "abc"))
        // An error wins over a code, and a long one is cut before it can reach the screen.
        assertEquals(SpotifyPkce.Callback.Denied("no_code"), SpotifyPkce.parseAppReply(SpotifyPkce.AppReply("AQD", "abc", "no_code"), "abc"))
        assertEquals(80, (SpotifyPkce.parseAppReply(SpotifyPkce.AppReply(error = "x".repeat(500)), "abc") as SpotifyPkce.Callback.Denied).error.length)
        assertEquals(SpotifyPkce.Callback.Denied("cancelled"), SpotifyPkce.parseAppReply(SpotifyPkce.AppReply("c".repeat(2001)), "abc"))
    }
    @Test fun onlyTheExactRedirectIsACallback() {
        assertTrue(SpotifyPkce.isCallback("runline://spotify-callback")); assertTrue(SpotifyPkce.isCallback("runline://spotify-callback?code=1&state=2"))
        assertFalse(SpotifyPkce.isCallback("runline://spotify-callback-other?code=1")); assertFalse(SpotifyPkce.isCallback("https://spotify-callback?code=1"))
        assertFalse(SpotifyPkce.isCallback(null)); assertFalse(SpotifyPkce.isCallback("content://files/route.gpx"))
        assertNull(SpotifyPkce.parseCallback("runline://elsewhere?code=1&state=abc", "abc"))
    }
}
