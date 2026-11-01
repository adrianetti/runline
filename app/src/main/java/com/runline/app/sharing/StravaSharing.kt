package com.runline.app.sharing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.runline.app.core.GpxExporter
import com.runline.app.core.RunSession
import com.runline.app.core.continuousSegments
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * User-driven website handoff, not a Strava connection or an upload API.
 * Android 10+ saves the GPX to Downloads without a storage permission or save dialog.
 * Android 8/9 and failed Downloads writes use Android's document picker instead.
 * Opening the website never attaches the GPX or publishes the activity.
 */
class StravaSharing(
    private val activity: AppCompatActivity,
    private val loadRun: (String) -> RunSession?,
    private val onMessage: (String) -> Unit,
) {
    private val model = ViewModelProvider(activity)[StravaShareViewModel::class.java]
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
            onMessage("Finish saving the current file first.")
            return
        }
        if (continuousSegments(run.points).none { it.size >= 2 }) {
            onMessage("This run has no GPS route to share. Its recorded time is still saved.")
            return
        }
        val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(INTRO_SEEN, false)) {
            MaterialAlertDialogBuilder(activity)
                .setTitle("Share to Strava")
                .setMessage(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    "The GPX will be saved to Downloads.\n\n1  Choose it on Strava\n2  Review and publish\n\nUse Desktop site if needed."
                    else "Save the GPX, choose it on Strava, then publish.\n\nUse Desktop site if needed.")
                .setPositiveButton("Continue") { _, _ ->
                    preferences.edit().putBoolean(INTRO_SEEN, true).apply()
                    prepareFile(run)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else prepareFile(run)
    }

    fun restoreState(state: Bundle?) {
        if (!model.isSaving && model.pendingRunId == null) {
            model.pendingRunId = state?.getString(STATE_RUN_ID)
        }
    }

    fun saveState(state: Bundle) {
        state.putString(STATE_RUN_ID, model.pendingRunId)
    }

    /** Return true when this helper has consumed the document-picker result. */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CREATE_GPX) return false
        val id = model.pendingRunId
        val run = model.pendingRun ?: id?.let { runCatching { loadRun(it) }.getOrNull() }
        model.pendingRunId = null
        model.pendingRun = null
        if (resultCode != Activity.RESULT_OK) return true
        val destination = data?.data
        if (run == null || destination == null) {
            onMessage("The file could not be saved. Open the run and try sharing again.")
            return true
        }
        if (model.isSaving) return true
        onMessage("Saving GPX…")
        model.export(activity.applicationContext, run, destination, filename(run))
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
            onMessage("Saving GPX…")
            model.exportToDownloads(activity.applicationContext, run, filename(run))
        } else chooseDestination(run)
    }

    @Suppress("DEPRECATION")
    private fun chooseDestination(run: RunSession) {
        model.pendingRunId = run.id
        model.pendingRun = run
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_TITLE, filename(run))
        }
        try {
            activity.startActivityForResult(intent, REQUEST_CREATE_GPX)
        } catch (_: ActivityNotFoundException) {
            model.pendingRunId = null
            model.pendingRun = null
            onMessage("No file picker is available. Use the GPX share action instead.")
        } catch (_: SecurityException) {
            model.pendingRunId = null
            model.pendingRun = null
            onMessage("The file picker could not open. Your run is still saved.")
        }
    }

    private fun deliverOutcome() {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val result = model.outcome ?: return
        model.outcome = null
        when (result) {
            is StravaExportOutcome.Saved -> {
                // Remains useful when the user returns from the browser. Copying is
                // explicit; do not put route-related data on the clipboard unasked.
                Snackbar.make(activity.findViewById(android.R.id.content),
                    "${if (result.inDownloads) "Downloads" else "Saved"}: ${result.filename}", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Copy name") { copyText("GPX filename", result.filename) }.show()
                openWebsite(result.filename)
            }
            is StravaExportOutcome.Failed -> {
                onMessage(result.message)
                result.pickerFallback?.let(::chooseDestination)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun openWebsite(filename: String) {
        // Resolve against a generic HTTPS page rather than the Strava upload URL.
        // The native Strava app may claim strava.com links without exposing an
        // activity-file uploader. Only generic browser handlers receive this URL.
        val browserProbe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val manager = activity.packageManager
        val browsers = manager.queryIntentActivities(browserProbe, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.takeIf { info -> info.exported }?.packageName }
            .distinct()
        val preferred = manager.resolveActivity(browserProbe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val target = Intent(Intent.ACTION_VIEW, Uri.parse(UPLOAD_URL)).addCategory(Intent.CATEGORY_BROWSABLE)
        val intent = when {
            preferred in browsers -> target.setPackage(preferred)
            browsers.size == 1 -> target.setPackage(browsers.first())
            browsers.isNotEmpty() -> Intent.createChooser(Intent(target).setPackage(browsers.first()), "Open Strava in browser")
                .putExtra(Intent.EXTRA_INITIAL_INTENTS, browsers.drop(1).map { Intent(target).setPackage(it) }.toTypedArray())
            else -> { showWebsiteFallback(filename); return }
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showWebsiteFallback(filename)
        } catch (_: SecurityException) {
            showWebsiteFallback(filename)
        }
    }

    private fun showWebsiteFallback(filename: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Your GPX file is saved")
            .setMessage("Open Strava's file uploader in a browser and choose $filename, then review and publish.")
            .setPositiveButton("Copy upload link") { _, _ ->
                copyText("Strava file uploader", UPLOAD_URL)
                onMessage("Upload link copied.")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun copyText(label: String, value: String) {
        activity.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(label, value))
    }

    private fun filename(run: RunSession): String {
        val date = Instant.ofEpochMilli(run.startedAtMs).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
        return "runline-$date.gpx"
    }

    companion object {
        const val REQUEST_CREATE_GPX = 3102
        const val UPLOAD_URL = "https://www.strava.com/upload/select"
        private const val PREFERENCES = "strava_website_sharing"
        private const val INTRO_SEEN = "downloads_intro_seen"
        private const val STATE_RUN_ID = "strava_website_pending_run"
    }
}

internal sealed interface StravaExportOutcome {
    data class Saved(val filename: String, val inDownloads: Boolean = false) : StravaExportOutcome
    data class Failed(val message: String, val pickerFallback: RunSession? = null) : StravaExportOutcome
}

/** Keeps an in-flight file write and its one-shot result across Activity recreation. */
class StravaShareViewModel : ViewModel() {
    internal var pendingRunId: String? = null
    internal var pendingRun: RunSession? = null
    internal var isSaving = false
    internal var outcome: StravaExportOutcome? = null
    internal var listener: (() -> Unit)? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @android.annotation.TargetApi(Build.VERSION_CODES.Q)
    internal fun exportToDownloads(context: Context, run: RunSession, filename: String) {
        isSaving = true
        worker.execute {
            val xml = try { prepareGpx(run) } catch (error: Exception) {
                complete(StravaExportOutcome.Failed(error.message ?: "This GPX file could not be prepared."))
                return@execute
            }
            var destination: Uri? = null
            val result = try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Downloads did not create a file.")
                destination = uri
                writeGpx(context, uri, xml)
                check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1) {
                    "The file could not be published to Downloads."
                }
                // MediaStore may choose a different name after a filename collision.
                val actualName = readFilename(context, uri) ?: filename
                StravaExportOutcome.Saved(actualName, inDownloads = true)
            } catch (_: Exception) {
                destination?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                StravaExportOutcome.Failed("Couldn't save to Downloads. Choose a file location instead.", pickerFallback = run)
            }
            complete(result)
        }
    }

    internal fun export(context: Context, run: RunSession, destination: Uri, filename: String) {
        isSaving = true
        worker.execute {
            val result = try {
                val xml = prepareGpx(run)
                writeGpx(context, destination, xml)
                StravaExportOutcome.Saved(readFilename(context, destination) ?: filename)
            } catch (error: IllegalArgumentException) {
                StravaExportOutcome.Failed(error.message ?: "This GPX file could not be prepared.")
            } catch (_: Exception) {
                StravaExportOutcome.Failed("Couldn't save the GPX file. Choose another location and try again. Your run is still saved.")
            }
            complete(result)
        }
    }

    private fun prepareGpx(run: RunSession): String = GpxExporter.export(run).also {
        require(it.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
            "This route exceeds Strava's 25 MB file limit. Your recording is still saved."
        }
    }

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

    private fun complete(result: StravaExportOutcome) {
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
        const val MAX_FILE_BYTES = 25_000_000
    }
}
