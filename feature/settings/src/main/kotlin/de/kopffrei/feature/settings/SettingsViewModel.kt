package de.kopffrei.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.kopffrei.core.common.WidgetUpdater
import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.database.DataManagement
import de.kopffrei.core.database.prefs.SettingsRepository
import de.kopffrei.core.reminders.NotificationHelper
import de.kopffrei.core.reminders.ReminderCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Einstellungen, Backup (JSON-Export) und „Alle Daten löschen“. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SettingsRepository,
    private val dataManagement: DataManagement,
    private val reminders: ReminderCoordinator,
    private val notifications: NotificationHelper,
    private val widgets: WidgetUpdater,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Speichert eine Änderung und aktualisiert die Widgets (Design und App-Sperre wirken auch dort). */
    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            repository.update(transform)
            widgets.requestUpdate()
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun sendTestNotification() {
        notifications.showTest()
    }

    /** Schreibt alle Daten als JSON in die vom Nutzer gewählte Datei (unverschlüsselt!). */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val output = context.contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("Datei konnte nicht geöffnet werden")
                    output.use { dataManagement.exportJson(it) }
                    "Backup gespeichert."
                } catch (e: IOException) {
                    "Export fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
                } catch (e: SecurityException) {
                    "Export fehlgeschlagen: kein Zugriff auf die Datei."
                }
            }
            _message.value = result
        }
    }

    /**
     * Löscht alle Inhalte, Anhänge, Einstellungen und den Fokus-Timer. Zuerst werden alle
     * Alarme und Benachrichtigungen abgebrochen, damit nichts auf gelöschte Daten zeigt.
     */
    fun deleteEverything() {
        viewModelScope.launch {
            try {
                reminders.cancelAll()
                dataManagement.deleteEverything()
                repository.reset()
                context.deleteSharedPreferences(FOCUS_PREFS)
                widgets.requestUpdate()
                _message.value = "Alle Daten wurden gelöscht."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Löschen fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
            }
        }
    }

    private companion object {
        /** Name der Einstellungsdatei des Fokus-Timers (siehe feature:focus, FocusStore). */
        const val FOCUS_PREFS = "focus_timer"
    }
}
