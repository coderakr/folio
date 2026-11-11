package com.sandboxuser.pdfviewer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Independent test-APK provider: no dependency on the application's Kotlin runtime. */
public class SharedPdfTestProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    private synchronized File fixture() {
        File file = new File(getContext().getCacheDir(), "shared-science.pdf");
        if (!file.exists()) {
            PdfDocument pdf = new PdfDocument();
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(400, 600, 1).create());
            Paint paint = new Paint(); paint.setTextSize(16);
            page.getCanvas().drawText("Science shared from another app", 20, 50, paint);
            pdf.finishPage(page);
            try (FileOutputStream out = new FileOutputStream(file)) { pdf.writeTo(out); }
            catch (IOException e) { throw new IllegalStateException(e); }
            finally { pdf.close(); }
        }
        return file;
    }
    @Override public String getType(Uri uri) { return "application/pdf"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] values = new Object[columns.length];
        for(int i = 0; i < columns.length; i++) values[i] = OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? "Shared science.pdf" : fixture().length();
        cursor.addRow(values); return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException { return ParcelFileDescriptor.open(fixture(), ParcelFileDescriptor.MODE_READ_ONLY); }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
