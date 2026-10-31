package com.runline.app.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import java.io.File

/** Android adapter: all files remain private, and file replacements also sync their directory. */
class RunStore(context: Context) {
    private val disk = DiskRunStore(File(context.filesDir, "runs"), syncDirectory = { folder ->
        val descriptor = Os.open(folder.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    })
    fun list(): List<RunSession> = disk.list()
    fun get(id: String): RunSession? = disk.get(id)
    fun save(run: RunSession) = disk.save(run)
    fun complete(run: RunSession) = disk.complete(run)
    fun delete(id: String) = disk.delete(id)
    fun loadDraft(): RunSession? = disk.loadDraft()
    fun loadDraftRecord(): DraftRecord? = disk.loadDraftRecord()
    fun beginDraft(run: RunSession) = disk.beginDraft(run)
    fun checkpoint(run: RunSession) = disk.checkpoint(run)
    fun checkpoint(record: DraftRecord) = disk.checkpoint(record)
    fun appendPoint(point: RunPoint) = disk.appendPoint(point)
    fun appendPoint(point: RunPoint, record: DraftRecord) = disk.appendPoint(point, record)
    fun clearDraft() = disk.clearDraft()
    fun discardDraft() = disk.discardDraft()
    fun archiveDraft() = disk.archiveDraft()
}
