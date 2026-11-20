package de.kopffrei.core.reminders

import de.kopffrei.core.common.DeepLinks
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.model.ReminderState
import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.reminder.ReminderAnchor
import de.kopffrei.core.common.reminder.ReminderDraft
import de.kopffrei.core.common.reminder.ReminderPlanner
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TaskTime
import de.kopffrei.core.common.time.TimeUtils
import de.kopffrei.core.database.entity.ReminderEntity
import de.kopffrei.core.database.prefs.SettingsRepository
import de.kopffrei.core.database.repository.EventRepository
import de.kopffrei.core.database.repository.NoteRepository
import de.kopffrei.core.database.repository.ReminderRepository
import de.kopffrei.core.database.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Eine ausgelöste, noch nicht bestätigte Erinnerung (für die Startseite). */
data class ActiveReminder(
    val id: String,
    val ownerType: OwnerType,
    val ownerId: String,
    val title: String,
    val label: String,
    val firedAt: Long?,
    val snoozedUntil: Long?,
    val deepLink: String,
)

/**
 * Zentrale Stelle für Erinnerungen: speichert Stufen, plant Alarme, löst Benachrichtigungen aus und
 * verarbeitet Bestätigen / Später / Erledigt.
 *
 * Zustände einer Erinnerung (ReminderState):
 *  - PENDING: wartet auf ihren nächsten Zeitpunkt.
 *  - FIRED: wurde angezeigt und wartet auf Bestätigung. Auch wenn die Benachrichtigung weggewischt
 *    oder angetippt wurde, bleibt sie FIRED – nur Bestätigen/Erledigt beendet sie.
 *  - DONE: bestätigt, abgelaufen oder ihr Besitzer ist erledigt/gelöscht.
 *
 * Alle Änderungen laufen nacheinander (Mutex), weil Alarm, Benachrichtigungsknopf und Oberfläche
 * gleichzeitig eintreffen können.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val reminders: ReminderRepository,
    private val events: EventRepository,
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val notifications: NotificationHelper,
) {
    private val mutex = Mutex()
    private val zone get() = TimeUtils.systemZone()

    private data class OwnerInfo(
        val title: String,
        val anchor: ReminderAnchor?,
        val active: Boolean,
        val deepLink: String,
    )

    // ------------------------------------------------------------------ Lesen

    suspend fun loadDrafts(ownerType: OwnerType, ownerId: String): List<ReminderDraft> =
        reminders.getForOwner(ownerType, ownerId).map {
            ReminderDraft(
                id = it.id,
                label = it.label,
                offsetMinutes = it.offsetMinutes,
                fixedAt = it.fixedAt,
                repeatEveryMinutes = it.repeatEveryMinutes,
            )
        }

    /** Ausgelöste, unbestätigte Erinnerungen, neueste zuerst. */
    fun observeActive(): Flow<List<ActiveReminder>> =
        reminders.observeFired().map { list ->
            list.mapNotNull { r ->
                val owner = resolveOwner(r)
                if (owner == null || !owner.active) {
                    null
                } else {
                    ActiveReminder(
                        id = r.id,
                        ownerType = r.ownerType,
                        ownerId = r.ownerId,
                        title = owner.title,
                        label = r.label,
                        firedAt = r.lastFiredAt,
                        snoozedUntil = r.snoozedUntil,
                        deepLink = owner.deepLink,
                    )
                }
            }
        }

    // ------------------------------------------------------------------ Speichern und Planen

    /**
     * Ersetzt die Erinnerungen eines Besitzers durch [drafts]. Beim Speichern beginnen alle Stufen
     * neu (Zustand PENDING), weil sich Zeit oder Inhalt des Besitzers geändert haben können.
     */
    suspend fun replaceForOwner(ownerType: OwnerType, ownerId: String, drafts: List<ReminderDraft>) =
        mutex.withLock {
            val now = TimeUtils.nowMillis()
            val valid = drafts.filter { it.offsetMinutes != null || it.fixedAt != null }
            val keepIds = valid.map { it.id }.toSet()
            val existing = reminders.getForOwner(ownerType, ownerId)
            existing.filter { it.id !in keepIds }.forEach { removeLocked(it.id) }
            valid.forEach { draft ->
                val old = existing.firstOrNull { it.id == draft.id }
                val entity = ReminderEntity(
                    id = draft.id,
                    ownerType = ownerType,
                    ownerId = ownerId,
                    label = draft.label.trim(),
                    offsetMinutes = draft.offsetMinutes,
                    fixedAt = draft.fixedAt,
                    repeatEveryMinutes = draft.repeatEveryMinutes,
                    createdAt = old?.createdAt ?: now,
                )
                reminders.save(entity)
                notifications.cancel(entity.id)
                refreshLocked(entity, now)
            }
        }

    /**
     * Plant alle Erinnerungen eines Besitzers neu, z. B. nachdem eine Aufgabe erledigt oder ein Termin
     * verschoben wurde. Mit [reset] beginnen die Stufen von vorn (sonst bleibt ihr Zustand erhalten).
     */
    suspend fun rescheduleOwner(ownerType: OwnerType, ownerId: String, reset: Boolean = true) =
        mutex.withLock { rescheduleOwnerLocked(ownerType, ownerId, reset) }

    /** Entfernt alle Erinnerungen eines Besitzers samt Alarmen (z. B. beim Löschen). */
    suspend fun deleteForOwner(ownerType: OwnerType, ownerId: String) = mutex.withLock {
        reminders.getForOwner(ownerType, ownerId).forEach { cancelAlarmAndNotification(it.id) }
        reminders.deleteForOwner(ownerType, ownerId)
    }

    /** Plant nach Neustart, Zeit-/Zeitzonenänderung oder App-Update alles neu. */
    suspend fun rescheduleAll() = mutex.withLock {
        notifications.ensureChannels()
        val now = TimeUtils.nowMillis()
        reminders.getNotDone().forEach { refreshLocked(it, now) }
    }

    /** Bricht alle Alarme und Benachrichtigungen ab (vor „Alle Daten löschen“). */
    suspend fun cancelAll() = mutex.withLock {
        reminders.getAll().forEach { cancelAlarmAndNotification(it.id) }
    }

    /** Einmalige Erinnerung ohne Besitzer, z. B. das Ende des Fokus-Timers. Gleiche [id] ersetzt die alte. */
    suspend fun scheduleStandalone(id: String, ownerId: String, label: String, atMillis: Long) = mutex.withLock {
        val entity = ReminderEntity(
            id = id,
            ownerType = OwnerType.STANDALONE,
            ownerId = ownerId,
            label = label,
            fixedAt = atMillis,
        )
        reminders.save(entity)
        notifications.cancel(id)
        refreshLocked(entity, TimeUtils.nowMillis())
    }

    suspend fun cancelStandalone(id: String) = mutex.withLock { removeLocked(id) }

    // ------------------------------------------------------------------ Auslösen

    /** Wird vom Alarm aufgerufen. */
    suspend fun onAlarm(reminderId: String) = mutex.withLock {
        val reminder = reminders.get(reminderId) ?: return@withLock
        if (reminder.state == ReminderState.DONE) return@withLock
        val now = TimeUtils.nowMillis()
        val owner = resolveOwner(reminder)
        if (owner == null || !owner.active) {
            refreshLocked(reminder, now)
            return@withLock
        }

        val isFollowUp = reminder.snoozedUntil != null
        val nominal: Long? = if (isFollowUp) {
            reminder.lastTriggeredAt
        } else {
            ReminderPlanner.nextTrigger(
                reminder.fixedAt, reminder.offsetMinutes, owner.anchor,
                reminder.lastTriggeredAt, null, now, zone,
            )
        }
        if (!isFollowUp) {
            if (nominal == null) {
                refreshLocked(reminder, now)
                return@withLock
            }
            if (nominal > now + EARLY_TOLERANCE_MS) {
                // Alarm kam deutlich zu früh (z. B. Uhr verstellt): zum richtigen Zeitpunkt neu planen.
                scheduler.schedule(reminder.id, nominal)
                return@withLock
            }
        }

        val repeatsDone = if (isFollowUp) reminder.repeatsDone + 1 else 0
        val repeatMinutes = reminder.repeatEveryMinutes
        val followUpAt = if (repeatMinutes != null && repeatsDone < ReminderPlanner.MAX_REPEATS) {
            now + repeatMinutes * TimeUtils.MINUTE_MS
        } else {
            null
        }
        var updated = reminder.copy(
            state = ReminderState.FIRED,
            lastFiredAt = now,
            lastTriggeredAt = nominal ?: reminder.lastTriggeredAt,
            snoozedUntil = followUpAt,
            repeatsDone = repeatsDone,
        )
        // Einmalige Erinnerungen ohne Besitzer (z. B. Fokus-Ende) brauchen keine Bestätigung.
        if (reminder.ownerType == OwnerType.STANDALONE && repeatMinutes == null) {
            updated = updated.copy(state = ReminderState.DONE)
        }
        reminders.save(updated)

        notifications.show(
            reminderId = reminder.id,
            title = owner.title,
            text = notificationText(updated),
            deepLink = owner.deepLink,
            showDoneAction = reminder.ownerType == OwnerType.TASK,
            snoozeMinutes = settings.settings.first().snoozeMinutes,
        )

        // Nächster Alarm: Wiederholung oder – bei Serien – der nächste Termin.
        if (updated.state != ReminderState.DONE) refreshLocked(updated, now)
    }

    // ------------------------------------------------------------------ Reaktion des Nutzers

    /** „Gesehen“: beendet Wiederholungen. Bei Serien wird der nächste Termin geplant. */
    suspend fun acknowledge(reminderId: String) = mutex.withLock { acknowledgeLocked(reminderId) }

    /** „Später“: erinnert nach [minutes] (Standard aus den Einstellungen) erneut. */
    suspend fun snooze(reminderId: String, minutes: Int? = null) = mutex.withLock {
        val reminder = reminders.get(reminderId) ?: return@withLock
        val delay = minutes ?: settings.settings.first().snoozeMinutes
        val until = TimeUtils.nowMillis() + delay * TimeUtils.MINUTE_MS
        reminders.save(reminder.copy(state = ReminderState.FIRED, snoozedUntil = until, repeatsDone = 0))
        notifications.cancel(reminderId)
        scheduler.schedule(reminderId, until)
    }

    /** „Erledigt“: wie „Gesehen“, und eine zugehörige Aufgabe wird abgehakt. */
    suspend fun complete(reminderId: String) = mutex.withLock {
        val reminder = reminders.get(reminderId) ?: return@withLock
        acknowledgeLocked(reminderId)
        if (reminder.ownerType == OwnerType.TASK) {
            tasks.complete(reminder.ownerId)
            rescheduleOwnerLocked(OwnerType.TASK, reminder.ownerId, reset = true)
        }
    }

    // ------------------------------------------------------------------ Intern (Mutex wird gehalten)

    private suspend fun acknowledgeLocked(reminderId: String) {
        val reminder = reminders.get(reminderId) ?: return
        val now = TimeUtils.nowMillis()
        cancelAlarmAndNotification(reminderId)
        val cleared = reminder.copy(snoozedUntil = null, repeatsDone = 0)
        val owner = resolveOwner(cleared)
        val next = if (owner != null && owner.active) {
            ReminderPlanner.nextTrigger(
                cleared.fixedAt, cleared.offsetMinutes, owner.anchor,
                cleared.lastTriggeredAt, null, now, zone,
            )
        } else {
            null
        }
        if (next != null) {
            reminders.save(cleared.copy(state = ReminderState.PENDING))
            scheduler.schedule(reminderId, next)
        } else {
            reminders.save(cleared.copy(state = ReminderState.DONE))
        }
    }

    private suspend fun rescheduleOwnerLocked(ownerType: OwnerType, ownerId: String, reset: Boolean) {
        val now = TimeUtils.nowMillis()
        reminders.getForOwner(ownerType, ownerId).forEach { stored ->
            val reminder = if (reset) {
                stored.copy(
                    state = ReminderState.PENDING,
                    snoozedUntil = null,
                    lastTriggeredAt = null,
                    repeatsDone = 0,
                ).also {
                    reminders.save(it)
                    notifications.cancel(it.id)
                }
            } else {
                stored
            }
            refreshLocked(reminder, now)
        }
    }

    /**
     * Bringt Alarm und gespeicherten Zustand einer Erinnerung in Einklang: verwaiste Erinnerungen
     * werden gelöscht, solche erledigter Besitzer beendet, sonst wird der nächste Zeitpunkt geplant.
     */
    private suspend fun refreshLocked(reminder: ReminderEntity, now: Long) {
        val owner = resolveOwner(reminder)
        if (owner == null) {
            cancelAlarmAndNotification(reminder.id)
            reminders.delete(reminder.id)
            return
        }
        if (!owner.active) {
            cancelAlarmAndNotification(reminder.id)
            if (reminder.state != ReminderState.DONE) {
                reminders.save(reminder.copy(state = ReminderState.DONE, snoozedUntil = null))
            }
            return
        }
        val next = ReminderPlanner.nextTrigger(
            reminder.fixedAt, reminder.offsetMinutes, owner.anchor,
            reminder.lastTriggeredAt, reminder.snoozedUntil, now, zone,
        )
        if (next != null) {
            scheduler.schedule(reminder.id, next)
        } else {
            scheduler.cancel(reminder.id)
            // PENDING ohne weiteren Zeitpunkt = verpasst/abgelaufen. FIRED wartet weiter auf Bestätigung.
            if (reminder.state == ReminderState.PENDING) {
                reminders.save(reminder.copy(state = ReminderState.DONE))
            }
        }
    }

    private suspend fun removeLocked(reminderId: String) {
        cancelAlarmAndNotification(reminderId)
        reminders.delete(reminderId)
    }

    private fun cancelAlarmAndNotification(reminderId: String) {
        scheduler.cancel(reminderId)
        notifications.cancel(reminderId)
    }

    private fun notificationText(reminder: ReminderEntity): String {
        val label = reminder.label.ifBlank { "Erinnerung" }
        val offset = reminder.offsetMinutes
        return if (offset != null && offset > 0) "$label (${TextUtils.formatOffset(offset)})" else label
    }

    private suspend fun resolveOwner(reminder: ReminderEntity): OwnerInfo? = when (reminder.ownerType) {
        OwnerType.EVENT -> events.get(reminder.ownerId)?.let { event ->
            // Ganztägige Termine werden auf 09:00 Uhr bezogen, damit Stufen sinnvoll vorher liegen.
            val start = if (event.allDay) {
                TimeUtils.millisOf(
                    TimeUtils.toLocalDate(event.startAt, zone), TimeUtils.DEFAULT_DUE_TIME, zone,
                )
            } else {
                event.startAt
            }
            OwnerInfo(
                title = event.title.ifBlank { "Termin" },
                anchor = ReminderAnchor(start, event.recurrence),
                active = true,
                deepLink = DeepLinks.openEvent(event.id),
            )
        }
        OwnerType.TASK -> tasks.get(reminder.ownerId)?.let { task ->
            OwnerInfo(
                title = task.title.ifBlank { "Aufgabe" },
                // Bei Aufgaben wandert die Fälligkeit beim Abhaken von selbst weiter (keine Serie im Anker).
                anchor = task.dueAt?.let { ReminderAnchor(TaskTime.anchorMillis(it, task.dueHasTime, zone)) },
                active = task.status == TaskStatus.OPEN,
                deepLink = DeepLinks.openTask(task.id),
            )
        }
        OwnerType.NOTE -> notes.get(reminder.ownerId)?.let { note ->
            val title = note.title.ifBlank { TextUtils.truncate(TextUtils.firstLine(note.body), 60) }
            OwnerInfo(
                title = title.ifBlank { "Notiz" },
                anchor = null,
                active = note.trashedAt == null,
                deepLink = DeepLinks.openNote(note.id),
            )
        }
        OwnerType.JOURNAL -> OwnerInfo("Tagebuch", null, true, DeepLinks.JOURNAL)
        OwnerType.STANDALONE -> OwnerInfo(
            title = reminder.label.ifBlank { "Erinnerung" },
            anchor = null,
            active = true,
            deepLink = if (reminder.ownerId == STANDALONE_FOCUS) DeepLinks.focus() else DeepLinks.TODAY,
        )
    }

    companion object {
        /** Besitzer-Kennung für das Ende des Fokus-Timers. */
        const val STANDALONE_FOCUS = "focus"

        /** Feste ID, damit höchstens ein Fokus-Alarm gleichzeitig existiert. */
        const val FOCUS_REMINDER_ID = "focus-timer-end"

        private const val EARLY_TOLERANCE_MS = 60_000L
    }
}
