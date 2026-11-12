package com.sandboxuser.pdfviewer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingModesTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var library: LibraryModel
    private fun model() = library
    private fun page() = model().books.first { it.id == "folio-welcome" }.page
    private fun waitPage(index: Int) {
        rule.waitUntil(10000) { page() == index && rule.onAllNodesWithContentDescription("PDF page ${index + 1} of 3").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun swipeScrollJumpAndAnnotationsWorkTogether() {
        library = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model().loading }
        rule.runOnIdle { model().update("folio-welcome") { it.copy(page = 0) } }
        rule.onNodeWithText("A little guide to Folio").performClick()
        waitPage(0)
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput { swipeLeft() }
        waitPage(1)
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput { swipeRight() }
        waitPage(0)
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput { swipeRight() }
        assertEquals(0, page())
        rule.onNodeWithContentDescription("Highlight tool").performClick()
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput { swipeLeft() }
        assertEquals(0, page())
        assertTrue(model().books.first { it.id == "folio-welcome" }.marks.any { it.kind == "highlight" })
        rule.onNodeWithContentDescription("Reader options").performClick()
        rule.onNodeWithText("Scroll pages").performClick()
        rule.onNodeWithContentDescription("Scrolling PDF pages").performTouchInput { swipeUp() }
        rule.onNodeWithContentDescription("Scrolling PDF pages").performTouchInput { swipeUp() }
        rule.waitUntil(10000) { page() > 0 }
        val scrolledPage = page()
        rule.waitForIdle()
        rule.activityRule.scenario.recreate()
        library = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.onNodeWithContentDescription("Scrolling PDF pages").assertExists()
        rule.waitUntil(10000) { page() == scrolledPage }
        rule.onNodeWithContentDescription("Previous page").performClick()
        rule.waitUntil(10000) { page() == scrolledPage - 1 }
        rule.onNodeWithText("${page() + 1}  /  3").performClick()
        rule.onNode(hasSetTextAction()).performTextReplacement("3")
        rule.onNodeWithText("Go", substring = false).performClick()
        rule.waitForIdle()
        rule.waitUntil(10000) { page() == 2 }
        rule.onNodeWithContentDescription("Read in full screen").performClick()
        rule.onNodeWithContentDescription("Scrolling PDF pages").assertExists()
        rule.onNodeWithContentDescription("PDF page 3 of 3").performTouchInput { click(center) }
        rule.onNodeWithContentDescription("Previous page").assertDoesNotExist()
        rule.onNodeWithContentDescription("PDF page 3 of 3").performTouchInput { click(center) }
        rule.onNodeWithContentDescription("Previous page").assertExists()
        rule.onNodeWithContentDescription("Exit full screen").performClick()
        rule.onNodeWithContentDescription("Note tool").performClick()
        rule.onNodeWithContentDescription("Scrolling PDF pages").assertDoesNotExist()
        rule.onNodeWithContentDescription("Page annotation canvas").assertExists()
        rule.runOnIdle { model().update("folio-welcome") { it.copy(page = 0, marks = emptyList()) } }
    }
}
