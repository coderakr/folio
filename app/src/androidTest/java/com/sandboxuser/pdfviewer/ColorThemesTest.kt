package com.sandboxuser.pdfviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sandboxuser.pdfviewer.data.ColorTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColorThemesTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun palettesApplyFromLibraryAndReaderAndSurviveAppearanceChangesAndReload() {
        val model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model.loading }
        val originalPalette = model.palette
        val originalMode = model.theme
        try {
            rule.runOnIdle { model.changeTheme("light"); model.changePalette(ColorTheme.FOLIO) }
            rule.onNodeWithContentDescription("Choose colour theme").performClick()
            rule.onNodeWithContentDescription("Folio colour theme").assertIsSelected()
            for (palette in ColorTheme.entries) {
                rule.onNodeWithContentDescription("${palette.title} colour theme").performScrollTo().performClick().assertIsSelected()
                rule.runOnIdle { assertEquals(palette, model.palette); assertEquals("light", model.theme) }
            }
            rule.onNodeWithText("Done").performClick()
            rule.onNodeWithContentDescription("Appearance").performClick()
            rule.runOnIdle { assertEquals("dark", model.theme); assertEquals(ColorTheme.ROSE, model.palette) }
            rule.onNodeWithContentDescription("Choose colour theme").performClick()
            rule.onNodeWithContentDescription("Ocean colour theme").performScrollTo().performClick().assertIsSelected()
            rule.onNodeWithText("Done").performClick()
            rule.onNodeWithText("A little guide to Folio").performClick()
            rule.onNodeWithContentDescription("Reader options").performClick()
            rule.onNodeWithText("Colour themes").performClick()
            rule.onNodeWithContentDescription("Ocean colour theme").assertIsSelected()
            rule.onNodeWithContentDescription("Lavender colour theme").performScrollTo().performClick()
            rule.onNodeWithText("Done").performClick()
            rule.waitForIdle()
            rule.waitUntil(15000) { !model.saving }
            val saved = runBlocking { model.store.loadState() }
            assertEquals(ColorTheme.LAVENDER, saved.palette)
            assertEquals("dark", saved.theme)
            rule.activityRule.scenario.recreate()
            rule.runOnIdle { model.reload() }
            rule.waitUntil(15000) { !model.loading }
            rule.onNodeWithContentDescription("Reader options").performClick()
            rule.onNodeWithText("Colour themes").performClick()
            rule.onNodeWithContentDescription("Lavender colour theme").assertIsSelected()
            rule.onNodeWithText("Done").performClick()
            rule.runOnIdle { assertEquals("dark", model.theme) }
        } finally {
            rule.runOnIdle { model.changePalette(originalPalette); model.changeTheme(originalMode) }
        }
    }
}
