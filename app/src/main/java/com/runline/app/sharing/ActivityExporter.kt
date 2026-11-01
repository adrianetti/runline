package com.runline.app.sharing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.runline.app.R
import com.runline.app.core.ActivityArchive
import com.runline.app.core.ActivityExportFileName
import com.runline.app.core.GpxExporter
import com.runline.app.core.RunSession
import com.runline.app.i18n.AppLanguage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** Save/share local files; preparation and destination writes never run on the UI thread. */
class ActivityExporter(
    private val activity: AppCompatActivity,
    @Suppress("UNUSED_PARAMETER") loadRun: (String) -> RunSession?, // Kept for the Activity integration.
    private val onMessage: (String) -> Unit,
) {
    private val model = ViewModelProvider(activity)[ActivityExportViewModel::class.java]
    private val listener: () -> Unit = { deliverOutcome() }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> deliverOutcome()
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }

    init { model.listener = listener; activity.lifecycle.addObserver(lifecycleObserver) }

    fun start(run: RunSession) = begin(listOf(run), ExportKind.GPX, ExportAction.DOWNLOAD)
    fun share(run: RunSession) = begin(listOf(run), ExportKind.GPX, ExportAction.SHARE)
    fun saveAs(run: RunSession) = begin(listOf(run), ExportKind.GPX, ExportAction.PICKER)
    fun startArchive(runs: List<RunSession>) = begin(runs, ExportKind.ARCHIVE, ExportAction.DOWNLOAD)
    fun shareArchive(runs: List<RunSession>) = begin(runs, ExportKind.ARCHIVE, ExportAction.SHARE)
    fun saveArchiveAs(runs: List<RunSession>) = begin(runs, ExportKind.ARCHIVE, ExportAction.PICKER)
    fun startSummary(runs: List<RunSession>) = begin(runs, ExportKind.SUMMARY, ExportAction.DOWNLOAD)
    fun shareSummary(runs: List<RunSession>) = begin(runs, ExportKind.SUMMARY, ExportAction.SHARE)
    fun saveSummaryAs(runs: List<RunSession>) = begin(runs, ExportKind.SUMMARY, ExportAction.PICKER)

    private fun begin(runs: List<RunSession>, kind: ExportKind, action: ExportAction) {
        if (model.isSaving || model.pending != null || model.outcome != null) {
            onMessage(tr(R.string.export_finish_saving)); return
        }
        if (runs.isEmpty()) { onMessage(tr(R.string.export_no_activities)); return }
        onMessage(tr(R.string.export_preparing))
        val localized = AppLanguage.localizedContext(activity)
        val locale = localized.resources.configuration.locales[0]
        val zone = ZoneId.systemDefault()
        val names = mapOf(
            com.runline.app.core.ActivityType.RUN to localized.getString(R.string.export_activity_run),
            com.runline.app.core.ActivityType.WALK to localized.getString(R.string.export_activity_walk),
            com.runline.app.core.ActivityType.HIKE to localized.getString(R.string.export_activity_hike),
        )
        val base = localized.getString(if (kind == ExportKind.SUMMARY) R.string.export_summary_name else R.string.export_archive_name)
        model.prepare(activity.applicationContext, runs.toList(), kind, action, locale, zone, names, base)
    }

    fun restoreState(state: Bundle?) {
        if (!model.isSaving && model.pending == null && model.outcome == null) {
            model.pending = PreparedExport.restore(activity.applicationContext, state)
        }
    }
    fun saveState(state: Bundle) { model.pending?.save(activity.applicationContext, state) }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CREATE_GPX) return false
        val pending = model.pending
        model.pending = null
        if (resultCode != Activity.RESULT_OK) return true
        val destination = data?.data
        if (pending == null || destination == null) { onMessage(tr(R.string.export_save_retry)); return true }
        onMessage(tr(R.string.export_saving))
        model.writeToDocument(activity.applicationContext, pending, destination)
        return true
    }

    fun onResume() = deliverOutcome()
    fun close() {
        if (model.listener === listener) model.listener = null
        activity.lifecycle.removeObserver(lifecycleObserver)
    }

    @Suppress("DEPRECATION")
    private fun chooseDestination(prepared: PreparedExport) {
        model.pending = prepared
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = prepared.kind.mimeType
            putExtra(Intent.EXTRA_TITLE, prepared.filename)
        }
        try { activity.startActivityForResult(intent, REQUEST_CREATE_GPX) }
        catch (_: ActivityNotFoundException) { model.pending = null; onMessage(tr(R.string.export_no_picker)) }
        catch (_: SecurityException) { model.pending = null; onMessage(tr(R.string.export_picker_failed)) }
    }

    private fun sharePrepared(prepared: PreparedExport) {
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", prepared.file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = prepared.kind.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, prepared.filename)
                clipData = ClipData.newUri(activity.contentResolver, prepared.filename, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(send, tr(R.string.export_share_title)))
        } catch (_: Exception) { onMessage(tr(R.string.export_share_failed)) }
    }

    private fun deliverOutcome() {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val result = model.outcome ?: return
        model.outcome = null
        when (result) {
            is ActivityExportOutcome.Saved -> Snackbar.make(activity.findViewById(android.R.id.content),
                tr(if (result.inDownloads) R.string.export_downloads_filename else R.string.export_saved_filename, result.filename), Snackbar.LENGTH_INDEFINITE)
                .setAction(tr(R.string.export_share_action)) { sharePrepared(result.prepared) }
                .apply { view.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 4 }
                .show()
            is ActivityExportOutcome.Pick -> chooseDestination(result.prepared)
            is ActivityExportOutcome.Share -> sharePrepared(result.prepared)
            is ActivityExportOutcome.Failed -> {
                onMessage(tr(result.messageRes))
                result.pickerFallback?.let(::chooseDestination)
            }
        }
    }
    private fun tr(id: Int, vararg args: Any): String = activity.getString(id, *args)

    companion object { const val REQUEST_CREATE_GPX = 3103 }
}

