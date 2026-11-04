package com.runline.app.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.runline.app.R
import com.runline.app.ai.AiFailure
import kotlin.math.roundToInt

internal class AiSheet(val activity: AppCompatActivity, title: Int) {
    val dialog = BottomSheetDialog(activity)
    val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(24)) }
    private val footer = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), dp(16)); visibility = View.GONE
    }
    var closed = false
    val isShowing get() = dialog.isShowing
    init {
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(12), dp(24), dp(8)) }
        header.addView(text(activity.getString(title), 24, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("", R.drawable.ic_close).apply { contentDescription = activity.getString(R.string.main_close); iconPadding = 0; setOnClickListener { close() } }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val shell = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        shell.addView(header, LinearLayout.LayoutParams(-1, -2))
        shell.addView(NestedScrollView(activity).apply { addView(column); clipToPadding = false }, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(footer, LinearLayout.LayoutParams(-1, -2))
        dialog.setContentView(shell, android.view.ViewGroup.LayoutParams(-1, -1))
        dialog.setOnDismissListener { closed = true }
        StableSheet.configure(dialog, activity)
    }
    fun show() = dialog.show()
    fun close() { closed = true; dialog.dismiss() }
    fun primaryAction(action: View, status: View? = null) {
        footer.removeAllViews()
        status?.let {
            (it.parent as? android.view.ViewGroup)?.removeView(it)
            footer.addView(it, row(0, 8))
        }
        (action.parent as? android.view.ViewGroup)?.removeView(action)
        footer.addView(action, row())
        footer.visibility = View.VISIBLE
    }
    fun text(value: String, size: Int = 15, bold: Boolean = false) = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(activity.getColor(if (bold) R.color.on_surface else R.color.on_surface_variant))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    fun button(label: String, icon: Int = R.drawable.ic_check, filled: Boolean = false) = MaterialButton(activity, null,
        if (filled) com.google.android.material.R.attr.materialButtonStyle else androidx.appcompat.R.attr.borderlessButtonStyle).apply {
        text = label; isAllCaps = false; textSize = 15f; minHeight = dp(52); minimumHeight = dp(52); minWidth = 0; minimumWidth = 0
        setIconResource(icon); iconSize = dp(22); iconPadding = dp(8); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; cornerRadius = dp(16)
        setPadding(dp(12), dp(8), dp(12), dp(8))
    }
    fun field(label: Int, type: Int): Pair<TextInputLayout, TextInputEditText> {
        val wrapper = TextInputLayout(activity).apply { hint = activity.getString(label); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val edit = TextInputEditText(wrapper.context).apply { inputType = type; maxLines = 1; minHeight = dp(56); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        wrapper.addView(edit); column.addView(wrapper, row(4, 12)); return wrapper to edit
    }
    fun row(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
    fun error(error: Throwable): String = activity.getString(when ((error as? AiFailure)?.reason) {
        AiFailure.Reason.KEY -> R.string.ai_error_key
        AiFailure.Reason.MODEL -> R.string.ai_error_model
        AiFailure.Reason.RATE_LIMIT -> R.string.ai_error_rate
        AiFailure.Reason.DISABLED -> R.string.ai_need_setup
        AiFailure.Reason.NETWORK -> R.string.ai_error_network
        AiFailure.Reason.STORAGE -> R.string.ai_error_storage
        else -> R.string.ai_error_response
    })
}
