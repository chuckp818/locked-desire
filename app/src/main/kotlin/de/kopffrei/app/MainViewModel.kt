package de.kopffrei.app

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.security.AppLockPolicy
import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.database.prefs.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Hält Einstellungen und den Entsperr-Zustand über Drehungen der Activity hinweg. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var lockWasEnabled: Boolean? = null
    private var stoppedAt: Long? = null

    /** null, solange die Einstellungen noch nicht gelesen sind (dann zeigt die Activity nur den Hintergrund). */
    val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .onEach { current ->
            // Schaltet der Nutzer die Sperre gerade in der laufenden App ein, bleibt die App offen.
            // Beim Start mit schon aktiver Sperre ist der Vorwert unbekannt – dann bleibt sie gesperrt.
            if (lockWasEnabled == false && current.biometricLock) _unlocked.value = true
            lockWasEnabled = current.biometricLock
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onUnlocked() {
        _unlocked.value = true
        stoppedAt = null
    }

    /** Notausgang, wenn auf dem Gerät keine Sperre mehr eingerichtet ist (sonst käme man nie wieder hinein). */
    fun disableLockAndOpen() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(biometricLock = false) }
            onUnlocked()
        }
    }

    fun onActivityStopped(changingConfigurations: Boolean) {
        // Eine Drehung ist kein „in den Hintergrund gehen“.
        if (!changingConfigurations) stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onActivityStarted() {
        if (AppLockPolicy.shouldRelock(stoppedAt, SystemClock.elapsedRealtime())) {
            _unlocked.value = false
        }
        stoppedAt = null
    }
}
