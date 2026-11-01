package com.runline.app.sharing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.runline.app.R
import com.runline.app.core.ActivityExportFileName
import com.runline.app.core.GpxExporter
import com.runline.app.core.RunSession
import java.util.concurrent.Executors

/**
 * Local GPX export. Android 10+ saves to Downloads without a storage permission or
 * save dialog; Android 8/9 and failed Downloads writes use the document picker.
 * An Activity recreation never interrupts the file write or repeats its result.
 */
class ActivityExporter(
    private val activity: AppCompatActivity,
    private val loadRun: (String) -> RunSession?,
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

    init {
        model.listener = listener
        activity.lifecycle.addObserver(lifecycleObserver)
    }

    fun start(run: RunSession) {
        if (model.isSaving || model.pendingRunId != null) {
            onMessage(tr(R.string.export_finish_saving))
            return
        }
        prepareFile(run)
    }

    fun restoreState(state: Bundle?) {
        if (!model.isSaving && model.pendingRunId == null) {
            model.pendingRunId = state?.getString(STATE_RUN_ID)
            model.pendingFilename = state?.getString(STATE_FILENAME)
        }
    }

    fun saveState(state: Bundle) {
        state.putString(STATE_RUN_ID, model.pendingRunId)
        state.putString(STATE_FILENAME, model.pendingFilename)
    }

    /** Return true when this helper has consumed the document-picker result. */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CREATE_GPX) return false
        val id = model.pendingRunId
        val run = model.pendingRun ?: id?.let { runCatching { loadRun(it) }.getOrNull() }
        val requestedFilename = model.pendingFilename
        model.pendingRunId = null
        model.pendingRun = null
        model.pendingFilename = null
        if (resultCode != Activity.RESULT_OK) return true
        val destination = data?.data
        if (run == null || destination == null) {
            onMessage(tr(R.string.export_save_retry))
            return true
        }
        if (model.isSaving) return true
        onMessage(tr(R.string.export_saving))
        model.export(activity.applicationContext, run, destination, requestedFilename ?: ActivityExportName.fileName(activity, run))
        return true
    }

    /** Safe to call from the Activity; lifecycle observation also delivers after rotation. */
    fun onResume() = deliverOutcome()

    fun close() {
        if (model.listener === listener) model.listener = null
        activity.lifecycle.removeObserver(lifecycleObserver)
    }

    private fun prepareFile(run: RunSession) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onMessage(tr(R.string.export_saving))
            model.exportToDownloads(activity.applicationContext, run, ActivityExportName.fileName(activity, run))
        } else chooseDestination(run)
    }

    @Suppress("DEPRECATION")
    private fun chooseDestination(run: RunSession) {
        model.pendingRunId = run.id
        model.pendingRun = run
        model.pendingFilename = ActivityExportName.fileName(activity, run)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_TITLE, model.pendingFilename)
        }
        try {
            activity.startActivityForResult(intent, REQUEST_CREATE_GPX)
        } catch (_: ActivityNotFoundException) {
            model.pendingRunId = null
            model.pendingRun = null
            model.pendingFilename = null
            onMessage(tr(R.string.export_no_picker))
        } catch (_: SecurityException) {
            model.pendingRunId = null
            model.pendingRun = null
            model.pendingFilename = null
            onMessage(tr(R.string.export_picker_failed))
        }
    }

    private fun deliverOutcome() {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val result = model.outcome ?: return
        model.outcome = null
        when (result) {
            is ActivityExportOutcome.Saved -> {
                // A readable filename makes the exported route easy to find. Clipboard
                // access is explicit, never automatic for location-related data.
                Snackbar.make(activity.findViewById(android.R.id.content),
                    tr(if (result.inDownloads) R.string.export_downloads_filename else R.string.export_saved_filename, result.filename), Snackbar.LENGTH_INDEFINITE)
                    .setAction(tr(R.string.export_copy_name)) { copyText(tr(R.string.export_filename_label), result.filename) }
                    .apply { view.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 4 }
                    .show()
            }
            is ActivityExportOutcome.Failed -> {
                onMessage(tr(result.messageRes))
                result.pickerFallback?.let(::chooseDestination)
            }
        }
    }

    private fun tr(id: Int, vararg args: Any): String = activity.getString(id, *args)

    private fun copyText(label: String, value: String) {
        activity.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(label, value))
    }

    companion object {
        const val REQUEST_CREATE_GPX = 3103
        private const val STATE_RUN_ID = "activity_export_pending_run"
        private const val STATE_FILENAME = "activity_export_pending_filename"
    }
}

