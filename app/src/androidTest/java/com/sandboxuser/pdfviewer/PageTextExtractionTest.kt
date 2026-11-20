package com.sandboxuser.pdfviewer

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sandboxuser.pdfviewer.data.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageTextExtractionTest {
    @Test fun textBoxesMatchRenderedCroppedPagesAtEveryIntrinsicRotation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LibraryStore(context)
        PDFBoxResourceLoader.init(context)
        for(rotation in listOf(0, 90, 180, 270)) {
            val id = "extraction-fixture-$rotation"
            try {
                PDDocument().use { doc ->
                    val page = PDPage(PDRectangle(600f, 800f))
                    page.cropBox = PDRectangle(100f, 200f, 400f, 500f)
                    page.rotation = rotation
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { content ->
                        content.beginText(); content.setFont(PDType1Font.HELVETICA, 24f)
                        content.newLineAtOffset(150f, 600f); content.showText("Alpha beta")
                        content.newLineAtOffset(0f, -40f); content.showText("Gamma delta")
                        content.endText()
                    }
                    doc.save(store.file(id))
                }
                val extracted = store.pageText(id, 0)
                assertEquals(listOf("Alpha", "beta", "Gamma", "delta"), extracted.words.map { it.text })
                assertTrue(extracted.words[0].line != extracted.words[2].line)
                val bitmap = store.render(id, 0, 800)
                for(word in extracted.words) {
                    val b = word.box
                    var dark = 0
                    for(y in (b.top * bitmap.height).toInt() until (b.bottom * bitmap.height).toInt())
                        for(x in (b.left * bitmap.width).toInt() until (b.right * bitmap.width).toInt())
                            if(Color.red(bitmap.getPixel(x, y)) < 120) dark++
                    assertTrue("${word.text} at rotation $rotation should overlap ink; $b", dark > 20)
                }
                bitmap.recycle()
            } finally { store.file(id).delete() }
        }
    }
    @Test fun imageOnlyPageHasNoPretendSelectableText() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LibraryStore(context)
        val id = "empty-selection-fixture"
        try {
            val pdf = android.graphics.pdf.PdfDocument()
            try {
                val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 400, 1).create())
                page.canvas.drawRect(20f, 20f, 100f, 100f, android.graphics.Paint())
                pdf.finishPage(page)
                store.file(id).outputStream().use { pdf.writeTo(it) }
            } finally { pdf.close() }
            assertTrue(store.pageText(id, 0).words.isEmpty())
        } finally { store.file(id).delete() }
    }
}
