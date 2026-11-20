package com.sandboxuser.pdfviewer

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
class GridSizeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun chosenSizeChangesFoldersAndPdfCardsAndSurvivesReload() {
        val model = ViewModelProvider(rule.activity)[LibraryModel::class.java]
        rule.waitUntil(15000) { !model.loading }
        rule.runOnIdle {
            model.changeGridSize(GridSize.MEDIUM)
            model.saveFolder("Grid sizing")
        }
        val folder = model.folders.first { it.name == "Grid sizing" }
        try {
            rule.onNodeWithContentDescription("Grid view").performScrollTo().performClick()
            fun choose(size: GridSize) {
                rule.onNodeWithContentDescription("Open sidebar").performClick()
                rule.onNodeWithContentDescription("Choose grid size").performScrollTo().performClick()
                rule.onNodeWithContentDescription("Grid size: ${size.title}").performClick().assertIsSelected()
                rule.onNodeWithText("Done").performClick()
            }
            choose(GridSize.SMALL)
            val smallFolder = rule.onNodeWithContentDescription("Open folder Grid sizing").performScrollTo().fetchSemanticsNode().boundsInRoot.width
            rule.onNodeWithTag("Library items").performScrollToNode(hasTestTag("PDF card folio-welcome"))
            val smallPdf = rule.onNodeWithTag("PDF card folio-welcome").performScrollTo().fetchSemanticsNode().boundsInRoot.width
            choose(GridSize.LARGE)
            val largeFolder = rule.onNodeWithContentDescription("Open folder Grid sizing").performScrollTo().fetchSemanticsNode().boundsInRoot.width
            rule.onNodeWithTag("Library items").performScrollToNode(hasTestTag("PDF card folio-welcome"))
            val largePdf = rule.onNodeWithTag("PDF card folio-welcome").performScrollTo().fetchSemanticsNode().boundsInRoot.width
            assertTrue("Small folder $smallFolder; large $largeFolder", largeFolder > smallFolder * 1.5f)
            assertTrue("Small PDF $smallPdf; large $largePdf", largePdf > smallPdf * 1.5f)
            rule.waitUntil { !model.saving }
            assertEquals(GridSize.LARGE, runBlocking { model.store.loadState() }.gridSize)
            rule.activityRule.scenario.recreate()
            rule.onNodeWithContentDescription("Open sidebar").performClick()
            rule.onNodeWithContentDescription("Choose grid size").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Grid size: Large").assertIsSelected()
            rule.onNodeWithText("Done").performClick()
        } finally {
            rule.runOnIdle { model.changeGridSize(GridSize.MEDIUM); model.removeFolder(folder.id) }
        }
    }
}
