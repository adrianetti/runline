package com.runline.app.atlas

object IntuitivenessRules {
    const val MAX_PRIMARY_ACTIONS = 1
    const val MAX_CHOICES = 12
    const val SENTENCE_WORDS = 12
    const val MAX_SENTENCE_WORDS = 20
    const val MAX_SENTENCES = 1
    private val WHITESPACE = Regex("\\s+")
    private const val SUBJECT_LENGTH = 48

    fun apply(
        tally: AuditTally,
        window: UiWindow,
        placed: List<PlacedNode>,
        policy: AuditPolicy,
    ) {
        val controls = placed.filter { it.node.actionable && it.node.enabled && !it.insideControl }.map { it.node }
        primary(tally, placed)
        duplicates(tally, controls)
        icons(tally, controls)
        if (!policy.reading) copy(tally, placed)
        choices(tally, window, controls)
        exit(tally, controls, policy)
    }

    private fun primary(
        tally: AuditTally,
        placed: List<PlacedNode>,
    ) {
        val filled = placed.map { it.node }.filter { it.emphasis == UiEmphasis.FILLED && it.actionable }
        tally.check(AuditRule.PRIMARY_ACTION, filled.size <= MAX_PRIMARY_ACTIONS, "filled actions") {
            filled.joinToString { UiAudit.describe(it) }
        }
    }

    private fun duplicates(
        tally: AuditTally,
        controls: List<UiNode>,
    ) {
        val names = controls.map { it.spokenName().trim().lowercase() }.filter(String::isNotBlank)
        names.distinct().forEach { name ->
            val count = names.count { it == name }
            tally.check(AuditRule.DUPLICATE_ACTION, count == 1, name) { "$count controls" }
        }
    }

    private fun icons(
        tally: AuditTally,
        controls: List<UiNode>,
    ) {
        val byShape = controls.filter { it.icon != null && it.role != UiRole.LIST_ITEM }.groupBy { it.icon?.shape.orEmpty() }
        byShape.values.forEach { sharing ->
            val names = sharing.map { it.spokenName().trim().lowercase() }.distinct()
            tally.check(AuditRule.ICON_REUSE, names.size == 1, UiAudit.describe(sharing.first())) {
                "same icon as ${names.drop(1).joinToString()}"
            }
        }
    }

    private fun copy(
        tally: AuditTally,
        placed: List<PlacedNode>,
    ) {
        val sentences =
            placed
                .filter { !it.insideControl && !it.node.actionable }
                .mapNotNull { it.node.text?.value }
                .filter { words(it) >= SENTENCE_WORDS }
        tally.check(AuditRule.COPY, sentences.size <= MAX_SENTENCES, "sentences") { "${sentences.size} sentences" }
        sentences.forEach { sentence ->
            tally.check(AuditRule.COPY, words(sentence) <= MAX_SENTENCE_WORDS, sentence.take(SUBJECT_LENGTH)) { "${words(sentence)} words" }
        }
    }

    private fun choices(
        tally: AuditTally,
        window: UiWindow,
        controls: List<UiNode>,
    ) {
        if (window.kind == UiWindowKind.MENU) return
        val count = controls.count { it.role != UiRole.LIST_ITEM && it.role != UiRole.TAB }
        tally.check(AuditRule.CHOICES, count <= MAX_CHOICES, window.kind.name) { "$count controls" }
    }

    private fun exit(
        tally: AuditTally,
        controls: List<UiNode>,
        policy: AuditPolicy,
    ) {
        val name = policy.exitName ?: return
        tally.check(AuditRule.EXIT, controls.any { it.spokenName().trim().equals(name, ignoreCase = true) }, name) { "not on screen" }
    }

    fun words(text: String): Int = text.trim().split(WHITESPACE).count(String::isNotBlank)
}
