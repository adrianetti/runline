package com.runline.app.atlas

object AccessibilityRules {
    const val MIN_TARGET_DP = 48f
    const val MIN_TEXT_SP = 12f
    const val TEXT_CONTRAST = 4.5
    const val LARGE_TEXT_CONTRAST = 3.0
    const val ICON_CONTRAST = 3.0
    private const val LARGE_TEXT_SP = 18f
    private const val LARGE_BOLD_TEXT_SP = 14f
    private const val BOLD_WEIGHT = 700
    private const val TOLERANCE = 0.5f

    fun apply(
        tally: AuditTally,
        environment: UiEnvironment,
        window: UiWindow,
        placed: List<PlacedNode>,
    ) {
        val controls = placed.filter { it.node.actionable && it.node.enabled }
        controls.filter { !it.node.clipped }.forEach { target(tally, environment, it.node) }
        controls.forEach { name(tally, it.node) }
        controls.forEach { icon(tally, it) }
        placed.filter { it.node.text != null && it.node.enabled }.forEach { text(tally, environment, it) }
        overlaps(tally, controls.filter { !it.insideControl && it.node.role != UiRole.MAP }.map { it.node })
        heading(tally, window, placed)
    }

    private fun target(
        tally: AuditTally,
        environment: UiEnvironment,
        node: UiNode,
    ) {
        val width = environment.dp(node.bounds.width)
        val height = environment.dp(node.bounds.height)
        tally.check(AuditRule.TOUCH_TARGET, width + TOLERANCE >= MIN_TARGET_DP && height + TOLERANCE >= MIN_TARGET_DP, UiAudit.describe(node)) {
            "%.0f x %.0f dp".format(width, height)
        }
    }

    private fun name(
        tally: AuditTally,
        node: UiNode,
    ) {
        tally.check(AuditRule.NAME, node.spokenName().isNotBlank(), node.type) { "no text and no description" }
    }

    private fun icon(
        tally: AuditTally,
        placed: PlacedNode,
    ) {
        val color = placed.node.icon?.color ?: return
        val backdrop = placed.backdrop ?: return
        val ratio = UiColor.contrast(UiColor.over(color, backdrop), backdrop)
        tally.check(AuditRule.ICON_CONTRAST, ratio >= ICON_CONTRAST, UiAudit.describe(placed.node)) {
            "%.2f:1, %s on %s".format(ratio, UiColor.hex(color), UiColor.hex(backdrop))
        }
    }

    private fun text(
        tally: AuditTally,
        environment: UiEnvironment,
        placed: PlacedNode,
    ) {
        val text = placed.node.text ?: return
        if (text.value.isBlank()) return
        val subject = text.value.take(SUBJECT_LENGTH)
        val size = environment.sp(text.sizePx)
        tally.check(AuditRule.TEXT_SIZE, size + TOLERANCE >= MIN_TEXT_SP, subject) { "%.1f sp".format(size) }
        tally.check(AuditRule.TRUNCATED, !text.truncated, subject) { "does not fit its box" }
        val backdrop = placed.backdrop ?: return
        val large = size >= LARGE_TEXT_SP || (size >= LARGE_BOLD_TEXT_SP && text.weight >= BOLD_WEIGHT)
        val needed = if (large) LARGE_TEXT_CONTRAST else TEXT_CONTRAST
        val ratio = UiColor.contrast(UiColor.over(text.color, backdrop), backdrop)
        tally.check(AuditRule.CONTRAST, ratio >= needed, subject) {
            "%.2f:1, needs %.1f:1, %s on %s".format(ratio, needed, UiColor.hex(text.color), UiColor.hex(backdrop))
        }
    }

    private fun overlaps(
        tally: AuditTally,
        controls: List<UiNode>,
    ) {
        controls.forEachIndexed { index, first ->
            controls.drop(index + 1).forEach { second ->
                tally.check(AuditRule.OVERLAP, !first.bounds.intersects(second.bounds), UiAudit.describe(first)) {
                    "overlaps ${UiAudit.describe(second)}"
                }
            }
        }
    }

    private fun heading(
        tally: AuditTally,
        window: UiWindow,
        placed: List<PlacedNode>,
    ) {
        if (window.kind != UiWindowKind.SCREEN && window.kind != UiWindowKind.SHEET) return
        tally.check(AuditRule.HEADING, placed.any { it.node.role == UiRole.HEADING }, window.kind.name) { "no heading" }
    }

    private const val SUBJECT_LENGTH = 48
}
