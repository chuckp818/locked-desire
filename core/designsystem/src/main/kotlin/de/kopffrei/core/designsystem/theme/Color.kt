package de.kopffrei.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import de.kopffrei.core.common.model.AccentPreset
import de.kopffrei.core.common.model.ThemeMode

/** Farbpalette aus dem Designkonzept (Premium-Dark-Modus ist der Standard). */
object KfPalette {
    val Background = Color(0xFF0B1020)
    val Card = Color(0xFF151D33)
    val CardElevated = Color(0xFF1D2944)
    val Turquoise = Color(0xFF39D6D0)
    val Violet = Color(0xFF9878F8)
    val Blue = Color(0xFF65B8FF)
    val TextPrimary = Color(0xFFF4F7FF)
    val TextSecondary = Color(0xFFA9B5CE)
    val Success = Color(0xFF65D6A0)
    val Warning = Color(0xFFF2BE68)
    val Error = Color(0xFFF17F91)
}

/** Zusätzliche Farben, die Material 3 nicht kennt. */
data class KfExtraColors(
    val success: Color,
    val warning: Color,
    val cardElevated: Color,
    /** Sanfter Verlauf für Hervorhebungen; null = keine Verläufe (reizarm/Kontrast). */
    val gradient: List<Color>?,
)

internal fun extraColorsFor(mode: ThemeMode, scheme: ColorScheme): KfExtraColors = when (mode) {
    ThemeMode.DARK -> KfExtraColors(
        success = KfPalette.Success,
        warning = KfPalette.Warning,
        cardElevated = KfPalette.CardElevated,
        gradient = listOf(KfPalette.Turquoise, KfPalette.Blue, KfPalette.Violet),
    )
    ThemeMode.LIGHT -> KfExtraColors(
        success = Color(0xFF1E8E5A),
        warning = Color(0xFF9A6A00),
        cardElevated = Color(0xFFEAF0FA),
        gradient = listOf(Color(0xFF0A8F8A), Color(0xFF1B73CC), Color(0xFF6B4BDB)),
    )
    ThemeMode.LOW_STIMULATION -> KfExtraColors(
        success = Color(0xFF7DB59A),
        warning = Color(0xFFC9AE79),
        cardElevated = Color(0xFF222732),
        gradient = null,
    )
    ThemeMode.HIGH_CONTRAST -> KfExtraColors(
        success = Color(0xFF5CFF9D),
        warning = Color(0xFFFFD84D),
        cardElevated = scheme.surfaceVariant,
        gradient = null,
    )
}

/** Textfarbe mit dem besseren Kontrast auf [background]. */
internal fun onColorFor(background: Color): Color {
    val l = background.luminance()
    val whiteContrast = 1.05f / (l + 0.05f)
    val darkContrast = (l + 0.05f) / 0.055f
    return if (darkContrast >= whiteContrast) Color(0xFF07121A) else Color.White
}

/** Drei Grundfarben (Türkis, Violett, Blau) je Modus; die gewählte Akzentfarbe wird zur Primärfarbe. */
private fun triad(mode: ThemeMode): Triple<Color, Color, Color> = when (mode) {
    ThemeMode.DARK -> Triple(KfPalette.Turquoise, KfPalette.Violet, KfPalette.Blue)
    ThemeMode.LIGHT -> Triple(Color(0xFF0A8F8A), Color(0xFF6B4BDB), Color(0xFF1B73CC))
    ThemeMode.LOW_STIMULATION -> Triple(Color(0xFF7FB3B0), Color(0xFFA29BC9), Color(0xFF86A9CC))
    ThemeMode.HIGH_CONTRAST -> Triple(Color(0xFF00FFF0), Color(0xFFC8B2FF), Color(0xFF8FD0FF))
}