internal sealed interface ActivityExportOutcome {
    data class Saved(val filename: String, val inDownloads: Boolean = false) : ActivityExportOutcome
    data class Failed(val messageRes: Int, val pickerFallback: RunSession? = null) : ActivityExportOutcome
}

/** Keeps an in-flight file write and its one-shot result across Activity recreation. */
class ActivityExportViewModel : ViewModel() {
    internal var pendingRunId: String? = null
    internal var pendingRun: RunSession? = null
    internal var pendingFilename: String? = null
    internal var isSaving = false
    internal var outcome: ActivityExportOutcome? = null
    internal var listener: (() -> Unit)? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    internal fun exportToDownloads(context: Context, run: RunSession, filename: String) {
        isSaving = true
        worker.execute {
            val xml = try { prepareGpx(run) } catch (_: Exception) {
                complete(ActivityExportOutcome.Failed(R.string.export_prepare_failed))
                return@execute
            }
            var destination: Uri? = null
            val result = try {
                val resolver = context.contentResolver
                val (uri, reservedName) = reserveDownload(context, filename)
                destination = uri
                writeGpx(context, uri, xml)
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1) {
                    "The file could not be published to Downloads."
                }
                // Use the provider's actual display name, including our pre-extension suffix.
                val actualName = readFilename(context, uri) ?: reservedName
                ActivityExportOutcome.Saved(actualName, inDownloads = true)
            } catch (_: Exception) {
                destination?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                ActivityExportOutcome.Failed(R.string.export_downloads_failed, pickerFallback = run)
            }
            complete(result)
        }
    }

    internal fun export(context: Context, run: RunSession, destination: Uri, filename: String) {
        isSaving = true
        worker.execute {
            val xml = try { prepareGpx(run) } catch (_: Exception) {
                complete(ActivityExportOutcome.Failed(R.string.export_prepare_failed))
                return@execute
            }
            val result = try {
                writeGpx(context, destination, xml)
                ActivityExportOutcome.Saved(readFilename(context, destination) ?: filename)
            } catch (_: Exception) {
                ActivityExportOutcome.Failed(R.string.export_write_failed)
            }
            complete(result)
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    private fun reserveDownload(context: Context, desiredName: String): Pair<Uri, String> = synchronized(DOWNLOAD_INSERT_LOCK) {
        val resolver = context.contentResolver
        val names = mutableSetOf<String>()
        // With no storage-read permission, scoped storage exposes this app's own rows.
        // Provider verification below also handles a competing or hidden pending item.
        resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(Environment.DIRECTORY_DOWNLOADS + "/"), null)?.use { cursor ->
            while (cursor.moveToNext()) cursor.getString(0)?.let(names::add)
        }
        repeat(5) {
            val candidate = ActivityExportFileName.availableName(desiredName, names)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, candidate)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Downloads did not create a file.")
            val actual = readFilename(context, uri)
            if (actual != null && actual.endsWith(".gpx", ignoreCase = true)) return@synchronized uri to actual
            // A hidden/parallel collision can still be renamed by the provider. Delete
            // only our new, empty pending row and try a new candidate; never overwrite.
            check(resolver.delete(uri, null, null) == 1) { "Could not remove an unused Downloads item." }
            names.add(candidate)
            actual?.let(names::add)
        }
        error("Downloads could not preserve the GPX extension.")
    }

    private fun prepareGpx(run: RunSession): String = GpxExporter.export(run)

    private fun writeGpx(context: Context, destination: Uri, xml: String) {
        (context.contentResolver.openOutputStream(destination, "wt")
            ?: error("The document provider did not open the file."))
            .bufferedWriter(Charsets.UTF_8).use { it.write(xml) }
    }

    private fun readFilename(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun complete(result: ActivityExportOutcome) {
        main.post {
            isSaving = false
            outcome = result
            listener?.invoke()
        }
    }

    override fun onCleared() {
        listener = null
        // Do not interrupt a file that is already being written. No Activity is retained.
        worker.shutdown()
    }

    private companion object {
        val DOWNLOAD_INSERT_LOCK = Any()
    }
}
