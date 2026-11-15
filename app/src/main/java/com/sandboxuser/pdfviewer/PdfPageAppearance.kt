package com.sandboxuser.pdfviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandboxuser.pdfviewer.data.PdfBackground
import com.sandboxuser.pdfviewer.ui.theme.*

internal data class PdfPageColors(val paper: Color, val ink: Color, val original: Boolean = false) {
    // Map each RGB channel from original black/white to the chosen ink/paper.
    // Applying the filter only to the PDF image leaves annotation colours unchanged.
    fun filter(): ColorFilter? = if(original) null else ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        paper.red - ink.red, 0f, 0f, 0f, ink.red * 255f,
        0f, paper.green - ink.green, 0f, 0f, ink.green * 255f,
        0f, 0f, paper.blue - ink.blue, 0f, ink.blue * 255f,
        0f, 0f, 0f, 1f, 0f
    )))
}

@Composable
internal fun PdfBackground.pageColors(): PdfPageColors = when(this) {
    PdfBackground.ORIGINAL -> PdfPageColors(Color.White, Color.Black, original = true)
    PdfBackground.THEME -> PdfPageColors(Paper, Ink)
    PdfBackground.SEPIA -> PdfPageColors(Color(0xFFF3E6CB), Color(0xFF403326))
    PdfBackground.BLACK -> PdfPageColors(Color.Black, Color(0xFFE6E6E6))
}

@Composable
internal fun PdfBackgroundPicker(selected: PdfBackground, onSelect: (PdfBackground) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("PDF page background") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Applies to all PDFs. Tinted backgrounds also change image colours; choose Original PDF for photos and diagrams.", color = Muted, fontSize = 13.sp)
            PdfBackground.entries.forEach { option ->
                val colors = option.pageColors()
                Row(Modifier.fillMaxWidth().selectable(selected == option, role = Role.RadioButton, onClick = { onSelect(option) })
                    .semantics { contentDescription = "PDF background: ${option.title}" }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).background(colors.paper, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("Aa", color = colors.ink, fontSize = 20.sp) }
                    Column(Modifier.weight(1f)) { Text(option.title, color = Ink); Text(option.description, color = Muted, fontSize = 12.sp) }
                    RadioButton(selected == option, onClick = null)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
