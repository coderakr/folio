package com.sandboxuser.pdfviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoubleTapZoomTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun doubleTapZoomsAndResetsInBothModesAndEveryRotation() {
        val model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model.loading }
        rule.runOnIdle { model.update("folio-welcome") { it.copy(page = 0) } }
        rule.onNodeWithText("A little guide to Folio").performClick()
        rule.waitUntil(15000) { rule.onAllNodesWithTag("PDF page surface 1").fetchSemanticsNodes().isNotEmpty() }
        for(scroll in listOf(false, true)) {
            if(scroll) {
                rule.onNodeWithContentDescription("Reader options").performClick()
                rule.onNodeWithText("Scroll pages").performClick()
            }
            rule.onNodeWithText("Swipe pages · Pinch to zoom · Tap notes to edit").assertDoesNotExist()
            rule.onNodeWithText("Scroll vertically · Select a tool to annotate a page").assertDoesNotExist()
            rule.onNodeWithContentDescription("Read in full screen").performClick()
            repeat(4) {
                val page = rule.onNodeWithTag("PDF page surface 1")
                page.performTouchInput { doubleClick(Offset(width * .68f, height * .4f)) }
                rule.onNodeWithText("Fit page").assertDoesNotExist()
                rule.onNodeWithTag("PDF zoom viewport 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 250 percent"))
                rule.runOnIdle { assertEquals(0, model.books.first { it.id == "folio-welcome" }.page) }
                page.performTouchInput { doubleClick(center) }
                rule.onNodeWithTag("PDF zoom viewport 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100 percent"))
                rule.onNodeWithText("Fit page").assertDoesNotExist()
                rule.onNodeWithContentDescription("Rotate pages clockwise").performClick()
            }
            rule.onNodeWithTag("PDF page surface 1").performTouchInput { doubleClick(center) }
            rule.onNodeWithText("Fit page").assertDoesNotExist()
            rule.onNodeWithContentDescription("Exit full screen").performClick()
            rule.onNodeWithTag("PDF zoom viewport 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100 percent"))
            if(!scroll) {
                rule.onNodeWithContentDescription("Zoom page").performClick()
                rule.onNodeWithText("Fit page").performClick()
                rule.onNodeWithTag("PDF zoom viewport 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100 percent"))
            }
        }
    }
}
