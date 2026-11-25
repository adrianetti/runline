package com.runline.app.ui

import android.content.Context
import android.os.Build
import android.util.TypedValue
import android.widget.TextView
import com.runline.app.R
import kotlin.math.roundToInt

internal object UiText {
    private const val HEADING_API = 28
    private const val LINE_GAP_DP = 2

    fun styled(
        context: Context,
        value: String,
        size: Int,
        bold: Boolean = false,
        muted: Boolean = false,
    ) = TextView(context).apply {
        text = value
        setTextColor(context.getColor(if (muted) R.color.on_surface_variant else R.color.on_surface))
        restyle(this, size, bold)
    }

    fun restyle(
        view: TextView,
        size: Int,
        bold: Boolean,
    ) {
        view.includeFontPadding = false
        if (bold) {
            view.typeface = Type.display(view.context)
            view.fontFeatureSettings = Type.TABULAR
            view.textSize = Type.displaySize(size).toFloat()
        } else {
            view.typeface = Type.body()
            view.textSize = Type.bodySize(size).toFloat()
            val gap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LINE_GAP_DP.toFloat(), view.resources.displayMetrics)
            view.setLineSpacing(gap, 1f)
        }
    }

    fun title(
        context: Context,
        value: String,
    ) = styled(context, value, TextScale.VALUE, bold = true).apply { heading(this) }

    fun section(
        context: Context,
        value: String,
    ) = styled(context, value, TextScale.SECTION, bold = true).apply { heading(this) }

    fun value(
        context: Context,
        value: String,
        size: Int = TextScale.VALUE,
    ) = styled(context, value, size, bold = true)

    fun label(
        context: Context,
        value: String,
    ) = styled(context, value, TextScale.LABEL, muted = true)

    fun caption(
        context: Context,
        value: String,
    ) = styled(context, value, TextScale.CAPTION, muted = true)

    fun body(
        context: Context,
        value: String,
    ) = styled(context, value, TextScale.BODY)

    fun heading(view: TextView) {
        if (Build.VERSION.SDK_INT >= HEADING_API) view.isAccessibilityHeading = true
    }

    fun dp(
        context: Context,
        value: Int,
    ) = (value * context.resources.displayMetrics.density).roundToInt()
}
