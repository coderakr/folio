package com.sandboxuser.pdfviewer

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandboxuser.pdfviewer.data.Book
import com.sandboxuser.pdfviewer.data.Mark
import com.sandboxuser.pdfviewer.ui.theme.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max

private val markColors = listOf(Color(0xFFF3C74E), Color(0xFF96BC88), Color(0xFFDEA3B3), Color(0xFF8FB5D8))

@Composable
fun ReaderScreen(book: Book, model: LibraryModel, initialPage: Int?, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, onBack: () -> Unit) {
    val dark = LocalDarkMode.current
    val accent = Terracotta
    var readerMenu by remember { mutableStateOf(false) }
    var originalColors by rememberSaveable(book.id) { mutableStateOf(false) }
    var fullControls by remember { mutableStateOf(true) }
    var page by rememberSaveable(book.id) { mutableIntStateOf((initialPage ?: book.page).coerceIn(0, book.pages - 1)) }
    var quarterTurns by rememberSaveable(book.id) { mutableIntStateOf(0) }
    val rotation = if(fullscreen) quarterTurns * 90 else 0
    var scrollMode by rememberSaveable(book.id) { mutableStateOf(false) }
    var scrollRequest by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    fun goToPage(target: Int) {
        page = target.coerceIn(0, book.pages - 1)
        scrollRequest = page to ((scrollRequest?.second ?: 0) + 1)
    }
    var tool by rememberSaveable(book.id) { mutableStateOf("read") }
    LaunchedEffect(fullscreen) { if(fullscreen) { tool = "read"; fullControls = true } }
    var color by rememberSaveable { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var pageMenu by remember { mutableStateOf(false) }
    var pageInput by remember { mutableStateOf("") }
    var marksMenu by remember { mutableStateOf(false) }
    var bookmarksMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Mark?>(null) }
    var noteText by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    LaunchedEffect(rotation) { zoom = 1f; pan = Offset.Zero; draft = emptyList() }
    var bitmap by remember(book.id, page) { mutableStateOf<Bitmap?>(null) }
    var renderError by remember(book.id, page) { mutableStateOf(false) }
    var renderAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(book.id, page, renderAttempt, scrollMode) {
        bitmap = null; renderError = false; zoom = 1f; pan = Offset.Zero
        if(scrollMode) return@LaunchedEffect
        try { bitmap = model.store.render(book.id, page, 1800) }
        catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: Exception) { renderError = true }
    }
    LaunchedEffect(page) { if(book.page != page) model.update(book.id) { it.copy(page = page) } }
    val currentMarks = book.marks.filter { it.page == page }
    fun addMark(mark: Mark) { model.update(book.id) { it.copy(marks = it.marks + mark) } }
    fun editNote(mark: Mark) { editing = mark; noteText = mark.text }
    Column(Modifier.fillMaxSize().background(Paper)) {
        if(!fullscreen) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction("back", "Back to library", onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(book.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if(model.saveFailed) Text("Not saved · Retry below", fontSize = 11.sp, color = Terracotta)
                else Text(if(model.saving) "Saving…" else "All changes saved on this device", fontSize = 10.sp, color = Muted)
            }
            IconAction("bookmark", if(page in book.bookmarks) "Remove page bookmark" else "Bookmark page", if(page in book.bookmarks) Terracotta else Muted) {
                model.update(book.id) { it.copy(bookmarks = if(page in it.bookmarks) it.bookmarks - page else it.bookmarks + page) }
            }
            IconAction("fullscreen", "Read in full screen", Muted) { onFullscreen(true) }
            Box {
                IconAction("more", "Reader options", Muted) { readerMenu = true }
                DropdownMenu(readerMenu, { readerMenu = false }) {
                    DropdownMenuItem(text = { Text("Notes & highlights") }, onClick = { marksMenu = true; readerMenu = false }, modifier = Modifier.semantics { contentDescription = "View notes and highlights" })
                    DropdownMenuItem(text = { Text(if(scrollMode) "Swipe pages" else "Scroll pages") }, onClick = { scrollRequest = null; scrollMode = !scrollMode; tool = "read"; readerMenu = false })
                    DropdownMenuItem(text = { Text("Appearance") }, onClick = { model.changeTheme(if(dark) "light" else "dark"); readerMenu = false })
                    if(dark) DropdownMenuItem(text = { Text(if(originalColors) "Use dark PDF colours" else "Use original PDF colours") }, onClick = { originalColors = !originalColors; readerMenu = false })
                }
            }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when(tool) { "highlight" -> "Drag across a line to highlight"; "ink" -> "Draw on the page with your finger"; "note" -> "Tap the page to leave a note"; else -> if(scrollMode) "Scroll vertically · Select a tool to annotate a page" else "Swipe pages · Pinch to zoom · Tap notes to edit" }, fontSize = 11.sp, color = Muted, modifier = Modifier.weight(1f))
            if(tool == "highlight" || tool == "ink") Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                markColors.forEachIndexed { i, c -> Box(Modifier.size(27.dp).clip(CircleShape).clickable { color = i }.border(if(color == i) 2.dp else 0.dp, if(color == i) Ink else Color.Transparent, CircleShape).padding(4.dp).background(c, CircleShape).semantics { contentDescription = "${listOf("Yellow", "Green", "Pink", "Blue")[i]} annotation colour" }) }
            }
        }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(ReaderBackground).clipToBounds(), contentAlignment = Alignment.Center) {
            val image = bitmap
            if(scrollMode) {
                ScrollingPages(book, model, page, scrollRequest, dark && !originalColors, rotation,
                    onPage = { page = it }, onNote = ::editNote, onTap = { if(fullscreen) fullControls = !fullControls })
            } else if(image != null) {
                val originalRatio = image.width.toFloat() / image.height
                val ratio = if(rotation % 180 == 0) originalRatio else 1f / originalRatio
                val pageWidth = minOf(maxWidth - 24.dp, (maxHeight - 24.dp) * ratio)
                val pageHeight = pageWidth / ratio
                Box(Modifier.fillMaxSize().pointerInput(tool, page, rotation) {
                    if(tool == "read") awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var travel = Offset.Zero
                        var multiple = false
                        val startedZoomed = zoom > 1f
                        do {
                            val event = awaitPointerEvent()
                            multiple = multiple || event.changes.count { it.pressed } > 1
                            val delta = event.calculatePan()
                            travel += delta
                            if(multiple || startedZoomed) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                val limitX = size.width * (zoom - 1) / 2
                                val limitY = size.height * (zoom - 1) / 2
                                pan = Offset((pan.x + delta.x).coerceIn(-limitX, limitX), (pan.y + delta.y).coerceIn(-limitY, limitY))
                            }
                            if(travel.getDistance() > viewConfiguration.touchSlop || multiple) event.changes.forEach { it.consume() }
                        } while(event.changes.any { it.pressed })
                        if(!multiple && !startedZoomed && abs(travel.x) > max(48.dp.toPx(), size.width * .15f) && abs(travel.x) > abs(travel.y) * 1.3f) {
                            goToPage(page + if(travel.x < 0) 1 else -1)
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                    RotatedPage(rotation, Modifier.size(pageWidth, pageHeight).testTag("PDF page surface ${page + 1}").graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y }) {
                        Image(image.asImageBitmap(), "PDF page ${page + 1} of ${book.pages}", Modifier.fillMaxSize(),
                            colorFilter = if(dark && !originalColors) ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                                -.78f,0f,0f,0f,223f, 0f,-.78f,0f,0f,228f, 0f,0f,-.78f,0f,219f, 0f,0f,0f,1f,0f
                            ))) else null)
                        Canvas(Modifier.fillMaxSize().semantics {
                            contentDescription = "Page annotation canvas"
                            if(fullscreen) customActions = listOf(
                                CustomAccessibilityAction("Previous page") { if(page > 0) goToPage(page - 1); true },
                                CustomAccessibilityAction("Next page") { if(page < book.pages - 1) goToPage(page + 1); true },
                                CustomAccessibilityAction("Exit full screen") { onFullscreen(false); true }
                            )
                        }
                            .pointerInput(tool, page, color) {
                                if(tool == "highlight" || tool == "ink") detectDragGestures(
                                    onDragStart = { point -> draft = listOf((point.x / size.width).coerceIn(0f,1f) to (point.y / size.height).coerceIn(0f,1f)) },
                                    onDrag = { change, _ -> change.consume(); draft = draft + ((change.position.x / size.width).coerceIn(0f,1f) to (change.position.y / size.height).coerceIn(0f,1f)) },
                                    onDragEnd = { if(draft.size > 1) addMark(Mark(page = page, kind = tool, points = if(tool == "highlight") listOf(draft.first(), draft.last()) else draft, color = color)); draft = emptyList() },
                                    onDragCancel = { draft = emptyList() }
                                )
                            }.pointerInput(tool, page, currentMarks, fullscreen, rotation) {
                                if(tool == "note" || tool == "read") detectTapGestures { point ->
                                    val x = point.x / size.width; val y = point.y / size.height
                                    val existing = currentMarks.lastOrNull { it.kind == "note" && it.points.any { p -> abs(p.first-x) < .06f && abs(p.second-y) < .04f } }
                                    if(existing != null) editNote(existing)
                                    else if(tool == "note") editNote(Mark(page = page, kind = "note", points = listOf(x to y)))
                                    else if(fullscreen) {
                                        val screenX = when(rotation) { 90 -> 1f - y; 180 -> 1f - x; 270 -> y; else -> x }
                                        if(screenX < .2f && zoom == 1f && page > 0) goToPage(page - 1)
                                        else if(screenX > .8f && zoom == 1f && page < book.pages - 1) goToPage(page + 1)
                                        else fullControls = !fullControls
                                    }
                                }
                            }) {
                            currentMarks.forEach { drawAnnotation(it, accent) }
                            if(draft.isNotEmpty()) drawAnnotation(Mark(page = page, kind = tool, points = draft, color = color), accent)
                        }
                    }
                }
                if(zoom > 1f) TextButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.align(if(fullscreen) Alignment.TopStart else Alignment.TopEnd).padding(8.dp).background(Paper, RoundedCornerShape(12.dp))) { Text("Fit page") }
            } else if(renderError) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("This page could not be displayed."); TextButton(onClick = { renderAttempt++ }) { Text("Try again") }
            } else CircularProgressIndicator()
            if(fullscreen) {
                Row(Modifier.align(Alignment.TopEnd).padding(12.dp).clip(RoundedCornerShape(24.dp)).background(Paper.copy(alpha = .92f)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.semantics { stateDescription = "Rotated $rotation degrees clockwise" }) {
                        IconAction("rotate", "Rotate pages clockwise") { quarterTurns = (quarterTurns + 1) % 4; fullControls = true }
                    }
                    Text("$rotation°", fontSize = 11.sp, color = Ink)
                    IconAction("collapse", "Exit full screen", onClick = { onFullscreen(false) })
                }
                if(fullControls) Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 440.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Paper.copy(alpha = .94f)).padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconAction("left", "Previous page", enabled = page > 0) { goToPage(page - 1) }
                        TextButton(onClick = { pageInput = (page + 1).toString(); pageMenu = true }) { Text("${page + 1}  /  ${book.pages}", color = Ink) }
                        IconAction("right", "Next page", enabled = page < book.pages - 1) { goToPage(page + 1) }
                    }
                    PageScrubber(page, book.pages, onPageSelected = ::goToPage)
                }
            }
        }
        if(!fullscreen) {
        PageScrubber(page, book.pages, Modifier.padding(horizontal = 18.dp)) { target ->
            draft = emptyList()
            goToPage(target)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 0.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconAction("left", "Previous page", enabled = page > 0) { goToPage(page - 1); draft = emptyList() }
            TextButton(onClick = { pageInput = (page + 1).toString(); pageMenu = true }) { Text("${page + 1}  /  ${book.pages}", color = Ink, fontSize = 13.sp); Spacer(Modifier.width(5.dp)); Glyph("down", Muted, Modifier.size(14.dp)) }
            IconAction("bookmark", "View bookmarked pages", Muted) { bookmarksMenu = true }
            IconAction("zoom", "Zoom page", Muted, enabled = tool == "read" && !scrollMode) { zoom = if(zoom >= 3f) 1f else zoom + 1f; pan = Offset.Zero }
            IconAction("right", "Next page", enabled = page < book.pages - 1) { goToPage(page + 1); draft = emptyList() }
        }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("read" to "book", "highlight" to "highlight", "ink" to "pen", "note" to "note").forEach { (id, icon) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if(tool == id) Sage else Color.Transparent).clickable {
                    if(id != "read") scrollMode = false
                    tool = id; zoom = 1f; pan = Offset.Zero; draft = emptyList()
                }.padding(vertical = 6.dp).semantics { contentDescription = "${if(id == "ink") "Draw" else id.replaceFirstChar { it.uppercase() }} tool" }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(icon, if(tool == id) Terracotta else Muted)
                    Text(if(id == "ink") "Draw" else id.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, lineHeight = 13.sp, color = if(tool == id) Terracotta else Muted, modifier = Modifier.padding(top = 4.dp))
                }
            }
            IconAction("undo", "Undo last annotation on this page", Muted, enabled = currentMarks.isNotEmpty()) {
                val id = currentMarks.last().id; model.update(book.id) { it.copy(marks = it.marks.filterNot { m -> m.id == id }) }
            }
        }
        }
        if(model.saveFailed) TextButton(onClick = model::retrySave, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Retry save") }
    }
    if(pageMenu) AlertDialog(onDismissRequest = { pageMenu = false }, title = { Text("Go to page") }, text = { OutlinedTextField(pageInput, { pageInput = it.filter(Char::isDigit).take(7) }, label = { Text("Page 1–${book.pages}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, isError = pageInput.toIntOrNull()?.let { it !in 1..book.pages } ?: true) }, confirmButton = { TextButton(enabled = pageInput.toIntOrNull()?.let { it in 1..book.pages } == true, onClick = { goToPage(pageInput.toInt() - 1); pageMenu = false }) { Text("Go") } }, dismissButton = { TextButton(onClick = { pageMenu = false }) { Text("Cancel") } })
    editing?.let { mark ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(if(book.marks.any { it.id == mark.id }) "Edit note" else "A thought to keep") }, text = {
            OutlinedTextField(noteText, { noteText = it.take(10000) }, placeholder = { Text("What's on your mind?") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
        }, confirmButton = { TextButton(enabled = noteText.isNotBlank(), onClick = {
            model.update(book.id) { b -> b.copy(marks = b.marks.filterNot { it.id == mark.id } + mark.copy(text = noteText.trim())) }; editing = null
        }) { Text("Save note") } }, dismissButton = { Row { if(book.marks.any { it.id == mark.id }) TextButton(onClick = { model.update(book.id) { it.copy(marks = it.marks.filterNot { m -> m.id == mark.id }) }; editing = null }) { Text("Delete") }; TextButton(onClick = { editing = null }) { Text("Cancel") } } })
    }
    if(marksMenu) AlertDialog(onDismissRequest = { marksMenu = false }, title = { Text("Notes & highlights") }, text = {
        if(book.marks.isEmpty()) Text("Your annotations will appear here. Choose Highlight, Draw or Note to get started.")
        else LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(book.marks, key = { it.id }) { mark -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { goToPage(mark.page); marksMenu = false; if(mark.kind == "note") editNote(mark) }.padding(vertical = 13.dp)) {
                    Text("${if(mark.kind == "ink") "Drawing" else mark.kind.replaceFirstChar { it.uppercase() }} · Page ${mark.page + 1}", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    if(mark.text.isNotEmpty()) Text(mark.text, maxLines = 3, overflow = TextOverflow.Ellipsis, color = Muted, fontSize = 12.sp)
                }
                IconAction("delete", "Delete annotation", Muted) { model.update(book.id) { it.copy(marks = it.marks.filterNot { m -> m.id == mark.id }) } }
            } }
        }
    }, confirmButton = { TextButton(onClick = { marksMenu = false }) { Text("Done") } })
    if(bookmarksMenu) AlertDialog(onDismissRequest = { bookmarksMenu = false }, title = { Text("Bookmarked pages") }, text = {
        if(book.bookmarks.isEmpty()) Text("Tap the bookmark at the top of a page to save your place here.")
        else LazyColumn(Modifier.heightIn(max = 350.dp)) { items(book.bookmarks.sorted()) { p -> TextButton(onClick = { goToPage(p); bookmarksMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Page ${p + 1}") } } }
    }, confirmButton = { TextButton(onClick = { bookmarksMenu = false }) { Text("Done") } })
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAnnotation(mark: Mark, accent: Color) {
    if(mark.points.isEmpty()) return
    val c = markColors[mark.color.coerceIn(0,3)]
    val points = mark.points.map { Offset(it.first * size.width, it.second * size.height) }
    when(mark.kind) {
        "highlight" -> {
            val a = points.first(); val b = points.last()
            val left = min(a.x, b.x); val top = min(a.y, b.y)
            drawRect(c.copy(alpha = .38f), Offset(left, top), Size(max(abs(a.x-b.x), size.width * .008f), max(abs(a.y-b.y), size.height * .016f)))
        }
        "ink" -> {
            val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x,it.y) } }
            drawPath(path, c.copy(alpha = 1f), style = Stroke(size.width * .004f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        "note" -> {
            val p = points.first(); val r = size.width * .024f
            drawCircle(accent, r, p)
            drawLine(Color.White, p + Offset(-r*.4f,-r*.25f), p + Offset(r*.4f,-r*.25f), r*.13f)
            drawLine(Color.White, p + Offset(-r*.4f,r*.2f), p + Offset(r*.2f,r*.2f), r*.13f)
        }
    }
}