internal enum class ExportKind(val extension: String, val mimeType: String) {
    GPX("gpx", "application/gpx+xml"), ARCHIVE("zip", "application/zip"), SUMMARY("csv", "text/csv")
}
internal enum class ExportAction { DOWNLOAD, SHARE, PICKER }

/** Only a small file reference is saved with the picker, never every activity in a Bundle. */
internal data class PreparedExport(val file: File, val filename: String, val kind: ExportKind) {
    fun save(context: Context, state: Bundle) {
        state.putString(STATE_FILE, file.relativeTo(context.cacheDir).path)
        state.putString(STATE_NAME, filename)
        state.putString(STATE_KIND, kind.name)
    }
    companion object {
        private const val STATE_FILE = "export_prepared_cache_file"
        private const val STATE_NAME = "export_prepared_filename"
        private const val STATE_KIND = "export_prepared_kind"
        fun restore(context: Context, state: Bundle?): PreparedExport? = runCatching {
            val relative = state?.getString(STATE_FILE) ?: return null
            val name = state.getString(STATE_NAME) ?: return null
            val kind = ExportKind.valueOf(state.getString(STATE_KIND) ?: return null)
            val file = File(context.cacheDir, relative).canonicalFile
            require(file.path.startsWith(File(context.cacheDir, "exports").canonicalPath + File.separator))
            PreparedExport(file, name, kind)
        }.getOrNull()
    }
}

internal sealed interface ActivityExportOutcome {
    data class Saved(val filename: String, val prepared: PreparedExport, val inDownloads: Boolean = false) : ActivityExportOutcome
    data class Pick(val prepared: PreparedExport) : ActivityExportOutcome
    data class Share(val prepared: PreparedExport) : ActivityExportOutcome
    data class Failed(val messageRes: Int, val pickerFallback: PreparedExport? = null) : ActivityExportOutcome
}

