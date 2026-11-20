package de.kopffrei.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.database.repository.MoodRepository
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SosViewModel @Inject constructor(
    private val moods: MoodRepository,
) : ViewModel() {
    private val _moodSaved = MutableStateFlow(false)
    val moodSaved: StateFlow<Boolean> = _moodSaved.asStateFlow()

    fun saveOverwhelmedMood() {
        viewModelScope.launch {
            try {
                moods.setToday(Mood.OVERWHELMED)
                _moodSaved.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _moodSaved.value = false
            }
        }
    }
}

/**
 * Einfache Hilfe bei Überforderung. Bewusst schlicht: kurze Sätze, wenige Schritte, kein Druck.
 * NICHT enthalten (Ausbaustufe 2): geführte Atemübungen mit Animation, Fokus-Übungen, erweiterte SOS-Hilfe.
 */
@Composable
fun SosScreen(
    onBack: () -> Unit,
    onBrainDump: () -> Unit,
    onStartFocus: (minutes: Int) -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SosViewModel = hiltViewModel(),
) {
    val moodSaved by viewModel.moodSaved.collectAsStateWithLifecycle()

    KfScreen(title = "Erst mal durchatmen", onBack = onBack, modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Du musst gerade nichts lösen. Ein kleiner Schritt reicht.",
                style = MaterialTheme.typography.headlineSmall,
            )
            KfCard(modifier = Modifier.fillMaxWidth()) {
                Text("Kurz atmen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Atme etwa vier Sekunden durch die Nase ein und sechs Sekunden langsam aus. Fünf Mal, in deinem Tempo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Geführte Atemübungen mit Animation sind noch nicht enthalten (geplant für Ausbaustufe 2).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text("Danach, wenn du magst:", style = MaterialTheme.typography.titleMedium)
            KfButton(
                "Kopf ausschütten",
                onClick = onBrainDump,
                icon = Icons.Rounded.EditNote,
                modifier = Modifier.fillMaxWidth(),
            )
            KfButton(
                "Nur 5 Minuten starten",
                onClick = { onStartFocus(5) },
                icon = Icons.Rounded.Timer,
                style = KfButtonStyle.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
            KfButton(
                "Eine einzige Aufgabe aussuchen",
                onClick = onOpenTasks,
                icon = Icons.Rounded.TaskAlt,
                style = KfButtonStyle.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
            KfButton(
                if (moodSaved) "Gespeichert: heute „Überfordert“" else "Heute als „Überfordert“ festhalten",
                onClick = viewModel::saveOverwhelmedMood,
                icon = Icons.Rounded.Mood,
                style = KfButtonStyle.Outlined,
                enabled = !moodSaved,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "KopfFrei ersetzt keine ärztliche oder therapeutische Hilfe. Wenn es dir über längere Zeit schlecht geht, sprich mit einer vertrauten Person oder mit Fachpersonal. In Deutschland ist die Telefonseelsorge kostenlos unter 0800 111 0 111 erreichbar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
