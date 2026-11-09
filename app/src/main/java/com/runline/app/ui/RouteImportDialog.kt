package com.runline.app.ui

import android.app.Application
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.R
import com.runline.app.core.RouteImportNaming
import com.runline.app.data.PreparedRouteImport
import com.runline.app.data.RouteStore
import java.text.NumberFormat
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Retains parsing/saving across rotation without retaining an Activity or its views. */
class RouteImportModel(application: Application) : AndroidViewModel(application) {
    sealed class State {
        data object Reading : State()
        data class Ready(val draft: PreparedRouteImport, val saveFailed: Boolean = false) : State()
        data object Saving : State()
        data class Saved(val id: String) : State()
        data object Failed : State()
    }
    val state = MutableLiveData<State>()
    private val worker = Executors.newSingleThreadExecutor()
    private val store = RouteStore(application)

    fun prepare(uri: Uri, id: String) {
        if (state.value != null) return
        state.value = State.Reading
        worker.execute {
            val result = runCatching {
                // The process may have died just after confirmation committed this stable draft ID.
                if (store.entry(id, "en") != null) State.Saved(id) else State.Ready(store.prepareImport(uri, id))
            }.getOrElse { State.Failed }
            state.postValue(result)
        }
    }

    fun save(name: String) {
        val ready = state.value as? State.Ready ?: return
        state.value = State.Saving
        worker.execute {
            val result = runCatching<State> { State.Saved(store.commitImport(ready.draft, name).id) }
                .getOrElse { ready.copy(saveFailed = true) }
            state.postValue(result)
        }
    }
    override fun onCleared() { worker.shutdownNow() }
}

/** A single decision: keep/edit the suggested name, then add. Cancel never saves the draft. */
class RouteImportDialog : DialogFragment() {
    private lateinit var model: RouteImportModel
    private lateinit var input: TextInputEditText
    private lateinit var field: TextInputLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var nameInitialized = false
    private var suggestion = ""
    private var delivered = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        model = ViewModelProvider(this)[RouteImportModel::class.java]
        nameInitialized = savedInstanceState?.getBoolean("nameInitialized") == true
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        status = TextView(context).apply { textSize = 14f; setTextColor(context.getColor(R.color.on_surface_variant)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        content.addView(status, LinearLayout.LayoutParams(-1, -2))
        progress = ProgressBar(context)
        content.addView(progress, LinearLayout.LayoutParams(dp(32), dp(32)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL; topMargin = dp(16) })
        field = TextInputLayout(context).apply { hint = getString(R.string.route_import_name) }
        input = TextInputEditText(field.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 3; filters = arrayOf(InputFilter.LengthFilter(RouteImportNaming.MAX_NAME))
            setText(savedInstanceState?.getString("name").orEmpty())
        }
        field.addView(input, LinearLayout.LayoutParams(-1, -2))
        content.addView(field, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val dialog = MaterialAlertDialogBuilder(context).setTitle(R.string.route_import_add)
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton(R.string.main_cancel, null).setPositiveButton(R.string.route_import_add, null).create()
        dialog.setCanceledOnTouchOutside(false)
        model.state.observe(this) { render(it) }
        model.prepare(Uri.parse(requireArguments().getString("uri")), requireArguments().getString("id")!!)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            model.save(RouteImportNaming.clean(input.text.toString()).ifBlank { suggestion })
        }
        model.state.value?.let(::render)
    }

    private fun render(state: RouteImportModel.State) {
        val alert = dialog as? AlertDialog ?: return
        val ready = state as? RouteImportModel.State.Ready
        val saving = state is RouteImportModel.State.Saving
        isCancelable = !saving
        alert.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !saving
        alert.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = ready != null
        progress.visibility = if (state is RouteImportModel.State.Reading || saving) View.VISIBLE else View.GONE
        field.visibility = if (ready != null || saving) View.VISIBLE else View.GONE
        input.isEnabled = !saving
        when (state) {
            RouteImportModel.State.Reading -> status.setText(R.string.route_import_reading)
            RouteImportModel.State.Saving -> status.setText(R.string.route_import_saving)
            RouteImportModel.State.Failed -> { status.setText(R.string.route_import_invalid); alert.getButton(AlertDialog.BUTTON_NEGATIVE)?.setText(R.string.main_close) }
            is RouteImportModel.State.Ready -> {
                val route = state.draft.route
                val shape = RouteImportNaming.shape(route)
                val km = NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply { maximumFractionDigits = 1 }.format(route.distanceMeters / 1000)
                val description = getString(when (shape) {
                    RouteImportNaming.Shape.LOOP -> R.string.route_import_loop
                    RouteImportNaming.Shape.OUT_AND_BACK -> R.string.route_import_out_back
                    RouteImportNaming.Shape.ROUTE -> R.string.route_import_route
                }, km)
                suggestion = state.draft.suppliedName ?: RouteImportNaming.clean(listOfNotNull(RouteImportNaming.places(route, shape), description).joinToString(" · "))
                status.text = if (state.saveFailed) getString(R.string.route_import_save_failed) else description
                field.helperText = getString(R.string.route_import_name_hint)
                if (!nameInitialized) { input.setText(suggestion); nameInitialized = true }
            }
            is RouteImportModel.State.Saved -> {
                if (delivered) return
                delivered = true
                parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString("id", state.id) })
                dismiss()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("nameInitialized", nameInitialized)
        if (::input.isInitialized) outState.putString("name", input.text.toString())
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    companion object {
        const val TAG = "routeImport"
        const val RESULT = "routeImported"
        fun create(uri: Uri) = RouteImportDialog().apply {
            arguments = Bundle().apply { putString("uri", uri.toString()); putString("id", "import-${UUID.randomUUID()}") }
        }
    }
}
