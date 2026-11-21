package com.runline.app.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import java.security.MessageDigest

object SpotifyAppSignIn {
    const val PACKAGE = "com.spotify.music"
    private const val ACTION = "com.spotify.sso.action.START_AUTH_FLOW"
    private const val PROTOCOL_VERSION = 1
    private const val MIN_VERSION = 132384743L
    private const val RESULT_ERROR = -2
    private val SPOTIFY_CERTIFICATES = setOf(
        "25a9b2d2745c098361edaa3b87936dc29a28e7f1", "80abdd17dcc4cb3a33815d354355bf87c9378624",
        "88df4d670ed5e01fc7b3eff13b63258628ff5a00", "d834ae340d1e854c5f4092722f9788216d9221e5",
        "1cbedd9e7345f64649bad2b493a20d9eea955352", "4b3d76a2de89033ea830f476a1f815692938e33b",
    )

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

    fun reply(resultCode: Int, data: Intent?): SpotifyPkce.AppReply = when (resultCode) {
        Activity.RESULT_OK -> {
            val reply = runCatching { data?.getBundleExtra("REPLY") }.getOrNull()
            SpotifyPkce.AppReply(reply?.getString("AUTHORIZATION_CODE"), reply?.getString("STATE"),
                if (reply?.getString("RESPONSE_TYPE") == "code") null else "no_code")
        }
        RESULT_ERROR -> SpotifyPkce.AppReply(error = runCatching { data?.getStringExtra("ERROR") }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown")
        else -> SpotifyPkce.AppReply()
    }

    fun browser(context: Context, url: String): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val default = runCatching {
            context.packageManager.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE), 0)?.activityInfo?.packageName
        }.getOrNull()
        if (default != null && default != "android" && default != PACKAGE) intent.setPackage(default)
        return intent
    }

    fun ownFingerprint(context: Context): String? = runCatching {
        PackageInfoCompat.getSignatures(context.packageManager, context.packageName).firstOrNull()?.let { fingerprint(it.toByteArray()) }
    }.getOrNull()

    fun fingerprint(certificate: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(certificate).joinToString(":") { "%02X".format(it) }

    fun genuine(signers: List<String>): Boolean = signers.isNotEmpty() && signers.all { signer ->
        MessageDigest.getInstance("SHA-1").digest(signer.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) } in SPOTIFY_CERTIFICATES
    }
}
