package com.runline.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** No real locations or user files: a short synthetic line, traversed in both directions. */
class GpxFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "application/gpx+xml"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(uri.lastPathSegment)) }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r")
        val text = if (uri.lastPathSegment == "invalid.gpx") "<not-gpx/>" else GPX
        val file = File(requireContext().cacheDir, "fixture-${uri.lastPathSegment!!.replace(Regex("[^a-zA-Z0-9.]"), "_")}")
        file.writeText(text)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    companion object {
        const val GPX = "<gpx><trk><trkseg><trkpt lat='0' lon='0'/><trkpt lat='0.01' lon='0'/><trkpt lat='0' lon='0'/></trkseg></trk></gpx>"
    }
}
