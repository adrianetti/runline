package com.runline.app.atlas

import org.json.JSONArray
import org.json.JSONObject

data class UiRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width get() = right - left
    val height get() = bottom - top
    val isEmpty get() = width <= 0 || height <= 0

    fun intersects(other: UiRect) = left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun contains(other: UiRect) = left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    fun json(): JSONArray = JSONArray(listOf(left, top, right, bottom))
}

data class UiText(
    val value: String,
    val sizePx: Float,
    val color: Int,
    val weight: Int,
    val family: String,
    val truncated: Boolean = false,
) {
    fun json(): JSONObject =
        JSONObject()
            .put("value", value)
            .put("sizePx", sizePx.toDouble())
            .put("color", UiColor.hex(color))
            .put("weight", weight)
            .put("family", family)
            .put("truncated", truncated)
}

enum class UiRole { GROUP, TEXT, HEADING, BUTTON, TAB, TOGGLE, FIELD, SLIDER, IMAGE, MAP, GRAPHIC, LIST_ITEM }

enum class UiEmphasis { NONE, FILLED, OUTLINED }

data class UiSurface(
    val color: Int? = null,
    val radiusPx: Float = 0f,
    val strokeColor: Int? = null,
    val strokeWidthPx: Float = 0f,
    val opaqueImage: Boolean = false,
) {
    fun json(): JSONObject =
        JSONObject()
            .put("color", color?.let(UiColor::hex) ?: JSONObject.NULL)
            .put("radiusPx", radiusPx.toDouble())
            .put("strokeColor", strokeColor?.let(UiColor::hex) ?: JSONObject.NULL)
            .put("strokeWidthPx", strokeWidthPx.toDouble())
            .put("opaqueImage", opaqueImage)
}

data class UiIcon(
    val shape: String,
    val color: Int?,
    val bounds: UiRect,
) {
    fun json(): JSONObject =
        JSONObject()
            .put("shape", shape)
            .put("color", color?.let(UiColor::hex) ?: JSONObject.NULL)
            .put("bounds", bounds.json())
}

data class UiNode(
    val type: String,
    val role: UiRole,
    val bounds: UiRect,
    val surface: UiSurface = UiSurface(),
    val text: UiText? = null,
    val label: String? = null,
    val icon: UiIcon? = null,
    val actionable: Boolean = false,
    val enabled: Boolean = true,
    val emphasis: UiEmphasis = UiEmphasis.NONE,
    val clipped: Boolean = false,
    val children: List<UiNode> = emptyList(),
) {
    fun walk(): Sequence<UiNode> = sequenceOf(this) + children.asSequence().flatMap(UiNode::walk)

    fun spokenName(): String =
        label?.takeIf(String::isNotBlank)
            ?: walk().mapNotNull { it.text?.value?.takeIf(String::isNotBlank) ?: it.label?.takeIf(String::isNotBlank) }.joinToString(" ")

    fun json(): JSONObject =
        JSONObject()
            .put("type", type)
            .put("role", role.name)
            .put("bounds", bounds.json())
            .put("surface", surface.json())
            .put("text", text?.json() ?: JSONObject.NULL)
            .put("label", label ?: JSONObject.NULL)
            .put("icon", icon?.json() ?: JSONObject.NULL)
            .put("actionable", actionable)
            .put("enabled", enabled)
            .put("emphasis", emphasis.name)
            .put("clipped", clipped)
            .put("children", JSONArray(children.map(UiNode::json)))
}

enum class UiWindowKind { SCREEN, SHEET, DIALOG, MENU }

data class UiWindow(
    val kind: UiWindowKind,
    val root: UiNode,
) {
    fun json(): JSONObject = JSONObject().put("kind", kind.name).put("root", root.json())
}

data class UiEnvironment(
    val locale: String,
    val night: Boolean,
    val density: Float,
    val fontScale: Float,
    val widthPx: Int,
    val heightPx: Int,
) {
    fun dp(px: Int): Float = px / density

    fun sp(px: Float): Float = px / (density * fontScale)

    fun json(): JSONObject =
        JSONObject()
            .put("locale", locale)
            .put("night", night)
            .put("density", density.toDouble())
            .put("fontScale", fontScale.toDouble())
            .put("widthPx", widthPx)
            .put("heightPx", heightPx)
}

data class UiScreen(
    val id: String,
    val environment: UiEnvironment,
    val windows: List<UiWindow>,
) {
    val top: UiWindow get() = windows.last()

    fun json(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("environment", environment.json())
            .put("windows", JSONArray(windows.map(UiWindow::json)))
}

object UiColor {
    private const val CHANNEL = 0xFF
    private const val ALPHA_SHIFT = 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val LINEAR_LIMIT = 0.04045
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_SCALE = 1.055
    private const val GAMMA = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val FLARE = 0.05
    const val OPAQUE = 0xFF

    fun alpha(color: Int) = color ushr ALPHA_SHIFT and CHANNEL

    fun red(color: Int) = color ushr RED_SHIFT and CHANNEL

    fun green(color: Int) = color ushr GREEN_SHIFT and CHANNEL

    fun blue(color: Int) = color and CHANNEL

    fun argb(
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ) = (alpha shl ALPHA_SHIFT) or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue

    fun hex(color: Int): String = "#%02x%02x%02x%02x".format(red(color), green(color), blue(color), alpha(color))

    fun over(
        top: Int,
        bottom: Int,
    ): Int {
        val share = alpha(top) / CHANNEL.toDouble()

        fun mix(
            upper: Int,
            lower: Int,
        ) = Math.round(upper * share + lower * (1 - share)).toInt()
        return argb(OPAQUE, mix(red(top), red(bottom)), mix(green(top), green(bottom)), mix(blue(top), blue(bottom)))
    }

    fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / CHANNEL.toDouble()
            return if (value <= LINEAR_LIMIT) value / LINEAR_DIVISOR else Math.pow((value + GAMMA_OFFSET) / GAMMA_SCALE, GAMMA)
        }
        return RED_WEIGHT * linear(red(color)) + GREEN_WEIGHT * linear(green(color)) + BLUE_WEIGHT * linear(blue(color))
    }

    fun contrast(
        first: Int,
        second: Int,
    ): Double {
        val lighter = maxOf(luminance(first), luminance(second))
        val darker = minOf(luminance(first), luminance(second))
        return (lighter + FLARE) / (darker + FLARE)
    }
}
