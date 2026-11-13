package com.sandboxuser.pdfviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sandboxuser.pdfviewer.data.Mark
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageRotationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun surface(page: Int = 1) = rule.onNodeWithTag("PDF page surface $page")
    private fun angle(degrees: Int, page: Int = 1) = surface(page).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Rotated $degrees degrees clockwise"))
    private fun waitForPage(page: Int) = rule.waitUntil(15000) { rule.onAllNodesWithTag("PDF page surface $page").fetchSemanticsNodes().isNotEmpty() }
    private fun rotate() = rule.onNodeWithContentDescription("Rotate pages clockwise").performClick()
    private fun openRotatedNote() {
        // Original point (.25, .30) becomes (.70, .25) after a clockwise quarter turn.
        surface().performTouchInput { click(Offset(width * .70f, height * .25f)) }
        rule.onNodeWithText("Rotation keeps this note aligned").assertExists()
        rule.onNodeWithText("Cancel").performClick()
    }
    @Test fun rotationFitsPagesPreservesNotesAndWorksInBothReadingModes() {
        val model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model.loading }
        val original = model.books.first { it.id == "folio-welcome" }
        val bytes = model.store.file(original.id).readBytes()
        val note = Mark(page = 0, kind = "note", points = listOf(.25f to .30f), text = "Rotation keeps this note aligned")
        rule.runOnIdle { model.update(original.id) { it.copy(page = 0, marks = listOf(note)) } }
        try {
            rule.onNodeWithText(original.title).performClick()
            waitForPage(1)
            rule.onNodeWithContentDescription("Rotate pages clockwise").assertDoesNotExist()
            rule.onNodeWithContentDescription("Read in full screen").performClick()
            rotate(); angle(90)
            val sideways = surface().fetchSemanticsNode().boundsInRoot
            assertTrue(sideways.width > sideways.height)
            openRotatedNote()
            rule.activityRule.scenario.recreate()
            waitForPage(1); angle(90)
            // Swipes remain screen-relative after rotation.
            surface().performTouchInput { swipeLeft() }
            waitForPage(2); angle(90, 2)
            surface(2).performTouchInput { swipeRight() }
            waitForPage(1)
            rotate(); angle(180)
            rotate(); angle(270)
            rotate(); angle(0)
            val upright = surface().fetchSemanticsNode().boundsInRoot
            assertTrue(upright.height > upright.width)
            rotate(); angle(90)
            rule.onNodeWithContentDescription("Exit full screen").performClick()
            angle(0)
            rule.onNodeWithContentDescription("Reader options").performClick()
            rule.onNodeWithText("Scroll pages").performClick()
            rule.onNodeWithContentDescription("Read in full screen").performClick()
            waitForPage(1); angle(90)
            openRotatedNote()
            rotate(); angle(180)
            rotate(); angle(270)
            rotate(); angle(0)
            assertEquals(listOf(note), model.books.first { it.id == original.id }.marks)
            assertArrayEquals(bytes, model.store.file(original.id).readBytes())
        } finally {
            rule.runOnIdle { model.update(original.id) { it.copy(marks = original.marks, page = original.page) } }
        }
    }
}
