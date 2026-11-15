package com.sandboxuser.pdfviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PageZoomTest {
    private fun assertEquals(expected: Offset, actual: Offset) {
        org.junit.Assert.assertEquals(expected.x, actual.x, .001f)
        org.junit.Assert.assertEquals(expected.y, actual.y, .001f)
    }
    @Test fun tappedPointMovesToCentreForEveryRotation() {
        val point = Offset(60f, 100f)
        val size = IntSize(200, 400)
        assertEquals(Offset(100f, 250f), focusedPagePan(point, size, 0, 2.5f))
        assertEquals(Offset(-250f, 100f), focusedPagePan(point, size, 90, 2.5f))
        assertEquals(Offset(-100f, -250f), focusedPagePan(point, size, 180, 2.5f))
        assertEquals(Offset(250f, -100f), focusedPagePan(point, size, 270, 2.5f))
    }
    @Test fun panStopsAtPageEdgesAndFitResetsIt() {
        assertEquals(Offset(150f, 300f), focusedPagePan(Offset.Zero, IntSize(200, 400), 0, 2.5f))
        assertEquals(Offset.Zero, clampPagePan(Offset(300f, -400f), 200f, 400f, 1f))
        assertEquals(Offset.Zero, focusedPagePan(Offset.Zero, IntSize.Zero, 90, 2.5f))
    }
}
