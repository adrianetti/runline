package com.runline.app.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import java.security.MessageDigest

/**
 * Sign-in handed to the installed Spotify app, so the user agrees there instead of signing in
 * again on a web page. This speaks the intent protocol of Spotify's own Apache-2.0 auth library
 * (github.com/spotify/android-auth) directly; no SDK is bundled. Spotify answers only apps whose
 * package name and SHA-1 signing fingerprint are listed in the developer dashboard, so the setup
 * guide shows both. Without Spotify on the phone the browser does the same job.
 */
object SpotifyAppSignIn {
    const val PACKAGE = "com.spotify.music"
    private const val ACTION = "com.spotify.sso.action.START_AUTH_FLOW"
    private const val PROTOCOL_VERSION = 1
    /** Spotify 9.0.78.360, the first release that honours a PKCE challenge in this flow. */
    private const val MIN_VERSION = 132384743L
    private const val RESULT_ERROR = -2
    /** Spotify's signing certificates, hashed the way its auth library hashes them. */
    private val SPOTIFY_CERTIFICATES = setOf(
        "25a9b2d2745c098361edaa3b87936dc29a28e7f1", "80abdd17dcc4cb3a33815d354355bf87c9378624",
        "88df4d670ed5e01fc7b3eff13b63258628ff5a00", "d834ae340d1e854c5f4092722f9788216d9221e5",
        "1cbedd9e7345f64649bad2b493a20d9eea955352", "4b3d76a2de89033ea830f476a1f815692938e33b",
    )

    /**
     * The sign-in to start for a result, or null when the browser must do it: Spotify is not
     * installed, is too old for PKCE, or is not signed by Spotify. An impostor under Spotify's
     * package name could otherwise show its own password prompt.
     */
    fun intent(context: Context, signIn: SpotifyPkce.SignIn): Intent? {
        val packages = context.packageManager
        val intent = Intent(ACTION).setPackage(PACKAGE)
        if (intent.resolveActivity(packages) == null) return null
        val recent = runCatching { PackageInfoCompat.getLongVersionCode(packages.getPackageInfo(PACKAGE, 0)) >= MIN_VERSION }.getOrDefault(false)
        val signers = runCatching { PackageInfoCompat.getSignatures(packages, PACKAGE).map { it.toCharsString() } }.getOrDefault(emptyList())
        if (!recent || !genuine(signers)) return null
        return intent.putExtra("VERSION", PROTOCOL_VERSION).putExtra("CLIENT_ID", signIn.clientId)
            .putExtra("REDIRECT_URI", SpotifyPkce.REDIRECT_URI).putExtra("RESPONSE_TYPE", "code")
            .putExtra("SCOPES", SpotifyPkce.SCOPES.toTypedArray()).putExtra("STATE", signIn.state)
            .putExtra("CODE_CHALLENGE", signIn.challenge).putExtra("CODE_CHALLENGE_METHOD", "S256")
    }

    /** What Spotify set as the activity result. Anything but a code leaves the sign-in unfinished. */
    fun reply(resultCode: Int, data: Intent?): SpotifyPkce.AppReply = when (resultCode) {
        Activity.RESULT_OK -> {
            val reply = runCatching { data?.getBundleExtra("REPLY") }.getOrNull()
            SpotifyPkce.AppReply(reply?.getString("AUTHORIZATION_CODE"), reply?.getString("STATE"),
                if (reply?.getString("RESPONSE_TYPE") == "code") null else "no_code")
        }
        RESULT_ERROR -> SpotifyPkce.AppReply(error = runCatching { data?.getStringExtra("ERROR") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown")
        else -> SpotifyPkce.AppReply()
    }

    /**
     * The authorize page in the user's browser, in the browser's own task. Left to Android, the
     * link can land in an app that claims Spotify's web addresses, and a page opened inside
     * Runline's task stacks a second Runline on top of itself when it returns.
     */
    fun browser(context: Context, url: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val default = runCatching {
            context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE), 0)?.activityInfo?.packageName
        }.getOrNull()
        // "android" is the chooser, shown when no browser is the default.
        if (default != null && default != "android" && default != PACKAGE) intent.setPackage(default)
        return intent
    }

    /** This build's signing fingerprint as the Spotify dashboard wants it: SHA-1, upper case, colons. */
    fun ownFingerprint(context: Context): String? = runCatching {
        PackageInfoCompat.getSignatures(context.packageManager, context.packageName).firstOrNull()?.let { fingerprint(it.toByteArray()) }
    }.getOrNull()

    fun fingerprint(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(certificate).joinToString(":") { "%02X".format(it) }

    /** Every signer must be one of Spotify's. [signers] are certificates as lower-case hex text. */
    fun genuine(signers: List<String>): Boolean = signers.isNotEmpty() && signers.all { signer ->
        MessageDigest.getInstance("SHA-1").digest(signer.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } in SPOTIFY_CERTIFICATES
    }
}
