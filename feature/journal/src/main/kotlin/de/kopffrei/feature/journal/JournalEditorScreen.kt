package de.kopffrei.feature.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.designsystem.components.ConfirmDialog
import de.kopffrei.core.designsystem.components.KfChip
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfTextField
import de.kopffrei.core.designsystem.components.MoodSelector
import de.kopffrei.core.designsystem.components.SectionTitle
import de.kopffrei.core.designsystem.components.VoiceInputButton

@Composable
fun JournalEditorScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

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
        title = if (state.exists) "Eintrag" else "Neuer Eintrag",
        modifier = modifier,
        onBack = { viewModel.leave(onClose) },
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = { viewModel.leave(onClose) }) {
                Icon(Icons.Rounded.Check, contentDescription = "Fertig")
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
        val zone = remember { TimeUtils.systemZone() }
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
            item(key = "date") {
                Text(
                    "${DateText.dateLong(TimeUtils.toLocalDate(state.createdAt, zone))}, ${DateText.clock(state.createdAt, zone)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "mood") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Wie geht es dir? (freiwillig)")
                    MoodSelector(selected = state.mood, onSelect = viewModel::setMood)
                }
            }
            item(key = "title") {
                KfTextField(state.title, viewModel::setTitle, label = "Titel (optional)", singleLine = true)
            }
            item(key = "text") {
                Row(verticalAlignment = Alignment.Top) {
                    KfTextField(
                        state.text,
                        viewModel::setText,
                        label = "Was beschäftigt dich?",
                        minLines = 8,
                        modifier = Modifier.weight(1f),
                    )
                    VoiceInputButton(onResult = viewModel::appendText)
                }
            }
            item(key = "tags") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KfTextField(
                        state.tagsText,
                        viewModel::setTagsText,
                        label = "Tags (mit Komma trennen)",
                        singleLine = true,
                    )
                    val current = TextUtils.parseTags(state.tagsText)
                    val suggestions = state.knownTags.filter { known -> current.none { it.equals(known, ignoreCase = true) } }
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.take(12).forEach { tag ->
                                KfChip("#$tag", selected = false, onClick = { viewModel.addTag(tag) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Eintrag löschen?",
            message = "Der Tagebucheintrag wird endgültig gelöscht.",
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
