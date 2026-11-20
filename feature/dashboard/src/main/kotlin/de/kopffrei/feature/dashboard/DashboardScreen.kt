package de.kopffrei.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.focus.FocusSession
import de.kopffrei.core.common.model.DashboardCard
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.model.MoreEntry
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.tasks.TaskLogic
import de.kopffrei.core.common.tasks.TaskView
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.Greeting
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.repository.EventOccurrence
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfTextField
import de.kopffrei.core.designsystem.components.MoodSelector
import de.kopffrei.core.designsystem.components.SectionTitle
import de.kopffrei.core.designsystem.components.minTouchSize
import de.kopffrei.core.designsystem.components.rememberVoiceInput
import de.kopffrei.core.designsystem.theme.KfTheme
import de.kopffrei.core.reminders.ActiveReminder

/** Startseite „Mein Kopf heute“. Alle Navigationsziele kommen als Rückruf von außen. */
@Composable
fun DashboardScreen(
    onNewNote: (voice: Boolean) -> Unit,
    onNewTask: () -> Unit,
    onNewEvent: () -> Unit,
    onBrainDump: () -> Unit,
    onOverwhelmed: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenTasks: () -> Unit,
    onOpenDeepLink: (String) -> Unit,
    onStartFocus: (minutes: Int) -> Unit,
    onOpenMore: (MoreEntry) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var customizing by remember { mutableStateOf(false) }

    KfScreen(
        title = "Mein Kopf heute",
        modifier = modifier,
        actions = {
            IconButton(onClick = { customizing = true }) {
                Icon(Icons.Rounded.Tune, contentDescription = "Startseite anpassen")
            }
        },
    ) { padding ->
        if (!state.loaded) return@KfScreen
        val settings = state.settings
        val compact = settings.dashboardCompact
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // Platz für den schwebenden Schnellaktions-Knopf.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            item(key = "header") { Header(state, compact, settings.showMotivation) }
            for (card in settings.visibleDashboardCards()) {
                item(key = card.name) {
                    when (card) {
                        DashboardCard.QUICK_ACTIONS -> QuickActionsCard(
                            compact = compact,
                            onNote = { onNewNote(false) },
                            onVoice = { onNewNote(true) },
                            onEvent = onNewEvent,
                            onTask = onNewTask,
                            onBrainDump = onBrainDump,
                            onOverwhelmed = onOverwhelmed,
                        )
                        DashboardCard.MOOD -> MoodCard(compact, state.mood, viewModel::setMood)
                        DashboardCard.NEXT_EVENT -> NextEventCard(
                            compact, state.nextEvent, state.nowMillis, onOpenEvent, onNewEvent,
                        )
                        DashboardCard.TOP_TASKS -> TopTasksCard(
                            compact, state.topTasks, state.nowMillis,
                            onOpenTask, viewModel::completeTask, onNewTask, onOpenTasks,
                        )
                        DashboardCard.REMINDERS -> RemindersCard(
                            compact, state.reminders, onOpenDeepLink,
                            viewModel::acknowledge, viewModel::snooze, viewModel::completeFromReminder,
                        )
                        DashboardCard.QUICK_THOUGHT -> QuickThoughtCard(compact, viewModel::saveThought)
                        DashboardCard.FOCUS -> FocusCard(compact, onStartFocus)
                        DashboardCard.SHORTCUTS -> ShortcutsCard(compact, settings.pinnedShortcuts, onOpenMore)
                    }
                }
            }
        }
    }

    if (customizing) {
        CustomizeSheet(
            settings = state.settings,
            onUpdate = viewModel::updateSettings,
            onDismiss = { customizing = false },
        )
    }
}

private fun cardPadding(compact: Boolean) = if (compact) 12.dp else 16.dp

