package de.kopffrei.feature.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.model.Priority
import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.designsystem.components.ConfirmDialog
import de.kopffrei.core.designsystem.components.DatePickerField
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.designsystem.components.KfChip
import de.kopffrei.core.designsystem.components.KfDropdownField
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfTextField
import de.kopffrei.core.designsystem.components.RecurrenceEditor
import de.kopffrei.core.designsystem.components.ReminderStagesEditor
import de.kopffrei.core.designsystem.components.SectionTitle
import de.kopffrei.core.designsystem.components.TimePickerField

@Composable
fun TaskEditorScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val text = state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    BackHandler { viewModel.leave(onClose) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushNow() }

    KfScreen(
        title = if (state.exists) "Aufgabe" else "Neue Aufgabe",
        modifier = modifier,
        onBack = { viewModel.leave(onClose) },
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (!state.exists) {
                IconButton(onClick = { viewModel.discard(onClose) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Verwerfen")
                }
            }
            IconButton(onClick = { viewModel.leave(onClose) }) {
                Icon(Icons.Rounded.Check, contentDescription = "Speichern")
            }
            if (state.exists) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.status != TaskStatus.DONE) {
                            DropdownMenuItem(
                                text = { Text("Als erledigt markieren") },
                                leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.complete(onClose)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (!state.loaded) return@KfScreen
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                KfTextField(state.title, viewModel::setTitle, label = "Was ist zu tun?", singleLine = false, minLines = 1, maxLines = 4)
            }
            item(key = "notes") {
                KfTextField(state.notes, viewModel::setNotes, label = "Notizen (optional)", minLines = 3)
            }
            item(key = "priority") {
                KfDropdownField(
                    label = "Priorität",
                    selected = state.priority,
                    options = listOf(Priority.LOW, Priority.NORMAL, Priority.HIGH, Priority.URGENT),
                    optionLabel = { Priority.label(it) },
                    onSelect = viewModel::setPriority,
                )
            }
            item(key = "due") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Fällig")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KfChip("Heute", selected = false, onClick = { viewModel.setDueInDays(0) })
                        KfChip("Morgen", selected = false, onClick = { viewModel.setDueInDays(1) })
                        KfChip("In einer Woche", selected = false, onClick = { viewModel.setDueInDays(7) })
                    }
                    DatePickerField(label = "Datum", dateMillis = state.dueDate, onChange = viewModel::setDueDate)
                    if (state.dueDate != null) {
                        TimePickerField(
                            label = "Uhrzeit",
                            minutesOfDay = state.dueMinutes,
                            onChange = viewModel::setDueMinutes,
                            clearable = true,
                        )
                    }
                }
            }
            item(key = "estimate") {
                KfTextField(
                    state.estimateText,
                    viewModel::setEstimate,
                    label = "Geschätzte Dauer in Minuten (optional)",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            item(key = "recurrence") {
                RecurrenceEditor(rule = state.recurrence, onChange = viewModel::setRecurrence)
            }
            item(key = "steps-title") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("Schritte")
                    Text(
                        "Teile größere Aufgaben selbst in kleine Schritte. " +
                            "(Automatisches Zerlegen mit KI ist noch nicht enthalten – das folgt in Phase 2.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.steps.size, key = { state.steps[it].id }) { index ->
                val step = state.steps[index]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = step.done, onCheckedChange = { viewModel.setStepDone(step.id, it) })
                    KfTextField(
                        value = step.title,
                        onValueChange = { viewModel.setStepTitle(step.id, it) },
                        label = "Schritt ${index + 1}",
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Column {
                        IconButton(onClick = { viewModel.moveStep(step.id, -1) }, enabled = index > 0) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Schritt ${index + 1} nach oben")
                        }
                        IconButton(onClick = { viewModel.moveStep(step.id, +1) }, enabled = index < state.steps.lastIndex) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Schritt ${index + 1} nach unten")
                        }
                    }
                    IconButton(onClick = { viewModel.removeStep(step.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Schritt ${index + 1} entfernen")
                    }
                }
            }
            item(key = "add-step") {
                KfButton("Schritt hinzufügen", viewModel::addStep, icon = Icons.Rounded.Add, style = KfButtonStyle.Tonal)
            }
            item(key = "reminders") {
                ReminderStagesEditor(
                    stages = state.reminders,
                    onChange = viewModel::setReminders,
                    canUseOffsets = state.dueDate != null,
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Aufgabe löschen?",
            message = "Die Aufgabe, ihre Schritte und Erinnerungen werden endgültig gelöscht.",
            confirmText = "Löschen",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onClose)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
