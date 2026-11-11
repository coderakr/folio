package com.sandboxuser.pdfviewer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.MatrixCursor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExternalPdfTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(rule.activity)[LibraryModel::class.java]
    private val uri = Uri.parse("content://com.sandboxuser.pdfviewer.test.sharedpdf/science.pdf")
    @Test fun androidCanResolveFolioForPdfViewAndShare() {
        for(action in listOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
            val intent = Intent(action).setType("application/pdf")
            if(action == Intent.ACTION_VIEW) intent.setDataAndType(uri, "application/pdf")
            val matches = rule.activity.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            assertTrue("Folio should handle $action", matches.any { it.activityInfo.packageName == "com.sandboxuser.pdfviewer" })
        }
    }
    @Test fun externalViewImportsAndShareReopensSamePdfWithoutDuplicates() {
        rule.waitUntil(15000) { !model().loading }
        val originalIntent = rule.activity.intent
        val before = model().books.size
        rule.runOnIdle {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .setClass(rule.activity, MainActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        rule.waitUntil(15000) { model().books.any { it.title == "Shared science" } && !model().importing }
        rule.onNodeWithText("Shared science").assertExists()
        rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Page annotation canvas").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before + 1, model().books.size)
        rule.onNodeWithContentDescription("Back to library").performClick()
        rule.runOnIdle {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                .setClass(rule.activity, MainActivity::class.java).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("Page annotation canvas").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before + 1, model().books.size)
        rule.onNodeWithContentDescription("Back to library").performClick()
        rule.runOnIdle { model().books.firstOrNull { it.title == "Shared science" }?.let { model().remove(it.id) } }
        rule.waitUntil { !model().saving }
        rule.runOnIdle { rule.activity.intent = originalIntent }
    }
}
