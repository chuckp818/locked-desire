package com.lockeddesire.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object LD {
    val Black = Color(0xFF09090D)
    val DarkRed = Color(0xFF650B19)
    val Blood = Color(0xFFB51230)
    val Bordeaux = Color(0xFF390817)
    val Violet = Color(0xFF251127)
    val Gold = Color(0xFFB59662)
    val Grey = Color(0xFFD8D3D7)
    val Leather = Color(0xFF14121A)
    val LeatherLight = Color(0xFF1E1A24)
    val Neon = Color(0xFFFF2D55)
    val Dim = Color(0xFF8E8791)
}

fun accentColor(key: String): Color = when (key) {
    "gold" -> LD.Gold
    "violet" -> Color(0xFF8E44AD)
    "neon" -> LD.Neon
    "silver" -> Color(0xFFB8C0CC)
    else -> LD.Blood
}

fun accentName(key: String): String = when (key) {
    "gold" -> "Antikgold"; "violet" -> "Dunkelviolett"; "neon" -> "Rotes Neon"; "silver" -> "Kaltes Silber"; else -> "Blutrot"
}

val LocalAccent = staticCompositionLocalOf { LD.Blood }
val LocalEffects = staticCompositionLocalOf { true }

private val Gothic = FontFamily.Serif

private val LdTypography = Typography(
    displaySmall = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 3.sp),
    headlineMedium = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.5.sp),
    headlineSmall = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 1.sp),
    titleLarge = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.sp),
    titleMedium = TextStyle(fontFamily = Gothic, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = 0.5.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 12.sp)
)

@Composable
fun LockedDesireTheme(accent: String = "blood", effects: Boolean = true, content: @Composable () -> Unit) {
    val a = accentColor(accent)
    val scheme = darkColorScheme(
        primary = a, onPrimary = Color.White,
        primaryContainer = LD.DarkRed, onPrimaryContainer = LD.Grey,
        secondary = LD.Gold, onSecondary = LD.Black,
        secondaryContainer = LD.Bordeaux, onSecondaryContainer = LD.Grey,
        tertiary = LD.Gold, onTertiary = LD.Black,
        background = LD.Black, onBackground = LD.Grey,
        surface = LD.Leather, onSurface = LD.Grey,
        surfaceVariant = LD.LeatherLight, onSurfaceVariant = LD.Grey,
        surfaceContainer = LD.Leather, surfaceContainerHigh = LD.LeatherLight, surfaceContainerHighest = LD.LeatherLight,
        surfaceContainerLow = LD.Leather, surfaceContainerLowest = LD.Black,
        outline = LD.Gold.copy(alpha = 0.6f), outlineVariant = LD.Gold.copy(alpha = 0.25f),
        error = Color(0xFFFF6B6B), onError = LD.Black
    )
    CompositionLocalProvider(LocalAccent provides a, LocalEffects provides effects) {
        MaterialTheme(colorScheme = scheme, typography = LdTypography, content = content)
    }
}
