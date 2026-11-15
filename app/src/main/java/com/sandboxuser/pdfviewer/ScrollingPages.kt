package com.sandboxuser.pdfviewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandboxuser.pdfviewer.data.Book
import com.sandboxuser.pdfviewer.data.Mark
import com.sandboxuser.pdfviewer.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@Composable
internal fun ScrollingPages(book: Book, model: LibraryModel, initialPage: Int, request: Pair<Int, Int>?, pageColors: PdfPageColors, rotation: Int, fullscreen: Boolean,
    onPage: (Int) -> Unit, onNote: (Mark) -> Unit, onTap: () -> Unit) {
    var zoom by remember(book.id, rotation, fullscreen, request) { mutableFloatStateOf(1f) }
    var pan by remember(book.id, rotation, fullscreen, request) { mutableStateOf(Offset.Zero) }
    var zoomedPage by remember(book.id, rotation, fullscreen, request) { mutableStateOf<Int?>(null) }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val updatePage by rememberUpdatedState(onPage)
    val openNote by rememberUpdatedState(onNote)
    val tapPage by rememberUpdatedState(onTap)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { updatePage(it) }
    }
    // Navigation requests are separate from reading progress so scrolling never jumps back.
    LaunchedEffect(request) { request?.let { state.scrollToItem(it.first) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Leave enough room after a short final page to align it at the top when jumping.
        // Without this, the list clamps to the bottom and reports the preceding page as current.
        val minimumLastPageHeight = (maxHeight - 32.dp).coerceAtLeast(0.dp)
        LazyColumn(state = state, userScrollEnabled = zoom == 1f, modifier = Modifier.fillMaxSize().semantics { contentDescription = "Scrolling PDF pages" },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(book.pages, key = { it }) { index ->
                var bitmap by remember(book.id, index) { mutableStateOf<Bitmap?>(null) }
                var failed by remember { mutableStateOf(false) }
                var attempt by remember { mutableIntStateOf(0) }
                LaunchedEffect(book.id, index, attempt) {
                    failed = false
                    try { bitmap = model.store.render(book.id, index, 1200) }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { failed = true }
                }
                val marks = book.marks.filter { it.page == index }
                val accent = Terracotta
                Column(if(index == book.pages - 1) Modifier.heightIn(min = minimumLastPageHeight) else Modifier) {
                    Text("Page ${index + 1} of ${book.pages}", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(bottom = 6.dp))
                    val image = bitmap
                    val originalRatio = image?.let { it.width.toFloat() / it.height } ?: .75f
                    val displayRatio = if(rotation % 180 == 0) originalRatio else 1f / originalRatio
                    Box(Modifier.fillMaxWidth().aspectRatio(displayRatio).clipToBounds()
                        .testTag("PDF zoom viewport ${index + 1}").semantics { stateDescription = "Zoom ${(if(zoomedPage == index) zoom * 100 else 100f).toInt()} percent" }
                        .background(pageColors.paper).pointerInput(index, rotation, fullscreen) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var travel = Offset.Zero
                                var multiple = false
                                do {
                                    val event = awaitPointerEvent()
                                    multiple = multiple || event.changes.count { it.pressed } > 1
                                    val delta = event.calculatePan()
                                    travel += delta
                                    if(multiple || (zoom > 1f && zoomedPage == index)) {
                                        zoomedPage = index
                                        zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                        pan = clampPagePan(pan + delta, size.width.toFloat(), size.height.toFloat(), zoom)
                                        if(multiple || travel.getDistance() > viewConfiguration.touchSlop) event.changes.forEach { it.consume() }
                                    }
                                } while(event.changes.any { it.pressed })
                            }
                        }, contentAlignment = Alignment.Center) {
                        if(image != null) RotatedPage(rotation, Modifier.fillMaxSize().testTag("PDF page surface ${index + 1}").graphicsLayer {
                            scaleX = if(zoomedPage == index) zoom else 1f; scaleY = scaleX
                            translationX = if(zoomedPage == index) pan.x else 0f; translationY = if(zoomedPage == index) pan.y else 0f
                        }) {
                            Image(image.asImageBitmap(), "PDF page ${index + 1} of ${book.pages}", Modifier.fillMaxSize(),
                                colorFilter = pageColors.filter())
                            Canvas(Modifier.fillMaxSize().pointerInput(marks, fullscreen, rotation) {
                                detectTapGestures(
                                    onDoubleTap = if(fullscreen) { point ->
                                        if(zoom > 1f && zoomedPage == index) { zoom = 1f; pan = Offset.Zero; zoomedPage = null }
                                        else { zoomedPage = index; zoom = 2.5f; pan = focusedPagePan(point, size, rotation, zoom) }
                                    } else null,
                                    onTap = { point ->
                                    val x = point.x / size.width; val y = point.y / size.height
                                    val note = marks.lastOrNull { it.kind == "note" && it.points.any { p -> abs(p.first-x) < .06f && abs(p.second-y) < .04f } }
                                    if(note != null) openNote(note) else tapPage()
                                })
                            }) { marks.forEach { drawAnnotation(it, accent) } }
                        } else if(failed) TextButton(onClick = { attempt++ }) { Text("Retry page ${index + 1}") }
                        else Text("Loading page ${index + 1}…", color = Muted)
                    }
                }
            }
        }
        if(!fullscreen && zoom > 1f) TextButton(onClick = { zoom = 1f; pan = Offset.Zero; zoomedPage = null },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Paper, RoundedCornerShape(12.dp))) { Text("Fit page") }

    }
}
