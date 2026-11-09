package com.runline.app.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

object RouteImportIntents {
    /** No network URLs or filesystem paths supplied to the exported launcher are ever opened. */
    fun uri(intent: Intent): Uri? = runCatching {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
            else -> null
        }
        uri?.takeIf { it.scheme == "content" && !it.authority.isNullOrBlank() }
    }.getOrNull()
}
