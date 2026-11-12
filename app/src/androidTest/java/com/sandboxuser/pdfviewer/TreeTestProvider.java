package com.sandboxuser.pdfviewer;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

/** A synthetic SAF tree served by the separate test APK. */
public class TreeTestProvider extends SharedPdfTestProvider {
    private static final String DIR = DocumentsContract.Document.MIME_TYPE_DIR;
    private String name(String id) {
        switch(id) {
            case "root": return "Imported science";
            case "physics": return "Physics";
            case "empty": return "Empty section";
            default: return id;
        }
    }
    private boolean directory(String id) { return id.equals("root") || id.equals("physics") || id.equals("empty"); }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        String id = DocumentsContract.getDocumentId(uri);
        String[] ids = uri.getLastPathSegment().equals("children") ?
            (id.equals("root") ? new String[]{"physics", "intro.pdf", "broken.pdf", "readme.txt"} :
            id.equals("physics") ? new String[]{"empty", "motion.PDF"} : new String[]{}) : new String[]{id};
        for(String child: ids) {
            Object[] row = new Object[columns.length];
            for(int i=0; i<columns.length; i++) {
                switch(columns[i]) {
                    case DocumentsContract.Document.COLUMN_DOCUMENT_ID: row[i] = child; break;
                    case OpenableColumns.DISPLAY_NAME: row[i] = name(child); break;
                    case DocumentsContract.Document.COLUMN_MIME_TYPE: row[i] = directory(child) ? DIR : child.endsWith(".pdf") ? "application/pdf" : "application/octet-stream"; break;
                    case OpenableColumns.SIZE: row[i] = 1L; break;
                    default: row[i] = null;
                }
            }
            cursor.addRow(row);
        }
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if(DocumentsContract.getDocumentId(uri).equals("broken.pdf")) {
            File broken = new File(getContext().getCacheDir(), "broken.pdf");
            try(FileOutputStream out = new FileOutputStream(broken)) { out.write("Invalid PDF".getBytes()); }
            catch(Exception e) { throw new FileNotFoundException(e.toString()); }
            return ParcelFileDescriptor.open(broken, ParcelFileDescriptor.MODE_READ_ONLY);
        }
        return super.openFile(uri, mode);
    }
}
