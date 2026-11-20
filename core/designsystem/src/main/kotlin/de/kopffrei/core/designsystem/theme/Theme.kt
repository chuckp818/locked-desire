package de.kopffrei.core.designsystem.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import de.kopffrei.core.common.model.ThemeMode
import de.kopffrei.core.common.settings.UserSettings

/** Darstellungs-Einstellungen, die Komponenten (nicht nur Farben) beeinflussen. */
@Immutable
data class KfUiPrefs(
    val largeButtons: Boolean = false,
    val reduceAnimations: Boolean = false,
    val lowStimulation: Boolean = false,
    val highContrast: Boolean = false,
)

val LocalKfUi = staticCompositionLocalOf { KfUiPrefs() }

val LocalKfExtraColors = staticCompositionLocalOf {
    extraColorsFor(ThemeMode.DARK, kopfFreiColorScheme(ThemeMode.DARK, de.kopffrei.core.common.model.AccentPreset.TURQUOISE))
}

/** True, solange unten die Hauptnavigation sichtbar ist (verhindert doppelten Randabstand). */
val LocalBottomBarVisible = compositionLocalOf { false }

/** Zugriff auf die zusätzlichen KopfFrei-Werte neben `MaterialTheme`. */
object KfTheme {
    val extra: KfExtraColors
        @Composable @ReadOnlyComposable get() = LocalKfExtraColors.current

    val ui: KfUiPrefs
        @Composable @ReadOnlyComposable get() = LocalKfUi.current
}

val KfTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    // Normaler Text mindestens 16 sp.
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

val KfShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun KopfFreiTheme(settings: UserSettings, content: @Composable () -> Unit) {
    val colorScheme = remember(settings.themeMode, settings.accent) {
        kopfFreiColorScheme(settings.themeMode, settings.accent)
    }
    val extra = remember(colorScheme, settings.themeMode) { extraColorsFor(settings.themeMode, colorScheme) }
    val ui = remember(settings.largeButtons, settings.reduceAnimations, settings.themeMode) {
        KfUiPrefs(
            largeButtons = settings.largeButtons,
            reduceAnimations = settings.reduceAnimations,
            lowStimulation = settings.themeMode == ThemeMode.LOW_STIMULATION,
            highContrast = settings.themeMode == ThemeMode.HIGH_CONTRAST,
        )
    }

    // Statusleisten-Symbole passend zum Hintergrund (hell nur im hellen Modus dunkel).
    val view = LocalView.current
    if (!view.isInEditMode) {
        val lightBars = settings.themeMode == ThemeMode.LIGHT
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = lightBars
                controller.isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    // Eigene Schriftgröße wird mit der Systemeinstellung multipliziert.
    val baseDensity = LocalDensity.current
    val density = remember(baseDensity, settings.fontScale) {
        Density(baseDensity.density, baseDensity.fontScale * settings.fontScale)
    }

    CompositionLocalProvider(
        LocalDensity provides density,
        LocalKfExtraColors provides extra,
        LocalKfUi provides ui,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KfTypography,
            shapes = KfShapes,
            content = content,
        )
    }
}
