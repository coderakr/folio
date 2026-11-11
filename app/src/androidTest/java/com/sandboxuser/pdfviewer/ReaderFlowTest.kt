package com.sandboxuser.pdfviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(rule.activity)[LibraryModel::class.java]

    @Test fun readAnnotateBookmarkAndResumeAfterRecreation() {
        rule.waitUntil(15000) { !model().loading }
        rule.runOnIdle { model().update("folio-welcome") { it.copy(marks = emptyList(), page = 0, bookmarks = emptySet()) } }
        rule.onNodeWithText("A little guide to Folio").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Page annotation canvas").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Highlight tool").performClick()
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput {
            swipe(Offset(width * .1f, height * .47f), Offset(width * .8f, height * .48f), 500)
        }
        rule.waitUntil { model().books.first { it.id == "folio-welcome" }.marks.size == 1 }
        rule.onNodeWithContentDescription("Note tool").performClick()
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput { click(Offset(width * .5f, height * .6f)) }
        rule.onNode(hasSetTextAction()).performTextInput("Remember this idea")
        rule.onNodeWithText("Save note").performClick()
        rule.waitUntil { model().books.first { it.id == "folio-welcome" }.marks.size == 2 }
        rule.onNodeWithContentDescription("Next page").performClick()
        rule.onNodeWithContentDescription("Bookmark page").performClick()
        rule.waitUntil { !model().saving }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("2  /  3").assertExists()
        rule.onNodeWithContentDescription("Remove page bookmark").assertExists()
        rule.onNodeWithContentDescription("Back to library").performClick()
        rule.onNodeWithText("My notes").performClick()
        rule.onNodeWithText("Remember this idea").assertExists().performClick()
        rule.onNodeWithText("1  /  3").assertExists()
        rule.onNodeWithContentDescription("Reader options").performClick()
        rule.onNodeWithContentDescription("View notes and highlights").performClick()
        rule.onNodeWithText("Remember this idea").assertExists()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithContentDescription("Undo last annotation on this page").performClick()
        rule.waitUntil { model().books.first { it.id == "folio-welcome" }.marks.size == 1 }
        assertEquals("highlight", model().books.first { it.id == "folio-welcome" }.marks.single().kind)
    }
}
