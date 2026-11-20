package de.kopffrei.feature.widgets

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import de.kopffrei.core.common.DeepLinks

/** Widget „Schnellnotiz“: öffnet direkt eine leere Notiz. Zeigt nie Inhalte. */
class QuickNoteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = paletteFor(context.currentSettings())
        provideContent {
            val size = LocalSize.current
            WidgetFrame(context, palette, DeepLinks.newNote()) {
                Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetTitle("Schnellnotiz", palette)
                    if (size.height >= WidgetSizes.SQUARE.height) {
                        WidgetText("Tippen und losschreiben.", palette, secondary = true)
                    }
                }
            }
        }
    }
}

class QuickNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickNoteWidget()
}

/** Widget „Spracheingabe“: öffnet die Notiz und startet die Spracheingabe. Zeigt nie Inhalte. */
class VoiceInputWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = paletteFor(context.currentSettings())
        provideContent {
            val size = LocalSize.current
            WidgetFrame(context, palette, DeepLinks.newNote(voice = true)) {
                Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetTitle("Einsprechen", palette)
                    if (size.height >= WidgetSizes.SQUARE.height) {
                        WidgetText("Tippen und sprechen.", palette, secondary = true)
                    }
                }
            }
        }
    }
}

class VoiceInputWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceInputWidget()
}

/**
 * Widget „Fokus-Timer“: Knöpfe öffnen die App und starten den Timer (Deep Link mit autostart).
 * Das Widget zeigt keinen laufenden Countdown – den sieht man in der App und in der Benachrichtigung am Ende.
 */
class FocusTimerWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val palette = paletteFor(context.currentSettings())
        provideContent {
            val size = LocalSize.current
            WidgetFrame(context, palette, DeepLinks.focus()) {
                Column(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    if (size.height >= WidgetSizes.SQUARE.height) {
                        WidgetTitle("Fokus-Timer", palette)
                        Spacer(GlanceModifier.height(6.dp))
                    }
                    if (size.width < WidgetSizes.WIDE.width) {
                        // Schmale Größen: ein einziger Knopf.
                        WidgetButton(
                            context, palette, "Fokus 25 Min.", DeepLinks.focus(25, autostart = true),
                            modifier = GlanceModifier.fillMaxWidth(),
                        )
                    } else {
                        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val minutes = listOf(5, 15, 25, 45)
                            minutes.forEachIndexed { index, m ->
                                WidgetButton(
                                    context, palette, "$m", DeepLinks.focus(m, autostart = true),
                                    modifier = GlanceModifier.defaultWeight(),
                                    primary = m == 25,
                                )
                                if (index < minutes.lastIndex) Spacer(GlanceModifier.width(6.dp))
                            }
                        }
                        if (size.height >= WidgetSizes.SQUARE.height) {
                            WidgetText("Minuten – Tippen startet den Timer.", palette, secondary = true)
                        }
                    }
                }
            }
        }
    }
}

class FocusTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusTimerWidget()
}
