package com.runline.app.data

import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile

/** Sync both contents and the rename before a recording or paid request can depend on it. */
internal fun writePrivateAtomic(file: AtomicFile, bytes: ByteArray) {
    val output = file.startWrite()
    try { output.write(bytes); file.finishWrite(output) }
    catch (error: Exception) { file.failWrite(output); throw error }
    val descriptor = Os.open(requireNotNull(file.baseFile.parent), OsConstants.O_RDONLY, 0)
    try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
}
