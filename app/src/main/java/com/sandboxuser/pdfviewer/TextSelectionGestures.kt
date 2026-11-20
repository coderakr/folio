package com.sandboxuser.pdfviewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.sandboxuser.pdfviewer.data.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

/** Shares the annotation canvas's hit target so taps, zoom and long-press selection can coexist. */
@Composable
internal fun Modifier.pdfTextSelection(
    page: Int, tool: String, text: PageText?,
    onSelection: (TextSelection?) -> Unit,
    onHighlight: (TextSelection) -> Unit,
    onUnavailable: (String) -> Unit
): Modifier {
    val select by rememberUpdatedState(onSelection)
    val highlight by rememberUpdatedState(onHighlight)
    val unavailable by rememberUpdatedState(onUnavailable)
    return pointerInput(page, tool, text) {
        if(tool != "read" && tool != "highlight") return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = if(tool == "read") awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture else down
            start.consume()
            val layout = text
            if(layout == null) {
                unavailable("Preparing text selection. Please try again in a moment.")
                return@awaitEachGesture
            }
            val origin = Offset(start.position.x / size.width, start.position.y / size.height)
            val anchor = layout.nearest(origin.x, origin.y, nearOnly = true)
            val area = tool == "highlight" && layout.words.isEmpty()
            if(anchor == null && !area) {
                unavailable(if(layout.words.isEmpty()) "No selectable text on this page. Use Highlight to mark an area." else "Touch a word to start selecting.")
                return@awaitEachGesture
            }
            var current: TextSelection? = anchor?.let { layout.select(page, it, it) }
            select(current)
            var cancelled = false
            try {
                while(true) {
                    val event = awaitPointerEvent()
                    if(event.changes.count { it.pressed } > 1) { cancelled = true; break }
                    val change = event.changes.firstOrNull { it.id == start.id } ?: run { cancelled = true; break }
                    val end = Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                    current = if(area) {
                        if(abs(end.x - origin.x) + abs(end.y - origin.y) > .005f) {
                            val left = min(origin.x, end.x).coerceIn(0f, .992f)
                            val top = min(origin.y, end.y).coerceIn(0f, .988f)
                            val box = TextBox(left, top,
                                max(max(origin.x, end.x), left + .008f).coerceAtMost(1f),
                                max(max(origin.y, end.y), top + .012f).coerceAtMost(1f))
                            TextSelection(page, listOf(PdfWord("", box, 0)))
                        } else null
                    } else layout.select(page, anchor!!, layout.nearest(end.x, end.y)!!)
                    select(current)
                    change.consume()
                    if(!change.pressed) break
                }
                if(!cancelled && tool == "highlight") current?.let(highlight)
            } finally {
                if(cancelled || tool == "highlight") select(null)
            }
        }
    }
}
