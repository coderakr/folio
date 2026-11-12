package com.sandboxuser.pdfviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryToolbarTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun searchFolderLayoutsAndDirectThemeToggle() {
        val model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model.loading }
        rule.onNodeWithText("Your little library.").assertDoesNotExist()
        rule.onNodeWithContentDescription("About local storage").assertDoesNotExist()
        rule.onNodeWithContentDescription("Search your PDFs").performTextInput("no-matching-pdf-123")
        rule.onNodeWithText("No matching reads").assertExists()
        rule.onNodeWithText("A little guide to Folio").assertDoesNotExist()
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.onNodeWithContentDescription("Search your PDFs").performImeAction()
        rule.onNodeWithText("A little guide to Folio").assertExists()
        rule.runOnIdle {
            model.changeTheme("light")
            model.saveFolder("Layout test")
            val root = model.folders.single { it.name == "Layout test" }.id
            model.saveFolder("Chapters", parentId = root)
            model.saveFolder("References", parentId = root)
        }
        val root = model.folders.single { it.name == "Layout test" }.id
        rule.onNodeWithContentDescription("Open folder Layout test").performClick()
        rule.onNodeWithContentDescription("Show folders as list").performClick()
        rule.onNodeWithContentDescription("Open folder Chapters").performClick()
        rule.onNodeWithContentDescription("Back to parent folder").performClick()
        rule.onNodeWithContentDescription("Show folders as grid").assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription("Show folders as grid").performClick()
        rule.onNodeWithContentDescription("Open folder References").assertIsDisplayed()
        rule.onNodeWithContentDescription("Appearance").performClick()
        rule.onNodeWithText("Use device setting").assertDoesNotExist()
        rule.waitUntil { !model.saving }
        assertEquals("dark", runBlocking { model.store.loadState() }.theme)
        rule.onNodeWithContentDescription("Appearance").performClick()
        rule.waitUntil { !model.saving }
        assertEquals("light", runBlocking { model.store.loadState() }.theme)
        rule.runOnIdle { model.removeFolder(root) }
        rule.onNodeWithText("A little guide to Folio").performClick()
        rule.onNodeWithContentDescription("Reader options").performClick()
        rule.onNodeWithText("Appearance").performClick()
        rule.waitUntil { !model.saving }
        assertEquals("dark", runBlocking { model.store.loadState() }.theme)
        rule.onNodeWithText("Use device setting").assertDoesNotExist()
        rule.runOnIdle { model.changeTheme("system") }
    }
}
