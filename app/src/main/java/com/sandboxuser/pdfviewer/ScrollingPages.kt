package com.sandboxuser.pdfviewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
internal fun ScrollingPages(book: Book, model: LibraryModel, initialPage: Int, request: Pair<Int, Int>?, dark: Boolean, rotation: Int,
    onPage: (Int) -> Unit, onNote: (Mark) -> Unit, onTap: () -> Unit) {
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
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().semantics { contentDescription = "Scrolling PDF pages" },
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
                    Box(Modifier.fillMaxWidth().aspectRatio(displayRatio)
                        .background(if(dark) Color(0xFF18201A) else Color.White), contentAlignment = Alignment.Center) {
                        if(image != null) RotatedPage(rotation, Modifier.fillMaxSize().testTag("PDF page surface ${index + 1}")) {
                            Image(image.asImageBitmap(), "PDF page ${index + 1} of ${book.pages}", Modifier.fillMaxSize(),
                                colorFilter = if(dark) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                                    -.78f,0f,0f,0f,223f, 0f,-.78f,0f,0f,228f, 0f,0f,-.78f,0f,219f, 0f,0f,0f,1f,0f))) else null)
                            Canvas(Modifier.fillMaxSize().pointerInput(marks) {
                                detectTapGestures { point ->
                                    val x = point.x / size.width; val y = point.y / size.height
                                    val note = marks.lastOrNull { it.kind == "note" && it.points.any { p -> abs(p.first-x) < .06f && abs(p.second-y) < .04f } }
                                    if(note != null) openNote(note) else tapPage()
                                }
                            }) { marks.forEach { drawAnnotation(it, accent) } }
                        } else if(failed) TextButton(onClick = { attempt++ }) { Text("Retry page ${index + 1}") }
                        else Text("Loading page ${index + 1}…", color = Muted)
                    }
                }
            }
        }
    }
}
