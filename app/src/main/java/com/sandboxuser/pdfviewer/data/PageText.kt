package com.sandboxuser.pdfviewer.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.StringWriter
import kotlin.math.max
import kotlin.math.min

/** Coordinates are relative to the rendered, intrinsically rotated PDF page (0..1). */
data class TextBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun distance(x: Float, y: Float): Float {
        val dx = max(max(left - x, x - right), 0f)
        val dy = max(max(top - y, y - bottom), 0f)
        return dx * dx + dy * dy
    }
}
data class PdfWord(val text: String, val box: TextBox, val line: Int)
data class TextSelection(val page: Int, val words: List<PdfWord>) {
    val text: String get() = buildString {
        words.forEachIndexed { i, word ->
            if(i > 0) append(if(word.line == words[i - 1].line) " " else "\n")
            append(word.text)
        }
    }
    fun mark(color: Int) = Mark(page = page, kind = "highlight", points = emptyList(), color = color,
        text = text, boxes = words.map { it.box })
}
data class PageText(val words: List<PdfWord>, val unavailable: Boolean = false) {
    fun nearest(x: Float, y: Float, nearOnly: Boolean = false): Int? {
        val index = words.indices.minByOrNull { words[it].box.distance(x, y) } ?: return null
        return index.takeUnless { nearOnly && words[it].box.distance(x, y) > .035f * .035f }
    }
    fun select(page: Int, anchor: Int, end: Int) =
        TextSelection(page, words.subList(min(anchor, end), max(anchor, end) + 1))
}

internal fun extractPageText(context: Context, file: File, pageIndex: Int): PageText {
    PDFBoxResourceLoader.init(context.applicationContext)
    PDDocument.load(file, MemoryUsageSetting.setupMixed(8L * 1024 * 1024).setTempDir(context.cacheDir)).use { document ->
        if(!document.currentAccessPermission.canExtractContent()) return PageText(emptyList(), unavailable = true)
        val page = document.getPage(pageIndex)
        val width = page.cropBox.width
        val height = page.cropBox.height
        val rotation = ((page.rotation % 360) + 360) % 360
        val outputWidth = if(rotation % 180 == 0) width else height
        val outputHeight = if(rotation % 180 == 0) height else width
        val words = mutableListOf<PdfWord>()
        var line = 0
        val stripper = object : PDFTextStripper() {
            override fun writeLineSeparator() { line++ }
            override fun writeString(text: String, positions: MutableList<TextPosition>) {
                var buffer = StringBuilder()
                var bounds: TextBox? = null
                fun flush() {
                    val box = bounds
                    if(buffer.isNotBlank() && box != null && box.right > box.left && box.bottom > box.top)
                        words += PdfWord(buffer.toString(), box, line)
                    buffer = StringBuilder(); bounds = null
                }
                var previous: TextPosition? = null
                for(p in positions) {
                    val value = p.unicode.orEmpty()
                    if(value.isBlank()) { flush(); previous = null; continue }
                    previous?.let {
                        if(p.xDirAdj - (it.xDirAdj + it.widthDirAdj) > max(p.widthOfSpace * .4f, 1f)) flush()
                    }
                    // TextPosition direction coordinates ignore the page's /Rotate. Undo the text
                    // direction first, then apply /Rotate to match PdfRenderer, including crop boxes.
                    fun transform(u: Float, v: Float): Pair<Float, Float> {
                        val (x, y) = when(p.dir.toInt()) {
                            90 -> v to height - u
                            180 -> width - u to height - v
                            270 -> width - v to u
                            else -> u to v
                        }
                        val (rx, ry) = when(rotation) {
                            90 -> height - y to x
                            180 -> width - x to height - y
                            270 -> y to width - x
                            else -> x to y
                        }
                        return (rx / outputWidth).coerceIn(0f, 1f) to (ry / outputHeight).coerceIn(0f, 1f)
                    }
                    val h = max(p.heightDir, 1f)
                    val corners = listOf(
                        transform(p.xDirAdj, p.yDirAdj - h * 1.15f),
                        transform(p.xDirAdj + p.widthDirAdj, p.yDirAdj - h * 1.15f),
                        transform(p.xDirAdj, p.yDirAdj + h * .25f),
                        transform(p.xDirAdj + p.widthDirAdj, p.yDirAdj + h * .25f))
                    val b = TextBox(corners.minOf { it.first }, corners.minOf { it.second }, corners.maxOf { it.first }, corners.maxOf { it.second })
                    bounds = bounds?.let { TextBox(min(it.left, b.left), min(it.top, b.top), max(it.right, b.right), max(it.bottom, b.bottom)) } ?: b
                    buffer.append(value); previous = p
                }
                flush()
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        stripper.writeText(document, StringWriter())
        return PageText(words)
    }
}
