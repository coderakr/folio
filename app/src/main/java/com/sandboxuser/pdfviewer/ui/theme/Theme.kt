package com.sandboxuser.pdfviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalDarkMode = staticCompositionLocalOf { false }
val Paper: Color @Composable get() = MaterialTheme.colorScheme.background
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Terracotta: Color @Composable get() = MaterialTheme.colorScheme.primary
val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Sage: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val CardSurface: Color @Composable get() = MaterialTheme.colorScheme.surface
val ReaderBackground: Color @Composable get() = if(LocalDarkMode.current) Color(0xFF111510) else Color(0xFFE9E7DF)

@Composable
fun PdfviewerTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when(mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
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
    CompositionLocalProvider(LocalDarkMode provides dark) {
        MaterialTheme(colorScheme = colors, typography = Typography, content = content)
    }
}
