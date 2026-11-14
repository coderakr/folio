package com.sandboxuser.pdfviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import com.sandboxuser.pdfviewer.data.ColorTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalReaderBackground = staticCompositionLocalOf { Color(0xFFE9E7DF) }
val LocalDarkMode = staticCompositionLocalOf { false }
val Paper: Color @Composable get() = MaterialTheme.colorScheme.background
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Terracotta: Color @Composable get() = MaterialTheme.colorScheme.primary
val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Sage: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val CardSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val ReaderBackground: Color @Composable get() = LocalReaderBackground.current

@Composable
fun PdfviewerTheme(mode: String = "system", palette: ColorTheme = ColorTheme.FOLIO, content: @Composable () -> Unit) {
    val dark = when(mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val selected = palette.colors(dark)
    CompositionLocalProvider(LocalDarkMode provides dark, LocalReaderBackground provides selected.readerBackground) {
        MaterialTheme(colorScheme = selected.scheme, typography = Typography, content = content)
    }
}

internal data class ThemeColors(val scheme: ColorScheme, val readerBackground: Color)

internal fun ColorTheme.colors(dark: Boolean): ThemeColors {
    if (this != ColorTheme.FOLIO) return when (this) {
        ColorTheme.OCEAN -> if(dark) paletteColors(true, 0xFF8CCCF2, 0xFF10202D, 0xFF101C25, 0xFF192934, 0xFFE1EDF5, 0xFFA9BECD, 0xFF243C4D, 0xFF354D5D, 0xFF09131C)
            else paletteColors(false, 0xFF246388, 0xFFFFFFFF, 0xFFF3F8FC, 0xFFFCFEFF, 0xFF203443, 0xFF536B7D, 0xFFDDECF5, 0xFFD0DFEA, 0xFFE2EBF2)
        ColorTheme.FOREST -> if(dark) paletteColors(true, 0xFF96CBA3, 0xFF102D1A, 0xFF121E17, 0xFF1D2D22, 0xFFE3EFE4, 0xFFAFC3B2, 0xFF2B4131, 0xFF3C5141, 0xFF0B140E)
            else paletteColors(false, 0xFF326748, 0xFFFFFFFF, 0xFFF4F8F1, 0xFFFDFFF9, 0xFF25392C, 0xFF5A705E, 0xFFE0ECDD, 0xFFD0DFCE, 0xFFE2EADD)
        ColorTheme.LAVENDER -> if(dark) paletteColors(true, 0xFFC4B2EF, 0xFF30204B, 0xFF1C1826, 0xFF2A2438, 0xFFEDE6F7, 0xFFBFB2D1, 0xFF3C324D, 0xFF4C425E, 0xFF120F1B)
            else paletteColors(false, 0xFF715494, 0xFFFFFFFF, 0xFFFAF6FF, 0xFFFFFCFF, 0xFF382D45, 0xFF756780, 0xFFEDE2F8, 0xFFDFD3EC, 0xFFEBE4F1)
        ColorTheme.ROSE -> if(dark) paletteColors(true, 0xFFE6A8BE, 0xFF482031, 0xFF261A20, 0xFF35262D, 0xFFF6E5EC, 0xFFD0B2BF, 0xFF4A323D, 0xFF5D424F, 0xFF1A1015)
            else paletteColors(false, 0xFF98516D, 0xFFFFFFFF, 0xFFFFF6F8, 0xFFFFFCFD, 0xFF452E38, 0xFF806571, 0xFFF5E0E8, 0xFFE9D1DB, 0xFFF0E2E7)
        else -> error("Original theme is handled below")
    }
    val colors = if(dark) darkColorScheme(
        primary = Color(0xFFE6A084), onPrimary = Color(0xFF422014),
        background = Color(0xFF191D18), surface = Color(0xFF242A23),
        onSurface = Color(0xFFE7EBDF), onBackground = Color(0xFFE7EBDF),
        surfaceVariant = Color(0xFF303B2D), onSurfaceVariant = Color(0xFFB0BAA8),
        outlineVariant = Color(0xFF404A3A), secondary = Color(0xFFBBCCA9)
    ) else lightColorScheme(
        primary = Color(0xFFAD563D), onPrimary = Color.White,
        background = Color(0xFFFAF8F3), surface = Color(0xFFFFFEFA),
        onSurface = Color(0xFF30382F), onBackground = Color(0xFF30382F),
        surfaceVariant = Color(0xFFE9EDDF), onSurfaceVariant = Color(0xFF717768),
        outlineVariant = Color(0xFFE5E5DA), secondary = Color(0xFF657354)
    )
    return ThemeColors(colors, if(dark) Color(0xFF111510) else Color(0xFFE9E7DF))
}

private fun paletteColors(dark: Boolean, accent: Long, onAccent: Long, paper: Long, surface: Long,
    ink: Long, muted: Long, variant: Long, line: Long, reader: Long): ThemeColors {
    val base = if(dark) darkColorScheme() else lightColorScheme()
    return ThemeColors(base.copy(
        primary = Color(accent), onPrimary = Color(onAccent), primaryContainer = Color(variant), onPrimaryContainer = Color(ink),
        secondary = Color(accent), onSecondary = Color(onAccent), secondaryContainer = Color(variant), onSecondaryContainer = Color(ink),
        tertiary = Color(accent), onTertiary = Color(onAccent), tertiaryContainer = Color(variant), onTertiaryContainer = Color(ink),
        background = Color(paper), onBackground = Color(ink), surface = Color(surface), onSurface = Color(ink),
        surfaceVariant = Color(variant), onSurfaceVariant = Color(muted), outline = Color(muted), outlineVariant = Color(line),
        surfaceTint = Color(accent), inverseSurface = Color(ink), inverseOnSurface = Color(paper), inversePrimary = Color(onAccent),
        surfaceContainerLowest = Color(paper), surfaceContainerLow = Color(surface), surfaceContainer = Color(surface),
        surfaceContainerHigh = Color(variant), surfaceContainerHighest = Color(variant), surfaceBright = Color(surface), surfaceDim = Color(paper)
    ), Color(reader))
}
