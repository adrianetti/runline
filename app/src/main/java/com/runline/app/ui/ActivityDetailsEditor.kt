package com.runline.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.R
import com.runline.app.core.ActivityDetails
import com.runline.app.core.ActivityType
import com.runline.app.core.RoutineType
import com.runline.app.core.RunSession
import com.runline.app.data.ActivityPhotoImporter
import com.runline.app.data.RunStore
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Editing never delays recording finalization: this sheet receives an already-saved session. */
class ActivityDetailsEditor(
    private val activity: AppCompatActivity,
    private val run: RunSession,
    state: Bundle? = null,
    private val onSaved: () -> Unit,
) {
    private val model = ViewModelProvider(activity).get("activity-details-${run.id}", ActivityDetailsEditModel::class.java)
    private val dialog = BottomSheetDialog(activity)
    private val title = field(R.string.details_title, singleLine = true, ActivityDetails.MAX_TITLE)
    private val description = field(R.string.details_description, singleLine = false, ActivityDetails.MAX_DESCRIPTION)
    private val status = text("", 14)
    private val photoContainer = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val save = button(R.string.details_save, R.drawable.ic_check, true)
    private val addPhotos = button(R.string.details_add_photos, R.drawable.ic_photo)
    private var selectedActivity = run.activityType
    private var selectedRoutine = RoutineType.NONE
    private var selectedEffort: Int? = null
    private var closed = false
    private var retainDraft = false
    private var photosVersion = -1
    private val listener: () -> Unit = { updateState() }
    val isShowing get() = dialog.isShowing

    init {
        model.initialize(activity.applicationContext, run.id, state)
        val details = model.initial
        title.second.setText(state?.getString("title") ?: details.title)
        description.second.setText(state?.getString("description") ?: details.description)
        selectedActivity = state?.getString("activity")?.let(ActivityType::fromStorage) ?: details.activityType ?: run.activityType
        selectedRoutine = state?.getString("routine")?.let(RoutineType::fromStorage) ?: details.routine
        selectedEffort = if (state?.containsKey("effort") == true) state.getInt("effort").takeIf { it in 1..10 } else details.effort
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(24)) }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text(activity.getString(R.string.details_edit), 24, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button(R.string.main_close, R.drawable.ic_close).apply {
            text = ""; contentDescription = activity.getString(R.string.main_close); iconPadding = 0
            setOnClickListener { requestDismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.setPadding(dp(24), dp(12), dp(24), dp(8))
        column.addView(text(activity.getString(R.string.details_saved_hint), 14), row(8, 20))
        column.addView(title.first, row(0, 16)); column.addView(description.first, row(0, 16))
        column.addView(choice(R.string.details_activity, ActivityType.entries.map(::activityName), ActivityType.entries.indexOf(selectedActivity)) {
            selectedActivity = ActivityType.entries[it]
        }, row(0, 16))
        column.addView(choice(R.string.details_routine, RoutineType.entries.map(::routineName), RoutineType.entries.indexOf(selectedRoutine)) {
            selectedRoutine = RoutineType.entries[it]
        }, row(0, 16))
        column.addView(choice(R.string.details_effort,
            listOf(activity.getString(R.string.details_effort_none)) + (1..10).map { activity.getString(R.string.details_effort_value, it) }, selectedEffort ?: 0) {
            selectedEffort = it.takeIf { index -> index > 0 }
        }, row())
        column.addView(text(activity.getString(R.string.details_effort_hint), 13), row(8, 24))
        column.addView(text(activity.getString(R.string.details_photos), 20, true), row(0, 12))
        column.addView(photoContainer, row())
        column.addView(addPhotos.apply { setOnClickListener { choosePhotos() } }, row(4, 8))
        column.addView(text(activity.getString(R.string.details_photo_limit), 13), row(0, 12))
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        column.addView(status, row(4, 12))
        column.addView(save.apply { setOnClickListener { saveDetails() } }, row())
        val shell = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        shell.addView(header, LinearLayout.LayoutParams(-1, -2))
        shell.addView(androidx.core.widget.NestedScrollView(activity).apply { addView(column, ViewGroup.LayoutParams(-1, -2)) }, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(shell, ViewGroup.LayoutParams(-1, -1))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, key, event ->
            if (key == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) { requestDismiss(); true } else false
        }
        StableSheet.configure(dialog, activity)
        dialog.setOnDismissListener {
            closed = true
            if (model.listener === listener) model.listener = null
            if (!retainDraft && !activity.isChangingConfigurations) model.discard()
        }
        model.listener = listener
        updateState()
    }

    fun show() { dialog.show(); updateState() }
    /** Root calls this on Activity destruction; explicit dismissal uses the close control. */
    fun close() { retainDraft = true; closed = true; if (model.listener === listener) model.listener = null; dialog.dismiss() }
    fun saveState() = Bundle().apply {
        putString("runId", run.id); putString("title", title.second.text.toString()); putString("description", description.second.text.toString())
        putString("activity", selectedActivity.storageValue); putString("routine", selectedRoutine.storageValue); putInt("effort", selectedEffort ?: 0)
        putStringArrayList("photos", ArrayList(model.photos)); putStringArrayList("stagedPhotos", ArrayList(model.stagedPhotos))
        putBoolean("picker", model.pickerRequested)
    }
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PHOTOS) return false
        model.pickerRequested = false
        if (resultCode != Activity.RESULT_OK || data == null) return true
        val uris = if (data.clipData != null) List(data.clipData!!.itemCount.coerceAtMost(ActivityDetails.MAX_PHOTOS)) { data.clipData!!.getItemAt(it).uri }
            else listOfNotNull(data.data)
        model.importPhotos(uris.distinct())
        return true
    }
    private fun choosePhotos() {
        if (model.busy || model.pickerRequested) return
        if (model.photos.size >= ActivityDetails.MAX_PHOTOS) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            model.pickerRequested = true
            @Suppress("DEPRECATION") activity.startActivityForResult(intent, REQUEST_PHOTOS)
        } catch (_: Exception) { model.pickerRequested = false; status.setText(R.string.details_no_picker) }
    }
    private fun snapshot() = ActivityDetails(title.second.text.toString(), description.second.text.toString(), selectedEffort,
        selectedActivity, selectedRoutine, model.photos.toList())
    private fun saveDetails() {
        if (model.busy) { status.setText(R.string.details_finish_import); return }
        val details = runCatching { snapshot().validated() }.getOrNull()
        if (details == null) { status.setText(R.string.details_invalid); return }
        model.save(details)
    }
    private fun requestDismiss() {
        if (model.saving) { status.setText(R.string.details_saving); return }
        val changed = model.busy || snapshot().copy(activityType = selectedActivity) != model.initial.copy(activityType = model.initial.activityType ?: run.activityType)
        if (!changed) { dialog.dismiss(); return }
        MaterialAlertDialogBuilder(activity).setTitle(R.string.details_discard_title).setMessage(R.string.details_discard_body)
            .setPositiveButton(R.string.details_discard) { _, _ -> dialog.dismiss() }
            .setNegativeButton(R.string.details_keep_editing, null).show()
    }
    private fun updateState() {
        if (closed) return
        if (model.saved && dialog.isShowing) {
            model.saved = false; retainDraft = true; model.finish(); onSaved(); close(); return
        }
        save.isEnabled = !model.busy && !model.loadFailed
        addPhotos.isEnabled = !model.busy && !model.loadFailed && model.photos.size < ActivityDetails.MAX_PHOTOS
        val message = when {
            model.loadFailed -> R.string.details_load_failed
            model.busy -> if (model.saving) R.string.details_saving else R.string.details_photo_loading
            else -> model.errorRes
        }
        status.text = if (message == 0) "" else activity.getString(message)
        if (photosVersion != model.photosVersion) {
            photosVersion = model.photosVersion
            photoContainer.removeAllViews()
            if (model.photos.isNotEmpty()) photoContainer.addView(ActivityPhotosView(activity, run.id, model.photos.toList()) { id ->
                if (!model.busy) { model.photos.remove(id); model.photosVersion++; updateState() }
            })
        }
    }
    private fun choice(label: Int, values: List<String>, selected: Int, changed: (Int) -> Unit): View {
        val wrapper = TextInputLayout(activity, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = activity.getString(label); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        }
        wrapper.addView(MaterialAutoCompleteTextView(wrapper.context).apply {
            inputType = InputType.TYPE_NULL; minHeight = dp(56); setPadding(dp(16), dp(12), dp(16), dp(12))
            setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, values)); setText(values[selected.coerceIn(values.indices)], false)
            setOnItemClickListener { _, _, index, _ -> changed(index) }
        }, LinearLayout.LayoutParams(-1, -2))
        return wrapper
    }
    private fun field(label: Int, singleLine: Boolean, maximum: Int): Pair<TextInputLayout, TextInputEditText> {
        val wrapper = TextInputLayout(activity).apply { hint = activity.getString(label); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val edit = TextInputEditText(wrapper.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
            if (singleLine) { setSingleLine(true); maxLines = 1 } else { minLines = 3; maxLines = 6; gravity = Gravity.TOP }
            filters = arrayOf(InputFilter.LengthFilter(maximum)); minHeight = dp(56); setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        wrapper.addView(edit, LinearLayout.LayoutParams(-1, -2)); return wrapper to edit
    }
    private fun text(value: String, size: Int, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(activity.getColor(if (bold) R.color.on_surface else R.color.on_surface_variant))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun button(label: Int, icon: Int, filled: Boolean = false) = MaterialButton(activity, null,
        if (filled) com.google.android.material.R.attr.materialButtonStyle else com.google.android.material.R.attr.borderlessButtonStyle).apply {
        setText(label); isAllCaps = false; textSize = 14f; minHeight = dp(48); minimumHeight = dp(48); minWidth = 0; minimumWidth = 0
        setIconResource(icon); iconSize = dp(22); iconPadding = dp(8); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; cornerRadius = dp(16)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    private fun activityName(type: ActivityType) = activity.getString(when(type) {
        ActivityType.RUN -> R.string.export_activity_run; ActivityType.WALK -> R.string.export_activity_walk; ActivityType.HIKE -> R.string.export_activity_hike
    })
    private fun routineName(type: RoutineType) = activity.getString(when(type) {
        RoutineType.NONE -> R.string.details_routine_none; RoutineType.EASY -> R.string.details_routine_easy; RoutineType.LONG -> R.string.details_routine_long
        RoutineType.TEMPO -> R.string.details_routine_tempo; RoutineType.INTERVALS -> R.string.details_routine_intervals
        RoutineType.RECOVERY -> R.string.details_routine_recovery; RoutineType.RACE -> R.string.details_routine_race; RoutineType.OTHER -> R.string.details_routine_other
    })
    private fun row(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
    companion object { const val REQUEST_PHOTOS = 3104 }
}

/** Only small metadata crosses recreation; photo decoding/import/saving stays on a worker. */
class ActivityDetailsEditModel : ViewModel() {
    internal var listener: (() -> Unit)? = null
    internal var initial = ActivityDetails()
    internal val photos = mutableListOf<String>()
    internal val stagedPhotos = mutableSetOf<String>()
    internal var photosVersion = 0
    internal var pickerRequested = false
    internal var busy = false
    internal var saving = false
    internal var saved = false
    internal var loadFailed = false
    internal var errorRes = 0
    private var initialized = false
    private lateinit var context: Context
    private lateinit var runId: String
    private val worker = Executors.newSingleThreadExecutor()
    private val photoWorker = Executors.newSingleThreadExecutor()
    private var generation = 0L
    private val main = Handler(Looper.getMainLooper())

    internal fun initialize(context: Context, id: String, state: Bundle?) {
        if (initialized) return
        initialized = true; this.context = context; runId = id
        runCatching { RunStore(context).details(id) }.onSuccess { initial = it }.onFailure { loadFailed = true }
        photos.addAll(state?.getStringArrayList("photos")?.filter(ActivityDetails.PHOTO_ID::matches)?.distinct()?.take(ActivityDetails.MAX_PHOTOS) ?: initial.photoIds)
        stagedPhotos.addAll(state?.getStringArrayList("stagedPhotos")?.filter(ActivityDetails.PHOTO_ID::matches).orEmpty())
        pickerRequested = state?.getBoolean("picker") ?: false
    }
    internal fun importPhotos(uris: List<Uri>) {
        if (busy || loadFailed) return
        val chosen = uris.take(ActivityDetails.MAX_PHOTOS - photos.size)
        if (chosen.isEmpty()) return
        busy = true; saving = false; errorRes = 0; listener?.invoke()
        val operation = generation
        photoWorker.execute {
            val imported = chosen.map { runCatching { ActivityPhotoImporter.import(context, runId, it) } }
            main.post {
                if (operation != generation) {
                    imported.mapNotNull { it.getOrNull() }.forEach { runCatching { RunStore(context).photoFile(runId, it).delete() } }
                    return@post
                }
                imported.mapNotNull { it.getOrNull() }.forEach { photos.add(it); stagedPhotos.add(it) }
                errorRes = if (imported.any { it.isFailure }) R.string.details_photo_failed else 0
                photosVersion++; busy = false; listener?.invoke()
            }
        }
    }
    internal fun save(details: ActivityDetails) {
        if (busy || loadFailed) return
        busy = true; saving = true; errorRes = 0; listener?.invoke()
        worker.execute {
            val result = runCatching { RunStore(context).saveDetails(runId, details) }
            main.post {
                busy = false; saving = false
                if (result.isSuccess) { initial = details; stagedPhotos.removeAll(details.photoIds.toSet()); saved = true }
                else errorRes = R.string.details_save_failed
                listener?.invoke()
            }
        }
    }
    internal fun finish() { discard(); initialized = false }
    internal fun discard() {
        generation++; busy = false; saving = false; pickerRequested = false
        val unused = stagedPhotos.toList(); stagedPhotos.clear()
        worker.execute { unused.forEach { runCatching { RunStore(context).photoFile(runId, it).delete() } } }
        photos.clear(); initial = ActivityDetails(); initialized = false; errorRes = 0; loadFailed = false; photosVersion++
    }
    override fun onCleared() { generation++; listener = null; worker.shutdown(); photoWorker.shutdown(); super.onCleared() }
}
