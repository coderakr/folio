package com.sandboxuser.pdfviewer

import android.provider.DocumentsContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sandboxuser.pdfviewer.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NestedLibraryTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(rule.activity)[LibraryModel::class.java]
    @Test fun recursiveImportPreservesTreeAndRepeatedImportKeepsAnnotations() {
        rule.waitUntil(15000) { !model().loading }
        val tree = DocumentsContract.buildTreeDocumentUri("com.sandboxuser.pdfviewer.test.tree", "root")
        rule.runOnIdle { model().saveFolder("Import destination") }
        val destination = model().folders.single { it.name == "Import destination" }.id
        rule.runOnIdle { model().importDirectory(tree, destination) }
        rule.waitUntil(20000) { model().directoryReport != null && !model().saving }
        assertTrue(model().directoryReport!!.contains("2 PDFs added · 3 folders created"))
        assertTrue(model().directoryReport!!.contains("1 non-PDF files ignored"))
        assertTrue(model().directoryReport!!.contains("1 files or folders"))
        val root = model().folders.single { it.name == "Imported science" }
        val physics = model().folders.single { it.name == "Physics" }
        val empty = model().folders.single { it.name == "Empty section" }
        assertEquals(destination, root.parentId)
        assertEquals(root.id, physics.parentId)
        assertEquals(physics.id, empty.parentId)
        assertEquals("Import destination / Imported science / Physics / Empty section", model().folders.folderPath(empty.id))
        val imported = model().books.filter { it.folderId in listOf(root.id, physics.id) }
        assertEquals(2, imported.size)
        val note = Mark(page = 0, kind = "note", points = listOf(.2f to .2f), text = "Keep this")
        rule.runOnIdle { model().update(imported.first().id) { it.copy(marks = listOf(note)) }; model().importDirectory(tree, destination) }
        rule.waitUntil(20000) { model().directoryReport?.contains("2 existing PDFs skipped") == true && !model().saving }
        assertEquals(2, model().books.count { it.folderId in listOf(root.id, physics.id) })
        assertEquals(listOf(note), model().books.first { it.id == imported.first().id }.marks)
        val saved = runBlocking { model().store.loadState() }
        assertEquals(model().folders, saved.folders)
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithContentDescription("Open folder Import destination").performClick()
        rule.onNodeWithContentDescription("Open folder Imported science").performClick()
        rule.onNodeWithContentDescription("Open folder Physics").performClick()
        rule.onNodeWithContentDescription("Open folder Empty section").assertExists()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithContentDescription("Open folder Empty section").assertExists()
        rule.runOnIdle { model().removeFolder(destination) }
        rule.waitUntil { !model().saving }
        assertTrue(model().folders.none { it.id in setOf(destination, root.id, physics.id, empty.id) })
        imported.forEach { b -> assertNull(model().books.first { it.id == b.id }.folderId); assertTrue(model().store.file(b.id).exists()) }
        rule.runOnIdle { imported.forEach { model().remove(it.id) } }
    }
    @Test fun nestedFoldersAllowSameNamesInDifferentParents() {
        rule.waitUntil(15000) { !model().loading }
        rule.runOnIdle {
            assertTrue(model().saveFolder("Nested test"))
            val parent = model().folders.single { it.name == "Nested test" }.id
            assertTrue(model().saveFolder("Section", parentId = parent))
            val child = model().folders.single { it.parentId == parent }.id
            assertFalse(model().saveFolder("section", parentId = parent))
            assertTrue(model().saveFolder("Section", parentId = child))
            assertEquals(3, model().folders.subtreeIds(parent).size)
            model().removeFolder(parent)
        }
    }
}
