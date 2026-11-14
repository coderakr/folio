package com.sandboxuser.pdfviewer

import android.content.ContextWrapper
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sandboxuser.pdfviewer.data.LibraryStore
import com.sandboxuser.pdfviewer.data.Mark
import com.sandboxuser.pdfviewer.data.Folder
import com.sandboxuser.pdfviewer.data.LibraryData
import com.sandboxuser.pdfviewer.data.ColorTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LibraryStoreTest {
    private lateinit var root: File
    private lateinit var store: LibraryStore
    private lateinit var context: ContextWrapper
    @Before fun prepare() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(target.cacheDir, "store-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(target) { override fun getFilesDir() = root }
        store = LibraryStore(context)
    }
    @After fun clean() { root.deleteRecursively() }

    @Test fun palettesPersistWithoutLosingLibraryAndOlderFilesKeepOriginalTheme() = runBlocking {
        val original = store.loadState()
        for (palette in ColorTheme.entries) {
            store.saveState(original.copy(theme = "dark", palette = palette))
            val loaded = LibraryStore(context).loadState()
            assertEquals(palette, loaded.palette)
            assertEquals(original.books, loaded.books)
            assertEquals("dark", loaded.theme)
        }
        val legacy = org.json.JSONObject(LibraryStore.encodeState(original)).apply { remove("palette") }
        assertEquals(ColorTheme.FOLIO, LibraryStore.decodeState(legacy.toString()).palette)
        legacy.put("palette", "future-unknown-theme")
        assertEquals(ColorTheme.FOLIO, LibraryStore.decodeState(legacy.toString()).palette)
    }

    @Test fun starterGuideIsARealRenderablePdf() = runBlocking {
        val guide = store.load().single()
        assertEquals(3, guide.pages)
        val bitmap = store.render(guide.id, 2, 600)
        assertEquals(600, bitmap.width)
        assertEquals(800, bitmap.height)
        bitmap.recycle()
    }

    @Test fun annotationsBookmarksAndProgressSurviveReloadWithoutChangingPdf() = runBlocking {
        val book = store.load().single()
        val original = store.file(book.id).readBytes()
        val edited = book.copy(title = "My reading", favorite = true, page = 2, bookmarks = setOf(0, 2), marks = listOf(
            Mark(page = 1, kind = "highlight", points = listOf(.1f to .2f, .8f to .23f), color = 2),
            Mark(page = 2, kind = "note", points = listOf(.3f to .4f), text = "Keep this idea — नमस्ते"),
            Mark(page = 0, kind = "ink", points = listOf(.1f to .1f, .3f to .4f, .5f to .2f))
        ))
        store.save(listOf(edited))
        assertEquals(edited, LibraryStore(context).load().single())
        assertArrayEquals(original, store.file(book.id).readBytes())
        assertEquals(1, File(root, "library").listFiles()!!.count { it.extension == "pdf" })
    }

    @Test fun importedCopyWorksAfterOriginalIsDeleted() = runBlocking {
        store.load()
        val original = File(root, "original.pdf")
        val pdf = PdfDocument()
        try {
            pdf.finishPage(pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create()))
            original.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        val imported = store.import(Uri.fromFile(original))
        assertTrue(imported.digest.isNotEmpty())
        assertTrue(original.delete())
        store.save(listOf(imported))
        val bitmap = store.render(imported.id, 0, 300)
        assertEquals(400, bitmap.height)
        bitmap.recycle()
    }

    @Test fun invalidPdfDoesNotLeaveAnImportedFile() = runBlocking {
        store.load()
        val before = File(root, "library").listFiles()!!.map { it.name }.toSet()
        val bad = File(root, "broken.pdf").apply { writeText("not a PDF") }
        var failed = false
        try { store.import(Uri.fromFile(bad)) } catch(e: Exception) { failed = true }
        assertTrue(failed)
        assertEquals(before, File(root, "library").listFiles()!!.map { it.name }.toSet())
    }

    @Test fun removingBookRemovesOnlyItsPrivateCopyAndDoesNotReseed() = runBlocking {
        val guide = store.load().single()
        store.save(emptyList())
        assertFalse(store.file(guide.id).exists())
        assertTrue(LibraryStore(context).load().isEmpty())
    }

    @Test fun corruptIndexDoesNotSilentlyReplaceLibrary() = runBlocking {
        val guide = store.load().single()
        val index = File(root, "library/library.json").apply { writeText("broken json") }
        var failed = false
        try { LibraryStore(context).load() } catch(e: Exception) { failed = true }
        assertTrue(failed)
        assertEquals("broken json", index.readText())
        assertTrue(store.file(guide.id).exists())
    }
    @Test fun legacyLibraryMigratesWithoutLosingAnnotations() = runBlocking {
        val book = store.load().single().copy(favorite = true, page = 1, marks = listOf(Mark(page = 1, kind = "note", points = listOf(.2f to .3f), text = "Keep me")))
        File(root, "library/library.json").writeText(LibraryStore.encode(listOf(book)))
        val migrated = LibraryStore(context).loadState()
        assertEquals(book, migrated.books.single())
        assertTrue(migrated.folders.isEmpty())
        assertEquals("system", migrated.theme)
        store.saveState(migrated.copy(folders = listOf(Folder("science", "Science")), theme = "dark"))
        assertEquals(book, LibraryStore(context).loadState().books.single())
    }

    @Test fun foldersAndAppearancePersistAlongsidePdfAnnotations() = runBlocking {
        val guide = store.load().single()
        val folder = Folder("science", "Science")
        val state = LibraryData(listOf(guide.copy(folderId = folder.id)), listOf(folder), "dark")
        store.saveState(state)
        assertEquals(state, LibraryStore(context).loadState())
        store.saveState(state.copy(folders = emptyList(), books = listOf(guide)))
        assertTrue(store.file(guide.id).exists())
        assertNull(LibraryStore(context).loadState().books.single().folderId)
    }

    @Test fun previousFolderSchemaLoadsAsRootFolders() = runBlocking {
        val guide = store.load().single().copy(folderId = "science")
        val old = org.json.JSONObject(LibraryStore.encodeState(LibraryData(listOf(guide), listOf(Folder("science", "Science")), "dark")))
        old.put("version", 1)
        old.getJSONArray("folders").getJSONObject(0).remove("parentId")
        File(root, "library/library.json").writeText(old.toString())
        val loaded = LibraryStore(context).loadState()
        assertEquals(guide, loaded.books.single())
        assertNull(loaded.folders.single().parentId)
        assertEquals("dark", loaded.theme)
    }

    @Test fun stoppingCopyCleansUpThePartialPdf() = runBlocking {
        val guide = store.load().single()
        val before = File(root, "library").listFiles()!!.map { it.name }.toSet()
        var stopped = false
        try { store.import(Uri.fromFile(store.file(guide.id))) { true } }
        catch(e: com.sandboxuser.pdfviewer.data.DirectoryImportStopped) { stopped = true }
        assertTrue(stopped)
        assertEquals(before, File(root, "library").listFiles()!!.map { it.name }.toSet())
    }

}
