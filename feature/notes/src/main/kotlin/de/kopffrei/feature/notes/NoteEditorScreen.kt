package de.kopffrei.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.checklist.ChecklistItem
import de.kopffrei.core.common.model.NoteType
import de.kopffrei.core.designsystem.components.AttachmentUi
import de.kopffrei.core.designsystem.components.AttachmentsSection
import de.kopffrei.core.designsystem.components.ColorPickerRow
import de.kopffrei.core.designsystem.components.FixedReminderEditor
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfTextField
import de.kopffrei.core.designsystem.components.SectionTitle
import de.kopffrei.core.designsystem.components.openAttachmentExternally
import de.kopffrei.core.designsystem.components.rememberAttachmentPickers
import de.kopffrei.core.designsystem.components.rememberVoiceInput
import kotlinx.coroutines.launch

/**
 * Notiz-Editor. Speichert automatisch; „Zurück“ speichert vorher. Umwandeln in Aufgabe oder Termin
 * übergibt die Notiz an den jeweiligen Editor, Tagebuch und Erinnerung passieren direkt hier.
 */
@Composable
fun NoteEditorScreen(
    onClose: () -> Unit,
    onConvertToTask: (noteId: String) -> Unit,
    onConvertToEvent: (noteId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var focusIndex by remember { mutableIntStateOf(-1) }
    var voiceStarted by rememberSaveable { mutableStateOf(false) }

    BackHandler { viewModel.flush(onClose) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushNow() }

    val startVoice = rememberVoiceInput { spoken -> viewModel.appendSpoken(spoken) }
    LaunchedEffect(state.loaded) {
        if (state.loaded && viewModel.startWithVoice && !voiceStarted) {
            voiceStarted = true
            startVoice()
        }
    }
    val pickers = rememberAttachmentPickers { uri -> viewModel.addAttachment(uri) }
    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    KfScreen(
        title = if (state.type == NoteType.THOUGHT) "Gedanke" else "Notiz",
        modifier = modifier,
        onBack = { viewModel.flush(onClose) },
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = { viewModel.setPinned(!state.isPinned) }) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (state.isPinned) "Nicht mehr anheften" else "Anheften",
                    tint = if (state.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("In Aufgabe umwandeln") },
                        onClick = {
                            menuOpen = false
                            viewModel.flush { onConvertToTask(state.id) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("In Termin umwandeln") },
                        onClick = {
                            menuOpen = false
                            viewModel.flush { onConvertToEvent(state.id) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Erinnerung festlegen") },
                        onClick = {
                            menuOpen = false
                            scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Als Tagebucheintrag speichern") },
                        onClick = {
                            menuOpen = false
                            viewModel.saveAsJournalEntry()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Archivieren") },
                        leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            viewModel.archive(onClose)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("In den Papierkorb") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            viewModel.moveToTrash(onClose)
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (!state.loaded) return@KfScreen
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                KfTextField(state.title, viewModel::setTitle, label = "Titel", singleLine = true)
            }
            if (state.isChecklist) {
                itemsIndexed(state.items) { index, item ->
                    ChecklistRow(
                        index = index,
                        item = item,
                        requestFocus = focusIndex == index,
                        onFocusHandled = { focusIndex = -1 },
                        onText = { viewModel.setItemText(index, it) },
                        onChecked = { viewModel.setItemChecked(index, it) },
                        onNext = { focusIndex = viewModel.addItemAfter(index) },
                        onDelete = { viewModel.removeItem(index) },
                    )
                }
                item(key = "add-item") {
                    KfButton(
                        "Punkt hinzufügen",
                        onClick = { focusIndex = viewModel.addItemAfter(state.items.lastIndex) },
                        icon = Icons.Rounded.Add,
                        style = KfButtonStyle.Tonal,
                    )
                }
            } else {
                item(key = "body") {
                    KfTextField(state.body, viewModel::setBody, label = "Notiz", minLines = 6)
                }
            }
            item(key = "tools") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KfButton("Einsprechen", startVoice, icon = Icons.Rounded.Mic, style = KfButtonStyle.Tonal)
                    KfButton(
                        if (state.isChecklist) "In Text umwandeln" else "Als Checkliste",
                        viewModel::toggleChecklist,
                        icon = Icons.Rounded.Checklist,
                        style = KfButtonStyle.Tonal,
                    )
                }
            }
            item(key = "color") {
                Column {
                    SectionTitle("Farbe")
                    ColorPickerRow(selected = state.colorIndex, onSelect = viewModel::setColor)
                }
            }
            item(key = "category") {
                KfTextField(state.category, viewModel::setCategory, label = "Kategorie (optional)", singleLine = true)
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
            item(key = "reminder") {
                FixedReminderEditor(draft = state.reminder, onChange = viewModel::setReminder)
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    index: Int,
    item: ChecklistItem,
    requestFocus: Boolean,
    onFocusHandled: () -> Unit,
    onText: (String) -> Unit,
    onChecked: (Boolean) -> Unit,
    onNext: () -> Unit,
    onDelete: () -> Unit,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            runCatching { requester.requestFocus() }
            onFocusHandled()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = onChecked)
        KfTextField(
            value = item.text,
            onValueChange = onText,
            label = "Punkt ${index + 1}",
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .focusRequester(requester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = "Punkt ${index + 1} entfernen")
        }
    }
}
