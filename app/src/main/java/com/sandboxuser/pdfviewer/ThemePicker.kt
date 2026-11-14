package com.sandboxuser.pdfviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandboxuser.pdfviewer.data.ColorTheme
import com.sandboxuser.pdfviewer.ui.theme.*

@Composable
internal fun ThemePicker(selected: ColorTheme, onSelect: (ColorTheme) -> Unit, onDismiss: () -> Unit) {
    val dark = LocalDarkMode.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Colour themes") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Make the library your own. Every theme works in light and dark mode.", color = Muted, fontSize = 13.sp)
            ColorTheme.entries.forEach { theme ->
                val colors = theme.colors(dark).scheme
                val active = selected == theme
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(colors.background)
                    .border(if(active) 2.dp else 1.dp, if(active) colors.primary else colors.outlineVariant, RoundedCornerShape(14.dp))
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(theme) })
                    .semantics { contentDescription = "${theme.title} colour theme" }
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(30.dp).background(colors.primary, CircleShape), contentAlignment = Alignment.Center) {
                        if(active) Glyph("check", colors.onPrimary, Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(theme.title, color = colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(theme.description, color = colors.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
