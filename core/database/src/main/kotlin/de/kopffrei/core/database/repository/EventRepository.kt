package de.kopffrei.core.database.repository

import de.kopffrei.core.common.WidgetUpdater
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.dao.CalendarEventDao
import de.kopffrei.core.database.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val dao: CalendarEventDao,
    private val attachments: AttachmentRepository,
    private val widgets: WidgetUpdater,
) {
    fun observeAll(): Flow<List<CalendarEventEntity>> = dao.observeAll()

    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<CalendarEventEntity>> =
        dao.observeBetween(fromMillis, toMillis)

    suspend fun getAll(): List<CalendarEventEntity> = dao.getAll()

    suspend fun get(id: String): CalendarEventEntity? = dao.get(id)

    suspend fun save(event: CalendarEventEntity) {
        dao.upsert(event.copy(updatedAt = TimeUtils.nowMillis()))
        widgets.requestUpdate()
    }

    suspend fun saveAll(events: List<CalendarEventEntity>) {
        dao.upsertAll(events)
        widgets.requestUpdate()
    }

    suspend fun delete(id: String) {
        attachments.removeAllFor(OwnerType.EVENT, id)
        dao.delete(id)
        widgets.requestUpdate()
    }
}