@Composable
private fun Header(state: DashboardUiState, compact: Boolean, showMotivation: Boolean) {
    val zone = remember { TimeUtils.systemZone() }
    val dateTime = TimeUtils.toLocalDateTime(state.nowMillis, zone)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(Greeting.headline(dateTime.hour), style = MaterialTheme.typography.headlineSmall)
        Text(
            DateText.dateLong(dateTime.toLocalDate()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showMotivation && !compact) {
            Text(
                Greeting.motivation(dateTime.toLocalDate().toEpochDay()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun QuickActionsCard(
    compact: Boolean,
    onNote: () -> Unit,
    onVoice: () -> Unit,
    onEvent: () -> Unit,
    onTask: () -> Unit,
    onBrainDump: () -> Unit,
    onOverwhelmed: () -> Unit,
) {
    KfCard(contentPadding = cardPadding(compact)) {
        SectionTitle("Schnellaktionen")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KfButton("Notiz", onNote, icon = Icons.Rounded.EditNote, style = KfButtonStyle.Tonal)
            KfButton("Einsprechen", onVoice, icon = Icons.Rounded.Mic, style = KfButtonStyle.Tonal)
            KfButton("Termin", onEvent, icon = Icons.Rounded.Event, style = KfButtonStyle.Tonal)
            KfButton("Aufgabe", onTask, icon = Icons.Rounded.TaskAlt, style = KfButtonStyle.Tonal)
            KfButton("Kopf ausschütten", onBrainDump, icon = Icons.Rounded.Psychology, style = KfButtonStyle.Tonal)
        }
        KfButton(
            "Mir ist alles zu viel",
            onOverwhelmed,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Spa,
            style = KfButtonStyle.Outlined,
        )
    }
}

@Composable
private fun MoodCard(compact: Boolean, mood: Mood?, onSelect: (Mood?) -> Unit) {
    KfCard(contentPadding = cardPadding(compact)) {
        SectionTitle("Wie geht es dir gerade?")
        MoodSelector(selected = mood, onSelect = onSelect)
        if (!compact) {
            Text(
                "Freiwillig – du musst nichts auswählen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextEventCard(
    compact: Boolean,
    occurrence: EventOccurrence?,
    nowMillis: Long,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
) {
    val zone = remember { TimeUtils.systemZone() }
    KfCard(
        onClick = occurrence?.let { { onOpen(it.event.id) } },
        contentPadding = cardPadding(compact),
    ) {
        SectionTitle("Nächster Termin")
        if (occurrence == null) {
            Text("Kein Termin in Sicht.", style = MaterialTheme.typography.bodyLarge)
            KfButton("Termin anlegen", onNew, style = KfButtonStyle.Text)
        } else {
            val today = TimeUtils.toLocalDate(nowMillis, zone)
            val day = DateText.relativeDay(TimeUtils.toLocalDate(occurrence.startMillis, zone), today)
            val event = occurrence.event
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "$day · " + DateText.eventRange(occurrence.startMillis, occurrence.endMillis, event.allDay, zone),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (event.location.isNotBlank()) {
                Text(
                    event.location,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TopTasksCard(
    compact: Boolean,
    tasks: List<TaskView>,
    nowMillis: Long,
    onOpen: (String) -> Unit,
    onComplete: (String) -> Unit,
    onNew: () -> Unit,
    onAll: () -> Unit,
) {
    KfCard(contentPadding = cardPadding(compact)) {
        SectionTitle("Drei wichtigste Aufgaben")
        if (tasks.isEmpty()) {
            Text("Keine offenen Aufgaben – gönn dir eine Pause.", style = MaterialTheme.typography.bodyLarge)
            KfButton("Aufgabe anlegen", onNew, style = KfButtonStyle.Text)
        } else {
            tasks.forEach { task -> TaskRow(task, nowMillis, onOpen, onComplete) }
            KfButton("Alle Aufgaben", onAll, style = KfButtonStyle.Text)
        }
    }
}

@Composable
private fun TaskRow(task: TaskView, nowMillis: Long, onOpen: (String) -> Unit, onComplete: (String) -> Unit) {
    val zone = remember { TimeUtils.systemZone() }
    val overdue = TaskLogic.isOverdue(task, nowMillis, zone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minTouchSize())
            .clickable { onOpen(task.id) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onComplete(task.id) }) {
            Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = "Erledigt: ${task.title}")
        }
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val due = task.dueAt
            if (due != null) {
                val today = TimeUtils.toLocalDate(nowMillis, zone)
                Text(
                    "Fällig: " + DateText.relativeDayTime(due, task.dueHasTime, today, zone),
                    style = MaterialTheme.typography.bodySmall,
                    // Überfällig wird nur sanft hervorgehoben (kein Rot, keine Vorwürfe).
                    color = if (overdue) KfTheme.extra.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RemindersCard(
    compact: Boolean,
    reminders: List<ActiveReminder>,
    onOpen: (String) -> Unit,
    onAck: (String) -> Unit,
    onSnooze: (String) -> Unit,
    onComplete: (String) -> Unit,
) {
    val zone = remember { TimeUtils.systemZone() }
    KfCard(contentPadding = cardPadding(compact)) {
        SectionTitle("Aktive Erinnerungen")
        if (reminders.isEmpty()) {
            Text("Keine offene Erinnerung.", style = MaterialTheme.typography.bodyLarge)
        }
        reminders.forEach { reminder ->
            KfCard(
                onClick = { onOpen(reminder.deepLink) },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentPadding = 12.dp,
            ) {
                Text(reminder.title, style = MaterialTheme.typography.titleSmall)
                if (reminder.label.isNotBlank()) Text(reminder.label, style = MaterialTheme.typography.bodyMedium)
                val snoozed = reminder.snoozedUntil
                if (snoozed != null) {
                    Text(
                        "Erinnert erneut um ${DateText.clock(snoozed, zone)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (reminder.ownerType == OwnerType.TASK) {
                        KfButton("Erledigt", { onComplete(reminder.id) }, icon = Icons.Rounded.Check)
                    }
                    KfButton("Gesehen", { onAck(reminder.id) }, style = KfButtonStyle.Tonal)
                    KfButton("Später", { onSnooze(reminder.id) }, style = KfButtonStyle.Outlined)
                }
            }
        }
    }
}

@Composable
private fun QuickThoughtCard(compact: Boolean, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val startVoice = rememberVoiceInput { spoken -> text = (text + " " + spoken).trim() }
    KfCard(contentPadding = cardPadding(compact)) {
        SectionTitle("Gedanke festhalten")
        KfTextField(
            value = text,
            onValueChange = { text = it },
            label = "Was geht dir durch den Kopf?",
            minLines = if (compact) 1 else 2,
            trailingIcon = {
                IconButton(onClick = startVoice) { Icon(Icons.Rounded.Mic, contentDescription = "Einsprechen") }
            },
        )
        KfButton(
            "Im Gedanken-Parkplatz ablegen",
            onClick = {
                onSave(text)
                text = ""
            },
            enabled = text.isNotBlank(),
            icon = Icons.Rounded.Check,
        )
    }
}

@Composable
private fun FocusCard(compact: Boolean, onStart: (Int) -> Unit) {
    KfCard(contentPadding = cardPadding(compact)) {
        SectionTitle("Fokus-Timer")
        if (!compact) {
            Text("Wie lange magst du dranbleiben?", style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusSession.PRESET_MINUTES.forEach { minutes ->
                KfButton("$minutes Min.", { onStart(minutes) }, style = KfButtonStyle.Tonal)
            }
        }
    }
}

@Composable
private fun ShortcutsCard(compact: Boolean, pinned: Set<String>, onOpen: (MoreEntry) -> Unit) {
    val entries = MoreEntry.entries.filter { it.name in pinned && it.isAvailable }
    KfCard(contentPadding = cardPadding(compact)) {
        SectionTitle("Meine Schnellzugriffe")
        if (entries.isEmpty()) {
            Text(
                "Noch nichts angeheftet. Unter „Mehr“ kannst du Einträge hier anheften.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entries.forEach { entry ->
                    KfButton(entry.label, { onOpen(entry) }, style = KfButtonStyle.Outlined)
                }
            }
        }
    }
}
