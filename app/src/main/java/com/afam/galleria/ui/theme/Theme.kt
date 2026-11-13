package com.afam.galleria.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Palette ispirata ai colori di sistema di iOS. */
@Immutable
data class Palette(
    val isDark: Boolean,
    val background: Color,
    val groupedBackground: Color,
    val card: Color,
    val elevated: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,
    val accent: Color,
    val red: Color,
    val yellow: Color,
    val green: Color,
    val bar: Color,
    val placeholder: Color,
)

val LightPalette = Palette(
    isDark = false,
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    elevated = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x1F767680),
    accent = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    yellow = Color(0xFFFFCC00),
    green = Color(0xFF34C759),
    bar = Color(0xF2F9F9F9),
    placeholder = Color(0xFFE5E5EA),
)

val DarkPalette = Palette(
    isDark = true,
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    elevated = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xA6545458),
    fill = Color(0x3D767680),
    accent = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    yellow = Color(0xFFFFD60A),
    green = Color(0xFF30D158),
    bar = Color(0xF0161616),
    placeholder = Color(0xFF1C1C1E),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/** Stili di testo in stile iOS (Dynamic Type "Large"). */
object IosType {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
    val title1 = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.005).em)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val subhead = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
}

object Ios {
    val colors: Palette
        @Composable
        @ReadOnlyComposable
        get() = LocalPalette.current
}

@Composable
fun GalleryTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent,
            background = palette.background,
            onBackground = palette.label,
            surface = palette.card,
            onSurface = palette.label,
            surfaceVariant = palette.elevated,
            onSurfaceVariant = palette.secondaryLabel,
            surfaceContainer = palette.card,
            surfaceContainerHigh = palette.elevated,
            surfaceContainerLow = palette.card,
            error = palette.red,
            outline = palette.separator,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent,
            background = palette.background,
            onBackground = palette.label,
            surface = palette.card,
            onSurface = palette.label,
            surfaceVariant = palette.elevated,
            onSurfaceVariant = palette.secondaryLabel,
            surfaceContainer = palette.card,
            surfaceContainerHigh = palette.card,
            surfaceContainerLow = palette.groupedBackground,
            error = palette.red,
            outline = palette.separator,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
