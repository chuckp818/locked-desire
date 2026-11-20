package de.kopffrei.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import de.kopffrei.core.common.model.Priority
import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.tasks.TaskFilter
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.designsystem.components.ConfirmDialog
import de.kopffrei.core.designsystem.components.EmptyState
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfChip
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfTextField
import de.kopffrei.core.designsystem.theme.KfTheme

/** Aufgabenübersicht mit Filtern, Suche und Schnellerfassung. */
@Composable
fun TasksScreen(
    onOpenTask: (taskId: String) -> Unit,
    onNewTask: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<TaskEntity?>(null) }
    var quickTitle by rememberSaveable { mutableStateOf("") }

    KfScreen(
        title = "Aufgaben",
        modifier = modifier,
        onBack = onBack,
        actions = {
            IconButton(onClick = onNewTask) { Icon(Icons.Rounded.Add, contentDescription = "Neue Aufgabe") }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KfTextField(
                    value = quickTitle,
                    onValueChange = { quickTitle = it },
                    label = "Schnell notieren: Was ist zu tun?",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                KfButton(
                    "Hinzu",
                    onClick = {
                        viewModel.quickAdd(quickTitle)
                        quickTitle = ""
                    },
                    enabled = quickTitle.isNotBlank(),
                )
            }
            KfTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = "Aufgaben durchsuchen",
                singleLine = true,
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Suche löschen")
                        }
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskFilter.entries.forEach { entry ->
                    KfChip(entry.label, selected = state.filter == entry, onClick = { viewModel.setFilter(entry) })
                }
            }

            if (state.items.isEmpty()) {
                EmptyState(
                    icon = Icons.Rounded.TaskAlt,
                    title = if (state.query.isNotBlank()) "Nichts gefunden" else "Hier ist nichts",
                    message = if (state.filter == TaskFilter.OPEN && state.query.isBlank()) {
                        "Keine offenen Aufgaben. Das darf auch mal so sein."
                    } else {
                        "Für diesen Filter gibt es gerade keine Aufgaben."
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items, key = { it.task.id }) { item ->
                        TaskCard(
                            item = item,
                            nowMillis = state.nowMillis,
                            onOpen = { onOpenTask(item.task.id) },
                            onComplete = { viewModel.complete(item.task) },
                            onReopen = { viewModel.reopen(item.task) },
                            onPostpone = { viewModel.postponeToTomorrow(item.task) },
                            onTogglePause = { viewModel.setPaused(item.task, item.task.status != TaskStatus.PAUSED) },
                            onDelete = { confirmDelete = item.task },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { task ->
        ConfirmDialog(
            title = "Aufgabe löschen?",
            message = "„${task.title}“ wird endgültig gelöscht.",
            confirmText = "Löschen",
            destructive = true,
            onConfirm = {
                viewModel.delete(task)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun TaskCard(
    item: TaskItem,
    nowMillis: Long,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onPostpone: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
) {
    val task = item.task
    val zone = remember { TimeUtils.systemZone() }
    var menu by remember { mutableStateOf(false) }
    val done = task.status == TaskStatus.DONE
    KfCard(onClick = onOpen, contentPadding = 8.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (done) onReopen else onComplete, modifier = Modifier.padding(end = 4.dp)) {
                if (done) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Wieder öffnen: ${task.title}",
                        tint = KfTheme.extra.success,
                    )
                } else {
                    Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = "Erledigt: ${task.title}")
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val parts = buildList {
                    task.dueAt?.let { due ->
                        val today = TimeUtils.toLocalDate(nowMillis, zone)
                        add("Fällig: " + DateText.relativeDayTime(due, task.dueHasTime, today, zone))
                    }
                    if (task.priority >= Priority.HIGH) add(Priority.label(task.priority))
                    task.estimatedMinutes?.let { add("ca. " + TextUtils.formatMinutes(it)) }
                    if (item.stepsTotal > 0) add("${item.stepsDone}/${item.stepsTotal} Schritte")
                    if (task.recurrence.isRecurring) add("wiederkehrend")
                    if (task.status == TaskStatus.PAUSED) add("pausiert")
                }
                if (parts.isNotEmpty()) {
                    Text(
                        parts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.overdue) KfTheme.extra.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr für ${task.title}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!done) {
                        DropdownMenuItem(
                            text = { Text("Auf morgen verschieben") },
                            onClick = {
                                menu = false
                                onPostpone()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (task.status == TaskStatus.PAUSED) "Fortsetzen" else "Pausieren") },
                            onClick = {
                                menu = false
                                onTogglePause()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Erledigt") },
                            leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                            onClick = {
                                menu = false
                                onComplete()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Wieder öffnen") },
                            onClick = {
                                menu = false
                                onReopen()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Löschen") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}
