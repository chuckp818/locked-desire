package de.kopffrei.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.tasks.TaskFilter
import de.kopffrei.core.common.tasks.TaskLogic
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.StepProgress
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.entity.toView
import de.kopffrei.core.database.repository.TaskRepository
import de.kopffrei.core.reminders.ReminderCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class TaskItem(
    val task: TaskEntity,
    val overdue: Boolean,
    val stepsDone: Int,
    val stepsTotal: Int,
)

data class TasksUiState(
    val filter: TaskFilter = TaskFilter.OPEN,
    val query: String = "",
    val nowMillis: Long = 0L,
    val items: List<TaskItem> = emptyList(),
    val openCount: Int = 0,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val reminders: ReminderCoordinator,
) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.OPEN)
    private val query = MutableStateFlow("")

    val state: StateFlow<TasksUiState> = combine(
        repository.observeAll(),
        repository.observeStepProgress(),
        filter,
        query,
    ) { tasks, progress, filter, query ->
        val zone = TimeUtils.systemZone()
        val now = TimeUtils.nowMillis()
        val progressById: Map<String, StepProgress> = progress.associateBy { it.taskId }
        val byId = tasks.associateBy { it.id }
        val views = tasks.map { it.toView() }
        val visible = TaskLogic.sortForList(
            views.filter { TaskLogic.matches(it, filter, now, zone) && TaskLogic.matchesQuery(it, query) },
            now,
            zone,
        )
        TasksUiState(
            filter = filter,
            query = query,
            nowMillis = now,
            items = visible.mapNotNull { view ->
                val task = byId[view.id] ?: return@mapNotNull null
                val steps = progressById[view.id]
                TaskItem(task, TaskLogic.isOverdue(view, now, zone), steps?.done ?: 0, steps?.total ?: 0)
            },
            openCount = views.count { TaskLogic.matches(it, TaskFilter.OPEN, now, zone) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /** Schnell erfassen: nur ein Titel genügt, den Rest kann man später ergänzen. */
    fun quickAdd(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.save(TaskEntity(title = title.trim()), emptyList()) }
    }

    fun complete(task: TaskEntity) {
        viewModelScope.launch {
            repository.complete(task.id)
            reminders.rescheduleOwner(OwnerType.TASK, task.id)
        }
    }

    fun reopen(task: TaskEntity) {
        viewModelScope.launch {
            repository.reopen(task.id)
            reminders.rescheduleOwner(OwnerType.TASK, task.id)
        }
    }

    fun setPaused(task: TaskEntity, paused: Boolean) {
        viewModelScope.launch {
            repository.setPaused(task.id, paused)
            reminders.rescheduleOwner(OwnerType.TASK, task.id, reset = false)
        }
    }

    /** Verschiebt auf morgen und behält die Uhrzeit, falls es eine gibt. */
    fun postponeToTomorrow(task: TaskEntity) {
        viewModelScope.launch {
            val zone = TimeUtils.systemZone()
            val tomorrow = TimeUtils.toLocalDate(TimeUtils.nowMillis(), zone).plusDays(1)
            val dueAt = task.dueAt
            val time = if (task.dueHasTime && dueAt != null) {
                TimeUtils.toLocalDateTime(dueAt, zone).toLocalTime()
            } else {
                LocalTime.MIDNIGHT
            }
            repository.postpone(task.id, TimeUtils.millisOf(tomorrow, time, zone))
            reminders.rescheduleOwner(OwnerType.TASK, task.id)
        }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch {
            reminders.deleteForOwner(OwnerType.TASK, task.id)
            repository.delete(task.id)
        }
    }
}
