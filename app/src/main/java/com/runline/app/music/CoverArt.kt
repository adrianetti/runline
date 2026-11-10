package com.runline.app.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** The small album cover of the track that is playing: one image from Spotify's image host, kept in memory only. */
object CoverArt {
    private const val LIMIT_BYTES = 400_000
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var cached: Pair<String, Bitmap>? = null

    /** [done] runs on the main thread, with null when the cover could not be shown. */
    fun load(url: String, done: (Bitmap?) -> Unit) {
        cached?.takeIf { it.first == url }?.let { done(it.second); return }
        if (!url.startsWith(SpotifyApi.COVER_ORIGIN)) { done(null); return }
        executor.execute {
            val bitmap = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.instanceFollowRedirects = false; connection.connectTimeout = 6_000; connection.readTimeout = 8_000
                    if (connection.responseCode != 200) return@runCatching null
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    connection.inputStream.use { stream -> while (true) { val size = stream.read(buffer); if (size < 0) break; if (out.size() + size > LIMIT_BYTES) return@runCatching null; out.write(buffer, 0, size) } }
                    BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
                } finally { connection.disconnect() }
            }.getOrNull()
            if (bitmap != null) cached = url to bitmap
            main.post { done(bitmap) }
        }
    }
}
