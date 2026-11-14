package com.sandboxuser.pdfviewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.sandboxuser.pdfviewer.ui.theme.Ink

@Composable
fun Glyph(name: String, color: Color = Ink, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            val p = Path()
            fun line(vararg xy: Float) { p.moveTo(xy[0], xy[1]); for (i in 2 until xy.size step 2) p.lineTo(xy[i], xy[i+1]) }
            when (name) {
                "palette" -> {
                    p.moveTo(20f,14f); p.cubicTo(27f,5f,15f,-2f,6f,4f); p.cubicTo(-3f,10f,3f,24f,12f,21f)
                    p.cubicTo(17f,19f,9f,15f,15f,14f); p.lineTo(20f,14f)
                    listOf(7f to 9f, 12f to 6f, 17f to 8f).forEach { (x,y) -> p.addOval(androidx.compose.ui.geometry.Rect(x-1,y-1,x+1,y+1)) }
                }
                "folder" -> line(3f,6f,3f,21f,21f,21f,21f,7f,12f,7f,10f,3f,3f,3f,3f,6f)
                "fullscreen" -> { line(9f,3f,3f,3f,3f,9f); line(15f,3f,21f,3f,21f,9f); line(3f,15f,3f,21f,9f,21f); line(15f,21f,21f,21f,21f,15f) }
                "collapse" -> { line(3f,9f,9f,9f,9f,3f); line(15f,3f,15f,9f,21f,9f); line(3f,15f,9f,15f,9f,21f); line(15f,21f,15f,15f,21f,15f) }
                "rotate" -> { p.moveTo(20f,10f); p.cubicTo(19f,2f,5f,1f,3f,11f); p.cubicTo(1f,21f,16f,25f,20f,16f); line(15f,10f,21f,10f,21f,4f) }
                "moon" -> { p.moveTo(18f,16f); p.cubicTo(5f,21f,3f,7f,11f,3f); p.cubicTo(-2f,3f,-1f,23f,12f,22f); p.quadraticTo(19f,22f,22f,15f); p.lineTo(18f,16f) }
                "plus" -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
                "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
                "search" -> { p.addOval(androidx.compose.ui.geometry.Rect(3f,3f,17f,17f)); line(16f,16f,21f,21f) }
                "book", "library" -> { line(12f,5f,12f,21f); p.moveTo(12f,5f); p.quadraticTo(7f,2f,3f,4f); p.lineTo(3f,19f); p.quadraticTo(7f,17f,12f,21f); p.quadraticTo(17f,17f,21f,19f); p.lineTo(21f,4f); p.quadraticTo(17f,2f,12f,5f) }
                "star" -> { line(12f,3f,15f,9f,22f,10f,17f,15f,18f,22f,12f,18f,6f,22f,7f,15f,2f,10f,9f,9f,12f,3f) }
                "note" -> { line(14f,3f,4f,3f,4f,21f,20f,21f,20f,12f); line(10f,14f,11f,10f,19f,2f,22f,5f,14f,13f,10f,14f) }
                "arrow" -> { line(4f,12f,20f,12f); line(14f,6f,20f,12f,14f,18f) }
                "back" -> { line(20f,12f,4f,12f); line(10f,6f,4f,12f,10f,18f) }
                "down" -> line(6f,9f,12f,15f,18f,9f)
                "left" -> line(15f,5f,8f,12f,15f,19f)
                "right" -> line(9f,5f,16f,12f,9f,19f)
                "grid" -> { listOf(3f to 3f,14f to 3f,3f to 14f,14f to 14f).forEach { (x,y) -> p.addRect(androidx.compose.ui.geometry.Rect(x,y,x+7,y+7)) } }
                "list" -> { line(8f,5f,21f,5f); line(8f,12f,21f,12f); line(8f,19f,21f,19f); line(3f,5f,4f,5f); line(3f,12f,4f,12f); line(3f,19f,4f,19f) }
                "more" -> { for (y in listOf(5f,12f,19f)) p.addOval(androidx.compose.ui.geometry.Rect(11f,y-1,13f,y+1)) }
                "check" -> line(4f,12f,9f,17f,20f,6f)
                "bookmark" -> line(6f,3f,18f,3f,18f,22f,12f,17f,6f,22f,6f,3f)
                "pen", "highlight" -> { line(4f,16f,15f,5f,20f,10f,9f,21f,3f,22f,4f,16f); line(13f,7f,18f,12f); if (name == "highlight") line(3f,4f,8f,4f) }
                "undo" -> { line(8f,4f,3f,9f,8f,14f); p.moveTo(3f,9f); p.lineTo(15f,9f); p.cubicTo(24f,9f,24f,21f,14f,21f) }
                "zoom" -> { p.addOval(androidx.compose.ui.geometry.Rect(2f,2f,17f,17f)); line(16f,16f,22f,22f); line(6f,9.5f,13f,9.5f); line(9.5f,6f,9.5f,13f) }
                "shield" -> { line(12f,2f,21f,6f,20f,15f,12f,22f,4f,15f,3f,6f,12f,2f); line(8f,11f,11f,14f,16f,9f) }
                "delete" -> { line(4f,6f,20f,6f); line(9f,6f,9f,3f,15f,3f,15f,6f); line(6f,6f,7f,21f,17f,21f,18f,6f); line(10f,10f,10f,17f); line(14f,10f,14f,17f) }
                "sun" -> { p.addOval(androidx.compose.ui.geometry.Rect(7f,7f,17f,17f)); line(12f,1f,12f,4f); line(12f,20f,12f,23f); line(1f,12f,4f,12f); line(20f,12f,23f,12f) }
            }
            drawPath(p, color, style = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
