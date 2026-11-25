package com.runline.app.ui

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.runline.app.R

internal class DataFieldView(
    context: Context,
    private val label: String,
    val number: TextView,
    unit: String = "",
) : LinearLayout(context) {
    private val name = UiText.label(context, label)
    private val large = number.textSize >= UiText.dp(context, TextScale.DATA)
    private val suffix = UiText.styled(context, unit, if (large) TextScale.BODY else TextScale.LABEL, muted = true)
    private val gap = UiText.dp(context, Space.XS)
    private var total = ""

    init {
        orientation = VERTICAL
        name.maxLines = 1
        suffix.maxLines = 1
        suffix.visibility = if (unit.isBlank()) GONE else VISIBLE
        addView(name, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val line = LinearLayout(context).apply { gravity = Gravity.BOTTOM }
        line.addView(number, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        line.addView(suffix, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = gap })
        line.isBaselineAligned = true
        addView(
            line,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = UiText.dp(context, Space.XS / 2) },
        )
    }

    constructor(context: Context, label: String, value: String, unit: String = "", size: Int = TextScale.VALUE) :
        this(context, label, UiText.value(context, value, size), unit)

    constructor(context: Context) : this(context, "", "")

    fun set(value: String) {
        if (number.text.toString() != value) number.text = value
    }

    fun outOf(value: String) {
        total = value
        place(width)
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { place(w) }
    }

    private fun place(available: Int) {
        if (total.isBlank()) return
        val beside = context.getString(R.string.v26_out_of, total)
        val needed = number.paint.measureText(total) + gap + suffix.paint.measureText(beside)
        val fits = available <= 0 || needed <= available
        val caption = if (fits) label else context.getString(R.string.v26_time_of, label, total)
        if (name.text.toString() != caption) name.text = caption
        if (suffix.text.toString() != beside) suffix.text = beside
        suffix.visibility = if (fits) VISIBLE else GONE
    }
}
