package de.kopffrei.feature.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.kopffrei.core.common.DeepLinks
import de.kopffrei.core.common.tasks.TaskLogic
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.common.widget.WidgetLogic
import de.kopffrei.core.database.entity.toView
import de.kopffrei.core.database.repository.EventExpander

// Näherungswerte (dp) für die Zeilenzahl: Rahmen + Titel bzw. eine Textzeile.
private const val HEADER_DP = 60
private const val LINE_DP = 28

/** Eine antippbare Textzeile, die per Deep Link in die App springt. */
@Composable
private fun WidgetLine(context: Context, palette: WidgetPalette, text: String, deepLink: String) {
    Text(
        text,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(actionStartActivity(deepLinkIntent(context, deepLink))),
        style = TextStyle(color = ColorProvider(palette.text), fontSize = 16.sp),
        maxLines = 1,
    )
}

private data class Line(val text: String, val deepLink: String)

// ---------------------------------------------------------------------------------- Heutige Termine

/** Widget „Heutige Termine“. Bei aktiver App-Sperre nur ein neutraler Hinweis. */
class TodayEventsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = context.currentSettings()
        val palette = paletteFor(settings)
        val show = WidgetLogic.showsContent(settings)
        val lines = if (show) loadTodayEvents(context) else emptyList()
        provideContent {
            val size = LocalSize.current
            val max = WidgetLogic.visibleItems(size.height.value.toInt(), HEADER_DP, LINE_DP, 8)
            WidgetFrame(context, palette, DeepLinks.CALENDAR) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    WidgetTitle("Heute", palette)
                    Spacer(GlanceModifier.height(4.dp))
                    when {
                        !show -> LockedContent(palette)
                        lines.isEmpty() -> WidgetText("Heute keine Termine mehr.", palette, secondary = true)
                        else -> lines.take(max).forEach { WidgetLine(context, palette, it.text, it.deepLink) }
                    }
                }
            }
        }
    }

    private suspend fun loadTodayEvents(context: Context): List<Line> {
        val zone = TimeUtils.systemZone()
        val now = TimeUtils.nowMillis()
        val all = context.widgetEntryPoint().events().getAll()
        return EventExpander.onDay(all, TimeUtils.startOfDay(now, zone), zone)
            // Beendete Termine blenden wir aus; ganztägige bleiben den ganzen Tag sichtbar.
            .filter { it.event.allDay || it.endMillis >= now }
            .map {
                Line(
                    WidgetLogic.eventLine(it.event.title, it.startMillis, it.event.allDay, zone),
                    DeepLinks.openEvent(it.event.id),
                )
            }
    }
}

class TodayEventsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayEventsWidget()
}

// ---------------------------------------------------------------------------------- Aufgabenliste

/** Widget „Aufgabenliste“: die wichtigsten offenen Aufgaben (überfällig, heute, Priorität). */
class TaskListWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = context.currentSettings()
        val palette = paletteFor(settings)
        val show = WidgetLogic.showsContent(settings)
        val lines = if (show) loadTasks(context, limit = 8) else emptyList()
        provideContent {
            val size = LocalSize.current
            val max = WidgetLogic.visibleItems(size.height.value.toInt(), HEADER_DP, LINE_DP, 8)
            WidgetFrame(context, palette, DeepLinks.TASKS) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    WidgetTitle("Aufgaben", palette)
                    Spacer(GlanceModifier.height(4.dp))
                    when {
                        !show -> LockedContent(palette)
                        lines.isEmpty() -> WidgetText("Keine offenen Aufgaben.", palette, secondary = true)
                        else -> lines.take(max).forEach { WidgetLine(context, palette, it.text, it.deepLink) }
                    }
                }
            }
        }
    }
}

private suspend fun loadTasks(context: Context, limit: Int): List<Line> {
    val zone = TimeUtils.systemZone()
    val now = TimeUtils.nowMillis()
    val open = context.widgetEntryPoint().tasks().getOpen().map { it.toView() }
    return TaskLogic.rank(open, now, zone, limit).map {
        Line(WidgetLogic.taskLine(it, now, zone), DeepLinks.openTask(it.id))
    }
}

class TaskListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskListWidget()
}

// ---------------------------------------------------------------------------------- Angeheftete Notiz

