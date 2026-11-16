package com.runline.app.music

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's own Spotify connection. Tokens are AES-256-GCM ciphertext under a non-exportable
 * Android Keystore key, in noBackupFilesDir; they never enter exports, logs or instance state.
 */
object SpotifyAccount {
    enum class Outcome { CONNECTED, DENIED, STALE }

    private const val ALIAS = "runline-spotify-v1"
    private const val PENDING_VALID_MS = 15 * 60_000L
    private val lock = Any()
    @Volatile private var cached: SpotifyTokens? = null

    private fun prefs(context: Context) = context.getSharedPreferences("spotify_account", Context.MODE_PRIVATE)
    private fun sessionFile(context: Context) = File(context.noBackupFilesDir, "spotify-session.enc")
    private fun pendingFile(context: Context) = File(context.noBackupFilesDir, "spotify-pending.enc")

    /** A public identifier of the user's Spotify app, not a secret. */
    fun clientId(context: Context): String = prefs(context).getString("clientId", null).orEmpty()
    fun saveClientId(context: Context, value: String) {
        require(SpotifyPkce.validClientId(value))
        if (value != clientId(context)) { disconnect(context); check(prefs(context).edit().putString("clientId", value).commit()) }
    }
    fun isConnected(context: Context): Boolean = sessionFile(context).exists()
    fun accountName(context: Context): String? = prefs(context).getString("name", null)

    /** Starts a sign-in for the Spotify app or the browser to answer. The verifier waits, encrypted, for that answer. */
    fun begin(context: Context): SpotifyPkce.SignIn {
        val clientId = clientId(context); require(SpotifyPkce.validClientId(clientId))
        val verifier = SpotifyPkce.verifier(); val state = SpotifyPkce.state()
        write(pendingFile(context), JSONObject().put("verifier", verifier).put("state", state).put("clientId", clientId).put("at", System.currentTimeMillis()))
        return SpotifyPkce.SignIn(clientId, SpotifyPkce.challenge(verifier), state)
    }

    /** The browser came back through the redirect link. Network: call off the main thread. Throws [SpotifyFailure] when the exchange itself fails. */
    fun complete(context: Context, callback: String, auth: SpotifyAuthApi = SpotifyAuthApi()): Outcome =
        finish(context, auth) { state -> SpotifyPkce.parseCallback(callback, state) }

    /** The Spotify app answered. Network: call off the main thread. Throws [SpotifyFailure] when the exchange itself fails. */
    fun complete(context: Context, reply: SpotifyPkce.AppReply, auth: SpotifyAuthApi = SpotifyAuthApi()): Outcome =
        finish(context, auth) { state -> SpotifyPkce.parseAppReply(reply, state) }

    private fun finish(context: Context, auth: SpotifyAuthApi, parse: (state: String) -> SpotifyPkce.Callback?): Outcome {
        val pending = read(pendingFile(context))
        AtomicFile(pendingFile(context)).delete()
        if (pending == null || System.currentTimeMillis() - pending.optLong("at") !in 0..PENDING_VALID_MS) return Outcome.STALE
        return when (val answer = parse(pending.optString("state"))) {
            null, SpotifyPkce.Callback.Mismatch -> Outcome.STALE
            is SpotifyPkce.Callback.Denied -> Outcome.DENIED
            is SpotifyPkce.Callback.Code -> {
                val tokens = auth.exchange(pending.getString("clientId"), answer.code, pending.getString("verifier"))
                synchronized(lock) { save(context, tokens) }
                // Shown beside "Connected"; the connection itself does not depend on it.
                runCatching { api(context).displayName() }.getOrNull()?.let { prefs(context).edit().putString("name", it).apply() }
                Outcome.CONNECTED
            }
        }
    }

    fun disconnect(context: Context) = synchronized(lock) {
        cached = null
        AtomicFile(sessionFile(context)).delete(); AtomicFile(pendingFile(context)).delete()
        prefs(context).edit().remove("name").apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
        Unit
    }

    fun api(context: Context, auth: SpotifyAuthApi = SpotifyAuthApi()): SpotifyApi {
        val app = context.applicationContext
        return SpotifyApi({ fresh -> access(app, auth, fresh) })
    }

    private fun access(context: Context, auth: SpotifyAuthApi, fresh: Boolean): String {
        synchronized(lock) {
            val session = cached ?: load(context) ?: throw SpotifyFailure(SpotifyFailure.Reason.AUTH)
            if (!fresh && session.expiresAtMs - System.currentTimeMillis() > 60_000) return session.access
            try { return auth.refresh(clientId(context), session.refresh).also { save(context, it) }.access }
            catch (error: SpotifyFailure) {
                // A refused refresh token cannot recover; a network error can, so keep the session.
                if (error.reason == SpotifyFailure.Reason.AUTH) disconnect(context)
                throw error
            }
        }
    }

    private fun save(context: Context, tokens: SpotifyTokens) {
        write(sessionFile(context), JSONObject().put("access", tokens.access).put("refresh", tokens.refresh).put("expires", tokens.expiresAtMs))
        cached = tokens
    }
    private fun load(context: Context): SpotifyTokens? = read(sessionFile(context))?.let { json ->
        runCatching { SpotifyTokens(json.getString("access"), json.getString("refresh"), json.getLong("expires")) }.getOrNull()
    }?.also { cached = it }

    private fun write(file: File, json: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write((Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)).toByteArray())
            atomic.finishWrite(stream)
        } catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
    private fun read(file: File): JSONObject? = runCatching {
        val parts = AtomicFile(file).readFully().toString(Charsets.UTF_8).split(':')
        require(parts.size == 2)
        JSONObject(Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8))
    }.getOrNull()
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
}
