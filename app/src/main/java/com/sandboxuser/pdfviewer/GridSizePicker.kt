package com.sandboxuser.pdfviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandboxuser.pdfviewer.data.GridSize
import com.sandboxuser.pdfviewer.ui.theme.*

@Composable
internal fun GridSizePicker(selected: GridSize, onSelect: (GridSize) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Grid size") }, text = {
        Column(Modifier.selectableGroup()) {
            Text("Choose the size of folders and PDF cards. Applies wherever you use grid view.", color = Muted)
            Spacer(Modifier.height(12.dp))
            GridSize.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().selectable(selected == option, role = Role.RadioButton, onClick = { onSelect(option) })
                    .semantics { contentDescription = "Grid size: ${option.title}" }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == option, onClick = null)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(option.title)
                        Text(when(option) { GridSize.SMALL -> "More on each row"; GridSize.MEDIUM -> "Balanced spacing"; GridSize.LARGE -> "Bigger covers and folders" }, fontSize = 12.sp, color = Muted)
                    }
                    Row(Modifier.width(64.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3 - option.ordinal) { Box(Modifier.weight(1f).height(26.dp).background(Terracotta.copy(alpha = .35f), RoundedCornerShape(3.dp))) }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