/** Widget „Angeheftete Notiz“: zeigt die zuletzt angeheftete Notiz. */
class PinnedNoteWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = context.currentSettings()
        val palette = paletteFor(settings)
        val show = WidgetLogic.showsContent(settings)
        val note = if (show) context.widgetEntryPoint().notes().getLatestPinned() else null
        provideContent {
            val size = LocalSize.current
            val big = size.height >= WidgetSizes.WIDE.height
            WidgetFrame(
                context,
                palette,
                if (note != null) DeepLinks.openNote(note.id) else DeepLinks.NOTES,
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    when {
                        !show -> {
                            WidgetTitle("Notiz", palette)
                            Spacer(GlanceModifier.height(4.dp))
                            LockedContent(palette)
                        }
                        note == null -> {
                            WidgetTitle("Notiz", palette)
                            Spacer(GlanceModifier.height(4.dp))
                            WidgetText(
                                "Keine angeheftete Notiz. Hefte in der Notizenliste eine Notiz an.",
                                palette,
                                secondary = true,
                                maxLines = 4,
                            )
                        }
                        else -> {
                            val title = note.title.ifBlank { TextUtils.truncate(TextUtils.firstLine(note.body), 40) }
                                .ifBlank { "Notiz" }
                            WidgetTitle(title, palette)
                            Spacer(GlanceModifier.height(4.dp))
                            WidgetText(
                                WidgetLogic.notePreview(
                                    body = note.body,
                                    isChecklist = note.isChecklist,
                                    maxChars = if (big) 300 else 90,
                                    maxChecklistItems = if (big) 8 else 3,
                                ),
                                palette,
                                maxLines = if (big) 10 else 4,
                            )
                        }
                    }
                }
            }
        }
    }
}

class PinnedNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PinnedNoteWidget()
}

// ---------------------------------------------------------------------------------- Tagesübersicht

/** Widget „Tagesübersicht“: Datum, nächster Termin und die wichtigsten Aufgaben. */
class DayOverviewWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = context.currentSettings()
        val palette = paletteFor(settings)
        val show = WidgetLogic.showsContent(settings)
        val zone = TimeUtils.systemZone()
        val now = TimeUtils.nowMillis()
        val today = TimeUtils.toLocalDate(now, zone)
        val nextEvent: Line? = if (show) {
            val all = context.widgetEntryPoint().events().getAll()
            EventExpander.next(all, now, zone, horizonDays = 7)?.let {
                Line(
                    WidgetLogic.eventLine(it.event.title, it.startMillis, it.event.allDay, zone) +
                        if (TimeUtils.toLocalDate(it.startMillis, zone) == today) "" else
                            " (${DateText.relativeDay(TimeUtils.toLocalDate(it.startMillis, zone), today)})",
                    DeepLinks.openEvent(it.event.id),
                )
            }
        } else {
            null
        }
        val taskLines = if (show) loadTasks(context, limit = 3) else emptyList()
        provideContent {
            val size = LocalSize.current
            val roomy = size.height >= WidgetSizes.WIDE.height
            WidgetFrame(context, palette, DeepLinks.TODAY) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    WidgetTitle(DateText.dateLong(today), palette)
                    Spacer(GlanceModifier.height(4.dp))
                    when {
                        !show -> LockedContent(palette)
                        else -> {
                            if (roomy) WidgetText("Nächster Termin", palette, secondary = true, maxLines = 1)
                            if (nextEvent != null) {
                                WidgetLine(context, palette, nextEvent.text, nextEvent.deepLink)
                            } else {
                                WidgetText("Kein Termin in den nächsten 7 Tagen.", palette, secondary = true)
                            }
                            if (roomy) {
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetText("Wichtigste Aufgaben", palette, secondary = true, maxLines = 1)
                                if (taskLines.isEmpty()) {
                                    WidgetText("Keine offenen Aufgaben.", palette, secondary = true)
                                } else {
                                    taskLines.forEach { WidgetLine(context, palette, it.text, it.deepLink) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class DayOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DayOverviewWidget()
}

// ---------------------------------------------------------------------------------- Gedanken-Parkplatz

/** Widget „Gedanken-Parkplatz“: Knopf zum Parken eines Gedankens und die letzten Gedanken. */
class ThoughtParkingWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = context.currentSettings()
        val palette = paletteFor(settings)
        val show = WidgetLogic.showsContent(settings)
        val thoughts = if (show) {
            context.widgetEntryPoint().notes().getLatestThoughts(6).map {
                Line(
                    TextUtils.truncate(TextUtils.firstLine(it.body.ifBlank { it.title }), 60).ifBlank { "Gedanke" },
                    DeepLinks.openNote(it.id),
                )
            }
        } else {
            emptyList()
        }
        provideContent {
            val size = LocalSize.current
            val max = WidgetLogic.visibleItems(size.height.value.toInt(), HEADER_DP + 56, LINE_DP, 6)
            WidgetFrame(context, palette, DeepLinks.NOTES) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    if (size.height >= WidgetSizes.SQUARE.height) {
                        WidgetTitle("Gedanken-Parkplatz", palette)
                        Spacer(GlanceModifier.height(4.dp))
                    }
                    // Der Knopf zum Parken ist immer verfügbar – auch bei aktiver App-Sperre.
                    WidgetButton(
                        context, palette, "Gedanke parken", DeepLinks.newNote(thought = true),
                        modifier = GlanceModifier.fillMaxWidth(),
                    )
                    if (size.height >= WidgetSizes.WIDE.height) {
                        Spacer(GlanceModifier.height(6.dp))
                        when {
                            !show -> LockedContent(palette)
                            thoughts.isEmpty() -> WidgetText("Noch nichts geparkt.", palette, secondary = true)
                            else -> thoughts.take(max).forEach { WidgetLine(context, palette, it.text, it.deepLink) }
                        }
                    }
                }
            }
        }
    }
}

class ThoughtParkingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ThoughtParkingWidget()
}
