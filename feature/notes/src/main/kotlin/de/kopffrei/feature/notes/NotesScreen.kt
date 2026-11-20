package de.kopffrei.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Unarchive
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.checklist.Checklist
import de.kopffrei.core.common.model.NoteType
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.NoteEntity
import de.kopffrei.core.designsystem.components.ConfirmDialog
import de.kopffrei.core.designsystem.components.EmptyState
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfChip
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfTextField
import de.kopffrei.core.designsystem.components.rememberVoiceInput
import de.kopffrei.core.designsystem.theme.KfItemColors

/** Notizen, Gedanken-Parkplatz, Archiv und Papierkorb mit Volltextsuche. */
@Composable
fun NotesScreen(
    onOpenNote: (noteId: String) -> Unit,
    onNewNote: (voice: Boolean, thought: Boolean) -> Unit,
    onThoughtToTask: (noteId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }

    KfScreen(
        title = "Notizen",
        modifier = modifier,
        actions = {
            if (tab == NotesTab.TRASH && items.isNotEmpty()) {
                IconButton(onClick = { confirmEmptyTrash = true }) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = "Papierkorb leeren")
                }
            }
            IconButton(onClick = { onNewNote(false, false) }) {
                Icon(Icons.Rounded.Add, contentDescription = "Neue Notiz")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KfTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = "Suchen",
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Suche löschen")
                        }
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    }
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NotesTab.entries.forEach { entry ->
                    KfChip(entry.label, selected = tab == entry, onClick = { viewModel.selectTab(entry) })
                }
            }
            if (tab == NotesTab.THOUGHTS) {
                ThoughtInput(onSave = viewModel::addThought)
            }
            if (tab == NotesTab.TRASH) {
                Text(
                    "Gelöschte Notizen bleiben hier, bis du sie endgültig löschst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (items.isEmpty()) {
                EmptyContent(tab, query.isNotBlank(), onNewNote)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            tab = tab,
                            onOpen = { if (tab != NotesTab.TRASH) onOpenNote(note.id) },
                            onPin = { viewModel.setPinned(note, !note.isPinned) },
                            onArchive = { viewModel.setArchived(note, true) },
                            onRestore = { viewModel.restore(note) },
                            onTrash = { viewModel.moveToTrash(note) },
                            onDeleteForever = { confirmDelete = note },
                            onThoughtToNote = { viewModel.thoughtToNote(note) },
                            onThoughtToTask = { onThoughtToTask(note.id) },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { note ->
        ConfirmDialog(
            title = "Endgültig löschen?",
            message = "Diese Notiz und ihre Anhänge werden unwiderruflich gelöscht.",
            confirmText = "Löschen",
            destructive = true,
            onConfirm = {
                viewModel.deleteForever(note)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
        )
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = "Papierkorb leeren?",
            message = "Alle Notizen im Papierkorb werden unwiderruflich gelöscht.",
            confirmText = "Leeren",
            destructive = true,
            onConfirm = {
                viewModel.emptyTrash(items)
                confirmEmptyTrash = false
            },
            onDismiss = { confirmEmptyTrash = false },
        )
    }
}

@Composable
private fun EmptyContent(tab: NotesTab, searching: Boolean, onNewNote: (Boolean, Boolean) -> Unit) {
    if (searching) {
        EmptyState(
            icon = Icons.Rounded.Search,
            title = "Nichts gefunden",
            message = "Versuche einen anderen Suchbegriff.",
        )
        return
    }
    when (tab) {
        NotesTab.NOTES -> EmptyState(
            icon = Icons.Rounded.StickyNote2,
            title = "Noch keine Notizen",
            message = "Schreib auf, was dir einfällt – ein Satz reicht.",
            actionText = "Notiz schreiben",
            onAction = { onNewNote(false, false) },
        )
        NotesTab.THOUGHTS -> EmptyState(
            icon = Icons.Rounded.Lightbulb,
            title = "Der Parkplatz ist leer",
            message = "Parke hier spontane Gedanken, damit sie dich nicht ablenken. Sortieren kannst du später.",
        )
        NotesTab.ARCHIVE -> EmptyState(
            icon = Icons.Rounded.Inbox,
            title = "Archiv ist leer",
            message = "Archivierte Notizen sind aus dem Weg, aber nicht weg.",
        )
        NotesTab.TRASH -> EmptyState(
            icon = Icons.Rounded.Delete,
            title = "Papierkorb ist leer",
            message = "Gelöschte Notizen kannst du hier wiederherstellen.",
        )
    }
}

@Composable
private fun ThoughtInput(onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val startVoice = rememberVoiceInput { spoken -> text = (text + " " + spoken).trim() }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KfTextField(
            value = text,
            onValueChange = { text = it },
            label = "Gedanken parken",
            modifier = Modifier.weight(1f),
            trailingIcon = {
                IconButton(onClick = startVoice) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Einsprechen")
                }
            },
        )
        KfButton(
            "Parken",
            onClick = {
                onSave(text)
                text = ""
            },
            enabled = text.isNotBlank(),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    tab: NotesTab,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onTrash: () -> Unit,
    onDeleteForever: () -> Unit,
    onThoughtToNote: () -> Unit,
    onThoughtToTask: () -> Unit,
) {
    val zone = remember { TimeUtils.systemZone() }
    var menu by remember { mutableStateOf(false) }
    KfCard(
        onClick = onOpen,
        accent = KfItemColors.colorOf(note.colorIndex),
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val title = note.title.ifBlank { TextUtils.truncate(TextUtils.firstLine(note.body), 60) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = "Angeheftet",
                            modifier = Modifier.padding(end = 6.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        title.ifBlank { "Ohne Titel" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val preview = if (note.isChecklist) {
                    Checklist.preview(note.body)
                } else if (note.title.isNotBlank()) {
                    TextUtils.truncate(note.body, 160)
                } else {
                    // Der Titel ersetzt die erste Zeile, sie soll nicht doppelt erscheinen.
                    TextUtils.truncate(note.body.lines().drop(1).joinToString(" ").trim(), 160)
                }
                if (preview.isNotBlank()) {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val date = DateText.relativeDay(
                    TimeUtils.toLocalDate(note.updatedAt, zone),
                    TimeUtils.toLocalDate(TimeUtils.nowMillis(), zone),
                )
                val meta = listOfNotNull(
                    note.category?.takeIf { it.isNotBlank() },
                    if (note.type == NoteType.THOUGHT) "Gedanke" else null,
                    date,
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Mehr für ${note.title.ifBlank { "Notiz" }}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val actions = buildList {
                        when (tab) {
                            NotesTab.NOTES, NotesTab.THOUGHTS -> {
                                if (note.type == NoteType.NOTE) {
                                    add(MenuAction(if (note.isPinned) "Lösen" else "Anheften", Icons.Rounded.PushPin, onPin))
                                } else {
                                    add(MenuAction("Als Notiz speichern", Icons.Rounded.StickyNote2, onThoughtToNote))
                                    add(MenuAction("Aufgabe daraus machen", Icons.Rounded.Add, onThoughtToTask))
                                }
                                add(MenuAction("Archivieren", Icons.Rounded.Archive, onArchive))
                                add(MenuAction("In den Papierkorb", Icons.Rounded.Delete, onTrash))
                            }
                            NotesTab.ARCHIVE -> {
                                add(MenuAction("Aus dem Archiv holen", Icons.Rounded.Unarchive, onRestore))
                                add(MenuAction("In den Papierkorb", Icons.Rounded.Delete, onTrash))
                            }
                            NotesTab.TRASH -> {
                                add(MenuAction("Wiederherstellen", Icons.Rounded.RestoreFromTrash, onRestore))
                                add(MenuAction("Endgültig löschen", Icons.Rounded.DeleteForever, onDeleteForever))
                            }
                        }
                    }
                    actions.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            leadingIcon = { Icon(action.icon, contentDescription = null) },
                            onClick = {
                                menu = false
                                action.onClick()
                            },
                        )
                    }
                }
            }
        }
    }
}

private class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)
