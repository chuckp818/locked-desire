package de.kopffrei.core.database.repository

import de.kopffrei.core.common.WidgetUpdater
import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.recurrence.Recurrence
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.dao.TaskDao
import de.kopffrei.core.database.entity.StepProgress
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.entity.TaskStepEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val dao: TaskDao,
    private val widgets: WidgetUpdater,
) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    fun observe(id: String): Flow<TaskEntity?> = dao.observe(id)

    fun observeStepProgress(): Flow<List<StepProgress>> = dao.observeStepProgress()

    suspend fun get(id: String): TaskEntity? = dao.get(id)

    suspend fun getOpen(): List<TaskEntity> = dao.getOpen()

    suspend fun getSteps(taskId: String): List<TaskStepEntity> = dao.getSteps(taskId)

    fun observeSteps(taskId: String): Flow<List<TaskStepEntity>> = dao.observeSteps(taskId)

    suspend fun save(task: TaskEntity, steps: List<TaskStepEntity>) {
        val cleaned = steps.filter { it.title.isNotBlank() }
            .mapIndexed { index, step -> step.copy(taskId = task.id, position = index) }
        dao.saveWithSteps(task.copy(updatedAt = TimeUtils.nowMillis()), cleaned)
        widgets.requestUpdate()
    }

    suspend fun delete(id: String) {
        dao.delete(id) // Unteraufgaben werden per Fremdschlüssel mitgelöscht
        widgets.requestUpdate()
    }

    suspend fun setStepDone(stepId: String, done: Boolean) {
        dao.setStepDone(stepId, done)
        widgets.requestUpdate()
    }

    /**
     * Schließt die Aufgabe ab. Wiederkehrende Aufgaben bleiben offen und springen zum nächsten Termin
     * (Unteraufgaben werden zurückgesetzt); nur ohne weiteren Termin wird sie „erledigt“.
     * @return die aktualisierte Aufgabe oder null, wenn es sie nicht gibt.
     */
    suspend fun complete(taskId: String): TaskEntity? {
        val task = dao.get(taskId) ?: return null
        val now = TimeUtils.nowMillis()
        val zone = TimeUtils.systemZone()
        val next = task.dueAt?.let {
            Recurrence.nextDue(it, task.recurrence, zone, TimeUtils.startOfDay(now, zone))
        }
        val updated = if (next != null) {
            dao.resetSteps(task.id)
            task.copy(dueAt = next, completedAt = now, updatedAt = now)
        } else {
            task.copy(status = TaskStatus.DONE, completedAt = now, updatedAt = now)
        }
        dao.upsert(updated)
        widgets.requestUpdate()
        return updated
    }

    suspend fun reopen(taskId: String) = updateTask(taskId) {
        it.copy(status = TaskStatus.OPEN, completedAt = null)
    }

    suspend fun setPaused(taskId: String, paused: Boolean) = updateTask(taskId) {
        it.copy(status = if (paused) TaskStatus.PAUSED else TaskStatus.OPEN)
    }

    /** Verschiebt die Fälligkeit (Uhrzeit-Information bleibt erhalten). */
    suspend fun postpone(taskId: String, newDueAt: Long?) = updateTask(taskId) {
        it.copy(dueAt = newDueAt, dueHasTime = if (newDueAt == null) false else it.dueHasTime)
    }

    private suspend fun updateTask(id: String, transform: (TaskEntity) -> TaskEntity) {
        val current = dao.get(id) ?: return
        dao.upsert(transform(current).copy(updatedAt = TimeUtils.nowMillis()))
        widgets.requestUpdate()
    }
}
