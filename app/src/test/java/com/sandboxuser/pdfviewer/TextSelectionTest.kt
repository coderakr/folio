package com.sandboxuser.pdfviewer

import com.sandboxuser.pdfviewer.data.*
import org.junit.Assert.*
import org.junit.Test

class TextSelectionTest {
    private val text = PageText(listOf(
        PdfWord("First", TextBox(.1f, .1f, .2f, .13f), 0),
        PdfWord("line", TextBox(.23f, .1f, .3f, .13f), 0),
        PdfWord("Second", TextBox(.1f, .2f, .23f, .23f), 1),
        PdfWord("line", TextBox(.25f, .2f, .32f, .23f), 1)))

    @Test fun reverseMultiLineSelectionKeepsReadingOrderAndSeparateBoxes() {
        val result = text.select(3, 3, 0)
        assertEquals("First line\nSecond line", result.text)
        assertEquals(4, result.mark(2).boxes.size)
        assertEquals(3, result.mark(2).page)
        assertTrue(result.mark(2).points.isEmpty())
    }
    @Test fun tapSnapsNearTextButNotAcrossBlankMargins() {
        assertEquals(0, text.nearest(.15f, .115f, true))
        assertEquals(0, text.nearest(.09f, .115f, true))
        assertNull(text.nearest(.9f, .8f, true))
        assertNull(PageText(emptyList()).nearest(.5f, .5f))
    }
    @Test fun gridSizesAdaptToWidthAndAccessibilityTextSize() {
        assertEquals(3, GridSize.SMALL.columns(320f))
        assertEquals(2, GridSize.MEDIUM.columns(320f))
        assertEquals(1, GridSize.LARGE.columns(320f))
        assertTrue(GridSize.LARGE.columns(1000f) > 1)
        assertTrue(GridSize.SMALL.columns(320f, 2f) < GridSize.SMALL.columns(320f))
        assertEquals(1, GridSize.LARGE.columns(100f))
    }
}
