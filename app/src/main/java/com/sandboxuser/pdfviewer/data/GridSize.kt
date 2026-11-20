package com.sandboxuser.pdfviewer.data

import kotlin.math.floor

enum class GridSize(val id: String, val title: String, val minimumWidth: Int, val coverHeight: Int) {
    SMALL("small", "Small", 88, 112),
    MEDIUM("medium", "Medium", 150, 155),
    LARGE("large", "Large", 240, 220);

    fun columns(availableWidth: Float, fontScale: Float = 1f): Int =
        floor((availableWidth + 12) / (minimumWidth * fontScale.coerceAtLeast(1f) + 12)).toInt().coerceIn(1, 8)

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}
