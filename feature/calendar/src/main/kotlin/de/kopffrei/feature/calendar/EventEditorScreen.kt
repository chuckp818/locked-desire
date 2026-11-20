package de.kopffrei.feature.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.designsystem.components.AttachmentUi
import de.kopffrei.core.designsystem.components.AttachmentsSection
import de.kopffrei.core.designsystem.components.ColorPickerRow
import de.kopffrei.core.designsystem.components.ConfirmDialog
import de.kopffrei.core.designsystem.components.DatePickerField
import de.kopffrei.core.designsystem.components.KfDropdownField
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfSwitchRow
import de.kopffrei.core.designsystem.components.KfTextField
import de.kopffrei.core.designsystem.components.RecurrenceEditor
import de.kopffrei.core.designsystem.components.ReminderStagesEditor
import de.kopffrei.core.designsystem.components.SectionTitle
import de.kopffrei.core.designsystem.components.TimePickerField
import de.kopffrei.core.designsystem.components.openAttachmentExternally
import de.kopffrei.core.designsystem.components.rememberAttachmentPickers

@Composable
fun EventEditorScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val pickers = rememberAttachmentPickers { uri -> viewModel.addAttachment(uri) }

    BackHandler { viewModel.leave(onClose) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushNow() }
    LaunchedEffect(state.message) {
        val text = state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    KfScreen(
        title = if (state.exists) "Termin" else "Neuer Termin",
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
                KfTextField(state.title, viewModel::setTitle, label = "Titel", singleLine = false, minLines = 1, maxLines = 3)
            }
            item(key = "time") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KfSwitchRow("Ganztägig", state.allDay, viewModel::setAllDay)
                    DatePickerField(
                        label = "Beginn – Datum",
                        dateMillis = state.startDate,
                        onChange = { viewModel.setStart(it, null) },
                        clearable = false,
                    )
                    if (!state.allDay) {
                        TimePickerField(
                            label = "Beginn – Uhrzeit",
                            minutesOfDay = state.startMinutes,
                            onChange = { viewModel.setStart(null, it) },
                        )
                    }
                    DatePickerField(
                        label = if (state.allDay) "Letzter Tag" else "Ende – Datum",
                        dateMillis = state.endDate,
                        onChange = { viewModel.setEnd(it, null) },
                        clearable = false,
                    )
                    if (!state.allDay) {
                        TimePickerField(
                            label = "Ende – Uhrzeit",
                            minutesOfDay = state.endMinutes,
                            onChange = { viewModel.setEnd(null, it) },
                        )
                    }
                    if (state.endBeforeStart) {
                        Text(
                            "Das Ende liegt vor dem Beginn – beim Speichern wird es auf den Beginn gesetzt.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item(key = "location") {
                KfTextField(state.location, viewModel::setLocation, label = "Ort (optional)", singleLine = true)
            }
            item(key = "prep") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Vorbereitung und Fahrzeit")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KfTextField(
                            state.prepText,
                            viewModel::setPrep,
                            label = "Vorbereitung (Min.)",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        KfTextField(
                            state.travelText,
                            viewModel::setTravel,
                            label = "Fahrzeit (Min.)",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item(key = "recurrence") {
                RecurrenceEditor(rule = state.recurrence, onChange = viewModel::setRecurrence)
            }
            item(key = "reminders") {
                ReminderStagesEditor(
                    stages = state.reminders,
                    onChange = viewModel::setReminders,
                    canUseOffsets = true,
                    onGenerateFromTimes = viewModel::generateStagesFromTimes,
                )
            }
            item(key = "color") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Farbe")
                    ColorPickerRow(selected = state.colorIndex, onSelect = viewModel::setColor)
                }
            }
            item(key = "category") {
                KfTextField(state.category, viewModel::setCategory, label = "Kategorie (optional)", singleLine = true)
            }
            item(key = "task") {
                KfDropdownField<String?>(
                    label = "Verknüpfte Aufgabe",
                    selected = state.linkedTaskId,
                    options = listOf<String?>(null) + state.selectableTasks.map { it.id },
                    optionLabel = { id ->
                        if (id == null) {
                            "Keine Verknüpfung"
                        } else {
                            state.selectableTasks.firstOrNull { it.id == id }?.title ?: "Aufgabe nicht mehr vorhanden"
                        }
                    },
                    onSelect = viewModel::setLinkedTask,
                )
            }
            item(key = "notes") {
                KfTextField(state.notes, viewModel::setNotes, label = "Notizen (optional)", minLines = 3)
            }
            item(key = "attachments") {
                val attachmentUis = remember(state.attachments) {
                    state.attachments.map {
                        AttachmentUi(it.id, it.displayName, it.mimeType.startsWith("image/"), viewModel.fileOf(it))
                    }
                }
                AttachmentsSection(
                    items = attachmentUis,
                    onAddImage = pickers.pickImage,
                    onAddFile = pickers.pickFile,
                    onOpen = { id ->
                        viewModel.attachmentOpenInfo(id)?.let { (uri, mime) ->
                            openAttachmentExternally(context, uri, mime)
                        }
                    },
                    onRemove = viewModel::removeAttachment,
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Termin löschen?",
            message = if (state.recurrence.isRecurring) {
                "Der Termin und alle seine Wiederholungen werden endgültig gelöscht."
            } else {
                "Der Termin, seine Anhänge und Erinnerungen werden endgültig gelöscht."
            },
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
