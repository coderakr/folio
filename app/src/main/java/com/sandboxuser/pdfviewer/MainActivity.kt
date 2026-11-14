package com.sandboxuser.pdfviewer

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.stateDescription
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.sandboxuser.pdfviewer.data.Book
import com.sandboxuser.pdfviewer.data.Folder
import com.sandboxuser.pdfviewer.data.folderTrail
import com.sandboxuser.pdfviewer.data.folderPath
import com.sandboxuser.pdfviewer.data.LibraryStore
import com.sandboxuser.pdfviewer.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[LibraryModel::class.java]
        if(savedInstanceState == null) acceptPdfIntent(intent, model)
        setContent { PdfviewerTheme(model.theme, model.palette) { FolioApp(model) } }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptPdfIntent(intent, ViewModelProvider(this)[LibraryModel::class.java])
    }
    @Suppress("DEPRECATION")
    private fun acceptPdfIntent(intent: Intent, model: LibraryModel) {
        val uris = when(intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }.ifEmpty {
            if(intent.action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE))
                intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
            else emptyList()
        }.filter { it.scheme == "content" || it.scheme == "file" }.distinct()
        if(uris.isNotEmpty()) model.openExternal(uris)
    }
}

@Composable
fun FolioApp(model: LibraryModel) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var jumpPage by rememberSaveable { mutableStateOf<Int?>(null) }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var importFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val activity = checkNotNull(androidx.activity.compose.LocalActivity.current)
    val dark = LocalDarkMode.current
    DisposableEffect(fullscreen, dark) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if(fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val snack = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { model.import(it, importFolder) }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if(uri != null) model.importDirectory(uri, importFolder) }
    LaunchedEffect(model) { for (message in model.messages) snack.showSnackbar(message) }
    LaunchedEffect(model) { for(id in model.openRequests) { selected = id; jumpPage = null; fullscreen = false } }
    val book = model.books.firstOrNull { it.id == selected }
    BackHandler(selected != null) { if(fullscreen) fullscreen = false else { selected = null; jumpPage = null } }
    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = Paper, contentWindowInsets = if(fullscreen) WindowInsets(0,0,0,0) else WindowInsets.safeDrawing) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                model.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                model.loadFailed -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(model.error.orEmpty()); Button(onClick = model::reload) { Text("Retry") }
                }
                book != null -> ReaderScreen(book, model, jumpPage, fullscreen, { fullscreen = it }, onBack = { selected = null; jumpPage = null; fullscreen = false })
                else -> LibraryScreen(model, folderId, { folderId = it }, onAdd = { destination -> importFolder = destination; picker.launch(arrayOf("application/pdf")) }, onImportDirectory = { destination -> importFolder = destination; directoryPicker.launch(null) }, onOpen = { b, page ->
                    model.open(b.id); jumpPage = page; selected = b.id
                })
            }
        }
    }
    model.directoryReport?.let { report -> AlertDialog(onDismissRequest = model::dismissDirectoryReport,
        title = { Text("Folder import") }, text = { Text(report, modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = model::dismissDirectoryReport) { Text("Done") } }) }
}

@Composable
fun IconAction(icon: String, label: String, tint: Color = Ink, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = label }) {
        Glyph(icon, if (enabled) tint else Muted.copy(alpha = .35f))
    }
}

