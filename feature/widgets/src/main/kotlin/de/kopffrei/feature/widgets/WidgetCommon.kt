package de.kopffrei.feature.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.kopffrei.core.common.model.ThemeMode
import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.common.widget.WidgetLogic
import de.kopffrei.core.database.prefs.SettingsRepository
import de.kopffrei.core.database.repository.EventRepository
import de.kopffrei.core.database.repository.NoteRepository
import de.kopffrei.core.database.repository.TaskRepository
import kotlinx.coroutines.flow.first

/** Zugriff auf die Repositories aus Widgets (Widgets werden vom System erzeugt, nicht von Hilt). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun notes(): NoteRepository
    fun tasks(): TaskRepository
    fun events(): EventRepository
    fun settings(): SettingsRepository
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

internal suspend fun Context.currentSettings(): UserSettings = widgetEntryPoint().settings().settings.first()

/** Größen, auf die sich die Widgets einstellen: 2×1, 2×2, 4×2 und 4×4 Felder. */
internal object WidgetSizes {
    val SMALL = DpSize(110.dp, 40.dp)
    val SQUARE = DpSize(110.dp, 110.dp)
    val WIDE = DpSize(250.dp, 110.dp)
    val BIG = DpSize(250.dp, 250.dp)
    val ALL = setOf(SMALL, SQUARE, WIDE, BIG)
}

/** Farben der Widgets: Hell nur im hellen Modus, sonst dunkel (wie die App). */
internal class WidgetPalette(
    val card: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val onAccent: Color,
    val button: Color,
)

private val DarkPalette = WidgetPalette(
    card = Color(0xFF151D33),
    text = Color(0xFFF4F7FF),
    secondary = Color(0xFFA9B5CE),
    accent = Color(0xFF39D6D0),
    onAccent = Color(0xFF0B1020),
    button = Color(0xFF1D2944),
)

private val LightPalette = WidgetPalette(
    card = Color(0xFFFFFFFF),
    text = Color(0xFF0B1020),
    secondary = Color(0xFF4A5670),
    accent = Color(0xFF0B8F8A),
    onAccent = Color(0xFFFFFFFF),
    button = Color(0xFFE6ECF7),
)

internal fun paletteFor(settings: UserSettings): WidgetPalette =
    if (settings.themeMode == ThemeMode.LIGHT) LightPalette else DarkPalette

/** Intent, der die App über einen Deep Link öffnet (Muster siehe core:common DeepLinks). */
internal fun deepLinkIntent(context: Context, uri: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        .setPackage(context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Gemeinsamer Rahmen: abgerundete Karte, optional als Ganzes antippbar. */
@Composable
internal fun WidgetFrame(
    context: Context,
    palette: WidgetPalette,
    deepLink: String?,
    content: @Composable () -> Unit,
) {
    val base = GlanceModifier
        .fillMaxSize()
        .background(ColorProvider(palette.card))
        .cornerRadius(20.dp)
        .padding(12.dp)
    Box(
        modifier = if (deepLink != null) base.clickable(actionStartActivity(deepLinkIntent(context, deepLink))) else base,
    ) {
        content()
    }
}

@Composable
internal fun WidgetTitle(text: String, palette: WidgetPalette) {
    Text(
        text,
        style = TextStyle(color = ColorProvider(palette.text), fontSize = 18.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

@Composable
internal fun WidgetText(
    text: String,
    palette: WidgetPalette,
    secondary: Boolean = false,
    maxLines: Int = 2,
    modifier: GlanceModifier = GlanceModifier,
) {
    Text(
        text,
        modifier = modifier,
        style = TextStyle(
            color = ColorProvider(if (secondary) palette.secondary else palette.text),
            fontSize = if (secondary) 14.sp else 16.sp,
        ),
        maxLines = maxLines,
    )
}

/** Großer antippbarer Knopf (mindestens 48 dp hoch). */
@Composable
internal fun WidgetButton(
    context: Context,
    palette: WidgetPalette,
    label: String,
    deepLink: String,
    modifier: GlanceModifier = GlanceModifier,
    primary: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .background(ColorProvider(if (primary) palette.accent else palette.button))
            .cornerRadius(14.dp)
            .clickable(actionStartActivity(deepLinkIntent(context, deepLink))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(if (primary) palette.onAccent else palette.text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

/** Datenschutz-Hinweis statt Inhalt, solange die App-Sperre aktiv ist. */
@Composable
internal fun LockedContent(palette: WidgetPalette) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        WidgetText(WidgetLogic.LOCKED_TEXT, palette, secondary = true, maxLines = 3)
    }
}
