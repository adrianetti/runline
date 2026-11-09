package com.runline.app.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.withStyledAttributes
import androidx.core.view.forEach
import com.google.android.material.button.MaterialButton
import com.runline.app.R
import com.runline.app.core.RouteDifficulty
import com.runline.app.core.RouteImportNaming
import com.runline.app.data.RouteEntry
import kotlin.math.roundToInt

/** The Routes tab: imported routes first, then the open catalog. */
class RoutesView(
    context: Context,
    entries: List<RouteEntry>,
    private val selected: String?,
    private val stats: (String) -> com.runline.app.core.RouteStats?,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    onRefresh: () -> Unit,
    onAbout: () -> Unit,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(context.getString(R.string.v19_routes_title), 24, bold = true), LayoutParams(0, -2, 1f))
        heading.addView(MaterialButton(context, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            setIconResource(R.drawable.ic_more); iconPadding = 0; iconSize = dp(24); minWidth = 0; minimumWidth = 0
            minHeight = dp(48); minimumHeight = dp(48); setPadding(dp(12), 0, dp(12), 0)
            contentDescription = context.getString(R.string.v19_routes_options); tooltipText = contentDescription
            setOnClickListener { PopupMenu(context, this).apply {
                menu.add(0, 2, 1, R.string.v19_routes_refresh).setIcon(R.drawable.ic_refresh)
                menu.add(0, 3, 2, R.string.v19_routes_about).setIcon(R.drawable.ic_info)
                menu.forEach { it.icon?.mutate()?.setTint(context.getColor(R.color.on_surface)) }
                setForceShowIcon(true)
                setOnMenuItemClickListener { when (it.itemId) { 2 -> onRefresh(); else -> onAbout() }; true }
                show()
            } }
        }, LayoutParams(dp(48), dp(48)))
        addView(heading)
        addView(text(context.getString(R.string.v19_routes_intro), 14, muted = true), margin(top = 4, bottom = 8))
        addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = context.getString(R.string.route_import_add); isAllCaps = false; setIconResource(R.drawable.ic_upload)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; minHeight = dp(56); cornerRadius = dp(16)
            setOnClickListener { onImport() }
        }, margin(top = 8))
        val mine = entries.filter { it.imported }
        if (mine.isNotEmpty()) {
            addView(text(context.getString(R.string.v19_routes_imported_section), 16, bold = true), margin(top = 16, bottom = 4))
            mine.forEach { addRow(it, onOpen) }
        }
        addView(text(context.getString(R.string.v19_routes_catalog_section), 16, bold = true), margin(top = 16, bottom = 4))
        entries.filter { !it.imported }.forEach { addRow(it, onOpen) }
    }

    private fun addRow(entry: RouteEntry, onOpen: (String) -> Unit) {
        val locale = resources.configuration.locales[0]
        val km = String.format(locale, "%.1f", entry.distanceMeters / 1000)
        val computed = stats(entry.id)
        val time = computed?.estimatedMinutes?.let { RouteFormat.duration(context, it) }
        val stats = listOfNotNull(context.getString(R.string.v19_km, km),
            (computed?.ascentMeters ?: entry.ascentMeters)?.let { context.getString(R.string.v19_ascent_short, it.roundToInt()) }, time).joinToString(" · ")
        val meta = listOfNotNull(computed?.difficulty?.let { RouteFormat.difficulty(context, it) },
            if (entry.shape != null) RouteFormat.shape(context, entry.shape)
            else context.getString(if (entry.loop) R.string.v19_shape_loop else R.string.v19_shape_line), entry.region).joinToString(" · ")
        val row = LinearLayout(context).apply {
            orientation = VERTICAL; setPadding(dp(4), dp(18), dp(4), dp(18)); minimumHeight = dp(80)
            isClickable = true; isFocusable = true
            context.withStyledAttributes(attrs = intArrayOf(android.R.attr.selectableItemBackground)) { background = getDrawable(0) }
            setOnClickListener { onOpen(entry.id) }
            contentDescription = context.getString(R.string.v19_route_row_description, entry.name, stats, meta) +
                if (entry.id == selected) " " + context.getString(R.string.v20_routes_selected) else ""
        }
        row.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(entry.name, 20, bold = true).apply { maxLines = 2 }, LayoutParams(0, -2, 1f))
            // The route chosen for the next recording; Record shows it too.
            if (entry.id == selected) addView(android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_check); imageTintList = android.content.res.ColorStateList.valueOf(context.getColor(R.color.on_surface))
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LayoutParams(dp(24), dp(24)).apply { marginStart = dp(12) })
        })
        row.addView(text(stats, 16, bold = true), margin(top = 6))
        row.addView(text(meta, 14, muted = true), margin(top = 4))
        addView(row)
        addView(View(context).apply { setBackgroundColor(context.getColor(R.color.surface_variant)) }, LayoutParams(-1, dp(1)))
    }

    private fun text(value: String, size: Int, bold: Boolean = false, muted: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); fontFeatureSettings = "tnum"
        setTextColor(context.getColor(if (muted) R.color.on_surface_variant else R.color.on_surface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun margin(top: Int = 0, bottom: Int = 0) = LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

object RouteFormat {
    fun shape(context: Context, value: RouteImportNaming.Shape): String? = when (value) {
        RouteImportNaming.Shape.LOOP -> context.getString(R.string.v19_shape_loop)
        RouteImportNaming.Shape.OUT_AND_BACK -> context.getString(R.string.route_shape_out_back)
        RouteImportNaming.Shape.ROUTE -> null
    }
    fun duration(context: Context, minutes: Int): String = if (minutes >= 60)
        context.getString(R.string.v19_duration_hm, minutes / 60, minutes % 60) else context.getString(R.string.v19_duration_m, minutes)
    fun difficulty(context: Context, value: RouteDifficulty): String = context.getString(when (value) {
        RouteDifficulty.EASY -> R.string.v19_difficulty_easy; RouteDifficulty.MODERATE -> R.string.v19_difficulty_moderate
        RouteDifficulty.HARD -> R.string.v19_difficulty_hard; RouteDifficulty.VERY_HARD -> R.string.v19_difficulty_very_hard
    })
}
