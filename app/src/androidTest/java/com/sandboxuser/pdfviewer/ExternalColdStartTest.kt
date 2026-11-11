package com.sandboxuser.pdfviewer

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.os.SystemClock

@RunWith(AndroidJUnit4::class)
class ExternalColdStartTest {
    @Test fun coldLaunchWithPdfViewImportsBeforeOpeningReader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://com.sandboxuser.pdfviewer.test.sharedpdf/science.pdf"), "application/pdf")
            .setClass(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 15000
            var opened = false
            while(!opened && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[LibraryModel::class.java]
                    opened = model.books.any { it.title == "Shared science" && it.opened > 0 } && !model.importing && !model.saving
                }
                if(!opened) SystemClock.sleep(100)
            }
            assertTrue("A cold PDF VIEW intent must import and open the PDF", opened)
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[LibraryModel::class.java]
                model.books.firstOrNull { it.title == "Shared science" }?.let { model.remove(it.id) }
            }
            var saved = false
            while(!saved && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { saved = !ViewModelProvider(it)[LibraryModel::class.java].saving }
                if(!saved) SystemClock.sleep(100)
            }
        }
    }
}
