package com.sandboxuser.pdfviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sandboxuser.pdfviewer.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextHighlightTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(rule.activity)[LibraryModel::class.java]
    private fun ready(label: String = "Page annotation canvas") {
        rule.waitUntil(20000) {
            rule.onAllNodes(hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Text selection ready")).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun open(): PageText {
        rule.waitUntil(15000) { !model().loading }
        rule.runOnIdle { model().update("folio-welcome") { it.copy(marks = emptyList(), page = 0) } }
        val text = runBlocking { model().store.pageText("folio-welcome", 0) }
        rule.onNodeWithText("A little guide to Folio").performClick()
        ready()
        return text
    }
    private fun center(word: PdfWord, width: Int, height: Int) = Offset((word.box.left + word.box.right) / 2 * width, (word.box.top + word.box.bottom) / 2 * height)

    @Test fun wordTapShortDragAndReverseMultilineHighlightPersist() {
        val text = open()
        val first = text.words.indexOfFirst { it.text == "Your" }
        assertTrue(text.words.map { it.text }.toString(), first >= 0)
        val last = text.words.indexOfFirst { it.text == "document." }
        assertTrue(last > first)
        val original = model().store.file("folio-welcome").readBytes()
        rule.onNodeWithContentDescription("Highlight tool").performClick()
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput { click(center(text.words[first], width, height)) }
        rule.waitUntil { model().books.first { it.id == "folio-welcome" }.marks.size == 1 }
        assertEquals("Your", model().books.first { it.id == "folio-welcome" }.marks.single().text)
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput {
            swipe(center(text.words[last], width, height), center(text.words[first], width, height), 700)
        }
        rule.waitUntil { model().books.first { it.id == "folio-welcome" }.marks.size == 2 }
        val mark = model().books.first { it.id == "folio-welcome" }.marks.last()
        assertEquals(text.select(0, first, last).text, mark.text)
        assertTrue(mark.boxes.size > 5)
        assertTrue(mark.text.contains('\n'))
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput {
            val point = center(text.words[first], width, height)
            swipe(point, point + Offset(2f, 0f), 120)
        }
        rule.waitUntil { model().books.first { it.id == "folio-welcome" }.marks.size == 3 && !model().saving }
        assertEquals(model().books.first { it.id == "folio-welcome" }.marks, runBlocking { model().store.loadState() }.books.first { it.id == "folio-welcome" }.marks)
        assertArrayEquals(original, model().store.file("folio-welcome").readBytes())
    }

    @Test fun scannedPageFallsBackToAreaHighlightWithoutInventingText() {
        rule.waitUntil(15000) { !model().loading }
        val source = java.io.File(rule.activity.cacheDir, "scan-selection.pdf")
        val pdf = android.graphics.pdf.PdfDocument()
        try {
            val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 400, 1).create())
            page.canvas.drawRect(30f, 80f, 200f, 100f, android.graphics.Paint())
            pdf.finishPage(page)
            source.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        val before = model().books.map { it.id }.toSet()
        rule.runOnIdle { model().import(listOf(android.net.Uri.fromFile(source))) }
        rule.waitUntil(15000) { !model().importing && model().books.any { it.id !in before } }
        val id = model().books.first { it.id !in before }.id
        try {
            rule.runOnIdle { model().update(id) { it.copy(title = "Scan selection fixture") } }
            rule.onNodeWithTag("Library items").performScrollToNode(hasText("Scan selection fixture"))
            rule.onNodeWithText("Scan selection fixture").performClick()
            rule.waitUntil(15000) { rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Area highlighting available")).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Highlight tool").performClick()
            rule.onNodeWithText("No selectable text · Drag to highlight an area").assertExists()
            rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput {
                swipe(Offset(width * .1f, height * .2f), Offset(width * .7f, height * .25f), 400)
            }
            rule.waitUntil { model().books.first { it.id == id }.marks.isNotEmpty() }
            val mark = model().books.first { it.id == id }.marks.single()
            assertTrue(mark.text.isEmpty())
            assertEquals(1, mark.boxes.size)
            assertTrue(mark.boxes.single().right - mark.boxes.single().left > .5f)
            rule.onNodeWithContentDescription("Undo last annotation on this page").performClick()
            rule.waitUntil { model().books.first { it.id == id }.marks.isEmpty() }
            rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput {
                swipe(Offset(width * .4f, height * .2f), Offset(width * .4f, height * .6f), 400)
            }
            rule.waitUntil { model().books.first { it.id == id }.marks.isNotEmpty() }
            val vertical = model().books.first { it.id == id }.marks.single().boxes.single()
            assertTrue(vertical.right > vertical.left)
            assertTrue(vertical.bottom - vertical.top > .35f)
            rule.onNodeWithContentDescription("Back to library").performClick()
        } finally { rule.runOnIdle { model().remove(id) }; source.delete() }
    }

    @Test fun rotatedAndZoomedLongPressKeepsTextCoordinatesInBothReadingModes() {
        val text = open()
        val word = text.words.minBy { kotlin.math.abs((it.box.left + it.box.right) / 2 - .5f) + kotlin.math.abs((it.box.top + it.box.bottom) / 2 - .5f) }
        for(scrolling in listOf(false, true)) {
            if(scrolling) {
                rule.onNodeWithContentDescription("Exit full screen").performClick()
                rule.onNodeWithContentDescription("Reader options").performClick()
                rule.onNodeWithText("Scroll pages").performClick()
                ready("Page annotation canvas 1")
            }
            rule.onNodeWithContentDescription("Read in full screen").performClick()
            for(rotation in listOf(0, 90, 180, 270)) {
                val x = (word.box.left + word.box.right) / 2
                val y = (word.box.top + word.box.bottom) / 2
                val (rx, ry) = when(rotation) { 90 -> 1f - y to x; 180 -> 1f - x to 1f - y; 270 -> y to 1f - x; else -> x to y }
                rule.onNodeWithTag("PDF page surface 1").performTouchInput { doubleClick(Offset(width * rx, height * ry)) }
                rule.onNodeWithTag("PDF zoom viewport 1").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 250 percent"))
                rule.onNodeWithTag("PDF zoom viewport 1").performTouchInput { longClick(center) }
                rule.onNodeWithContentDescription("Selected PDF text").assertTextEquals(word.text)
                rule.onNodeWithContentDescription("Clear text selection").performClick()
                rule.onNodeWithTag("PDF zoom viewport 1").performTouchInput { doubleClick(center) }
                rule.onNodeWithContentDescription("Rotate pages clockwise").performClick()
            }
        }
    }

    @Test fun longPressSelectsWhileReadingAndScrollingInFullscreen() {
        val text = open()
        val word = text.words.first { it.text == "Your" }
        val end = text.words.first { it.text == "document." }
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput {
            down(center(word, width, height)); advanceEventTime(700); moveTo(center(end, width, height)); up()
        }
        rule.onNodeWithContentDescription("Selected PDF text").assertTextEquals(text.select(0, text.words.indexOf(word), text.words.indexOf(end)).text)
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithContentDescription("Selected PDF text").assertDoesNotExist()
        rule.onNodeWithContentDescription("Page annotation canvas").performTouchInput { longClick(center(word, width, height)) }
        rule.onNodeWithContentDescription("Selected PDF text").assertTextEquals("Your")
        rule.onNodeWithText("Copy", useUnmergedTree = true).performClick()
        rule.onNodeWithContentDescription("Selected PDF text").assertDoesNotExist()
        rule.runOnIdle {
            assertEquals("Your", rule.activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString())
        }
        rule.onNodeWithContentDescription("Reader options").performClick()
        rule.onNodeWithText("Scroll pages").performClick()
        ready("Page annotation canvas 1")
        rule.onNodeWithContentDescription("Read in full screen").performClick()
        rule.onNodeWithContentDescription("Page annotation canvas 1").performTouchInput { longClick(center(word, width, height)) }
        rule.onNodeWithContentDescription("Selected PDF text").assertTextEquals("Your")
        rule.onNodeWithText("Highlight", useUnmergedTree = true).performClick()
        rule.waitUntil { model().books.first { it.id == "folio-welcome" }.marks.size == 1 }
        assertEquals("Your", model().books.first { it.id == "folio-welcome" }.marks.single().text)
    }
}
