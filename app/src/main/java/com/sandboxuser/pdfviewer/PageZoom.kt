package com.sandboxuser.pdfviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** Convert an unrotated page tap into a translation of its rotated, centre-scaled frame. */
internal fun focusedPagePan(point: Offset, contentSize: IntSize, rotation: Int, scale: Float): Offset {
    if(contentSize.width == 0 || contentSize.height == 0) return Offset.Zero
    val x = (point.x / contentSize.width).coerceIn(0f, 1f)
    val y = (point.y / contentSize.height).coerceIn(0f, 1f)
    val position = when(rotation) {
        90 -> Offset(1f - y, x)
        180 -> Offset(1f - x, 1f - y)
        270 -> Offset(y, 1f - x)
        else -> Offset(x, y)
    }
    val width = if(rotation % 180 == 0) contentSize.width.toFloat() else contentSize.height.toFloat()
    val height = if(rotation % 180 == 0) contentSize.height.toFloat() else contentSize.width.toFloat()
    return clampPagePan(Offset((.5f - position.x) * width * scale, (.5f - position.y) * height * scale), width, height, scale)
}

internal fun clampPagePan(pan: Offset, width: Float, height: Float, scale: Float): Offset {
    val limitX = width * (scale - 1).coerceAtLeast(0f) / 2
    val limitY = height * (scale - 1).coerceAtLeast(0f) / 2
    return Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
}
