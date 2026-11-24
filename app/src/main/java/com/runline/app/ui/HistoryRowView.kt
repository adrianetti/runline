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
    val distance: String,
    val stats: String,
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
        name.addView(UiText.label(context, row.date), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = UiText.dp(context, Space.XS) })
        addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val result = LinearLayout(context).apply { orientation = VERTICAL }
        result.addView(
            DataFieldView(context, "", row.distance, context.getString(R.string.island_km)).apply {
                getChildAt(0).visibility = GONE
                gravity = Gravity.END
            },
        )
        result.addView(UiText.label(context, row.stats).apply { gravity = Gravity.END }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(result, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = UiText.dp(context, Space.L) })
    }
}
