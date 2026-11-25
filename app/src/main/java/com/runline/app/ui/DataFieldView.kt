package com.runline.app.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

internal class DataFieldView(
    context: Context,
    label: String,
    val number: TextView,
    unit: String = "",
) : LinearLayout(context) {
    private val name = UiText.label(context, label)

    init {
        orientation = VERTICAL
        name.maxLines = 1
        addView(name, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val line = LinearLayout(context).apply { gravity = Gravity.BOTTOM }
        line.addView(number, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        if (unit.isNotBlank()) {
            val large = number.textSize >= UiText.dp(context, TextScale.DATA)
            val suffix = UiText.styled(context, unit, if (large) TextScale.BODY else TextScale.LABEL, muted = true)
            line.addView(
                suffix,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = UiText.dp(context, Space.XS) },
            )
        }
        line.isBaselineAligned = true
        addView(
            line,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = UiText.dp(context, Space.XS / 2) },
        )
    }

    constructor(context: Context, label: String, value: String, unit: String = "", size: Int = TextScale.VALUE) :
        this(context, label, UiText.value(context, value, size), unit)

    constructor(context: Context) : this(context, "", "")

    fun relabel(value: String) {
        name.text = value
    }

    fun set(value: String) {
        if (number.text.toString() != value) number.text = value
    }
}
