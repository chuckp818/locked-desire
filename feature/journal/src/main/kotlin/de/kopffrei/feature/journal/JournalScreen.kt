package de.kopffrei.feature.journal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.designsystem.components.EmptyState
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfChip
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfTextField
import java.time.LocalDate
import java.time.ZoneId

/** Tagebuch: Liste mit Suche und Filtern. Alles hier ist freiwillig – es gibt keine Serien oder Pflichttage. */
@Composable
fun JournalScreen(
    onOpenEntry: (entryId: String) -> Unit,
    onNewEntry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = remember { TimeUtils.systemZone() }
    val today = remember { LocalDate.now(zone) }
    val snackbar = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }

    LaunchedEffect(state.message) {
        val text = state.message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    KfScreen(
        title = "Tagebuch",
        modifier = modifier,
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (state.hasAnyEntries) {
                IconButton(onClick = { exportLauncher.launch("kopffrei-tagebuch.txt") }) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = "Tagebuch als Textdatei exportieren")
                }
            }
        },
    ) { padding ->
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
            item(key = "new") {
                KfButton("Neuer Eintrag", onNewEntry, icon = Icons.Rounded.Add)
            }
            if (state.hasAnyEntries) {
                item(key = "search") {
                    KfTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = "Suchen",
                        singleLine = true,
                    )
                }
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Mood.entries.forEach { mood ->
                                KfChip(
                                    label = "${mood.emoji} ${mood.label}",
                                    selected = state.moodFilter == mood,
                                    onClick = { viewModel.setMoodFilter(if (state.moodFilter == mood) null else mood) },
                                )
                            }
                        }
                        if (state.allTags.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.allTags.forEach { tag ->
                                    KfChip(
                                        label = "#$tag",
                                        selected = state.tagFilter.equals(tag, ignoreCase = true),
                                        onClick = {
                                            viewModel.setTagFilter(
                                                if (state.tagFilter.equals(tag, ignoreCase = true)) null else tag,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (state.items.isEmpty()) {
                item(key = "empty") {
                    if (!state.hasAnyEntries) {
                        EmptyState(
                            icon = Icons.Rounded.AutoStories,
                            title = "Noch nichts im Tagebuch",
                            message = "Schreib auf, was dich beschäftigt – kurz oder lang, mit Stimme oder Tastatur. " +
                                "Alles ist freiwillig und bleibt auf deinem Gerät.",
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Rounded.AutoStories,
                            title = "Nichts gefunden",
                            message = "Zu Suche und Filtern gibt es keinen Eintrag.",
                        )
                    }
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    JournalCard(item, today, zone, onClick = { onOpenEntry(item.id) })
                }
            }
        }
    }
}

@Composable
private fun JournalCard(item: JournalListItem, today: LocalDate, zone: ZoneId, onClick: () -> Unit) {
    KfCard(onClick = onClick) {
        Text(
            DateText.relativeDayTime(item.createdAt, true, today, zone),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            (item.mood?.let { "${it.emoji} " }.orEmpty()) + item.title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (item.preview.isNotBlank()) {
            Text(
                item.preview,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (item.tags.isNotEmpty()) {
            Text(
                item.tags.joinToString("  ") { "#$it" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
