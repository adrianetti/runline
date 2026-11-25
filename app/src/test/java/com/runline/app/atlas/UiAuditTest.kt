package com.runline.app.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiAuditTest {
    private val environment = UiEnvironment("en", false, DENSITY, 1f, SCREEN_WIDTH, SCREEN_HEIGHT)

    private fun box(
        left: Int,
        top: Int,
        widthDp: Int,
        heightDp: Int,
    ) = UiRect(left, top, left + widthDp * DENSITY.toInt(), top + heightDp * DENSITY.toInt())

    private fun label(
        value: String,
        color: Int = INK,
        sizeSp: Float = BODY_SP,
        truncated: Boolean = false,
    ) = UiNode("TextView", UiRole.TEXT, box(0, 0, TARGET, TARGET), text = UiText(value, sizeSp * DENSITY, color, REGULAR, "sans-serif", truncated))

    private fun heading() = label("Settings").copy(role = UiRole.HEADING)

    private fun control(
        name: String?,
        left: Int = 0,
        widthDp: Int = TARGET,
        icon: UiIcon? = null,
        emphasis: UiEmphasis = UiEmphasis.NONE,
    ) = UiNode("Button", UiRole.BUTTON, box(left, 0, widthDp, TARGET), label = name, icon = icon, actionable = true, emphasis = emphasis)

    private fun screen(
        vararg children: UiNode,
        kind: UiWindowKind = UiWindowKind.SCREEN,
    ): UiScreen {
        val root = UiNode("Root", UiRole.GROUP, UiRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT), UiSurface(color = PAPER), children = children.toList())
        return UiScreen("test", environment, listOf(UiWindow(kind, root)))
    }

    private fun broken(
        screen: UiScreen,
        policy: AuditPolicy = AuditPolicy(),
    ) = UiAudit
        .audit(screen, policy)
        .findings
        .map { it.rule }
        .toSet()

    @Test fun cleanScreenScoresFullMarks() {
        val result = UiAudit.audit(screen(heading(), control("Start", emphasis = UiEmphasis.FILLED), control("Close", left = FAR)), AuditPolicy(exitName = "Close"))
        assertEquals(emptyList<AuditFinding>(), result.findings)
        assertEquals(AuditResult.FULL_SCORE, result.score(AuditGroup.ACCESSIBILITY))
        assertEquals(AuditResult.FULL_SCORE, result.score(AuditGroup.INTUITIVENESS))
    }

    @Test fun smallControlBreaksTouchTarget() {
        assertEquals(setOf(AuditRule.TOUCH_TARGET), broken(screen(heading(), control("Close", widthDp = SMALL))))
    }

    @Test fun clippedControlIsNotMeasured() {
        assertEquals(emptySet<AuditRule>(), broken(screen(heading(), control("Close", widthDp = SMALL).copy(clipped = true))))
    }

    @Test fun unnamedControlBreaksName() {
        assertEquals(setOf(AuditRule.NAME), broken(screen(heading(), control(null))))
    }

    @Test fun faintTextBreaksContrast() {
        assertEquals(setOf(AuditRule.CONTRAST), broken(screen(heading(), label("Saved on phone", color = FAINT))))
    }

    @Test fun faintLargeTextPassesTheLargeTextRatio() {
        assertEquals(emptySet<AuditRule>(), broken(screen(heading(), label("5:30", color = MID, sizeSp = HERO_SP))))
        assertEquals(setOf(AuditRule.CONTRAST), broken(screen(heading(), label("5:30", color = MID))))
    }

    @Test fun textOverImageryIsNotGuessed() {
        val map = UiNode("MapView", UiRole.MAP, UiRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT), UiSurface(opaqueImage = true), children = listOf(label("Credits", color = FAINT)))
        assertEquals(emptySet<AuditRule>(), broken(screen(heading(), map)))
    }

    @Test fun faintIconBreaksIconContrast() {
        val icon = UiIcon("a", FAINT, box(0, 0, TARGET, TARGET))
        assertEquals(setOf(AuditRule.ICON_CONTRAST), broken(screen(heading(), control("Share", icon = icon))))
    }

    @Test fun tinyTextBreaksTextSize() {
        assertEquals(setOf(AuditRule.TEXT_SIZE), broken(screen(heading(), label("Credits", sizeSp = TINY_SP))))
    }

    @Test fun cutTextBreaksTruncated() {
        assertEquals(setOf(AuditRule.TRUNCATED), broken(screen(heading(), label("Recording protec", truncated = true))))
    }

    @Test fun stackedControlsBreakOverlap() {
        assertEquals(setOf(AuditRule.OVERLAP), broken(screen(heading(), control("Share"), control("Export", left = HALF))))
        assertEquals(emptySet<AuditRule>(), broken(screen(heading(), control("Run"), control("Walk", left = TARGET * DENSITY.toInt() - 2))))
    }

    @Test fun mapUnderFloatingControlsIsNotAnOverlap() {
        val map = UiNode("MapView", UiRole.MAP, UiRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT), UiSurface(opaqueImage = true), label = "Map", actionable = true)
        assertEquals(emptySet<AuditRule>(), broken(screen(heading(), map, control("Share"))))
    }

    @Test fun screenWithoutHeadingBreaksHeading() {
        assertEquals(setOf(AuditRule.HEADING), broken(screen(control("Start"))))
        assertEquals(emptySet<AuditRule>(), broken(screen(control("Start"), kind = UiWindowKind.MENU)))
    }

    @Test fun twoFilledActionsBreakPrimaryAction() {
        val found = broken(screen(heading(), control("Start", emphasis = UiEmphasis.FILLED), control("Save", left = FAR, emphasis = UiEmphasis.FILLED)))
        assertEquals(setOf(AuditRule.PRIMARY_ACTION), found)
    }

    @Test fun sameNameTwiceBreaksDuplicateAction() {
        assertEquals(setOf(AuditRule.DUPLICATE_ACTION), broken(screen(heading(), control("Options"), control("options", left = FAR))))
    }

    @Test fun sameIconOnTwoActionsBreaksIconReuse() {
        val icon = UiIcon("a", INK, box(0, 0, TARGET, TARGET))
        assertEquals(setOf(AuditRule.ICON_REUSE), broken(screen(heading(), control("Download", icon = icon), control("Export", left = FAR, icon = icon))))
    }

    @Test fun paragraphsBreakCopyUnlessTheScreenIsForReading() {
        val long = label((1..LONG_WORDS).joinToString(" ") { "word" })
        val short = label((1..IntuitivenessRules.SENTENCE_WORDS).joinToString(" ") { "word" })
        assertEquals(setOf(AuditRule.COPY), broken(screen(heading(), long)))
        assertEquals(setOf(AuditRule.COPY), broken(screen(heading(), short, short)))
        assertEquals(emptySet<AuditRule>(), broken(screen(heading(), short)))
        assertEquals(emptySet<AuditRule>(), broken(screen(heading(), long, long), AuditPolicy(reading = true)))
    }

    @Test fun crowdedScreenBreaksChoices() {
        val many = (0..IntuitivenessRules.MAX_CHOICES).map { control("Action $it", left = it * FAR) }
        assertEquals(setOf(AuditRule.CHOICES), broken(screen(heading(), *many.toTypedArray())))
    }

    @Test fun missingWayBackBreaksExit() {
        assertEquals(setOf(AuditRule.EXIT), broken(screen(heading(), control("Start")), AuditPolicy(exitName = "Close")))
    }

    @Test fun scoreFallsWithEveryFinding() {
        val result = UiAudit.audit(screen(heading(), control(null, widthDp = SMALL)))
        assertTrue(result.score(AuditGroup.ACCESSIBILITY) < AuditResult.FULL_SCORE)
        assertEquals(AuditResult.FULL_SCORE, result.score(AuditGroup.INTUITIVENESS))
    }

    @Test fun everyRuleHasASeededFailure() {
        val seeded =
            setOf(
                AuditRule.TOUCH_TARGET,
                AuditRule.NAME,
                AuditRule.CONTRAST,
                AuditRule.ICON_CONTRAST,
                AuditRule.TEXT_SIZE,
                AuditRule.TRUNCATED,
                AuditRule.OVERLAP,
                AuditRule.HEADING,
                AuditRule.PRIMARY_ACTION,
                AuditRule.DUPLICATE_ACTION,
                AuditRule.ICON_REUSE,
                AuditRule.COPY,
                AuditRule.CHOICES,
                AuditRule.EXIT,
            )
        assertEquals(AuditRule.entries.toSet(), seeded)
    }

    private companion object {
        const val DENSITY = 2f
        const val SCREEN_WIDTH = 720
        const val SCREEN_HEIGHT = 1_600
        const val TARGET = 48
        const val SMALL = 40
        const val FAR = 120
        const val HALF = 40
        const val BODY_SP = 16f
        const val HERO_SP = 32f
        const val TINY_SP = 10f
        const val REGULAR = 400
        const val LONG_WORDS = 24
        val INK = 0xFF171717.toInt()
        val PAPER = 0xFFFAFAFA.toInt()
        val MID = 0xFF8A8A8A.toInt()
        val FAINT = 0xFFC8C8C8.toInt()
    }
}
