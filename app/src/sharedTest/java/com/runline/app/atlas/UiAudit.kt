package com.runline.app.atlas

import org.json.JSONArray
import org.json.JSONObject

enum class AuditGroup { ACCESSIBILITY, INTUITIVENESS }

enum class AuditRule(
    val group: AuditGroup,
    val promise: String,
) {
    TOUCH_TARGET(AuditGroup.ACCESSIBILITY, "Every control is at least 48dp wide and 48dp high."),
    NAME(AuditGroup.ACCESSIBILITY, "Every control has a spoken name."),
    CONTRAST(AuditGroup.ACCESSIBILITY, "Text contrast is at least 4.5:1, or 3:1 for large text."),
    ICON_CONTRAST(AuditGroup.ACCESSIBILITY, "Icons on controls have a contrast of at least 3:1."),
    TEXT_SIZE(AuditGroup.ACCESSIBILITY, "No text is smaller than 12sp."),
    TRUNCATED(AuditGroup.ACCESSIBILITY, "No text is cut off."),
    OVERLAP(AuditGroup.ACCESSIBILITY, "Controls never overlap."),
    HEADING(AuditGroup.ACCESSIBILITY, "Every screen and sheet has a heading."),
    PRIMARY_ACTION(AuditGroup.INTUITIVENESS, "A screen has at most one filled primary action."),
    DUPLICATE_ACTION(AuditGroup.INTUITIVENESS, "No two controls on a screen share a name."),
    ICON_REUSE(AuditGroup.INTUITIVENESS, "No two controls on a screen share an icon."),
    COPY(AuditGroup.INTUITIVENESS, "A screen explains itself in at most one short sentence."),
    CHOICES(AuditGroup.INTUITIVENESS, "A screen offers at most twelve controls at once."),
    EXIT(AuditGroup.INTUITIVENESS, "Every screen that is not a tab shows its way back."),
}

data class AuditFinding(
    val rule: AuditRule,
    val subject: String,
    val detail: String,
) {
    fun json(): JSONObject = JSONObject().put("rule", rule.name).put("subject", subject).put("detail", detail)
}

data class AuditPolicy(
    val reading: Boolean = false,
    val exitName: String? = null,
)

data class AuditResult(
    val screen: String,
    val checks: Map<AuditRule, Int>,
    val findings: List<AuditFinding>,
) {
    fun score(group: AuditGroup): Int {
        val total = checks.filterKeys { it.group == group }.values.sum()
        val failed = findings.count { it.rule.group == group }
        return if (total == 0) FULL_SCORE else (FULL_SCORE * (total - failed).coerceAtLeast(0)) / total
    }

    fun json(): JSONObject =
        JSONObject()
            .put("screen", screen)
            .put("accessibility", score(AuditGroup.ACCESSIBILITY))
            .put("intuitiveness", score(AuditGroup.INTUITIVENESS))
            .put("checks", checks.values.sum())
            .put("findings", JSONArray(findings.map(AuditFinding::json)))

    companion object {
        const val FULL_SCORE = 100
    }
}

class AuditTally {
    private val checks = mutableMapOf<AuditRule, Int>()
    private val findings = mutableListOf<AuditFinding>()

    fun check(
        rule: AuditRule,
        passed: Boolean,
        subject: String,
        detail: () -> String,
    ) {
        checks[rule] = (checks[rule] ?: 0) + 1
        if (!passed) findings += AuditFinding(rule, subject, detail())
    }

    fun result(screen: String) = AuditResult(screen, checks.toMap(), findings.toList())
}

data class PlacedNode(
    val node: UiNode,
    val backdrop: Int?,
    val insideControl: Boolean,
)

object UiAudit {
    fun audit(
        screen: UiScreen,
        policy: AuditPolicy = AuditPolicy(),
    ): AuditResult {
        val tally = AuditTally()
        val window = screen.top
        val placed = place(window.root, null, false).toList()
        AccessibilityRules.apply(tally, screen.environment, window, placed)
        IntuitivenessRules.apply(tally, window, placed, policy)
        return tally.result(screen.id)
    }

    fun place(
        node: UiNode,
        backdrop: Int?,
        insideControl: Boolean,
    ): Sequence<PlacedNode> {
        val behind = if (node.surface.opaqueImage) null else backdrop
        val own = node.surface.color
        val resolved =
            when {
                own == null -> behind
                UiColor.alpha(own) == UiColor.OPAQUE -> own
                behind == null -> null
                else -> UiColor.over(own, behind)
            }
        val nested = insideControl || node.actionable
        return sequenceOf(PlacedNode(node, resolved, insideControl)) + node.children.asSequence().flatMap { place(it, resolved, nested) }
    }

    fun describe(node: UiNode): String = node.spokenName().ifBlank { node.type }.take(SUBJECT_LENGTH)

    private const val SUBJECT_LENGTH = 48
}
