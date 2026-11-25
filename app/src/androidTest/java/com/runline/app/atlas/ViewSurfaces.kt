package com.runline.app.atlas

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.createBitmap
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable

internal object ViewSurfaces {
    private const val ICON_GRID = 16
    private const val INK_ALPHA = 96
    private const val OPAQUE = 255

    fun surface(view: View): UiSurface =
        when (view) {
            is MaterialButton -> {
                UiSurface(
                    color = view.backgroundTintList?.let { state(it, view) },
                    radiusPx = view.cornerRadius.toFloat(),
                    strokeColor = view.strokeColor?.takeIf { view.strokeWidth > 0 }?.let { state(it, view) },
                    strokeWidthPx = view.strokeWidth.toFloat(),
                )
            }

            is MaterialCardView -> {
                UiSurface(
                    color = view.cardBackgroundColor.defaultColor,
                    radiusPx = view.radius,
                    strokeColor = view.strokeColorStateList?.takeIf { view.strokeWidth > 0 }?.defaultColor,
                    strokeWidthPx = view.strokeWidth.toFloat(),
                )
            }

            else -> {
                view.background?.let(::drawable) ?: UiSurface()
            }
        }

    fun emphasis(
        view: View,
        surface: UiSurface,
    ): UiEmphasis {
        if (view !is MaterialButton || view.isCheckable) return UiEmphasis.NONE
        val primary = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)
        return when {
            surface.color == primary -> UiEmphasis.FILLED
            surface.strokeWidthPx > 0 -> UiEmphasis.OUTLINED
            else -> UiEmphasis.NONE
        }
    }

    fun icon(
        view: View,
        bounds: UiRect,
    ): UiIcon? {
        val drawable =
            when (view) {
                is MaterialButton -> view.icon
                is ImageView -> view.drawable
                is TextView -> compound(view)
                else -> null
            }
        val copy = drawable?.constantState?.newDrawable(view.resources)?.mutate() ?: return null
        copy.state = view.drawableState
        return sample(copy, bounds)
    }

    private fun compound(view: TextView): Drawable? {
        val relative = view.compoundDrawablesRelative.firstOrNull { it != null }
        return relative ?: view.compoundDrawables.firstOrNull { it != null }
    }

    private fun sample(
        copy: Drawable,
        bounds: UiRect,
    ): UiIcon? {
        val bitmap = createBitmap(ICON_GRID, ICON_GRID)
        copy.setBounds(0, 0, ICON_GRID, ICON_GRID)
        copy.draw(Canvas(bitmap))
        val pixels = IntArray(ICON_GRID * ICON_GRID)
        bitmap.getPixels(pixels, 0, ICON_GRID, 0, 0, ICON_GRID, ICON_GRID)
        bitmap.recycle()
        val ink = pixels.filter { Color.alpha(it) >= INK_ALPHA }
        if (ink.isEmpty()) return null
        val shape = pixels.map { Color.alpha(it) >= INK_ALPHA }.hashCode().toString(ICON_GRID)
        val color = Color.rgb(ink.sumOf(Color::red) / ink.size, ink.sumOf(Color::green) / ink.size, ink.sumOf(Color::blue) / ink.size)
        return UiIcon(shape, color, bounds)
    }

    private fun drawable(drawable: Drawable): UiSurface =
        when (drawable) {
            is ColorDrawable -> UiSurface(color = drawable.color)
            is MaterialShapeDrawable -> shape(drawable)
            is GradientDrawable -> gradient(drawable)
            is DrawableWrapper -> drawable.drawable?.let(::drawable) ?: UiSurface()
            is LayerDrawable -> layers(drawable)
            else -> UiSurface(opaqueImage = drawable.opacity == android.graphics.PixelFormat.OPAQUE)
        }

    private fun gradient(drawable: GradientDrawable): UiSurface {
        val radius = runCatching { drawable.cornerRadius }.getOrDefault(0f)
        return UiSurface(color = drawable.color?.defaultColor, radiusPx = radius)
    }

    private fun shape(drawable: MaterialShapeDrawable): UiSurface {
        val fill = drawable.fillColor?.getColorForState(drawable.state, drawable.fillColor?.defaultColor ?: Color.TRANSPARENT)
        val tinted = drawable.tintList?.getColorForState(drawable.state, Color.TRANSPARENT)?.takeIf { Color.alpha(it) > 0 }
        val stroke = drawable.strokeColor?.getColorForState(drawable.state, Color.TRANSPARENT)?.takeIf { drawable.strokeWidth > 0 }
        val faded = fill?.let { Color.argb(Color.alpha(it) * drawable.alpha / OPAQUE, Color.red(it), Color.green(it), Color.blue(it)) }
        return UiSurface(
            color = tinted ?: faded,
            radiusPx = drawable.topLeftCornerResolvedSize,
            strokeColor = stroke,
            strokeWidthPx = drawable.strokeWidth,
        )
    }

    private fun layers(drawable: LayerDrawable): UiSurface =
        (0 until drawable.numberOfLayers)
            .asSequence()
            .filter { drawable.getId(it) != android.R.id.mask }
            .mapNotNull { index -> drawable.getDrawable(index)?.let(::drawable) }
            .firstOrNull { it.color != null || it.opaqueImage }
            ?: UiSurface()

    private fun state(
        colors: ColorStateList,
        view: View,
    ) = colors.getColorForState(view.drawableState, colors.defaultColor)
}
