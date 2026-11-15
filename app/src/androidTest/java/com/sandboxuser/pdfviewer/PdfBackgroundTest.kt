package com.sandboxuser.pdfviewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sandboxuser.pdfviewer.data.ColorTheme
import com.sandboxuser.pdfviewer.data.PdfBackground
import com.sandboxuser.pdfviewer.ui.theme.colors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBackgroundTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun pixel(): Color {
        val pixels = rule.onNodeWithContentDescription("PDF page 1 of 3").captureToImage().toPixelMap()
        return pixels[5, 5]
    }
    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .035f)
        assertEquals(expected.green, actual.green, .035f)
        assertEquals(expected.blue, actual.blue, .035f)
    }
    @Test fun originalColoursThemeBackgroundsAndSidebarPreferencePersistWithoutEditingPdf() {
        val model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model.loading }
        val mode = model.theme
        val palette = model.palette
        val background = model.pdfBackground
        val bytes = model.store.file("folio-welcome").readBytes()
        try {
            rule.runOnIdle { model.changeTheme("light"); model.changePalette(ColorTheme.FOLIO); model.update("folio-welcome") { it.copy(page = 0) } }
            rule.onNodeWithContentDescription("Open sidebar").performClick()
            rule.onNodeWithContentDescription("Choose PDF background").performScrollTo().performClick()
            rule.onNodeWithContentDescription("PDF background: Original PDF").performClick().assertIsSelected()
            rule.onNodeWithText("Done").performClick()
            rule.onNodeWithText("A little guide to Folio").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithTag("PDF page surface 1").fetchSemanticsNodes().isNotEmpty() }
            val original = pixel()
            rule.runOnIdle { model.changePalette(ColorTheme.BLACK) }
            assertColor(original, pixel())
            rule.onNodeWithContentDescription("Reader options").performClick()
            rule.onNodeWithText("PDF page background").performClick()
            rule.onNodeWithContentDescription("PDF background: Follow app theme").performClick()
            rule.onNodeWithText("Done").performClick()
            assertTrue(pixel().red < .1f)
            rule.runOnIdle { model.changeTheme("light"); model.changePalette(ColorTheme.OCEAN) }
            val scheme = ColorTheme.OCEAN.colors(false).scheme
            assertColor(Color(
                scheme.onBackground.red + original.red * (scheme.background.red - scheme.onBackground.red),
                scheme.onBackground.green + original.green * (scheme.background.green - scheme.onBackground.green),
                scheme.onBackground.blue + original.blue * (scheme.background.blue - scheme.onBackground.blue)
            ), pixel())
            val ocean = pixel()
            rule.onNodeWithContentDescription("Reader options").performClick()
            rule.onNodeWithText("Scroll pages").performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("PDF page 1 of 3").fetchSemanticsNodes().isNotEmpty() }
            assertColor(ocean, pixel())
            rule.waitUntil(15000) { !model.saving }
            assertEquals(PdfBackground.THEME, runBlocking { model.store.loadState() }.pdfBackground)
            rule.activityRule.scenario.recreate()
            rule.onNodeWithContentDescription("Reader options").performClick()
            rule.onNodeWithText("PDF page background").performClick()
            rule.onNodeWithContentDescription("PDF background: Follow app theme").assertIsSelected()
            rule.onNodeWithContentDescription("PDF background: Original PDF").performClick()
            rule.onNodeWithText("Done").performClick()
            assertColor(original, pixel())
            assertArrayEquals(bytes, model.store.file("folio-welcome").readBytes())
        } finally {
            rule.runOnIdle { model.changePalette(palette); model.changeTheme(mode); model.changePdfBackground(background) }
        }
    }
}
