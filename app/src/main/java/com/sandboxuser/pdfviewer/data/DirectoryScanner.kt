package com.sandboxuser.pdfviewer.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DirectoryImportStopped : Exception()
data class SourceDirectory(val key: String, val parentKey: String?, val name: String)
data class SourcePdf(val uri: Uri, val directoryKey: String, val name: String)
data class DirectoryScan(val directories: List<SourceDirectory>, val pdfs: List<SourcePdf>, val ignored: Int, val issues: List<String>)

/** Queries one directory at a time on IO; document IDs, not file paths, define identity. */
class DirectoryScanner(private val context: Context) {
    suspend fun scan(tree: Uri, shouldStop: () -> Boolean = { false }, onProgress: suspend (Int) -> Unit = {}): DirectoryScan = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        val rootName = resolver.query(root, projection, null, null, null)?.use { cursor ->
            require(cursor.moveToFirst() && cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) { "Choose an accessible folder." }
            cursor.getString(1) ?: "Imported folder"
        } ?: error("This folder could not be read. Choose it again in the file picker.")
        val directories = mutableListOf(SourceDirectory(rootId, null, rootName))
        val pdfs = mutableListOf<SourcePdf>()
        val issues = mutableListOf<String>()
        val pending = ArrayDeque<SourceDirectory>().apply { add(directories.first()) }
        val seen = mutableSetOf(rootId)
        var ignored = 0
        var entries = 0
        while(pending.isNotEmpty()) {
            if(shouldStop()) throw DirectoryImportStopped()
            val directory = pending.removeFirst()
            // Stage rows before publishing, so a partially failing provider doesn't leave a half-scanned branch.
            val children = mutableListOf<Triple<String, String, String>>()
            try {
                val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, directory.key)
                resolver.query(uri, projection, null, null, null)?.use { cursor ->
                    while(cursor.moveToNext()) {
                        if(shouldStop()) throw DirectoryImportStopped()
                        entries++
                        require(entries <= 50000) { "This folder has more than 50,000 entries. Import a smaller subfolder." }
                        children += Triple(cursor.getString(0), cursor.getString(1) ?: "Untitled", cursor.getString(2) ?: "")
                    }
                    if(cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) error("The cloud folder is still loading. Make it available offline and try again.")
                } ?: error("Folder listing is unavailable")
            } catch(e: DirectoryImportStopped) { throw e }
            catch(e: IllegalArgumentException) { throw e }
            catch(e: Exception) {
                issues += "${directory.name}: ${e.message ?: "Unable to read this folder"}"
                continue
            }
            children.forEach { (id, name, mime) ->
                if(!seen.add(id)) return@forEach
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> {
                        val child = SourceDirectory(id, directory.key, name)
                        directories += child; pending.add(child)
                    }
                    mime.equals("application/pdf", true) || name.endsWith(".pdf", true) -> pdfs += SourcePdf(DocumentsContract.buildDocumentUriUsingTree(tree, id), directory.key, name)
                    else -> ignored++
                }
            }
            onProgress(pdfs.size)
        }
        DirectoryScan(directories, pdfs, ignored, issues)
    }
}
