package com.sandboxuser.pdfviewer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sandboxuser.pdfviewer.data.Book
import com.sandboxuser.pdfviewer.data.LibraryStore
import com.sandboxuser.pdfviewer.data.LibraryData
import com.sandboxuser.pdfviewer.data.Folder
import com.sandboxuser.pdfviewer.data.DirectoryScanner
import com.sandboxuser.pdfviewer.data.DirectoryImportStopped
import com.sandboxuser.pdfviewer.data.subtreeIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

class LibraryModel(application: Application) : AndroidViewModel(application) {
    val store = LibraryStore(application)
    var books by mutableStateOf<List<Book>>(emptyList()); private set
    var folders by mutableStateOf<List<Folder>>(emptyList()); private set
    var theme by mutableStateOf("system"); private set
    var loading by mutableStateOf(true); private set
    var importing by mutableStateOf(false); private set
    var directoryProgress by mutableStateOf<String?>(null); private set
    var directoryReport by mutableStateOf<String?>(null); private set
    private val stopDirectory = AtomicBoolean(false)
    var error by mutableStateOf<String?>(null); private set
    var loadFailed by mutableStateOf(false); private set
    var saving by mutableStateOf(false); private set
    var saveFailed by mutableStateOf(false); private set
    val openRequests = Channel<String>(Channel.UNLIMITED)
    val messages = Channel<String>(Channel.UNLIMITED)
    private var revision = 0
    private val writes = Channel<Pair<Int, LibraryData>>(Channel.UNLIMITED)
    init {
        reload()
        viewModelScope.launch {
            for ((version, snapshot) in writes) {
                try { store.saveState(snapshot); saveFailed = false }
                catch (e: Exception) { saveFailed = true; messages.send("Changes could not be saved. Free up space and tap Retry save.") }
                if (version == revision) saving = false
            }
        }
    }
    fun reload() { viewModelScope.launch {
        loading = true; error = null
        try { val state = store.loadState(); books = state.books; folders = state.folders; theme = state.theme; loadFailed = false }
        catch (e: Exception) { error = "Your library could not be loaded. Your files have not been changed."; loadFailed = true }
        loading = false
    } }
    private fun persist() { revision++; saving = true; writes.trySend(revision to LibraryData(books, folders, theme)) }
    fun retrySave() = persist()
    fun update(id: String, transform: (Book) -> Book) {
        if (loading || loadFailed) return
        books = books.map { if (it.id == id) transform(it) else it }; persist()
    }
    fun open(id: String) = update(id) { it.copy(opened = System.currentTimeMillis()) }
    fun remove(id: String) {
        // Save the index first. A failed save leaves the document recoverable.
        books = books.filterNot { it.id == id }; persist()
    }
    fun changeTheme(value: String) {
        if (loading || loadFailed || value !in listOf("system", "light", "dark")) return
        theme = value; persist()
    }
    fun saveFolder(name: String, id: String? = null, parentId: String? = null): Boolean {
        val title = name.trim()
        val parent = if(id == null) parentId else folders.firstOrNull { it.id == id }?.parentId
        if (loading || loadFailed || importing || title.isEmpty() || (parent != null && folders.none { it.id == parent }) ||
            folders.any { it.id != id && it.parentId == parent && it.name.equals(title, true) }) return false
        folders = if(id == null) folders + Folder(name = title, parentId = parent) else folders.map { if(it.id == id) it.copy(name = title) else it }
        persist(); return true
    }
    fun removeFolder(id: String) {
        if (loading || loadFailed || importing) return
        val removed = folders.subtreeIds(id)
        folders = folders.filterNot { it.id in removed }
        books = books.map { if(it.folderId in removed) it.copy(folderId = null) else it }
        persist()
    }
    fun dismissDirectoryReport() { directoryReport = null }
    fun stopDirectoryImport() { stopDirectory.set(true) }
    fun importDirectory(uri: Uri, parentId: String? = null) {
        if(importing || loading || loadFailed) return
        viewModelScope.launch {
            importing = true; stopDirectory.set(false); directoryReport = null
            directoryProgress = "Looking for PDFs and subfolders…"
            var added = 0
            var created = 0
            var duplicates = 0
            var ignored = 0
            var failureCount = 0
            var stopped = false
            val details = mutableListOf<String>()
            try {
                val scan = DirectoryScanner(getApplication()).scan(uri, stopDirectory::get) { found ->
                    withContext(Dispatchers.Main) { directoryProgress = "Scanning folders · $found PDFs found" }
                }
                ignored = scan.ignored
                failureCount += scan.issues.size
                details += scan.issues.take(20)
                val destination = parentId?.takeIf { id -> folders.any { it.id == id } }
                val mapping = mutableMapOf<String, String>()
                val updated = folders.toMutableList()
                for(source in scan.directories) {
                    if(stopDirectory.get()) throw DirectoryImportStopped()
                    val parent = source.parentKey?.let { mapping.getValue(it) } ?: destination
                    val folder = updated.firstOrNull { it.parentId == parent && it.name == source.name }
                        ?: Folder(name = source.name, parentId = parent).also { updated += it; created++ }
                    mapping[source.key] = folder.id
                }
                folders = updated; persist()
                for((index, source) in scan.pdfs.withIndex()) {
                    if(stopDirectory.get()) throw DirectoryImportStopped()
                    directoryProgress = "Importing ${index + 1} of ${scan.pdfs.size} · ${source.name}"
                    try {
                        val book = store.import(source.uri, stopDirectory::get)
                        val target = mapping.getValue(source.directoryKey)
                        // A document in two source folders belongs in both imported folders.
                        // Re-importing the same folder skips existing PDFs and keeps their annotations.
                        if(books.any { it.folderId == target && it.digest == book.digest && it.digest.isNotEmpty() }) {
                            store.deleteFile(book.id); duplicates++
                        } else {
                            books = books + book.copy(folderId = target); added++; persist()
                        }
                    } catch(e: DirectoryImportStopped) { throw e }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) {
                        failureCount++
                        if(details.size < 20) details += "${source.name}: ${if(e is SecurityException) "Locked or inaccessible PDF" else "Could not import this PDF"}"
                    }
                }
            } catch(e: DirectoryImportStopped) { stopped = true }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { failureCount++; if(details.size < 20) details += (e.message ?: "Unable to read the selected folder.") }
            finally { importing = false; directoryProgress = null }
            directoryReport = buildString {
                append(if(stopped) "Import stopped. Completed PDFs are kept.\n\n" else "Folder import finished.\n\n")
                append("$added PDFs added · $created folders created\n")
                append("$duplicates existing PDFs skipped · $ignored non-PDF files ignored")
                if(failureCount > 0) append("\n\n$failureCount files or folders could not be imported:\n" + details.joinToString("\n") + if(failureCount > details.size) "\n…and ${failureCount - details.size} more." else "")
            }
        }
    }
    fun moveBook(id: String, folderId: String?) {
        if(folderId != null && folders.none { it.id == folderId }) return
        update(id) { it.copy(folderId = folderId) }
    }
    fun openExternal(uris: List<Uri>) {
        viewModelScope.launch {
            snapshotFlow { loading || importing }.first { !it }
            if(loadFailed) messages.send("Load your library before opening this PDF.")
            else import(uris, openAfterImport = true)
        }
    }
    fun import(uris: List<Uri>, folderId: String? = null, openAfterImport: Boolean = false) {
        if (importing || loading || loadFailed || uris.isEmpty()) return
        viewModelScope.launch {
            importing = true
            var added = 0
            var openId: String? = null
            for (uri in uris) {
                try {
                    val book = store.import(uri)
                    val existing = books.firstOrNull { it.digest.isNotEmpty() && it.digest == book.digest }
                    if (existing != null) {
                        store.deleteFile(book.id)
                        if(openId == null) openId = existing.id
                        if(!openAfterImport) messages.send("This PDF is already in your library.")
                    } else {
                        books = books + book.copy(folderId = folderId?.takeIf { id -> folders.any { it.id == id } })
                        persist(); added++
                        if(openId == null) openId = book.id
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    messages.send(if (e is SecurityException) "This PDF is locked or inaccessible. Choose an unlocked copy."
                        else e.message?.takeIf { it.contains("300 MB") } ?: "Could not add this file. Choose a valid, unlocked PDF.")
                }
            }
            importing = false
            if(openAfterImport) openId?.let { open(it); openRequests.send(it) }
            if (added > 0) messages.send("$added ${if (added == 1) "PDF" else "PDFs"} added to your library")
        }
    }
}
