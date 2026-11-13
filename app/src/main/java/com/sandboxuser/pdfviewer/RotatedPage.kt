package com.sandboxuser.pdfviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints

/** The caller sizes the rotated frame; page content keeps its original coordinate system.
 * Measuring the child with swapped dimensions prevents clipping at quarter turns and
 * lets Compose inverse-transform pointer events for existing note and annotation handlers.
 */
@Composable
internal fun RotatedPage(rotation: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(modifier = modifier.semantics { stateDescription = "Rotated $rotation degrees clockwise" },
        content = { Box(Modifier.graphicsLayer { rotationZ = rotation.toFloat() }.background(Color.White)) { content() } }) { children, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val sideways = rotation % 180 != 0
        val child = children.single().measure(if(sideways) Constraints.fixed(height, width) else Constraints.fixed(width, height))
        layout(width, height) { child.place((width - child.width) / 2, (height - child.height) / 2) }
    }
}
