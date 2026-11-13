package com.sandboxuser.pdfviewer

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var model: LibraryModel
    private fun jump(number: Int) {
        rule.onNodeWithContentDescription("Jump to page")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(number.toFloat()) }
        rule.waitForIdle()
        rule.waitUntil(15000) { model.books.first { it.id == "folio-welcome" }.page == number - 1 }
        rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("PDF page $number of 3").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun sliderNavigatesSwipeScrollAndRotatedFullscreenAndRestoresPosition() {
        model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model.loading }
        val original = model.books.first { it.id == "folio-welcome" }
        val bytes = model.store.file(original.id).readBytes()
        rule.runOnIdle { model.update(original.id) { it.copy(page = 0) } }
        try {
            rule.onNodeWithText(original.title).performClick()
            jump(3); jump(2)
            rule.onNodeWithContentDescription("Read in full screen").performClick()
            rule.onNodeWithContentDescription("Rotate pages clockwise").performClick()
            jump(1); jump(3)
            rule.waitForIdle()
            rule.activityRule.scenario.recreate()
            model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
            rule.onNodeWithText("Page 3").assertExists()
            assertEquals(2, model.books.first { it.id == original.id }.page)
            rule.onNodeWithContentDescription("Exit full screen").performClick()
            rule.onNodeWithContentDescription("Reader options").performClick()
            rule.onNodeWithText("Scroll pages").performClick()
            jump(1); jump(3)
            rule.onNodeWithContentDescription("Read in full screen").performClick()
            // A sideways final page must remain selected even when shorter than the viewport.
            jump(1); jump(3)
            rule.waitForIdle()
            rule.waitUntil(15000) { !model.saving }
            assertEquals(2, kotlinx.coroutines.runBlocking { model.store.load() }.first { it.id == original.id }.page)
            assertArrayEquals(bytes, model.store.file(original.id).readBytes())
            assertEquals(original.marks, model.books.first { it.id == original.id }.marks)
        } finally {
            rule.runOnIdle { model.update(original.id) { it.copy(page = original.page) } }
        }
    }
}
