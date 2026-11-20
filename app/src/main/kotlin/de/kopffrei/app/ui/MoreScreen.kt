package de.kopffrei.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.model.MoreEntry
import de.kopffrei.core.database.prefs.SettingsRepository
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.SectionTitle
import de.kopffrei.core.designsystem.components.minTouchSize
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val pinned: StateFlow<Set<String>> = settingsRepository.settings
        .map { it.pinnedShortcuts }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Heftet einen Eintrag an die Startseite („Meine Schnellzugriffe“) oder löst ihn wieder. */
    fun togglePin(entry: MoreEntry) {
        if (!entry.isAvailable) return
        viewModelScope.launch {
            settingsRepository.update { settings ->
                val next = if (entry.name in settings.pinnedShortcuts) {
                    settings.pinnedShortcuts - entry.name
                } else {
                    settings.pinnedShortcuts + entry.name
                }
                settings.copy(pinnedShortcuts = next)
            }
        }
    }
}

/**
 * „Mehr“: Tagebuch, Aufgabenübersicht und Einstellungen sind nutzbar. Routinen, KI, Statistiken,
 * Community und Partner gehören zu späteren Ausbaustufen und sind hier deutlich als „noch nicht
 * verfügbar“ gekennzeichnet.
 */
@Composable
fun MoreScreen(
    onOpen: (MoreEntry) -> Unit,
    onBrainDump: () -> Unit,
    onOverwhelmed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember { versionName(context) }

    KfScreen(title = "Mehr", modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // Platz für den schwebenden Schnellaktions-Knopf.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Wenn es viel ist") }
            item {
                ActionCard(
                    icon = Icons.Rounded.EditNote,
                    title = "Kopf ausschütten",
                    description = "Alles ungeordnet aufschreiben oder einsprechen und danach sortieren.",
                    onClick = onBrainDump,
                )
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.Spa,
                    title = "Ich bin überfordert",
                    description = "Ein ruhiger Ort mit kleinen nächsten Schritten.",
                    onClick = onOverwhelmed,
                )
            }

            item { SectionTitle("Bereiche") }
            val available = MoreEntry.entries.filter { it.isAvailable }
            val later = MoreEntry.entries.filter { !it.isAvailable }
            items(available.size) { index ->
                val entry = available[index]
                AvailableEntryCard(
                    entry = entry,
                    pinned = entry.name in pinned,
                    onOpen = { onOpen(entry) },
                    onTogglePin = { viewModel.togglePin(entry) },
                )
            }

            item { SectionTitle("Noch nicht verfügbar") }
            items(later.size) { index -> LaterEntryCard(later[index]) }

            item {
                Text(
                    "KopfFrei $version · Ausbaustufe 1 · Alle Daten bleiben auf deinem Gerät.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    KfCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AvailableEntryCard(entry: MoreEntry, pinned: Boolean, onOpen: () -> Unit, onTogglePin: () -> Unit) {
    KfCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(iconFor(entry), contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.label, style = MaterialTheme.typography.titleMedium)
                Text(descriptionFor(entry), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(minTouchSize())) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = if (pinned) "Von der Startseite lösen" else "An die Startseite anheften",
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LaterEntryCard(entry: MoreEntry) {
    // Bewusst nicht anklickbar: Die Funktion gibt es in dieser Version nicht.
    KfCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                iconFor(entry),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${descriptionFor(entry)} Noch nicht verfügbar – geplant für Ausbaustufe ${entry.phase}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun iconFor(entry: MoreEntry): ImageVector = when (entry) {
    MoreEntry.JOURNAL -> Icons.Rounded.AutoStories
    MoreEntry.ROUTINES -> Icons.Rounded.Repeat
    MoreEntry.AI -> Icons.Rounded.Psychology
    MoreEntry.COMMUNITY -> Icons.Rounded.Groups
    MoreEntry.TASKS -> Icons.Rounded.TaskAlt
    MoreEntry.STATS -> Icons.Rounded.BarChart
    MoreEntry.SETTINGS -> Icons.Rounded.Settings
    MoreEntry.PARTNER -> Icons.Rounded.People
}

private fun descriptionFor(entry: MoreEntry): String = when (entry) {
    MoreEntry.JOURNAL -> "Gedanken und Stimmung festhalten."
    MoreEntry.TASKS -> "Alle Aufgaben, Schritte und Fälligkeiten."
    MoreEntry.SETTINGS -> "Design, Erinnerungen, Sperre und Daten."
    MoreEntry.ROUTINES -> "Wiederkehrende Abläufe."
    MoreEntry.AI -> "Hilfe beim Sortieren, ohne Diagnosen."
    MoreEntry.STATS -> "Ein Rückblick ohne Druck."
    MoreEntry.COMMUNITY -> "Austausch mit anderen."
    MoreEntry.PARTNER -> "Gemeinsam planen mit einer vertrauten Person."
}

@Suppress("DEPRECATION")
private fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (_: PackageManager.NameNotFoundException) {
    ""
}
