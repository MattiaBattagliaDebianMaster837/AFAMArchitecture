package com.afam.pura.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette in stile Nothing: nero, bianco e un rosso come unico accento. */
object Pura {
    val Accent = Color(0xFFD71921)
    val Yellow = Color(0xFFFFD60A)
    val Green = Color(0xFF34C759)
    val Scrim = Color(0x99000000)
    val Panel = Color(0xCC111111)
    val Chip = Color(0x33FFFFFF)
    val TextDim = Color(0xB3FFFFFF)
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Pura.Accent,
    onSecondary = Color.White,
    tertiary = Pura.Yellow,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF111111),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFB0B0B5),
    surfaceContainer = Color(0xFF161618),
    surfaceContainerHigh = Color(0xFF1C1C1E),
    outline = Color(0xFF3A3A3C),
)

@Composable
fun PuraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
