package com.runline.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AiConfig(
    val enabled: Boolean = false,
    val liveEnabled: Boolean = false,
    val automaticReviews: Boolean = false,
    val automaticPlanUpdates: Boolean = false,
    val liveModel: String = AiProviderCatalog.DEFAULT_LIVE_MODEL,
    val reviewModel: String = AiProviderCatalog.DEFAULT_REVIEW_MODEL,
    val intervalMinutes: Int = 5,
    val includeNotes: Boolean = false,
    val provider: String = AiProviderCatalog.ANTHROPIC,
) {
    fun valid() = AiProviderCatalog.find(provider) != null && intervalMinutes in 3..10 && validModel(liveModel) && validModel(reviewModel)
    /** Locally configured, not a guarantee of network availability or model access. */
    fun isReady(hasKey: Boolean) = enabled && hasKey && valid()
    companion object { fun validModel(value: String) = value.matches(Regex("claude-[a-zA-Z0-9._-]{3,100}")) }
}

/** The app backup policy excludes all data; key ciphertext additionally lives in noBackupFilesDir. */
object AiSettings {
    private val changes = java.util.concurrent.atomic.AtomicLong()
    fun revision(): Long = changes.get()
    private fun changed() { changes.incrementAndGet(); AiSpokenHistory.clearAll() }
    private const val ALIAS = "runline-anthropic-v1"
    private fun prefs(context: Context) = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    fun load(context: Context): AiConfig = prefs(context).let { p -> AiConfig(
        p.getBoolean("enabled", false), p.getBoolean("live", false), p.getBoolean("reviews", false), p.getBoolean("adapt", false),
        p.getString("liveModel", null) ?: AiProviderCatalog.DEFAULT_LIVE_MODEL,
        p.getString("reviewModel", null) ?: AiProviderCatalog.DEFAULT_REVIEW_MODEL,
        p.getInt("interval", 5), p.getBoolean("includeNotes", false),
        p.getString("provider", null) ?: AiProviderCatalog.ANTHROPIC,
    ).takeIf { it.valid() } ?: AiConfig() }
    fun isReady(context: Context): Boolean = load(context).isReady(hasKey(context))
    fun save(context: Context, config: AiConfig) {
        require(config.valid())
        changed()
        check(prefs(context).edit().putBoolean("enabled", config.enabled).putBoolean("live", config.liveEnabled)
            .putBoolean("reviews", config.automaticReviews).putBoolean("adapt", config.automaticPlanUpdates)
            .putString("liveModel", config.liveModel).putString("reviewModel", config.reviewModel).putInt("interval", config.intervalMinutes)
            .putBoolean("includeNotes", config.includeNotes).putString("provider", config.provider).commit())
    }
    fun hasKey(context: Context): Boolean = keyFile(context).exists()
    fun validKey(value: String) = value.length in 20..512 && value.all { it.code in 33..126 }
    fun saveKey(context: Context, value: String) {
        require(validKey(value))
        changed()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val atomic = android.util.AtomicFile(keyFile(context))
        val stream = atomic.startWrite()
        try {
            stream.write((Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).toByteArray())
            atomic.finishWrite(stream)
        } catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    internal fun readKey(context: Context): String? = runCatching {
        val parts = android.util.AtomicFile(keyFile(context)).readFully().toString(Charsets.UTF_8).split(':')
        require(parts.size == 2)
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        }.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrNull()
    fun clearKey(context: Context) {
        changed()
        android.util.AtomicFile(keyFile(context)).delete()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
        save(context, load(context).copy(enabled = false, liveEnabled = false, automaticReviews = false, automaticPlanUpdates = false))
    }
    private fun keyFile(context: Context) = java.io.File(context.noBackupFilesDir, "anthropic-key.enc")
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
}
