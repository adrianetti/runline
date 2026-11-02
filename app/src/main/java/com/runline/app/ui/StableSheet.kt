package com.runline.app.ui

import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.roundToInt

/** Scrolling edits content; only Back or the visible close control dismisses it. */
internal object StableSheet {
    fun configure(dialog: BottomSheetDialog, activity: AppCompatActivity, heightFraction: Float = .92f) {
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { view ->
                val available = activity.window.decorView.height.takeIf { it > 0 }
                    ?: activity.resources.displayMetrics.heightPixels
                val preferredHeight = (available * heightFraction).roundToInt()
                val container = view.parent as? View
                fun fitAvailableHeight() {
                    val height = minOf(preferredHeight, container?.height?.takeIf { it > 0 } ?: preferredHeight)
                    if (view.layoutParams.height != height) {
                        view.layoutParams = view.layoutParams.apply { this.height = height }
                    }
                }
                // The dialog window shrinks for the keyboard. Keep fixed actions inside
                // that visible window instead of leaving them below the IME.
                val resize = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitAvailableHeight() }
                container?.addOnLayoutChangeListener(resize)
                view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit
                    override fun onViewDetachedFromWindow(v: View) {
                        container?.removeOnLayoutChangeListener(resize)
                        v.removeOnAttachStateChangeListener(this)
                    }
                })
                fitAvailableHeight()
                BottomSheetBehavior.from(view).apply {
                    isDraggable = false
                    skipCollapsed = true
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }
}
