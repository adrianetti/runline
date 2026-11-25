package com.runline.app.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AppFlowTest {
    private val keys =
        AppFlow.screens
            .flatMap { screen -> listOfNotNull(screen.marker, screen.exit) + screen.steps.map { it.control } + screen.arrange }
            .toSet()

    private fun strings(folder: String): Map<String, String> {
        val files = File("src/main/res/$folder").listFiles { file -> file.name.startsWith("strings") }.orEmpty()
        return files
            .flatMap { file ->
                val nodes =
                    DocumentBuilderFactory
                        .newInstance()
                        .newDocumentBuilder()
                        .parse(file)
                        .getElementsByTagName("string")
                (0 until nodes.length).map {
                    nodes
                        .item(it)
                        .attributes
                        .getNamedItem("name")
                        .nodeValue to nodes.item(it).textContent
                }
            }.toMap()
    }

    @Test fun screenIdsAreUniqueAndEveryStepLeadsSomewhere() {
        val ids = AppFlow.screens.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            emptyList<String>(),
            AppFlow.screens
                .flatMap { it.steps }
                .map { it.to }
                .filter { it !in ids },
        )
    }

    @Test fun everyScreenIsReachableFromItsRoot() {
        assertEquals(emptyList<String>(), AppFlow.screens.filter { AppFlow.path(it.id) == null }.map { it.id })
    }

    @Test fun nothingIsMoreThanFourTapsFromItsTabAndScreensAreWithinThree() {
        val deep = AppFlow.screens.filter { FlowDiagram.depth(it.id) > MAX_TAPS }.map { it.id }
        val buried = AppFlow.screens.filter { it.kind == UiWindowKind.SCREEN && FlowDiagram.depth(it.id) > MAX_SCREEN_TAPS }.map { it.id }
        assertEquals(emptyList<String>(), deep + buried)
    }

    @Test fun everyDestinationHasOneEntryFromEachScreen() {
        val repeated =
            AppFlow.screens.flatMap { screen ->
                screen.steps
                    .groupBy { it.to }
                    .filterValues { it.size > 1 }
                    .keys
                    .map { "${screen.id}->$it" }
            }
        val sameControl =
            AppFlow.screens.flatMap { screen ->
                screen.steps
                    .groupBy { it.control }
                    .filterValues { it.size > 1 }
                    .keys
                    .map { "${screen.id}:$it" }
            }
        assertEquals(emptyList<String>(), repeated + sameControl)
    }

    @Test fun everyFullScreenAndSheetOutsideTheTabsHasAWayBack() {
        val trapped =
            AppFlow.screens
                .filter { it.kind == UiWindowKind.SCREEN || it.kind == UiWindowKind.SHEET }
                .filter { !it.isTab && it.state == FlowState.IDLE && it.exit == null }
                .map { it.id }
        assertEquals(emptyList<String>(), trapped)
    }

    @Test fun everyControlHasAnEnglishAndSpanishName() {
        val english = strings("values")
        val spanish = strings("values-es")
        assertEquals(emptyList<String>(), keys.filter { it !in english || it !in spanish })
    }

    @Test fun councilScreensExist() {
        assertTrue(AppFlow.COUNCIL.all { id -> AppFlow.screens.any { it.id == id } })
    }

    @Test fun flowDocumentMatchesTheGraph() {
        val diagram = FlowDiagram.mermaid(strings("values"))
        val document = File("../docs/FLOW.md").readText()
        assertTrue("docs/FLOW.md is out of date. Its diagram must be:\n$diagram", document.contains(diagram))
    }

    private companion object {
        const val MAX_TAPS = 4
        const val MAX_SCREEN_TAPS = 3
    }
}
