package de.kopffrei.feature.focus

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.kopffrei.core.common.focus.FocusSession
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.reminders.ReminderCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FocusUiState(
    val session: FocusSession = FocusSession.idle(FocusSession.minutesToMs(DEFAULT_MINUTES)),
    val nowMs: Long = 0L,
) {
    val remainingMs: Long get() = session.remainingMs(nowMs)
    val finished: Boolean get() = session.isFinished(nowMs)
    val running: Boolean get() = session.isRunning && !finished
    val paused: Boolean get() = session.isPaused
    val idle: Boolean get() = session.isIdle
    val progress: Float get() = session.progress(nowMs)
    val totalMinutes: Int get() = (session.totalMs / TimeUtils.MINUTE_MS).toInt()

    companion object {
        const val DEFAULT_MINUTES = 25
    }
}

/**
 * Leichter Fokus-Timer (Phase 1). Der Timer speichert nur den Endzeitpunkt; das Ende wird zusätzlich
 * über einen Alarm gemeldet, auch wenn die App geschlossen ist.
 *
 * Aufrufargumente (Deep Link `kopffrei://focus?minutes=25&autostart=true`): `minutes`, `autostart`.
 * Ein bereits laufender Timer wird dadurch nie überschrieben.
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    private val reminders: ReminderCoordinator,
) : ViewModel() {

    private val store = FocusStore(context)

    private val session = MutableStateFlow(
        store.load() ?: FocusSession.idle(FocusSession.minutesToMs(FocusUiState.DEFAULT_MINUTES)),
    )

    private val ticker = flow {
        while (true) {
            emit(TimeUtils.nowMillis())
            delay(TICK_MS)
        }
    }

    val state: StateFlow<FocusUiState> = combine(session, ticker) { current, now ->
        FocusUiState(current, now)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FocusUiState(session.value, TimeUtils.nowMillis()),
    )

    init {
        val minutes = savedStateHandle.get<Any?>("minutes")?.toString()?.toIntOrNull()?.takeIf { it in 1..MAX_MINUTES }
        val autostart = savedStateHandle.get<Any?>("autostart")?.toString().toBoolean()
        val alreadyHandled = savedStateHandle.get<Boolean>(HANDLED_KEY) == true
        if (minutes != null && !alreadyHandled) {
            savedStateHandle[HANDLED_KEY] = true
            val now = TimeUtils.nowMillis()
            val current = session.value
            // Einen laufenden oder pausierten Timer nie überschreiben.
            if (current.isIdle || current.isFinished(now)) {
                if (autostart) start(minutes) else selectMinutes(minutes)
            }
        }
    }

    /**
     * Liest den gespeicherten Timer neu ein (z. B. nachdem in den Einstellungen alle Daten gelöscht
     * wurden). Ein laufender Timer bleibt dabei unverändert, weil jede Änderung sofort gespeichert wird.
     */
    fun reload() {
        session.value = store.load() ?: FocusSession.idle(FocusSession.minutesToMs(FocusUiState.DEFAULT_MINUTES))
    }

    /** Dauer wählen – nur solange nichts läuft. */
    fun selectMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, MAX_MINUTES)
        val current = session.value
        if (current.isRunning || current.isPaused) {
            if (!current.isFinished(TimeUtils.nowMillis())) return
        }
        val idle = FocusSession.idle(FocusSession.minutesToMs(clamped))
        session.value = idle
        store.save(idle)
    }

    fun start(minutes: Int? = null) {
        val total = minutes?.let { FocusSession.minutesToMs(it.coerceIn(1, MAX_MINUTES)) } ?: session.value.totalMs
        val now = TimeUtils.nowMillis()
        val started = FocusSession.start(total, now)
        session.value = started
        store.save(started)
        scheduleEnd(started)
    }

    fun pause() {
        val paused = session.value.pause(TimeUtils.nowMillis())
        session.value = paused
        store.save(paused)
        viewModelScope.launch { reminders.cancelStandalone(ReminderCoordinator.FOCUS_REMINDER_ID) }
    }

    fun resume() {
        val resumed = session.value.resume(TimeUtils.nowMillis())
        session.value = resumed
        store.save(resumed)
        scheduleEnd(resumed)
    }

    /** Abbrechen bzw. „Fertig“ nach Ablauf: zurück auf Bereit mit der zuletzt gewählten Dauer. */
    fun reset() {
        val idle = FocusSession.idle(session.value.totalMs)
        session.value = idle
        store.save(idle)
        viewModelScope.launch { reminders.cancelStandalone(ReminderCoordinator.FOCUS_REMINDER_ID) }
    }

    private fun scheduleEnd(running: FocusSession) {
        val endAt = running.endAtMs ?: return
        viewModelScope.launch {
            reminders.scheduleStandalone(
                id = ReminderCoordinator.FOCUS_REMINDER_ID,
                ownerId = ReminderCoordinator.STANDALONE_FOCUS,
                label = "Fokus-Zeit ist um",
                atMillis = endAt,
            )
        }
    }

    companion object {
        const val MAX_MINUTES = 240
        private const val TICK_MS = 500L
        private const val HANDLED_KEY = "focusArgsHandled"
    }
}
