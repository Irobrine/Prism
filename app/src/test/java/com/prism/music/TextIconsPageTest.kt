package com.prism.music

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.prism.music.data.prefs.ButtonShape
import com.prism.music.data.prefs.Corners
import com.prism.music.data.prefs.CoverShape
import com.prism.music.data.prefs.FontChoice
import com.prism.music.data.prefs.ThemeMode
import com.prism.music.data.prefs.TypeSpacing
import com.prism.music.data.prefs.TypeWeight
import com.prism.music.ui.screens.settings.SettingsPageId
import com.prism.music.ui.screens.settings.SettingsRowChanged
import com.prism.music.ui.screens.settings.SettingsTags
import com.prism.music.ui.screens.settings.index
import com.prism.music.ui.screens.settings.lookKeys
import com.prism.music.ui.screens.settings.oldGroups
import com.prism.music.ui.theme.AppIcon
import com.prism.music.ui.theme.AppIcons
import com.prism.music.ui.theme.IconFill
import com.prism.music.ui.theme.IconPack
import com.prism.music.ui.theme.IconStyle
import com.prism.music.ui.theme.IconWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.8.4, Settings → Text & icons rebuilt with the kit (plan §2.3–2.7, the ledger's Text & icons table): four groups of
 * the five rows, every example-card choice in a pick sheet, icon weight and rounded as one row, "App text size", and a
 * control for every stored setting the ledger gives the page. On a window tall enough to hold the whole page (the list
 * is lazy).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h4000dp-xhdpi")
class TextIconsPageTest : TextTabsPageHarness(SettingsPageId.TEXT_ICONS) {
    private val text = "Text"
    private val icons = "Icons"
    private val shapes = "Shapes"
    private val buttons = "Buttons"

    /** The ledger's rows for the page, as the page names them (with a fresh install's settings). */
    private val expected = listOf(
        "Text › Font", "Text › App text size", "Text › Type", "Text › Weight", "Text › Letter spacing", "Text › Titles",
        "Icons › Fill", "Icons › Pack", "Icons › Icon weight", "Icons › Likes as stars", "Icons › App icon",
        "Shapes › Card and cover corners", "Shapes › Songs' covers", "Shapes › Albums' covers", "Shapes › Artists' pictures", "Shapes › Square video covers",
        "Buttons › Shape", "Buttons › Play on album and playlist pages", "Buttons › Switches, sliders and buttons",
    )

    @Test fun theGroupsAndRowsAreTheLedgersWithinTheBudgets() {
        show()
        assertEquals(expected, rowNames())
        assertBudgets()
        // The ledger's counts: Text 6 · Icons 5 · Shapes 5 · Buttons 3.
        assertEquals(listOf(6, 5, 5, 3), groups().map { it.rows.size })
        // Four titled groups: the jump chips are there, and so is the pinned preview.
        assertEquals(1, compose.onAllNodes(hasTestTag(SettingsTags.JUMP_CHIPS)).fetchSemanticsNodes().size)
        assertEquals(1, compose.onAllNodes(hasTestTag(SettingsTags.PREVIEW)).fetchSemanticsNodes().size)
        // Nothing is marked as changed on a fresh install.
        assertTrue(groups().flatMap { it.rows }.none { it.config[SettingsRowChanged] })
    }

    @Test fun everyExampleChoiceIsAPickRowAndTheRestAreTheirTypes() {
        show()
        fun type(group: String, title: String) = typeOf(rowNode(group, title))
        for ((g, t) in listOf(
            text to "Font", text to "Type", text to "Weight", text to "Letter spacing", text to "Titles",
            icons to "Fill", icons to "Pack", icons to "Icon weight", icons to "App icon",
            shapes to "Songs' covers", shapes to "Albums' covers", shapes to "Artists' pictures",
            buttons to "Shape", buttons to "Play on album and playlist pages", buttons to "Switches, sliders and buttons",
        )) assertEquals("$g › $t", SettingsTags.ROW_PICK, type(g, t))
        assertEquals(SettingsTags.ROW_SLIDER, type(text, "App text size"))
        assertEquals(SettingsTags.ROW_SLIDER, type(shapes, "Card and cover corners"))
        assertEquals(SettingsTags.ROW_SWITCH, type(icons, "Likes as stars"))
        assertEquals(SettingsTags.ROW_SWITCH, type(shapes, "Square video covers"))
        // Each says its value in words.
        assertEquals("Prism", valueOf(rowNode(text, "Font")))
        assertEquals("100%", valueOf(rowNode(text, "App text size")))
        assertEquals("Same as the text", valueOf(rowNode(text, "Titles")))
        assertEquals("Regular, rounded ends", valueOf(rowNode(icons, "Icon weight")))
        assertEquals("Round", valueOf(rowNode(shapes, "Card and cover corners")))
        assertEquals("Circle", valueOf(rowNode(shapes, "Artists' pictures")))
        assertEquals("Filled", valueOf(rowNode(buttons, "Play on album and playlist pages")))
    }

    /** §2.6: the renames, and what has its one home on another page. */
    @Test fun renamedAndMovedAway() {
        show()
        val names = rowNames().map { it.substringAfter(" › ") }
        assertTrue("App text size" in names && "Text size" !in names)
        assertTrue("Corners" !in names)
        // The lyrics font is Lyrics & Sing's, the player's cover shape is Player's.
        assertTrue(names.none { it.contains("Lyrics", ignoreCase = true) || it.contains("Player", ignoreCase = true) })
        assertTrue("lyrics_font" !in SettingsPageId.TEXT_ICONS.lookKeys && "cover_shape_player" !in SettingsPageId.TEXT_ICONS.lookKeys)
        assertTrue(SettingsPageId.TEXT_ICONS.index.none { it.title == tr(R.string.lyrics_font) })
    }

    // ------------------------------------------------------------------ Nothing missing (the ledger's keys)

    @Test fun textRowsChangeWhatIsStored() {
        show()
        // font_choice: in the sheet with the fonts.
        pick(text, "Font", "System")
        await("font_choice") { stored.fontChoice == FontChoice.SYSTEM }
        assertEquals("System", valueOf(rowNode(text, "Font")))
        assertTrue("changed from Prism's own", rowNode(text, "Font").config[SettingsRowChanged])
        // text_scale
        slide(text, "App text size", 1.15f)
        await("text_scale") { kotlin.math.abs(stored.textScale - 1.15f) < 0.01f }
        // type_classic
        pick(text, "Type", "Classic")
        await("type_classic") { stored.type.classic }
        // type_weight
        pick(text, "Weight", "Bold")
        await("type_weight") { stored.type.weight == TypeWeight.BOLD }
        // type_spacing
        pick(text, "Letter spacing", "Wide")
        await("type_spacing") { stored.type.spacing == TypeSpacing.WIDE }
        // title_font
        pick(text, "Titles", "Editorial")
        await("title_font") { stored.type.titleFont == FontChoice.SERIF }
        assertEquals("Editorial", valueOf(rowNode(text, "Titles")))
    }

    /** The font's sheet also holds a font file, the phone's installed fonts and (with a file) its removal. */
    @Test fun theFontSheetHoldsTheFontFileRows() {
        show()
        tap(text, "Font")
        assertTrue(sheetOpen())
        val options = compose.onAllNodes(hasTestTag(SettingsTags.SHEET_OPTION)).fetchSemanticsNodes()
        assertEquals("a card per font", FontChoice.entries.size, options.size)
        assertEquals("the current font is the marked one", 1, options.count { it.config[SemanticsProperties.Selected] })
        val links = compose.onAllNodes(hasTestTag(SettingsTags.ROW_LINK), useUnmergedTree = true).fetchSemanticsNodes().map { titleOf(it) }
        assertTrue("$links", tr(R.string.use_a_font_from_your_phone) in links)
        assertTrue("$links", tr(R.string.installed_fonts) in links)
        assertFalse("nothing to remove without a font file", tr(R.string.remove_your_font) in links)
    }

    @Test fun shapeAndButtonRowsChangeWhatIsStored() {
        show()
        // corners (a named step) and corner_scale (a free value): one slider.
        slide(shapes, "Card and cover corners", Corners.SHARP.scale)
        await("corners") { stored.corners == Corners.SHARP && stored.shapes.cornerScale == null }
        assertEquals("Sharp", valueOf(rowNode(shapes, "Card and cover corners")))
        slide(shapes, "Card and cover corners", 1.3f)
        await("corner_scale") { stored.shapes.cornerScale?.let { kotlin.math.abs(it - 1.3f) < 0.01f } == true }
        assertEquals("130%", valueOf(rowNode(shapes, "Card and cover corners")))
        // cover_shape_songs / _albums / _artists
        pick(shapes, "Songs' covers", "Circle")
        await("cover_shape_songs") { stored.shapes.songs == CoverShape.CIRCLE }
        pick(shapes, "Albums' covers", "Squircle")
        await("cover_shape_albums") { stored.shapes.albums == CoverShape.SQUIRCLE }
        pick(shapes, "Artists' pictures", "Flower")
        await("cover_shape_artists") { stored.shapes.artists == CoverShape.FLOWER }
        assertEquals("the player's cover is left alone", CoverShape.ROUNDED, stored.shapes.player)
        // square_video_covers
        assertTrue(stored.squareVideoCovers)
        tap(shapes, "Square video covers")
        await("square_video_covers") { !stored.squareVideoCovers }
        // button_shape, play_outlined, controls_classic
        pick(buttons, "Shape", "Square")
        await("button_shape") { stored.shapes.button == ButtonShape.SQUARE }
        pick(buttons, "Play on album and playlist pages", "Outlined")
        await("play_outlined") { stored.shapes.playOutlined }
        pick(buttons, "Switches, sliders and buttons", "Classic")
        await("controls_classic") { stored.shapes.classicControls }
    }

    /** The icon style is kept apart from the settings ([IconStyle]); the app icon is the launcher's. */
    @Test fun iconRowsChangeWhatIsSaved() {
        show()
        assertFalse("nothing to restart for yet", rowNames().any { it.endsWith("Restart Prism to see them") })
        pick(icons, "Fill", "Filled")
        assertEquals(IconFill.FILLED, IconStyle.savedFill(app))
        pick(icons, "Pack", "Dots")
        assertEquals(IconPack.DOTS, IconStyle.savedPack(app))
        // §2.5: weight and rounded are one row; a pick writes both.
        pick(icons, "Icon weight", "Bold, square ends")
        assertEquals(IconWeight.BOLD to false, IconStyle.saved(app))
        assertEquals("Bold, square ends", valueOf(rowNode(icons, "Icon weight")))
        pick(icons, "Icon weight", "Thin, rounded ends")
        assertEquals(IconWeight.THIN to true, IconStyle.saved(app))
        tap(icons, "Likes as stars")
        assertTrue(IconStyle.savedLikeStar(app))
        pick(icons, "App icon", "Spectrum")
        assertEquals(AppIcon.SPECTRUM, AppIcons.current(app))
        assertEquals("Spectrum", valueOf(rowNode(icons, "App icon")))
        // Icons are built as Prism starts: the page says so once something changed.
        assertEquals(SettingsTags.ROW_LINK, typeOf(rowNode(icons, "Restart Prism to see them")))
        assertBudgets()
    }

    @Test fun resetThisPageListsTheChangesAndPutsThemBack() {
        container.settings.setFontChoice(FontChoice.SYSTEM)
        container.settings.setCoverShape("songs", CoverShape.CIRCLE)
        container.settings.setSquareVideoCovers(false)
        IconStyle.saveFill(app, IconFill.FILLED)
        await("the changes") { stored.fontChoice == FontChoice.SYSTEM && stored.shapes.songs == CoverShape.CIRCLE && !stored.squareVideoCovers }
        show()
        compose.onNodeWithTag(SettingsTags.PAGE_MORE).performClick()
        compose.onNodeWithTag(SettingsTags.RESET_ITEM).performClick()
        val listed = compose.onAllNodes(hasTestTag(SettingsTags.RESET_CHANGE)).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString { t -> t.text } }
        assertEquals(
            listOf("Font: System → Prism", "Fill: Filled → Outline", "Songs' covers: Circle → Rounded", "Square video covers: Off → On"),
            listed,
        )
        compose.onNodeWithTag(SettingsTags.RESET_CONFIRM).performClick()
        await("the reset") { stored.fontChoice == FontChoice.PRISM && stored.shapes.songs == CoverShape.ROUNDED && stored.squareVideoCovers }
        assertEquals(IconFill.OUTLINE, IconStyle.savedFill(app))
        assertEquals("Outline", valueOf(rowNode(icons, "Fill")))
    }

    // ------------------------------------------------------------------ Search and old links

    @Test fun everyRowIsInThePagesSearchLinesUnderItsGroup() {
        show()
        val index = SettingsPageId.TEXT_ICONS.index
        val missing = groups().flatMap { g -> g.rows.map { g.title to titleOf(it) } }
            .filter { (g, t) -> index.none { it.group == g && (it.title == t || it.shown == t) } }
        assertTrue("rows without a search line in their group: $missing", missing.isEmpty())
        // A line names a group the page has.
        val drawn = groups().map { it.title }.toSet()
        assertTrue(index.filter { it.group !in drawn }.map { it.title }.toString(), index.all { it.group in drawn })
        // The old pages' groups land on the groups that hold their rows now.
        val old = SettingsPageId.TEXT_ICONS.oldGroups
        assertEquals("Text", old["Type"]); assertEquals("Text", old["Weight and spacing"]); assertEquals("Text", old["Titles"])
        assertEquals("Icons", old["App icon"]); assertEquals("Shapes", old["Shape"]); assertEquals("Shapes", old["Covers' shapes"])
        assertTrue(old.values.all { it in drawn })
        // "Text size" still finds it, under its new name.
        val size = com.prism.music.ui.screens.SettingsIndex.search("text size").first { it.page == SettingsPageId.TEXT_ICONS }
        assertEquals("App text size", size.shown)
    }

    // ------------------------------------------------------------------ Drawn

    private fun draws(name: String, fontScale: Float = 1f, glass: Boolean = true, set: () -> Unit, ready: () -> Boolean) {
        set()
        await(name, ready)
        show(fontScale, glass)
        assertEquals(expected, rowNames())
        assertBudgets()
        assertEquals("$name: cut-off text", emptyList<String>(), clippedTexts())
    }

    @Test fun drawsInLight() = draws("light", set = { container.settings.setThemeMode(ThemeMode.LIGHT) }) { stored.themeMode == ThemeMode.LIGHT }
    @Test fun drawsInDark() = draws("dark", set = { container.settings.setThemeMode(ThemeMode.DARK) }) { stored.themeMode == ThemeMode.DARK }
    @Test fun drawsInPerformanceMode() = draws("performance", glass = false, set = { container.settings.setPerformanceMode(true) }) { stored.performanceMode }
    @Test fun drawsAtLargeTextWithNothingCutOff() = draws("large text", fontScale = 1.5f, set = { container.settings.setThemeMode(ThemeMode.DARK) }) { stored.themeMode == ThemeMode.DARK }

    /** PNGs for a look (app/build/settings-pages/text_icons/), on a 360 dp phone. */
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    @Test fun renders() {
        container.settings.setThemeMode(ThemeMode.DARK)
        await("dark") { stored.themeMode == ThemeMode.DARK }
        show(glass = true)
        assertEquals(1080, save("dark").width)
        compose.onNodeWithTag(SettingsTags.PREVIEW).assertExists()
        // Scrolled: the preview is a strip under the bar.
        pageList().performScrollToIndex(5)
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.PREVIEW).assertHeightIsEqualTo(72.dp)
        save("dark-scrolled")
        pageList().performScrollToIndex(0)
        container.settings.setThemeMode(ThemeMode.LIGHT)
        await("light") { stored.themeMode == ThemeMode.LIGHT }
        save("light")
        container.settings.setThemeMode(ThemeMode.DARK)
        container.settings.setPerformanceMode(true)
        await("performance") { stored.performanceMode && stored.themeMode == ThemeMode.DARK }
        save("performance")
    }

    @Config(qualifiers = "w360dp-h4000dp-xhdpi")
    @Test fun rendersTheWholePage() {
        container.settings.setThemeMode(ThemeMode.DARK)
        await("dark") { stored.themeMode == ThemeMode.DARK }
        show(glass = true)
        save("dark-whole")
    }
}
