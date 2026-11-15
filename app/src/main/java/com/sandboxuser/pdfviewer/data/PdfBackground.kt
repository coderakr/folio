package com.sandboxuser.pdfviewer.data

/** Display-only preference. The original PDF is never rewritten. */
enum class PdfBackground(val id: String, val title: String, val description: String) {
    ORIGINAL("original", "Original PDF", "Keep the document’s own colours"),
    THEME("theme", "Follow app theme", "Match the selected theme and light/dark mode"),
    SEPIA("sepia", "Sepia", "Warm paper with dark text"),
    BLACK("black", "Black", "Black paper with light text");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}
