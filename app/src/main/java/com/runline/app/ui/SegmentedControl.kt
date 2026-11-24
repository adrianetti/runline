package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.runline.app.R

internal class SegmentedControl(
    context: Context,
    labels: List<String>,
    selected: Int,
    icons: List<Int> = emptyList(),
    private val onSelect: (Int) -> Unit,
) : MaterialButtonToggleGroup(context) {
    private val tall = icons.isNotEmpty()

    init {
        isSingleSelection = true
        isSelectionRequired = true
        labels.forEachIndexed { index, label ->
            addView(segment(label, icons.getOrNull(index)), LayoutParams(0, UiText.dp(context, if (tall) Measure.NAVIGATION else Measure.TOUCH), 1f))
        }
        check(getChildAt(selected.coerceIn(0, labels.lastIndex)).id)
        addOnButtonCheckedListener { _, id, checked -> if (checked) onSelect(indexOfChild(findViewById(id))) }
    }

    private fun segment(
        label: String,
        icon: Int?,
    ) = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
        id = View.generateViewId()
        text = label
        isAllCaps = false
        typeface = Type.emphasis()
        textSize = TextScale.LABEL.toFloat()
        maxLines = 1
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(UiText.dp(context, Space.XS), 0, UiText.dp(context, Space.XS), 0)
        cornerRadius = UiText.dp(context, Corner.CONTROL)
        strokeWidth = UiText.dp(context, Space.HAIRLINE)
        strokeColor = ColorStateList.valueOf(context.getColor(R.color.outline))
        backgroundTintList = states(context.getColor(R.color.primary), context.getColor(R.color.surface))
        setTextColor(states(context.getColor(R.color.on_primary), context.getColor(R.color.on_surface)))
        if (icon != null) {
            setIconResource(icon)
            iconGravity = MaterialButton.ICON_GRAVITY_TOP
            iconSize = UiText.dp(context, Measure.ICON)
            iconPadding = UiText.dp(context, Space.XS)
            iconTint = states(context.getColor(R.color.on_primary), context.getColor(R.color.on_surface))
        }
    }

    private fun states(
        checked: Int,
        normal: Int,
    ) = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(checked, normal))
}
