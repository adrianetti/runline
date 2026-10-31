package com.runline.app.sharing

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.core.GpxExporter
import com.runline.app.core.RunSession
import com.runline.app.core.continuousSegments
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * User-driven website handoff, not a Strava connection or an upload API.
 * The GPX is written only to the destination chosen in Android's document picker.
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
                .setMessage("1  Save the file\n2  Pick it on Strava\n3  Review and publish\n\nBrowser desktop mode may be needed.")
                .setPositiveButton("Save & continue") { _, _ ->
                    preferences.edit().putBoolean(INTRO_SEEN, true).apply()
                    chooseDestination(run)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else chooseDestination(run)
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
                onMessage("GPX saved. Choose ${result.filename} on Strava.")
                openWebsite()
            }
            is StravaExportOutcome.Failed -> onMessage(result.message)
        }
    }

    private fun openWebsite() {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UPLOAD_URL)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            })
        } catch (_: ActivityNotFoundException) {
            showWebsiteFallback()
        } catch (_: SecurityException) {
            showWebsiteFallback()
        }
    }

    private fun showWebsiteFallback() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Your GPX file is saved")
            .setMessage("Open Strava's file uploader in a browser, choose the file, then review and publish your run.")
            .setPositiveButton("Copy upload link") { _, _ ->
                activity.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Strava file uploader", UPLOAD_URL))
                onMessage("Upload link copied.")
            }
            .setNegativeButton("Close", null)
            .show()
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
        private const val INTRO_SEEN = "intro_seen"
        private const val STATE_RUN_ID = "strava_website_pending_run"
    }
}

internal sealed interface StravaExportOutcome {
    data class Saved(val filename: String) : StravaExportOutcome
    data class Failed(val message: String) : StravaExportOutcome
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

    internal fun export(context: Context, run: RunSession, destination: Uri, filename: String) {
        isSaving = true
        worker.execute {
            val result = try {
                val xml = GpxExporter.export(run)
                // The official website accepts a file up to 25 MB. Fail before writing
                // an export we already know the intended destination will reject.
                require(xml.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
                    "This route exceeds Strava's 25 MB file limit. Your recording is still saved."
                }
                (context.contentResolver.openOutputStream(destination, "wt")
                    ?: error("The document provider did not open the file."))
                    .bufferedWriter(Charsets.UTF_8).use { it.write(xml) }
                StravaExportOutcome.Saved(filename)
            } catch (error: IllegalArgumentException) {
                StravaExportOutcome.Failed(error.message ?: "This GPX file could not be prepared.")
            } catch (_: Exception) {
                StravaExportOutcome.Failed("Couldn't save the GPX file. Choose another location and try again. Your run is still saved.")
            }
            main.post {
                isSaving = false
                outcome = result
                listener?.invoke()
            }
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
