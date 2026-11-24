package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import com.runline.app.R

internal object UiButtons {
    private const val LABEL_SPACING = 0.02f
    private const val FLOAT_ELEVATION = 3

    fun primary(
        context: Context,
        label: String,
        icon: Int = 0,
    ) = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply { action(this, label, icon) }

    fun outlined(
        context: Context,
        label: String,
        icon: Int = 0,
    ) = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        action(this, label, icon)
        strokeColor = ColorStateList.valueOf(context.getColor(R.color.outline))
    }

    fun icon(
        context: Context,
        icon: Int,
        label: String,
    ) = MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
        glyph(this, icon, label)
        iconTint = ColorStateList.valueOf(context.getColor(R.color.on_surface))
    }

    fun floating(
        context: Context,
        icon: Int,
        label: String,
    ) = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonStyle).apply {
        glyph(this, icon, label)
        cornerRadius = UiText.dp(context, Measure.TOUCH / 2)
        backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.surface))
        iconTint = ColorStateList.valueOf(context.getColor(R.color.on_surface))
        stateListAnimator = null
        elevation = UiText.dp(context, FLOAT_ELEVATION).toFloat()
    }

    private fun action(
        button: MaterialButton,
        label: String,
        icon: Int,
    ) {
        val context = button.context
        button.text = label
        button.isAllCaps = false
        button.typeface = Type.display(context)
        button.textSize = TextScale.SECTION.toFloat()
        button.letterSpacing = LABEL_SPACING
        button.insetTop = 0
        button.insetBottom = 0
        button.minHeight = UiText.dp(context, Measure.ACTION)
        button.minimumHeight = UiText.dp(context, Measure.ACTION)
        button.cornerRadius = UiText.dp(context, Corner.CONTROL)
        if (icon != 0) {
            button.setIconResource(icon)
            button.iconSize = UiText.dp(context, Measure.ICON)
            button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            button.iconPadding = UiText.dp(context, Space.S)
        }
    }

    private fun glyph(
        button: MaterialButton,
        icon: Int,
        label: String,
    ) {
        val context = button.context
        val side = UiText.dp(context, Measure.TOUCH)
        button.setIconResource(icon)
        button.iconSize = UiText.dp(context, Measure.ICON)
        button.iconPadding = 0
        button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        button.insetTop = 0
        button.insetBottom = 0
        button.minWidth = side
        button.minimumWidth = side
        button.minHeight = side
        button.minimumHeight = side
        button.setPadding(0, 0, 0, 0)
        button.contentDescription = label
        button.tooltipText = label
    }
}
