package com.runline.app.ui

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.runline.app.R

internal object Type {
    const val TABULAR = "tnum"
    private const val BODY_FAMILY = "sans-serif"
    private const val EMPHASIS_FAMILY = "sans-serif-medium"

    fun display(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.barlow_condensed_semibold) ?: Typeface.DEFAULT_BOLD

    fun displayMedium(context: Context): Typeface = ResourcesCompat.getFont(context, R.font.barlow_condensed_medium) ?: Typeface.DEFAULT

    fun body(): Typeface = Typeface.create(BODY_FAMILY, Typeface.NORMAL)

    fun emphasis(): Typeface = Typeface.create(EMPHASIS_FAMILY, Typeface.NORMAL)

    fun displaySize(requested: Int): Int =
        when {
            requested >= TextScale.DATA - SNAP -> requested
            requested >= TextScale.VALUE - SNAP -> TextScale.VALUE
            else -> TextScale.SECTION
        }

    fun bodySize(requested: Int): Int =
        when {
            requested <= TextScale.CAPTION -> TextScale.CAPTION
            requested <= TextScale.LABEL -> TextScale.LABEL
            else -> TextScale.BODY
        }

    fun number(view: TextView) {
        view.typeface = display(view.context)
        view.fontFeatureSettings = TABULAR
    }

    private const val SNAP = 4
}
