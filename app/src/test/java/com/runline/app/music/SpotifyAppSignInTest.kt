package com.runline.app.music

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SpotifyAppSignInTest {
    @Test fun fingerprintIsSha1InTheDashboardForm() {
        // FIPS 180 test vectors.
        assertEquals("A9:99:3E:36:47:06:81:6A:BA:3E:25:71:78:50:C2:6C:9C:D0:D8:9D", SpotifyAppSignIn.fingerprint("abc".toByteArray()))
        assertEquals("DA:39:A3:EE:5E:6B:4B:0D:32:55:BF:EF:95:60:18:90:AF:D8:07:09", SpotifyAppSignIn.fingerprint(ByteArray(0)))
        assertTrue(SpotifyAppSignIn.fingerprint(ByteArray(900) { it.toByte() }).matches(Regex("([0-9A-F]{2}:){19}[0-9A-F]{2}")))
    }
    @Test fun anAppNotSignedBySpotifyIsNotHandedTheSignIn() {
        assertFalse(SpotifyAppSignIn.genuine(emptyList()))
        assertFalse(SpotifyAppSignIn.genuine(listOf("308201e53082014ea0030201020204")))
        assertFalse(SpotifyAppSignIn.genuine(listOf("")))
    }
    @Test fun spotifyCertificatesAreHashedAsHexTextLikeSpotifysLibrary() {
        // The library hashes the certificate's hex text, not its bytes, so "ab" must not hash as 0xAB.
        val asText = MessageDigest.getInstance("SHA-1").digest("ab".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals("da23614e02469a0d7c7bd1bdab5c9c474b1904dc", asText)
        assertFalse(SpotifyAppSignIn.genuine(listOf("ab")))
    }
}
