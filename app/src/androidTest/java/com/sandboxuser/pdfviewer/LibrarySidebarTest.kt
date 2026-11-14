package com.sandboxuser.pdfviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySidebarTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun sidebarPreservesSearchAndFolderAndCreatesSubfoldersInPlace() {
        val model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model.loading }
        rule.onNodeWithContentDescription("Search your PDFs").performTextInput("guide")
        rule.onNodeWithContentDescription("Open sidebar").performClick()
        rule.onNodeWithContentDescription("Choose colour theme").assertIsDisplayed()
        rule.onNodeWithContentDescription("Appearance").assertIsDisplayed()
        rule.onNodeWithContentDescription("Close sidebar").performClick()
        rule.onNodeWithContentDescription("Search your PDFs").assertTextEquals("guide")
        rule.onNodeWithContentDescription("Clear search").performClick()
        rule.runOnIdle { model.saveFolder("Sidebar test") }
        val parent = model.folders.single { it.name == "Sidebar test" }.id
        try {
            rule.onNodeWithContentDescription("Open folder Sidebar test").performClick()
            rule.onNodeWithContentDescription("Open sidebar").performClick()
            rule.onNodeWithText("In: Sidebar test").assertExists()
            Espresso.pressBack()
            rule.onNodeWithContentDescription("Back to parent folder").assertIsDisplayed()
            rule.onNodeWithContentDescription("Open sidebar").performClick()
            rule.onNodeWithContentDescription("Create folder here").performScrollTo().performClick()
            rule.onNodeWithText("Folder name").performTextInput("Chapter one")
            rule.onNodeWithText("Create folder").performClick()
            rule.runOnIdle { assertEquals(parent, model.folders.single { it.name == "Chapter one" }.parentId) }
            rule.onNodeWithContentDescription("Open folder Chapter one").assertIsDisplayed()
            rule.onNodeWithContentDescription("Add PDFs").assertIsDisplayed()
            rule.onNodeWithContentDescription("Open sidebar").performClick()
            rule.onNodeWithContentDescription("Import folder here").performScrollTo().assertIsDisplayed()
            rule.onNodeWithContentDescription("Close sidebar").performScrollTo().performClick()
            rule.onNodeWithText("Favourites").performClick()
            rule.onNodeWithContentDescription("Open sidebar").performClick()
            rule.onNodeWithText("In: Library").assertExists()
            rule.onNodeWithContentDescription("Close sidebar").performClick()
            rule.onNodeWithText("The keepers.").assertExists()
        } finally {
            rule.runOnIdle { model.removeFolder(parent) }
        }
    }
}
