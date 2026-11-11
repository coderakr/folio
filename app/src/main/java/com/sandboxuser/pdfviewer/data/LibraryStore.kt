package com.sandboxuser.pdfviewer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class Mark(val id: String = UUID.randomUUID().toString(), val page: Int, val kind: String,
    val points: List<Pair<Float, Float>>, val color: Int = 0, val text: String = "")
data class Book(val id: String, val title: String, val pages: Int, val bytes: Long,
    val added: Long = System.currentTimeMillis(), val opened: Long = 0, val page: Int = 0,
    val favorite: Boolean = false, val bookmarks: Set<Int> = emptySet(), val marks: List<Mark> = emptyList(),
    val digest: String = "", val folderId: String? = null)

data class Folder(val id: String = UUID.randomUUID().toString(), val name: String)
data class LibraryData(val books: List<Book> = emptyList(), val folders: List<Folder> = emptyList(), val theme: String = "system")

class LibraryStore(private val context: Context) {
    private val directory = File(context.filesDir, "library").apply { mkdirs() }
    private val index = AtomicFile(File(directory, "library.json"))
    private val lock = Mutex()
    private var persistedIds = emptySet<String>()
    fun file(id: String) = File(directory, "$id.pdf")
    private var savedState = LibraryData()
    suspend fun load(): List<Book> = loadState().books
    suspend fun loadState(): LibraryData = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!index.baseFile.exists()) {
                LibraryData(books = listOf(createGuide())).also { write(it) }
            } else decodeState(index.openRead().bufferedReader().use { it.readText() }).also {
                savedState = it
                persistedIds = it.books.map { book -> book.id }.toSet()
            }
        }
    }
    suspend fun save(books: List<Book>) = withContext(Dispatchers.IO) { lock.withLock { write(savedState.copy(books = books)) } }
    suspend fun saveState(state: LibraryData) = withContext(Dispatchers.IO) { lock.withLock { write(state) } }
    private fun write(state: LibraryData) {
        val output = index.startWrite()
        try {
            output.write(encodeState(state).toByteArray()); index.finishWrite(output)
        } catch (e: Exception) { index.failWrite(output); throw e }
        val ids = state.books.map { it.id }.toSet()
        (persistedIds - ids).forEach { file(it).delete() }
        persistedIds = ids
        savedState = state
    }
    suspend fun import(uri: Uri): Book = withContext(Dispatchers.IO) {
        var name = "Untitled PDF"
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) name = it.getString(0) ?: name
        }
        val id = UUID.randomUUID().toString()
        val target = file(id)
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var count: Int
                    var total = 0L
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        require(total <= 300L * 1024 * 1024) { "Please choose a PDF smaller than 300 MB." }
                        hash.update(buffer, 0, count); output.write(buffer, 0, count)
                    }
                }
            } ?: error("This file could not be opened. Please choose it again.")
            val count = ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { it.pageCount }
            }
            require(count > 0) { "This PDF has no pages." }
            Book(id, name.removeSuffix(".pdf").removeSuffix(".PDF"), count, target.length(),
                digest = hash.digest().joinToString("") { "%02x".format(it) })
        } catch (e: Exception) { target.delete(); throw e }
    }
    suspend fun deleteFile(id: String) = withContext(Dispatchers.IO) { file(id).delete() }
    suspend fun render(id: String, page: Int, width: Int): Bitmap = withContext(Dispatchers.IO) {
        ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { pdfPage ->
                    // Bound memory even for unusually tall PDF pages.
                    val ratio = pdfPage.height.toFloat() / pdfPage.width
                    val w = minOf(width, (2800 / ratio).toInt()).coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, (w * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
    private fun createGuide(): Book {
        val id = "folio-welcome"
        val pdf = PdfDocument()
        val headings = listOf("Make room for\na good read.", "Make it yours.", "Right where\nyou left off.")
        val descriptions = listOf(
            listOf("A LITTLE GUIDE TO FOLIO", "Your favourite PDFs, finally in one place.", "Tap Add PDF in your library to bring in a document.", "Folio keeps a private copy, so your reading is always", "close at hand — even when you are offline.", "", "Try it out", "This little guide is a real PDF. Turn the page, leave", "a note, or highlight a sentence to make yourself at home."),
            listOf("IDEAS WORTH KEEPING", "Read with a pencil in hand.", "Highlight: choose a colour, then drag over a line.", "Draw: use the pen to underline, circle, or sketch.", "Note: tap anywhere on a page to add a thought.", "", "Always saved, quietly", "Your highlights and notes are saved in Folio.", "The original PDF stays untouched. No extra exports,", "no versions to keep track of. Just your ideas."),
            listOf("A PLACE TO RETURN TO", "Pick up your next chapter.", "Folio remembers the last page you read.", "Bookmark a page to find it quickly later.", "Star your favourites to keep them within easy reach.", "", "Your library, on this device", "Your PDFs and notes live in the app's private storage.", "Removing a PDF from Folio also removes its notes.", "Keep your original files as a backup.")
        )
        repeat(3) { number ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, number + 1).create())
            val c = page.canvas
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            c.drawColor(Color.rgb(250, 247, 240))
            p.color = Color.rgb(174, 82, 57); p.textSize = 20f; p.typeface = Typeface.create("serif", Typeface.BOLD)
            c.drawText("folio.", 52f, 64f, p)
            p.textSize = 11f; p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            c.drawText(descriptions[number][0], 52f, 147f, p)
            p.color = Color.rgb(43, 48, 42); p.textSize = 52f; p.typeface = Typeface.create("serif", Typeface.NORMAL)
            headings[number].split('\n').forEachIndexed { i, line -> c.drawText(line, 48f, 215f + i * 60, p) }
            p.color = Color.rgb(174, 82, 57); p.strokeWidth = 2f
            c.drawLine(52f, 325f, 548f, 325f, p)
            p.textSize = 17f; p.color = Color.rgb(72, 77, 68); p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            descriptions[number].drop(1).forEachIndexed { i, line -> c.drawText(line, 52f, 375f + i * 31, p) }
            p.textSize = 11f; p.color = Color.rgb(120, 123, 111)
            c.drawText("THE FOLIO FIELD GUIDE", 52f, 750f, p)
            c.drawText("0${number + 1} / 03", 492f, 750f, p)
            pdf.finishPage(page)
        }
        file(id).outputStream().use { pdf.writeTo(it) }; pdf.close()
        return Book(id, "A little guide to Folio", 3, file(id).length())
    }
    companion object {
        fun encodeState(state: LibraryData): String = JSONObject().apply {
            put("version", 1)
            put("books", JSONArray(encode(state.books)))
            put("folders", JSONArray().apply { state.folders.forEach { folder ->
                put(JSONObject().put("id", folder.id).put("name", folder.name))
            } })
            put("theme", state.theme)
        }.toString()
        fun decodeState(raw: String): LibraryData {
            // Existing installations stored a bare book array. Migrate without changing PDFs or marks.
            if (raw.trimStart().startsWith("[")) return LibraryData(books = decode(raw))
            val root = JSONObject(raw)
            require(root.getInt("version") == 1) { "Unsupported library version" }
            val folders = root.getJSONArray("folders")
            return LibraryData(decode(root.getJSONArray("books").toString()),
                List(folders.length()) { folders.getJSONObject(it).let { f -> Folder(f.getString("id"), f.getString("name")) } },
                root.optString("theme", "system").takeIf { it in listOf("system", "light", "dark") } ?: "system")
        }

        fun encode(books: List<Book>): String = JSONArray().apply {
            books.forEach { b -> put(JSONObject().apply {
                put("id", b.id); put("title", b.title); put("pages", b.pages); put("bytes", b.bytes)
                put("added", b.added); put("opened", b.opened); put("page", b.page); put("favorite", b.favorite)
                put("folderId", b.folderId ?: JSONObject.NULL); put("digest", b.digest); put("bookmarks", JSONArray(b.bookmarks.toList()))
                put("marks", JSONArray().apply { b.marks.forEach { m -> put(JSONObject().apply {
                    put("id", m.id); put("page", m.page); put("kind", m.kind); put("color", m.color); put("text", m.text)
                    put("points", JSONArray().apply { m.points.forEach { (x, y) -> put(JSONArray().put(x).put(y)) } })
                }) } })
            }) }
        }.toString()
        fun decode(raw: String): List<Book> {
            val array = JSONArray(raw)
            return List(array.length()) { i ->
                val b = array.getJSONObject(i)
                val bookmarks = b.optJSONArray("bookmarks") ?: JSONArray()
                val marks = b.optJSONArray("marks") ?: JSONArray()
                Book(b.getString("id"), b.getString("title"), b.getInt("pages"), b.getLong("bytes"),
                    b.getLong("added"), b.optLong("opened"), b.optInt("page"), b.optBoolean("favorite"),
                    List(bookmarks.length()) { bookmarks.getInt(it) }.toSet(),
                    List(marks.length()) { j ->
                        val m = marks.getJSONObject(j); val points = m.getJSONArray("points")
                        Mark(m.getString("id"), m.getInt("page"), m.getString("kind"),
                            List(points.length()) { k -> points.getJSONArray(k).let { it.getDouble(0).toFloat() to it.getDouble(1).toFloat() } },
                            m.optInt("color"), m.optString("text"))
                    }, b.optString("digest"), if(b.isNull("folderId")) null else b.getString("folderId"))
            }
        }
    }
}
