package com.sandboxuser.pdfviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFeaturesTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(rule.activity)[LibraryModel::class.java]
    @Test fun foldersDarkModeAndFullscreenWorkTogether() {
        rule.waitUntil(15000) { !model().loading }
        rule.onNodeWithText("New folder", substring = true).performClick()
        rule.onNodeWithText("Science", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Create folder").performClick()
        rule.onNodeWithContentDescription("Options for A little guide to Folio").performClick()
        rule.onNodeWithText("Move to folder", substring = true).performClick()
        rule.onNodeWithText("  Science").performClick()
        rule.onNodeWithContentDescription("Open folder Science").performClick()
        rule.onNodeWithText("A little guide to Folio").assertExists()
        rule.onNodeWithContentDescription("Appearance").performClick()
        rule.onNodeWithText("Dark", useUnmergedTree = true).performClick()
        rule.onNodeWithText("Done").performClick()
        rule.waitUntil { !model().saving }
        assertEquals("dark", runBlocking { model().store.loadState() }.theme)
        rule.onNodeWithText("A little guide to Folio").performClick()
        rule.onNodeWithContentDescription("Read in full screen").performClick()
        rule.onNodeWithContentDescription("Highlight tool").assertDoesNotExist()
        rule.onNodeWithContentDescription("Exit full screen").assertExists()
        rule.waitUntil(5000) { ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.statusBars()) == false }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription("Exit full screen").performClick()
        rule.onNodeWithContentDescription("Highlight tool").assertExists()
        rule.onNodeWithContentDescription("Back to library").performClick()
        rule.onNodeWithText("In this folder").assertExists()
        rule.onNodeWithContentDescription("Folder options").performClick()
        rule.onNodeWithText("Delete folder").performClick()
        rule.onNodeWithText("Delete folder").performClick()
        rule.onNodeWithText("A little guide to Folio").assertExists()
        rule.waitUntil { !model().saving }
        val saved = runBlocking { model().store.loadState() }
        assertNull(saved.books.first { it.id == "folio-welcome" }.folderId)
        assertTrue(model().store.file("folio-welcome").exists())
        rule.runOnIdle { model().changeTheme("system") }
    }
}
