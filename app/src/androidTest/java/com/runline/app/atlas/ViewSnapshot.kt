package com.runline.app.atlas

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.widget.AbsListView
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout

internal object ViewSnapshot {
    private const val NORMAL_WEIGHT = 400
    private const val BOLD_WEIGHT = 700
    private const val FIT_SLACK_PX = 2
    private const val WEIGHT_API = 28
    private const val WINDOWS_API = 29
    private const val FAMILY_API = 34
    private val WEIGHT_SUFFIX = Regex("-(thin|light|medium|semibold|black)$")

    fun windows(fallback: View): List<UiWindow> {
        val roots = if (Build.VERSION.SDK_INT >= WINDOWS_API) WindowInspector.getGlobalWindowViews() else listOf(fallback)
        return roots.filter { it.isShown && it.width > 0 }.mapNotNull { root -> node(root, false)?.let { UiWindow(kind(root), it) } }
    }

    private fun kind(root: View): UiWindowKind {
        val type = (root.layoutParams as? WindowManager.LayoutParams)?.type
        return when {
            type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION -> UiWindowKind.SCREEN
            root.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) != null -> UiWindowKind.SHEET
            type == WindowManager.LayoutParams.TYPE_APPLICATION -> UiWindowKind.DIALOG
            else -> UiWindowKind.MENU
        }
    }

    private fun node(
        view: View,
        insideMap: Boolean,
    ): UiNode? {
        val visible = Rect()
        if (insideMap || view.visibility != View.VISIBLE || view.alpha == 0f || !view.getGlobalVisibleRect(visible)) return null
        val origin = IntArray(2).also(view.rootView::getLocationOnScreen)
        visible.offset(origin[0], origin[1])
        val bounds = UiRect(visible.left, visible.top, visible.right, visible.bottom)
        val role = role(view)
        val surface = if (role == UiRole.MAP) UiSurface(opaqueImage = true) else ViewSurfaces.surface(view)
        val children = (view as? ViewGroup)?.takeIf { role != UiRole.MAP }?.let { group -> (0 until group.childCount).mapNotNull { node(group.getChildAt(it), false) } }
        return UiNode(
            type =
                view.javaClass.simpleName.ifBlank {
                    view.javaClass.superclass
                        ?.simpleName
                        .orEmpty()
                },
            role = role,
            bounds = bounds,
            surface = surface,
            text = (view as? TextView)?.let(::text),
            label = label(view),
            icon = ViewSurfaces.icon(view, bounds),
            actionable = view.isImportantForAccessibility && (view.isClickable || role in setOf(UiRole.FIELD, UiRole.SLIDER, UiRole.LIST_ITEM)),
            enabled = view.isEnabled,
            emphasis = ViewSurfaces.emphasis(view, surface),
            clipped = visible.width() < view.width || visible.height() < view.height,
            children = children.orEmpty(),
        )
    }

    private fun role(view: View): UiRole =
        when {
            view.javaClass.name.startsWith("org.maplibre") -> UiRole.MAP
            view is CompoundButton -> UiRole.TOGGLE
            view is EditText -> UiRole.FIELD
            view is SeekBar || view is Slider -> UiRole.SLIDER
            view is Button || view is ImageButton -> UiRole.BUTTON
            view is TabLayout.TabView -> UiRole.TAB
            view.parent is AbsListView -> UiRole.LIST_ITEM
            view is TextView -> if (heading(view)) UiRole.HEADING else UiRole.TEXT
            view is ImageView -> UiRole.IMAGE
            view is ViewGroup -> UiRole.GROUP
            else -> UiRole.GRAPHIC
        }

    private fun heading(view: View) = Build.VERSION.SDK_INT >= WEIGHT_API && view.isAccessibilityHeading

    private fun label(view: View): String? =
        view.contentDescription?.toString()
            ?: (view as? EditText)?.let { field ->
                generateSequence(field.parent) { it.parent }
                    .filterIsInstance<TextInputLayout>()
                    .firstOrNull()
                    ?.hint
                    ?.toString() ?: field.hint?.toString()
            }

    private fun text(view: TextView): UiText {
        val face = view.typeface
        val weight =
            when {
                face != null && Build.VERSION.SDK_INT >= WEIGHT_API -> face.weight
                face?.isBold == true -> BOLD_WEIGHT
                else -> NORMAL_WEIGHT
            }
        val family = if (face != null && Build.VERSION.SDK_INT >= FAMILY_API) face.systemFontFamilyName else null
        return UiText(
            value = view.text?.toString().orEmpty(),
            sizePx = view.textSize,
            color = view.currentTextColor,
            weight = if (view.paint.isFakeBoldText) BOLD_WEIGHT else weight,
            family = (family ?: "sans-serif").replace(WEIGHT_SUFFIX, ""),
            truncated = view !is EditText && truncated(view),
        )
    }

    private fun truncated(view: TextView): Boolean {
        val layout = view.layout ?: return false
        val lines = 0 until layout.lineCount
        val room = view.width - view.compoundPaddingLeft - view.compoundPaddingRight + FIT_SLACK_PX
        val height = view.height - view.compoundPaddingTop - view.compoundPaddingBottom + FIT_SLACK_PX
        return lines.any { layout.getEllipsisCount(it) > 0 || layout.getLineWidth(it) > room } || layout.height > height
    }
}