/** A retained worker owns I/O; only immutable outcomes cross back to the main thread. */
class ActivityExportViewModel : ViewModel() {
    internal var pending: PreparedExport? = null
    internal var isSaving = false
    internal var outcome: ActivityExportOutcome? = null
    internal var listener: (() -> Unit)? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    internal fun prepare(context: Context, runs: List<RunSession>, kind: ExportKind, action: ExportAction,
                         locale: Locale, zone: ZoneId, names: Map<com.runline.app.core.ActivityType, String>, base: String) {
        isSaving = true
        worker.execute {
            var directory: File? = null
            val prepared = try {
                val entries = ActivityArchive.entries(runs, locale, zone) { names.getValue(it) }
                val stamp = Instant.now().atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm", Locale.ROOT))
                val filename = if (kind == ExportKind.GPX) entries.single().filename.substringAfterLast('/')
                    else "$base - $stamp.${kind.extension}"
                directory = File(context.cacheDir, "exports/${UUID.randomUUID()}")
                check(directory.mkdirs()) { "Could not prepare an export directory." }
                val file = File(directory, filename)
                when (kind) {
                    ExportKind.GPX -> file.bufferedWriter(Charsets.UTF_8).use { it.write(GpxExporter.export(runs.single())) }
                    ExportKind.ARCHIVE -> file.outputStream().buffered().use { ActivityArchive.writeZip(entries, zone, it) }
                    ExportKind.SUMMARY -> file.bufferedWriter(Charsets.UTF_8).use { it.write(ActivityArchive.summaryCsv(entries, zone)) }
                }
                PreparedExport(file, filename, kind)
            } catch (_: Exception) {
                directory?.deleteRecursively()
                complete(ActivityExportOutcome.Failed(R.string.export_prepare_failed)); return@execute
            }
            val result = when {
                action == ExportAction.SHARE -> ActivityExportOutcome.Share(prepared)
                action == ExportAction.PICKER || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> ActivityExportOutcome.Pick(prepared)
                else -> writeToDownloads(context, prepared)
            }
            complete(result)
        }
    }

    internal fun writeToDocument(context: Context, prepared: PreparedExport, destination: Uri) {
        isSaving = true
        worker.execute {
            val result = try {
                copyToUri(context, prepared.file, destination)
                ActivityExportOutcome.Saved(readFilename(context, destination) ?: prepared.filename, prepared)
            } catch (_: Exception) { ActivityExportOutcome.Failed(R.string.export_write_failed) }
            complete(result)
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun writeToDownloads(context: Context, prepared: PreparedExport): ActivityExportOutcome {
        var destination: Uri? = null
        return try {
            val resolver = context.contentResolver
            val (uri, reservedName) = reserveDownload(context, prepared)
            destination = uri
            copyToUri(context, prepared.file, uri)
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
            ActivityExportOutcome.Saved(readFilename(context, uri) ?: reservedName, prepared, inDownloads = true)
        } catch (_: Exception) {
            destination?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            ActivityExportOutcome.Failed(R.string.export_downloads_failed, prepared)
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun reserveDownload(context: Context, prepared: PreparedExport): Pair<Uri, String> = synchronized(DOWNLOAD_INSERT_LOCK) {
        val resolver = context.contentResolver
        val names = mutableSetOf<String>()
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(Environment.DIRECTORY_DOWNLOADS + "/"), null)?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let(names::add)
        }
        repeat(5) {
            val candidate = ActivityExportFileName.availableName(prepared.filename, names)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, candidate)
                put(MediaStore.MediaColumns.MIME_TYPE, prepared.kind.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Downloads did not create a file.")
            val actual = readFilename(context, uri)
            if (actual != null && actual.endsWith(".${prepared.kind.extension}", ignoreCase = true)) return@synchronized uri to actual
            check(resolver.delete(uri, null, null) == 1) { "Could not remove an unused Downloads item." }
            names += candidate
            actual?.let(names::add)
        }
        error("Downloads could not preserve the file extension.")
    }

    private fun copyToUri(context: Context, source: File, destination: Uri) {
        // Opening the source first avoids truncating an existing picker destination if a
        // cached prepared export was evicted while the external picker was open.
        source.inputStream().use { input ->
            (context.contentResolver.openOutputStream(destination, "wt") ?: error("Could not open destination."))
                .buffered().use { output -> input.copyTo(output) }
        }
    }
    private fun readFilename(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    private fun complete(result: ActivityExportOutcome) {
        main.post { isSaving = false; outcome = result; listener?.invoke() }
    }
    override fun onCleared() { listener = null; worker.shutdown() }
    private companion object { val DOWNLOAD_INSERT_LOCK = Any() }
}
