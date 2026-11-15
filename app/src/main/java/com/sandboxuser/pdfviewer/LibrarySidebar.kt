package com.sandboxuser.pdfviewer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sandboxuser.pdfviewer.ui.theme.*

/** Secondary library controls; actions use the folder currently being viewed. */
@Composable
internal fun LibrarySidebar(
    model: LibraryModel,
    location: String,
    onClose: () -> Unit,
    onThemes: () -> Unit,
    onPdfBackground: () -> Unit,
    onNewFolder: () -> Unit,
    onImportFolder: () -> Unit
) {
    val dark = LocalDarkMode.current
    ModalDrawerSheet(modifier = Modifier.width(320.dp), drawerContainerColor = Paper, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Folio", fontSize = 30.sp, fontFamily = FontFamily.Serif, color = Ink)
                    Text("Make room for a good read.", fontSize = 12.sp, color = Muted)
                }
                IconAction("close", "Close sidebar", onClick = onClose)
            }
            SidebarHeading("Appearance")
            NavigationDrawerItem(
                label = { Column { Text("Themes"); Text(model.palette.title, color = Muted, fontSize = 12.sp) } },
                icon = { Glyph("palette", Terracotta) }, selected = false, onClick = onThemes,
                modifier = Modifier.semantics { contentDescription = "Choose colour theme" }
            )
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .toggleable(value = dark, role = Role.Switch, onValueChange = { model.changeTheme(if(it) "dark" else "light") })
                .semantics { contentDescription = "Appearance" }
                .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Glyph(if(dark) "moon" else "sun", Terracotta)
                Column(Modifier.weight(1f)) {
                    Text("Dark mode")
                    Text(if(dark) "On" else "Off", color = Muted, fontSize = 12.sp)
                }
                Switch(checked = dark, onCheckedChange = null)
            }
            NavigationDrawerItem(
                label = { Column { Text("PDF page background"); Text(model.pdfBackground.title, color = Muted, fontSize = 12.sp) } },
                icon = { Glyph("book", Terracotta) }, selected = false, onClick = onPdfBackground,
                modifier = Modifier.semantics { contentDescription = "Choose PDF background" }
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Line)
            SidebarHeading("Organise")
            Text("In: $location", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            NavigationDrawerItem(
                label = { Text("New folder") }, icon = { Glyph("plus", Terracotta) }, selected = false,
                onClick = { if(!model.importing) onNewFolder() },
                modifier = Modifier.semantics { contentDescription = "Create folder here"; if(model.importing) disabled() },
                colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = if(model.importing) Muted else Ink)
            )
            NavigationDrawerItem(
                label = { Column { Text("Import folder"); Text(if(model.importing) "Import in progress…" else "Includes PDFs and subfolders", color = Muted, fontSize = 12.sp) } },
                icon = { Glyph("folder", Terracotta) }, selected = false,
                onClick = { if(!model.importing) onImportFolder() },
                modifier = Modifier.semantics { contentDescription = "Import folder here"; if(model.importing) disabled() },
                colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = if(model.importing) Muted else Ink)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SidebarHeading(title: String) {
    Text(title, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
}
