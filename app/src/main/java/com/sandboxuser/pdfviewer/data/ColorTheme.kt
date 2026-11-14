package com.sandboxuser.pdfviewer.data

/** Colour choice is independent of the light/dark appearance preference. */
enum class ColorTheme(val id: String, val title: String, val description: String) {
    FOLIO("folio", "Folio", "Warm paper & terracotta · Original"),
    OCEAN("ocean", "Ocean", "Cool blues & soft mist"),
    FOREST("forest", "Forest", "Leaf green & natural tones"),
    LAVENDER("lavender", "Lavender", "Soft violet & lilac"),
    ROSE("rose", "Rose", "Dusty pink & warm blush");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: FOLIO
    }
}
