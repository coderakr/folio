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
    fun saveFolder(name: String, id: String? = null): Boolean {
        val title = name.trim()
        if (loading || loadFailed || title.isEmpty() || folders.any { it.id != id && it.name.equals(title, true) }) return false
        folders = if(id == null) folders + Folder(name = title) else folders.map { if(it.id == id) it.copy(name = title) else it }
        persist(); return true
    }
    fun removeFolder(id: String) {
        if (loading || loadFailed) return
        folders = folders.filterNot { it.id == id }
        books = books.map { if(it.folderId == id) it.copy(folderId = null) else it }
        persist()
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
