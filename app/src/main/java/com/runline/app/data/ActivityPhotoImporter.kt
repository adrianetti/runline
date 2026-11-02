package com.runline.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.max

/** Bounded, private JPEG copies; no provider URI, EXIF location or original filename is retained. */
object ActivityPhotoImporter {
    fun import(context: Context, runId: String, uri: Uri): String {
        val store = RunStore(context)
        val staging = File.createTempFile("activity-photo-", ".source", context.cacheDir)
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var resized: Bitmap? = null
        var outputFile: File? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(staging).use { output ->
                    val buffer = ByteArray(32 * 1024); var total = 0L
                    while (true) {
                        val read = input.read(buffer); if (read < 0) break
                        total += read
                        if (total > MAX_SOURCE_BYTES) throw IOException("Image is too large.")
                        output.write(buffer, 0, read)
                    }
                    if (total == 0L) throw IOException("Empty image.")
                }
            } ?: throw IOException("Image could not be opened.")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(staging.path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0 || options.outWidth.toLong() * options.outHeight > 120_000_000L)
                throw IOException("Image format or dimensions are unsupported.")
            var sample = 1
            while (max(options.outWidth, options.outHeight) / sample > 2400) sample *= 2
            decoded = BitmapFactory.decodeFile(staging.path, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: throw IOException("Image could not be decoded.")
            val orientation = runCatching { ExifInterface(staging.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val scale = (1600.0 / max(oriented.width, oriented.height)).coerceAtMost(1.0)
            resized = if (scale < 1) Bitmap.createScaledBitmap(oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1), (oriented.height * scale).toInt().coerceAtLeast(1), true) else oriented
            val folder = store.photoDirectory(runId)
            check(folder.isDirectory || folder.mkdirs()) { "Could not create photo storage." }
            val photoId = UUID.randomUUID().toString() + ".jpg"
            val target = store.photoFile(runId, photoId)
            outputFile = File(target.path + ".new")
            FileOutputStream(outputFile).use { output ->
                check(resized.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Could not save the photo." }
                output.fd.sync()
            }
            Files.move(outputFile.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            outputFile = null
            val descriptor = Os.open(folder.path, OsConstants.O_RDONLY, 0)
            try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
            return photoId
        } finally {
            staging.delete(); outputFile?.delete()
            listOfNotNull(decoded, oriented, resized).distinct().forEach { if (!it.isRecycled) it.recycle() }
        }
    }
    private const val MAX_SOURCE_BYTES = 20L * 1024 * 1024
}
