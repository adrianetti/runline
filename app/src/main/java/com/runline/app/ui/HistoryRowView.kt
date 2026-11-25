package com.runline.app.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import com.runline.app.R

internal data class HistoryRow(
    val title: String,
    val date: String,
    val pace: String,
    val summary: String,
    val description: String,
)

internal class HistoryRowView(
    context: Context,
    row: HistoryRow,
    open: () -> Unit,
) : LinearLayout(context) {
    init {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = UiText.dp(context, Measure.NAVIGATION)
        setPadding(0, UiText.dp(context, Space.M), 0, UiText.dp(context, Space.M))
        isClickable = true
        isFocusable = true
        contentDescription = row.description
        background =
            TypedValue().let { value ->
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                AppCompatResources.getDrawable(context, value.resourceId)
            }
        setOnClickListener { open() }
        val name = LinearLayout(context).apply { orientation = VERTICAL }
        name.addView(UiText.styled(context, row.title, TextScale.SECTION, bold = true))
        name.addView(UiText.label(context, row.date), wrap().apply { topMargin = UiText.dp(context, Space.XS) })
        addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val result =
            LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.END
            }
        val pace = LinearLayout(context).apply { gravity = Gravity.BOTTOM }
        pace.addView(UiText.value(context, row.pace))
        pace.addView(
            UiText.label(context, context.getString(R.string.v26_unit_per_km)),
            wrap().apply { marginStart = UiText.dp(context, Space.XS) },
        )
        result.addView(pace, wrap())
        result.addView(UiText.label(context, row.summary), wrap())
        addView(result, wrap().apply { marginStart = UiText.dp(context, Space.L) })
    }

    constructor(context: Context) : this(context, HistoryRow("", "", "", "", ""), {})

    private fun wrap() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
}
