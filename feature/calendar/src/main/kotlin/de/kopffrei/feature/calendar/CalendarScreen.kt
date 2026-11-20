package de.kopffrei.feature.calendar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.calendar.CalendarLogic
import de.kopffrei.core.common.calendar.CalendarMode
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.RecurrenceText
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.repository.EventOccurrence
import de.kopffrei.core.designsystem.components.EmptyState
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfChip
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.theme.KfItemColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Kalender mit Monats-, Wochen-, Tages- und Agenda-Ansicht. */
@Composable
fun CalendarScreen(
    onOpenEvent: (eventId: String) -> Unit,
    onNewEvent: (dayStartMillis: Long) -> Unit,
    onOpenTask: (taskId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = remember { TimeUtils.systemZone() }
    val snackbar = remember { SnackbarHostState() }
    var menuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importFrom(uri) }

    LaunchedEffect(state.message) {
        val text = state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    val newEventForSelectedDay = { onNewEvent(TimeUtils.startOfDay(state.selectedDate, zone)) }

    KfScreen(
        title = "Kalender",
        modifier = modifier,
        actions = {
            IconButton(onClick = viewModel::showToday) {
                Icon(Icons.Rounded.Today, contentDescription = "Zu heute springen")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Kalender importieren (.ics)") },
                        leadingIcon = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            importLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Alle Termine exportieren (.ics)") },
                        leadingIcon = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch("kopffrei-kalender.ics")
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "modes") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalendarMode.entries.forEach { mode ->
                        KfChip(mode.label, selected = state.mode == mode, onClick = { viewModel.setMode(mode) })
                    }
                }
            }
            item(key = "pager") {
                PagerHeader(
                    title = state.title,
                    mode = state.mode,
                    onPrevious = { viewModel.page(-1) },
                    onNext = { viewModel.page(1) },
                )
            }
            when (state.mode) {
                CalendarMode.MONTH -> {
                    item(key = "grid") { MonthGrid(state, onSelect = viewModel::select) }
                    item(key = "day-header") { DayHeader(state.selectedDate, state.today) }
                    val day = state.selectedDay
                    if (day == null || (day.events.isEmpty() && day.tasks.isEmpty())) {
                        item(key = "day-empty") {
                            Text(
                                "Nichts geplant.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        dayEntries(day, zone, "m", onOpenEvent, onOpenTask)
                    }
                    item(key = "day-add") {
                        KfButton("Termin hinzufügen", newEventForSelectedDay, icon = Icons.Rounded.Add, style = KfButtonStyle.Tonal)
                    }
                }

                CalendarMode.WEEK -> state.days.forEach { day ->
                    item(key = "wh-${day.date}") {
                        DayHeader(
                            date = day.date,
                            today = state.today,
                            onClick = {
                                viewModel.select(day.date)
                                viewModel.setMode(CalendarMode.DAY)
                            },
                        )
                    }
                    if (day.events.isEmpty() && day.tasks.isEmpty()) {
                        item(key = "we-${day.date}") {
                            Text(
                                "Nichts geplant.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        dayEntries(day, zone, "w-${day.date}", onOpenEvent, onOpenTask)
                    }
                }

                CalendarMode.DAY -> {
                    item(key = "dh") { DayHeader(state.selectedDate, state.today) }
                    val day = state.selectedDay
                    if (day == null || (day.events.isEmpty() && day.tasks.isEmpty())) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.Rounded.Event,
                                title = "Nichts geplant",
                                message = "Für diesen Tag gibt es keine Termine oder fälligen Aufgaben.",
                                actionText = "Termin hinzufügen",
                                onAction = newEventForSelectedDay,
                            )
                        }
                    } else {
                        dayEntries(day, zone, "d", onOpenEvent, onOpenTask)
                        item(key = "add") {
                            KfButton("Termin hinzufügen", newEventForSelectedDay, icon = Icons.Rounded.Add, style = KfButtonStyle.Tonal)
                        }
                    }
                }

                CalendarMode.AGENDA -> {
                    val filled = state.days.filter { it.events.isNotEmpty() || it.tasks.isNotEmpty() }
                    if (filled.isEmpty()) {
                        item(key = "agenda-empty") {
                            EmptyState(
                                icon = Icons.Rounded.CalendarMonth,
                                title = "Keine Termine in Sicht",
                                message = "In den nächsten ${CalendarLogic.AGENDA_DAYS} Tagen ist nichts geplant.",
                                actionText = "Termin hinzufügen",
                                onAction = newEventForSelectedDay,
                            )
                        }
                    } else {
                        filled.forEach { day ->
                            item(key = "ah-${day.date}") {
                                DayHeader(
                                    date = day.date,
                                    today = state.today,
                                    onClick = {
                                        viewModel.select(day.date)
                                        viewModel.setMode(CalendarMode.DAY)
                                    },
                                )
                            }
                            dayEntries(day, zone, "a-${day.date}", onOpenEvent, onOpenTask)
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.dayEntries(
    day: DayEntries,
    zone: ZoneId,
    keyPrefix: String,
    onOpenEvent: (String) -> Unit,
    onOpenTask: (String) -> Unit,
) {
    items(day.events, key = { "$keyPrefix-e-${it.event.id}-${it.startMillis}" }) { occurrence ->
        EventCard(occurrence, zone, onClick = { onOpenEvent(occurrence.event.id) })
    }
    items(day.tasks, key = { "$keyPrefix-t-${it.id}" }) { task ->
        DueTaskCard(task, zone, onClick = { onOpenTask(task.id) })
    }
}

@Composable
private fun PagerHeader(
    title: String,
    mode: CalendarMode,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val (previous, next) = when (mode) {
        CalendarMode.MONTH -> "Vorheriger Monat" to "Nächster Monat"
        CalendarMode.WEEK -> "Vorherige Woche" to "Nächste Woche"
        CalendarMode.DAY -> "Vorheriger Tag" to "Nächster Tag"
        CalendarMode.AGENDA -> "Früher" to "Später"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.Rounded.ChevronLeft, contentDescription = previous) }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) { Icon(Icons.Rounded.ChevronRight, contentDescription = next) }
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, onClick: (() -> Unit)? = null) {
    val diff = date.toEpochDay() - today.toEpochDay()
    val label = if (diff in -1L..2L) {
        "${DateText.relativeDay(date, today)}, ${DateText.dateShort(date)}"
    } else {
        "${DateText.weekdayLong(date)}, ${DateText.dateShort(date)}"
    }
    val base = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
    Box(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val month = YearMonth.from(state.selectedDate)
    Column {
        Row(Modifier.fillMaxWidth()) {
            (1..7).forEach { weekday ->
                Text(
                    DateText.weekdayLetters(weekday),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        state.days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        inMonth = YearMonth.from(day.date) == month,
                        selected = day.date == state.selectedDate,
                        isToday = day.date == state.today,
                        onClick = { onSelect(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: DayEntries,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(14.dp)
    val description = buildString {
        append(DateText.dateLong(day.date))
        if (day.events.isNotEmpty()) append(", ${day.events.size} Termine")
        if (day.tasks.isNotEmpty()) append(", ${day.tasks.size} Aufgaben fällig")
    }
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .padding(2.dp)
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.22f) else Color.Transparent)
            .then(if (isToday) Modifier.border(BorderStroke(2.dp, primary), shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (inMonth) 1f else 0.45f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.heightIn(min = 8.dp)) {
                day.events.take(3).forEach { occurrence ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(KfItemColors.colorOf(occurrence.event.colorIndex) ?: primary, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(occurrence: EventOccurrence, zone: ZoneId, onClick: () -> Unit) {
    val event = occurrence.event
    val color = KfItemColors.colorOf(event.colorIndex) ?: MaterialTheme.colorScheme.primary
    KfCard(onClick = onClick, accent = color) {
        Text(event.title, style = MaterialTheme.typography.titleMedium)
        Text(
            DateText.eventRange(occurrence.startMillis, occurrence.endMillis, event.allDay, zone),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (event.location.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Place, contentDescription = "Ort", modifier = Modifier.size(18.dp))
                Text(event.location, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val times = listOfNotNull(
            event.prepMinutes.takeIf { it > 0 }?.let { "Vorbereitung ${TextUtils.formatMinutes(it)}" },
            event.travelMinutes.takeIf { it > 0 }?.let { "Fahrzeit ${TextUtils.formatMinutes(it)}" },
        )
        if (times.isNotEmpty()) {
            Text(
                times.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (event.recurrence.isRecurring) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Repeat, contentDescription = "Wiederholt sich", modifier = Modifier.size(18.dp))
                Text(RecurrenceText.describe(event.recurrence), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DueTaskCard(task: TaskEntity, zone: ZoneId, onClick: () -> Unit) {
    KfCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.TaskAlt, contentDescription = "Aufgabe")
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                val due = task.dueAt
                Text(
                    if (task.dueHasTime && due != null) "Fällig um ${DateText.clock(due, zone)}" else "Fällig an diesem Tag",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
