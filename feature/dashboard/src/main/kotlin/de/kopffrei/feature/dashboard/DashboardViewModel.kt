package de.kopffrei.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.settings.UserSettings
import de.kopffrei.core.common.tasks.TaskLogic
import de.kopffrei.core.common.tasks.TaskView
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.toView
import de.kopffrei.core.database.prefs.SettingsRepository
import de.kopffrei.core.database.repository.EventOccurrence
import de.kopffrei.core.database.repository.EventExpander
import de.kopffrei.core.database.repository.EventRepository
import de.kopffrei.core.database.repository.MoodRepository
import de.kopffrei.core.database.repository.NoteRepository
import de.kopffrei.core.database.repository.TaskRepository
import de.kopffrei.core.reminders.ActiveReminder
import de.kopffrei.core.reminders.ReminderCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val loaded: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val nowMillis: Long = 0L,
    val nextEvent: EventOccurrence? = null,
    val topTasks: List<TaskView> = emptyList(),
    val reminders: List<ActiveReminder> = emptyList(),
    val mood: Mood? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    eventRepository: EventRepository,
    private val taskRepository: TaskRepository,
    private val noteRepository: NoteRepository,
    private val moodRepository: MoodRepository,
    private val reminders: ReminderCoordinator,
) : ViewModel() {

    /** Aktualisiert „nächster Termin“ und „überfällig“ im Minutentakt. */
    private val ticker = flow {
        while (true) {
            emit(TimeUtils.nowMillis())
            delay(TICK_MS)
        }
    }

    private data class Base(
        val settings: UserSettings,
        val now: Long,
        val nextEvent: EventOccurrence?,
        val topTasks: List<TaskView>,
    )

    private val base = combine(
        settingsRepository.settings,
        eventRepository.observeAll(),
        taskRepository.observeAll(),
        ticker,
    ) { settings, events, tasks, now ->
        val zone = TimeUtils.systemZone()
        Base(
            settings = settings,
            now = now,
            nextEvent = EventExpander.next(events, now, zone),
            topTasks = TaskLogic.rank(tasks.map { it.toView() }, now, zone, limit = TOP_TASKS),
        )
    }

    val state: StateFlow<DashboardUiState> = combine(
        base,
        reminders.observeActive(),
        moodRepository.observeToday(),
    ) { base, active, mood ->
        DashboardUiState(
            loaded = true,
            settings = base.settings,
            nowMillis = base.now,
            nextEvent = base.nextEvent,
            topTasks = base.topTasks,
            reminders = active,
            mood = mood?.mood,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setMood(mood: Mood?) {
        viewModelScope.launch {
            if (mood == null) moodRepository.clearToday() else moodRepository.setToday(mood)
        }
    }

    fun saveThought(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { noteRepository.addThought(text) }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.complete(taskId)
            reminders.rescheduleOwner(OwnerType.TASK, taskId)
        }
    }

    fun acknowledge(reminderId: String) {
        viewModelScope.launch { reminders.acknowledge(reminderId) }
    }

    fun snooze(reminderId: String) {
        viewModelScope.launch { reminders.snooze(reminderId) }
    }

    fun completeFromReminder(reminderId: String) {
        viewModelScope.launch { reminders.complete(reminderId) }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private companion object {
        const val TICK_MS = 60_000L
        const val TOP_TASKS = 3
    }
}