@Composable
fun LibraryScreen(model: LibraryModel, folderId: String?, onFolder: (String?) -> Unit, onAdd: (String?) -> Unit, onImportDirectory: (String?) -> Unit, onOpen: (Book, Int?) -> Unit) {
    var themesMenu by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var folderGrid by rememberSaveable { mutableStateOf(true) }
    val dark = LocalDarkMode.current
    var grid by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var menuBook by remember { mutableStateOf<Book?>(null) }
    var renameBook by remember { mutableStateOf<Book?>(null) }
    var removeBook by remember { mutableStateOf<Book?>(null) }
    var moveBook by remember { mutableStateOf<Book?>(null) }
    var rename by remember { mutableStateOf("") }
    var folderEditor by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var editFolder by remember { mutableStateOf<Folder?>(null) }
    var folderError by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf(false) }
    val folder = model.folders.firstOrNull { it.id == folderId }
    val inFolder = tab == 0 && folder != null
    val children = model.folders.filter { it.parentId == if(inFolder) folderId else null }
    BackHandler(inFolder) { onFolder(folder?.parentId); query = "" }
    val filtered = model.books.filter {
        (tab != 1 || it.favorite) && (!inFolder || it.folderId == folderId) && it.title.contains(query, true)
    }.let { when(sort) { 1 -> it.sortedBy { b -> b.title.lowercase() }; 2 -> it.sortedByDescending { b -> b.added }; else -> it.sortedByDescending { b -> b.opened } } }
    fun newFolder() { editFolder = null; folderName = ""; folderError = false; folderEditor = true }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if(inFolder) IconAction("back", "Back to parent folder") { onFolder(folder?.parentId); query = "" }
            else Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Terracotta), contentAlignment = Alignment.Center) { Glyph("book", MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.width(8.dp))
            LibrarySearch(query, { query = it }, if(tab == 2) "Search PDFs or notes" else if(inFolder) "Search this folder" else "Search your PDFs", Modifier.weight(1f))
            IconAction(if(dark) "moon" else "sun", "Appearance", Muted) { model.changeTheme(if(dark) "light" else "dark") }
            Box {
                Button(onClick = { addMenu = true }, enabled = !model.importing, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(42.dp)) {
                    if(model.importing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Glyph("plus", MaterialTheme.colorScheme.onPrimary, Modifier.size(17.dp))
                    Text(if(model.importing) " Adding…" else " Add", fontSize = 12.sp)
                    Glyph("down", MaterialTheme.colorScheme.onPrimary, Modifier.size(15.dp))
                }
                DropdownMenu(addMenu, { addMenu = false }) {
                    DropdownMenuItem(text = { Text("Add PDFs") }, onClick = { addMenu = false; onAdd(if(inFolder) folderId else null) })
                    DropdownMenuItem(text = { Column { Text("Import folder"); Text("Includes PDFs and all subfolders", fontSize = 11.sp, color = Muted) } }, onClick = { addMenu = false; onImportDirectory(if(inFolder) folderId else null) })
                }
            }
        }
        model.directoryProgress?.let { progress ->
            Row(Modifier.fillMaxWidth().background(Sage).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(progress, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                TextButton(onClick = model::stopDirectoryImport) { Text("Stop") }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if(inFolder) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onFolder(null); query = "" }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Library", fontSize = 12.sp) }
                    model.folders.folderTrail(folderId).forEach { ancestor ->
                        Glyph("right", Muted, Modifier.size(13.dp))
                        TextButton(onClick = { onFolder(ancestor.id); query = "" }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(ancestor.name, fontSize = 12.sp, maxLines = 1) }
                    }
                }
            }
            if(inFolder || tab != 0) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(when { inFolder -> folder!!.name; tab == 1 -> "The keepers."; tab == 2 -> "Your thoughts."; else -> "Library" }, fontSize = 29.sp, fontFamily = FontFamily.Serif, lineHeight = 34.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(when { inFolder -> "${filtered.size} PDFs · A shelf of your own"; tab == 1 -> "Good reads, always within reach."; tab == 2 -> "Ideas you wanted to remember."; else -> "" }, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
                    }
                    if(inFolder) Box {
                        IconAction("more", "Folder options") { folderMenu = true }
                        DropdownMenu(folderMenu, { folderMenu = false }) {
                            DropdownMenuItem(text = { Text("Rename folder") }, enabled = !model.importing, onClick = { editFolder = folder; folderName = folder!!.name; folderError = false; folderEditor = true; folderMenu = false })
                            DropdownMenuItem(text = { Text("Delete folder") }, enabled = !model.importing, onClick = { deleteFolder = true; folderMenu = false })
                        }
                    }
                }
            }
            if(tab == 0 && query.isBlank()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if(inFolder) "Subfolders" else "Your folders", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { newFolder() }, enabled = !model.importing) { Glyph("plus", Terracotta, Modifier.size(16.dp)); Text(" New folder", fontSize = 12.sp) }
                        Box(Modifier.semantics { stateDescription = if(folderGrid) "Folders shown in a grid" else "Folders shown in a list" }) {
                            IconAction(if(folderGrid) "list" else "grid", if(folderGrid) "Show folders as list" else "Show folders as grid", Muted) { folderGrid = !folderGrid }
                        }
                    }
                }
                if(children.isEmpty()) item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Sage).clickable(enabled = !model.importing) { newFolder() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph("folder", Terracotta, Modifier.size(28.dp))
                        Column(Modifier.padding(start = 12.dp)) { Text(if(inFolder) "Organise this section" else "A place for every subject", fontSize = 14.sp, fontWeight = FontWeight.Medium); Text(if(inFolder) "Create a subfolder or import a folder here." else "Create Science, Maths, Art, or your own.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                } else if(folderGrid) items(children.chunked(2), key = { "folders-${it.first().id}" }) { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { f ->
                            FolderTile(f, model.books.count { it.folderId == f.id }, model.folders.count { it.parentId == f.id }, true, Modifier.weight(1f)) { onFolder(f.id); query = "" }
                        }
                        if(pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                } else items(children, key = { "folder-${it.id}" }) { f ->
                    FolderTile(f, model.books.count { it.folderId == f.id }, model.folders.count { it.parentId == f.id }, false, Modifier.fillMaxWidth()) { onFolder(f.id); query = "" }
                }
            }
            if(tab != 2) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if(tab == 1) "Favourites" else if(inFolder) "In this folder" else "All PDFs", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("  ${filtered.size}", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { sortMenu = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(listOf("Recent", "A–Z", "Newest")[sort], color = Muted, fontSize = 12.sp); Glyph("down", Muted, Modifier.size(15.dp)) }
                            DropdownMenu(sortMenu, { sortMenu = false }) { listOf("Recently read", "Title A–Z", "Recently added").forEachIndexed { i, title -> DropdownMenuItem(text = { Text(title) }, onClick = { sort = i; sortMenu = false }) } }
                        }
                        IconAction(if(grid) "list" else "grid", if(grid) "List view" else "Grid view", Muted) { grid = !grid }
                    }
                }
                if(filtered.isEmpty() && (!inFolder || children.isEmpty() || query.isNotEmpty())) item { EmptyPanel(if(query.isNotEmpty()) "No matching reads" else if(inFolder) "Make room for a new read" else if(tab == 1) "Save your favourites" else "Your next read belongs here", if(inFolder) "Use Add to import PDFs or a directory into this folder. You can also move PDFs here from All PDFs." else if(tab == 1) "Tap the star on a PDF to keep it here." else "Add a PDF or try a different search.") }
                if(grid) items(filtered.chunked(2), key = { it.first().id }) { pair -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    pair.forEach { b -> Box(Modifier.weight(1f)) { BookCard(b, model.store, { onOpen(b, null) }, { model.update(b.id) { it.copy(favorite = !it.favorite) } }, { menuBook = b }) } }
                    if(pair.size == 1) Spacer(Modifier.weight(1f))
                } } else items(filtered, key = { it.id }) { b ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardSurface).border(1.dp, Line, RoundedCornerShape(14.dp)).clickable { onOpen(b, null) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        PdfCover(b, model.store, Modifier.width(46.dp).height(64.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(b.title, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            val location = model.folders.folderPath(b.folderId)
                            Text("${b.pages} pages · $location", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if(b.opened > 0) Text("Continue on page ${b.page + 1}", color = Terracotta, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        IconAction("star", if(b.favorite) "Unfavourite ${b.title}" else "Favourite ${b.title}", if(b.favorite) Terracotta else Muted) { model.update(b.id) { it.copy(favorite = !it.favorite) } }
                        IconAction("more", "Options for ${b.title}", Muted) { menuBook = b }
                    }
                }
            } else {
                val notes = model.books.flatMap { b -> b.marks.map { b to it } }.filter { (b,m) -> b.title.contains(query, true) || m.text.contains(query, true) }.reversed()
                if(notes.isEmpty()) item { EmptyPanel("A thought worth keeping?", "Highlights, drawings and notes from your PDFs will appear here.") }
                items(notes, key = { it.second.id }) { (b,m) -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardSurface).border(1.dp, Line, RoundedCornerShape(14.dp)).clickable { onOpen(b, m.page) }.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Glyph(if(m.kind == "note") "note" else if(m.kind == "ink") "pen" else "highlight", Terracotta); Text("  ${m.kind.replaceFirstChar { it.uppercase() }} · Page ${m.page + 1}", color = Terracotta, fontSize = 12.sp) }
                    if(m.text.isNotBlank()) Text(m.text, modifier = Modifier.padding(top = 12.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(b.title, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                } }
            }
        }
        if(model.saveFailed) TextButton(onClick = model::retrySave, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Changes not saved · Retry save") }
        HorizontalDivider(color = Line)
        Row(Modifier.fillMaxWidth().background(CardSurface).padding(vertical = 5.dp)) {
            listOf("Library" to "library", "Favourites" to "star", "My notes" to "note").forEachIndexed { i, (label, icon) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { tab = i; query = ""; onFolder(null) }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(56.dp).height(30.dp).background(if(tab == i) Sage else Color.Transparent, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Glyph(icon, if(tab == i) Terracotta else Muted, Modifier.size(21.dp)) }
                    Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = if(tab == i) Terracotta else Muted, fontWeight = if(tab == i) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 3.dp))
                }
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { themesMenu = true }.padding(vertical = 7.dp).semantics { contentDescription = "Choose colour theme" }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(56.dp).height(30.dp), contentAlignment = Alignment.Center) { Glyph("palette", Muted, Modifier.size(21.dp)) }
                Text("Themes", fontSize = 11.sp, lineHeight = 14.sp, color = Muted, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
    if(themesMenu) ThemePicker(model.palette, model::changePalette) { themesMenu = false }
    menuBook?.let { b -> AlertDialog(onDismissRequest = { menuBook = null }, title = { Text(b.title) }, text = { Column {
        TextButton(onClick = { moveBook = b; menuBook = null }) { Glyph("folder", Terracotta); Text("  Move to folder") }
        TextButton(onClick = { rename = b.title; renameBook = b; menuBook = null }) { Glyph("pen", Terracotta); Text("  Rename PDF") }
        TextButton(onClick = { removeBook = b; menuBook = null }) { Glyph("delete", Terracotta); Text("  Remove from library") }
    } }, confirmButton = { TextButton(onClick = { menuBook = null }) { Text("Done") } }) }
    moveBook?.let { b -> AlertDialog(onDismissRequest = { moveBook = null }, title = { Text("Move to folder") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { TextButton(onClick = { model.moveBook(b.id, null); moveBook = null }, modifier = Modifier.fillMaxWidth()) { Text("Unfiled (no folder)") } }
            items(model.folders.sortedBy { model.folders.folderPath(it.id).lowercase() }, key = { it.id }) { f -> TextButton(onClick = { model.moveBook(b.id, f.id); moveBook = null }, modifier = Modifier.fillMaxWidth()) { Glyph("folder", Terracotta); Text("  ${model.folders.folderPath(f.id)}") } }
            item { TextButton(onClick = { newFolder() }, enabled = !model.importing) { Text("+ Create a folder") } }
        }
    }, confirmButton = { TextButton(onClick = { moveBook = null }) { Text("Cancel") } }) }
    if(folderEditor) AlertDialog(onDismissRequest = { folderEditor = false }, title = { Text(if(editFolder == null) "New folder" else "Rename folder") }, text = { Column {
        OutlinedTextField(folderName, { folderName = it.take(60); folderError = false }, label = { Text("Folder name") }, singleLine = true, isError = folderError, supportingText = { if(folderError) Text("A folder with this name already exists in this section.") })
        if(editFolder == null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Science", "Maths", "Art").forEach { name -> SuggestionChip(onClick = { folderName = name; folderError = false }, label = { Text(name, fontSize = 12.sp) }) } }
    } }, confirmButton = { TextButton(enabled = folderName.isNotBlank(), onClick = { if(model.saveFolder(folderName, editFolder?.id, if(inFolder) folderId else null)) folderEditor = false else folderError = true }) { Text(if(editFolder == null) "Create folder" else "Save") } }, dismissButton = { TextButton(onClick = { folderEditor = false }) { Text("Cancel") } })
    if(deleteFolder && folder != null) AlertDialog(onDismissRequest = { deleteFolder = false }, title = { Text("Delete ${folder.name}?") }, text = { Text("This folder and all of its subfolders will be removed. All their PDFs and annotations will be kept in All PDFs as unfiled documents.") }, confirmButton = { TextButton(onClick = { model.removeFolder(folder.id); onFolder(folder.parentId); deleteFolder = false }) { Text("Delete folder") } }, dismissButton = { TextButton(onClick = { deleteFolder = false }) { Text("Cancel") } })
    renameBook?.let { b -> AlertDialog(onDismissRequest = { renameBook = null }, title = { Text("Rename PDF") }, text = { OutlinedTextField(rename, { rename = it.take(160) }, label = { Text("Title") }, singleLine = true) }, confirmButton = { TextButton(enabled = rename.isNotBlank(), onClick = { model.update(b.id) { it.copy(title = rename.trim()) }; renameBook = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { renameBook = null }) { Text("Cancel") } }) }
    removeBook?.let { b -> AlertDialog(onDismissRequest = { removeBook = null }, title = { Text("Remove this PDF?") }, text = { Text("“${b.title}” and its notes will be removed from your library. Your original downloaded file is unaffected.") }, confirmButton = { TextButton(onClick = { model.remove(b.id); removeBook = null }) { Text("Remove") } }, dismissButton = { TextButton(onClick = { removeBook = null }) { Text("Keep PDF") } }) }
}

@Composable
private fun LibrarySearch(query: String, onQuery: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    BasicTextField(query, onQuery, modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
        .background(CardSurface).border(1.dp, Line, RoundedCornerShape(14.dp)).semantics { contentDescription = label },
        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink), cursorBrush = SolidColor(Terracotta),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        decorationBox = { field ->
            Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph("search", Muted, Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Box(Modifier.weight(1f)) {
                    if(query.isEmpty()) Text("Search", color = Muted, fontSize = 14.sp, maxLines = 1)
                    field()
                }
                if(query.isNotEmpty()) Box(Modifier.size(28.dp).clip(CircleShape).clickable { onQuery("") }.semantics { contentDescription = "Clear search" }, contentAlignment = Alignment.Center) { Glyph("close", Muted, Modifier.size(16.dp)) }
            }
        })
}

@Composable
private fun FolderTile(folder: Folder, pdfCount: Int, childCount: Int, grid: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val tile = modifier.clip(RoundedCornerShape(14.dp)).background(Sage).clickable(onClick = onOpen)
        .semantics { contentDescription = "Open folder ${folder.name}" }.padding(16.dp)
    val details: @Composable () -> Unit = {
        Text(folder.name, maxLines = if(grid) 2 else 1, minLines = if(grid) 2 else 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("$pdfCount PDFs · $childCount folders", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
    if(grid) Column(tile) {
        Glyph("folder", Terracotta)
        Spacer(Modifier.height(10.dp))
        details()
    } else Row(tile, verticalAlignment = Alignment.CenterVertically) {
        Glyph("folder", Terracotta, Modifier.size(28.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) { details() }
        Glyph("right", Muted, Modifier.size(18.dp))
    }
}

@Composable
fun ContinueCard(book: Book, store: LibraryStore, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Sage).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if(book.opened == 0L) "MAKE YOURSELF AT HOME" else "CONTINUE READING", fontSize = 9.sp, letterSpacing = 1.5.sp, color = Muted, fontWeight = FontWeight.Bold)
            Text(book.title, fontFamily = FontFamily.Serif, fontSize = 20.sp, lineHeight = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp, end = 12.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(if(book.opened == 0L) "Take a look" else "Pick up where you left off", fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Glyph("arrow", Ink, Modifier.size(16.dp)) }
        }
        PdfCover(book, store, Modifier.width(67.dp).height(90.dp))
    }
}

@Composable
fun BookCard(book: Book, store: LibraryStore, onClick: () -> Unit, onStar: () -> Unit, onMenu: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardSurface).border(1.dp, Line, RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(155.dp).background(Sage).padding(15.dp)) {
            PdfCover(book, store, Modifier.align(Alignment.Center).width(91.dp).fillMaxHeight())
            Box(Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-9).dp).size(36.dp).background(Paper.copy(alpha = .95f), CircleShape)) {
                IconAction("star", if(book.favorite) "Unfavourite ${book.title}" else "Favourite ${book.title}", if(book.favorite) Terracotta else Muted, onClick = onStar)
            }
        }
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            Text(book.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) { Text("${book.pages} pages · ${fileSize(book.bytes)}", fontSize = 9.sp, color = Muted, modifier = Modifier.weight(1f)); Box(Modifier.width(25.dp)) { IconAction("more", "Options for ${book.title}", Muted, onClick = onMenu) } }
        }
        LinearProgressIndicator(progress = { if(book.opened == 0L) 0f else (book.page + 1f) / book.pages }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Terracotta, trackColor = Line, drawStopIndicator = {})
    }
}

@Composable
fun PdfCover(book: Book, store: LibraryStore, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, book.id) { value = runCatching { store.render(book.id, 0, 220) }.getOrNull() }
    Box(modifier.clip(RoundedCornerShape(3.dp)).background(Color.White).border(1.dp, Color.Black.copy(alpha = .06f), RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), "Cover of ${book.title}", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit) } ?: Glyph("book", Muted)
    }
}

@Composable
fun EmptyPanel(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(Sage, CircleShape), contentAlignment = Alignment.Center) { Glyph("book", Muted, Modifier.size(28.dp)) }
        Text(title, fontFamily = FontFamily.Serif, fontSize = 22.sp, modifier = Modifier.padding(top = 18.dp))
        Text(body, fontSize = 13.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp))
    }
}
fun fileSize(bytes: Long): String = if(bytes < 1024 * 1024) "${(bytes / 1024).coerceAtLeast(1)} KB" else "%.1f MB".format(bytes / (1024f * 1024))
