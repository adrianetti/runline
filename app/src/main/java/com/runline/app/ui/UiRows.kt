package com.runline.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Matrix
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.materialswitch.MaterialSwitch
import com.runline.app.R

internal object UiRows {
    fun hairline(context: Context) =
        View(context).apply {
            setBackgroundColor(context.getColor(R.color.hairline))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, UiText.dp(context, Space.HAIRLINE))
        }

    fun navigation(
        context: Context,
        icon: Int,
        label: String,
        value: String = "",
        action: () -> Unit,
    ) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = UiText.dp(context, Measure.ACTION)
        isClickable = true
        isFocusable = true
        background = ripple(context)
        setOnClickListener { action() }
        addView(glyph(context, icon, R.color.on_surface), square(context))
        addView(UiText.body(context, label), weighted().apply { marginStart = UiText.dp(context, Space.L) })
        if (value.isNotBlank()) {
            addView(
                UiText.label(context, value).apply { gravity = Gravity.END },
                weighted().apply { marginStart = UiText.dp(context, Space.M) },
            )
        }
        addView(
            glyph(context, R.drawable.ic_chevron, R.color.on_surface_variant).apply {
                scaleType = ImageView.ScaleType.MATRIX
                imageMatrix = Matrix().apply { setTranslate(-UiText.dp(context, Space.S).toFloat(), 0f) }
            },
            LinearLayout.LayoutParams(UiText.dp(context, Space.S), UiText.dp(context, Measure.ICON)).apply {
                marginStart = UiText.dp(context, Space.S)
            },
        )
    }

    fun toggle(
        context: Context,
        icon: Int,
        label: String,
        checked: Boolean,
        changed: (Boolean) -> Unit,
    ) = MaterialSwitch(context).apply {
        text = label
        isChecked = checked
        typeface = Type.body()
        textSize = TextScale.BODY.toFloat()
        setTextColor(context.getColor(R.color.on_surface))
        minHeight = UiText.dp(context, Measure.ACTION)
        compoundDrawablePadding = UiText.dp(context, Space.L)
        val drawable = AppCompatResources.getDrawable(context, icon)?.mutate()
        drawable?.setTint(context.getColor(R.color.on_surface))
        drawable?.setBounds(0, 0, UiText.dp(context, Measure.ICON), UiText.dp(context, Measure.ICON))
        setCompoundDrawablesRelative(drawable, null, null, null)
        setOnCheckedChangeListener { _, value -> changed(value) }
    }

    private fun glyph(
        context: Context,
        icon: Int,
        tint: Int,
    ) = ImageView(context).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(context.getColor(tint))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun square(context: Context) = LinearLayout.LayoutParams(UiText.dp(context, Measure.ICON), UiText.dp(context, Measure.ICON))

    private fun ripple(context: Context) =
        TypedValue().let { value ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            AppCompatResources.getDrawable(context, value.resourceId)
        }
}