fun kopfFreiColorScheme(mode: ThemeMode, accent: AccentPreset): ColorScheme {
    val (turquoise, violet, blue) = triad(mode)
    val (primary, secondary, tertiary) = when (accent) {
        AccentPreset.TURQUOISE -> Triple(turquoise, violet, blue)
        AccentPreset.VIOLET -> Triple(violet, turquoise, blue)
        AccentPreset.BLUE -> Triple(blue, turquoise, violet)
    }
    return when (mode) {
        ThemeMode.LIGHT -> {
            val bg = Color(0xFFF5F7FC)
            val card = Color.White
            val text = Color(0xFF111A2E)
            lightColorScheme(
                primary = primary, onPrimary = onColorFor(primary),
                primaryContainer = lerp(Color.White, primary, 0.18f), onPrimaryContainer = text,
                secondary = secondary, onSecondary = onColorFor(secondary),
                secondaryContainer = lerp(Color.White, secondary, 0.18f), onSecondaryContainer = text,
                tertiary = tertiary, onTertiary = onColorFor(tertiary),
                tertiaryContainer = lerp(Color.White, tertiary, 0.18f), onTertiaryContainer = text,
                background = bg, onBackground = text,
                surface = card, onSurface = text,
                surfaceVariant = Color(0xFFEAF0FA), onSurfaceVariant = Color(0xFF4B5873),
                outline = Color(0xFF8A96AD), outlineVariant = Color(0xFFD3DBEA),
                error = Color(0xFFB3261E), onError = Color.White,
                errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
                surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF9FAFE),
                surfaceContainer = card, surfaceContainerHigh = Color(0xFFEAF0FA),
                surfaceContainerHighest = Color(0xFFDFE7F5),
            )
        }
        ThemeMode.LOW_STIMULATION -> {
            val bg = Color(0xFF12151B)
            val card = Color(0xFF1A1E26)
            val text = Color(0xFFE6E9EF)
            darkColorScheme(
                primary = primary, onPrimary = onColorFor(primary),
                primaryContainer = lerp(bg, primary, 0.2f), onPrimaryContainer = text,
                secondary = secondary, onSecondary = onColorFor(secondary),
                secondaryContainer = lerp(bg, secondary, 0.2f), onSecondaryContainer = text,
                tertiary = tertiary, onTertiary = onColorFor(tertiary),
                tertiaryContainer = lerp(bg, tertiary, 0.2f), onTertiaryContainer = text,
                background = bg, onBackground = text,
                surface = card, onSurface = text,
                surfaceVariant = Color(0xFF222732), onSurfaceVariant = Color(0xFF9AA3B2),
                outline = Color(0xFF3B4250), outlineVariant = Color(0xFF2A303B),
                error = Color(0xFFC98A93), onError = Color(0xFF2B0A10),
                errorContainer = Color(0xFF3A2428), onErrorContainer = text,
                surfaceContainerLowest = bg, surfaceContainerLow = Color(0xFF161A21),
                surfaceContainer = card, surfaceContainerHigh = Color(0xFF222732),
                surfaceContainerHighest = Color(0xFF2A303B),
            )
        }
        ThemeMode.HIGH_CONTRAST -> {
            val bg = Color.Black
            val text = Color.White
            darkColorScheme(
                primary = primary, onPrimary = Color.Black,
                primaryContainer = Color(0xFF003B38), onPrimaryContainer = text,
                secondary = secondary, onSecondary = Color.Black,
                secondaryContainer = Color(0xFF2A2145), onSecondaryContainer = text,
                tertiary = tertiary, onTertiary = Color.Black,
                tertiaryContainer = Color(0xFF12304A), onTertiaryContainer = text,
                background = bg, onBackground = text,
                surface = Color(0xFF0A0A0A), onSurface = text,
                surfaceVariant = Color(0xFF161616), onSurfaceVariant = Color(0xFFE6E6E6),
                outline = Color.White, outlineVariant = Color(0xFFB0B0B0),
                error = Color(0xFFFF8A9B), onError = Color.Black,
                errorContainer = Color(0xFF5A0E1A), onErrorContainer = text,
                surfaceContainerLowest = bg, surfaceContainerLow = Color(0xFF050505),
                surfaceContainer = Color(0xFF0A0A0A), surfaceContainerHigh = Color(0xFF161616),
                surfaceContainerHighest = Color(0xFF222222),
            )
        }
        ThemeMode.DARK -> {
            val bg = KfPalette.Background
            darkColorScheme(
                primary = primary, onPrimary = onColorFor(primary),
                primaryContainer = lerp(bg, primary, 0.22f), onPrimaryContainer = KfPalette.TextPrimary,
                secondary = secondary, onSecondary = onColorFor(secondary),
                secondaryContainer = lerp(bg, secondary, 0.22f), onSecondaryContainer = KfPalette.TextPrimary,
                tertiary = tertiary, onTertiary = onColorFor(tertiary),
                tertiaryContainer = lerp(bg, tertiary, 0.22f), onTertiaryContainer = KfPalette.TextPrimary,
                background = bg, onBackground = KfPalette.TextPrimary,
                surface = KfPalette.Card, onSurface = KfPalette.TextPrimary,
                surfaceVariant = KfPalette.CardElevated, onSurfaceVariant = KfPalette.TextSecondary,
                outline = Color(0xFF4A587A), outlineVariant = Color(0xFF2A3452),
                error = KfPalette.Error, onError = Color(0xFF2B0A10),
                errorContainer = lerp(bg, KfPalette.Error, 0.25f), onErrorContainer = KfPalette.TextPrimary,
                surfaceContainerLowest = bg, surfaceContainerLow = Color(0xFF11182B),
                surfaceContainer = KfPalette.Card, surfaceContainerHigh = KfPalette.CardElevated,
                surfaceContainerHighest = Color(0xFF26355A),
            )
        }
    }
}

/** Farben für Notizen und Termine (Index 0 = keine Farbe). */
object KfItemColors {
    val palette: List<Color> = listOf(
        Color(0xFF39D6D0), // 1 Türkis
        Color(0xFF65B8FF), // 2 Blau
        Color(0xFF9878F8), // 3 Violett
        Color(0xFF65D6A0), // 4 Grün
        Color(0xFFF2BE68), // 5 Gelb
        Color(0xFFF17F91), // 6 Rot
        Color(0xFFF29BD0), // 7 Rosa
    )
    val names: List<String> = listOf("Türkis", "Blau", "Violett", "Grün", "Gelb", "Rot", "Rosa")

    /** Farbe zum Index (1..7) oder null für „keine Farbe“. */
    fun colorOf(index: Int): Color? = palette.getOrNull(index - 1)
}
