package com.runline.app.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.runline.app.core.ActivityDetails
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import java.io.File

/** Android adapter: all files remain private, and file replacements also sync their directory. */
class RunStore(context: Context) {
    private val contextApp = context.applicationContext
    private val disk = DiskRunStore(File(context.filesDir, "runs"), syncDirectory = { folder ->
        val descriptor = Os.open(folder.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    })
    private val annotations by lazy { ActivityDetailsStore(File(context.filesDir, "activity-details"), syncDirectory = { folder ->
        val descriptor = Os.open(folder.path, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }) }
    fun details(id: String): ActivityDetails = annotations.get(id)
    fun saveDetails(id: String, details: ActivityDetails) {
        require(disk.get(id) != null) { "Save the activity before editing its details." }
        annotations.save(id, details)
    }
    fun photoFile(id: String, photoId: String) = annotations.photoFile(id, photoId)
    internal fun photoDirectory(id: String) = annotations.photoDirectory(id)
    private fun withDetails(run: RunSession): RunSession {
        val type = runCatching { annotations.get(run.id).activityType }.getOrNull()
        return if (type != null) run.copy(activityType = type) else run
    }
    fun list(): List<RunSession> = disk.list().map(::withDetails)
    fun get(id: String): RunSession? = disk.get(id)?.let(::withDetails)
    fun save(run: RunSession) = disk.save(run)
    fun complete(run: RunSession) = disk.complete(run)
    fun delete(id: String) { disk.delete(id); annotations.delete(id); SessionIntentStore(contextApp).delete(id) }
    fun loadDraft(): RunSession? = disk.loadDraft()
    fun loadDraftRecord(): DraftRecord? = disk.loadDraftRecord()
    fun beginDraft(run: RunSession) = disk.beginDraft(run)
    fun checkpoint(run: RunSession) = disk.checkpoint(run)
    fun checkpoint(record: DraftRecord) = disk.checkpoint(record)
    fun appendPoint(point: RunPoint) = disk.appendPoint(point)
    fun appendPoint(point: RunPoint, record: DraftRecord) = disk.appendPoint(point, record)
    fun clearDraft() = disk.clearDraft()
    fun discardDraft() {
        val id = runCatching { disk.loadDraft()?.id }.getOrNull()
        disk.discardDraft(); id?.let { SessionIntentStore(contextApp).delete(it) }
    }
    fun archiveDraft() = disk.archiveDraft()
}
