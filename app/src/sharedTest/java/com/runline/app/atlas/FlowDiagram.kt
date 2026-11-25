package com.runline.app.atlas

object FlowDiagram {
    private const val FORMAT_ARGUMENT = "%(\\d+\\$)?[sd]"
    private val ARGUMENT = Regex(FORMAT_ARGUMENT)
    private val PUNCTUATION = Regex("[\"\\[\\]{}|<>]")

    fun depth(id: String): Int = AppFlow.path(id)?.size ?: Int.MAX_VALUE

    fun mermaid(labels: Map<String, String>): String {
        val lines = mutableListOf("flowchart LR")
        FlowState.entries.forEach { state ->
            lines += "    subgraph ${state.name.lowercase()}"
            AppFlow.screens.filter { it.state == state }.forEach { screen ->
                lines += "        ${screen.id}${shape(screen, label(labels, screen.marker))}"
            }
            lines += "    end"
        }
        AppFlow.screens.forEach { screen ->
            screen.steps.forEach { step -> lines += "    ${screen.id} -->|${label(labels, step.control)}| ${step.to}" }
        }
        return lines.joinToString("\n")
    }

    private fun shape(
        screen: FlowScreen,
        label: String,
    ) = when (screen.kind) {
        UiWindowKind.SCREEN -> "[\"${screen.id}: $label\"]"
        UiWindowKind.SHEET -> "([\"${screen.id}: $label\"])"
        UiWindowKind.DIALOG -> "{{\"${screen.id}: $label\"}}"
        UiWindowKind.MENU -> "[/\"${screen.id}: $label\"/]"
    }

    private fun label(
        labels: Map<String, String>,
        key: String,
    ) = labels.getValue(key).replace(ARGUMENT, "…").replace(PUNCTUATION, "").trim()
}
