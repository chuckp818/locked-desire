package de.kopffrei.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.model.NoteType
import de.kopffrei.core.common.model.OwnerType
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.model.ReminderState
import de.kopffrei.core.common.model.TaskStatus
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.database.entity.CalendarEventEntity
import de.kopffrei.core.database.entity.DraftEntity
import de.kopffrei.core.database.entity.MoodEntryEntity
import de.kopffrei.core.database.entity.NoteEntity
import de.kopffrei.core.database.entity.ReminderEntity
import de.kopffrei.core.database.entity.TaskEntity
import de.kopffrei.core.database.entity.TaskStepEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentierte Tests der DAOs gegen eine echte (In-Memory-)Room-Datenbank auf Gerät oder Emulator.
 * Hinweis: In dieser Entstehungsumgebung gab es kein Android – diese Tests wurden geschrieben, aber
 * NICHT ausgeführt. Ausführen: `./gradlew :core:database:connectedDebugAndroidTest`.
 * (Namen ohne Leerzeichen, weil Android-Tests unter API 30 keine Leerzeichen in Methodennamen erlauben.)
 */
@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: KopfFreiDatabase

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, KopfFreiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------- Notizen

    @Test
    fun activeNotesAreSortedPinnedFirstThenNewest() = runTest {
        val dao = db.noteDao()
        dao.upsert(NoteEntity(id = "old", title = "alt", updatedAt = 100))
        dao.upsert(NoteEntity(id = "new", title = "neu", updatedAt = 300))
        dao.upsert(NoteEntity(id = "pin", title = "angeheftet", isPinned = true, updatedAt = 50))

        val result = dao.observeActive(NoteType.NOTE, "%").first().map { it.id }
        assertEquals(listOf("pin", "new", "old"), result)
    }

    @Test
    fun searchFindsTitleBodyAndCategoryCaseInsensitive() = runTest {
        val dao = db.noteDao()
        dao.upsert(NoteEntity(id = "a", title = "Einkaufsliste", body = "Milch, Brot"))
        dao.upsert(NoteEntity(id = "b", title = "Idee", body = "App bauen", category = "Arbeit"))
        dao.upsert(NoteEntity(id = "c", title = "Sonstiges", body = "nichts"))

        assertEquals(listOf("a"), dao.observeActive(NoteType.NOTE, TextUtils.likePattern("milch")).first().map { it.id })
        assertEquals(listOf("b"), dao.observeActive(NoteType.NOTE, TextUtils.likePattern("arbeit")).first().map { it.id })
        assertEquals(listOf("a"), dao.observeActive(NoteType.NOTE, TextUtils.likePattern("EINKAUF")).first().map { it.id })
        assertTrue(dao.observeActive(NoteType.NOTE, TextUtils.likePattern("gibtesnicht")).first().isEmpty())
    }

    @Test
    fun searchTreatsPercentAndUnderscoreAsPlainCharacters() = runTest {
        val dao = db.noteDao()
        dao.upsert(NoteEntity(id = "a", title = "50% Rabatt"))
        dao.upsert(NoteEntity(id = "b", title = "Preis"))
        assertEquals(listOf("a"), dao.observeActive(NoteType.NOTE, TextUtils.likePattern("50%")).first().map { it.id })
        assertTrue(dao.observeActive(NoteType.NOTE, TextUtils.likePattern("_")).first().isEmpty())
    }

    @Test
    fun archivedAndTrashedNotesLeaveTheActiveList() = runTest {
        val dao = db.noteDao()
        dao.upsert(NoteEntity(id = "active", title = "a"))
        dao.upsert(NoteEntity(id = "archived", title = "b", isArchived = true))
        dao.upsert(NoteEntity(id = "trashed", title = "c", trashedAt = 1_000))

        assertEquals(listOf("active"), dao.observeActive(NoteType.NOTE, "%").first().map { it.id })
        assertEquals(listOf("archived"), dao.observeArchived(NoteType.NOTE, "%").first().map { it.id })
        assertEquals(listOf("trashed"), dao.observeTrashed(NoteType.NOTE, "%").first().map { it.id })
    }

    @Test
    fun restoringFromTrashBringsTheNoteBack() = runTest {
        val dao = db.noteDao()
        dao.upsert(NoteEntity(id = "n", title = "x", trashedAt = 1_000))
        dao.upsert(dao.get("n")!!.copy(trashedAt = null))
        assertEquals(listOf("n"), dao.observeActive(NoteType.NOTE, "%").first().map { it.id })
        assertTrue(dao.observeTrashed(NoteType.NOTE, "%").first().isEmpty())
    }

    @Test
    fun thoughtsAndNotesStaySeparate() = runTest {
        val dao = db.noteDao()
        dao.upsert(NoteEntity(id = "n", type = NoteType.NOTE, title = "Notiz"))
        dao.upsert(NoteEntity(id = "t", type = NoteType.THOUGHT, body = "Gedanke"))
        assertEquals(listOf("n"), dao.observeActive(NoteType.NOTE, "%").first().map { it.id })
        assertEquals(listOf("t"), dao.observeActive(NoteType.THOUGHT, "%").first().map { it.id })
        assertEquals(listOf("t"), dao.getLatestThoughts(10).map { it.id })
    }

    @Test
    fun latestPinnedIgnoresTrashedAndThoughts() = runTest {
        val dao = db.noteDao()
        dao.upsert(NoteEntity(id = "trashed", isPinned = true, trashedAt = 5, updatedAt = 900))
        dao.upsert(NoteEntity(id = "thought", type = NoteType.THOUGHT, isPinned = true, updatedAt = 800))
        dao.upsert(NoteEntity(id = "ok", isPinned = true, updatedAt = 100))
        assertEquals("ok", dao.getLatestPinned()?.id)
    }

    @Test
    fun trashedIdsBeforeCutoffOnlyReturnsOldOnes() = runTest {
        val dao = db.noteDao()
        dao.upsert(NoteEntity(id = "old", trashedAt = 1_000))
        dao.upsert(NoteEntity(id = "recent", trashedAt = 9_000))
        dao.upsert(NoteEntity(id = "active"))
        assertEquals(listOf("old"), dao.getTrashedIdsBefore(5_000))
        assertEquals(setOf("old", "recent"), dao.getTrashedIds().toSet())
    }

    // ------------------------------------------------------------- Aufgaben

    @Test
    fun taskStepsKeepTheirOrderAndAreReplacedOnSave() = runTest {
        val dao = db.taskDao()
        val task = TaskEntity(id = "t", title = "Wohnung")
        dao.saveWithSteps(
            task,
            listOf(
                TaskStepEntity(id = "s2", taskId = "t", title = "zweitens", position = 1),
                TaskStepEntity(id = "s1", taskId = "t", title = "erstens", position = 0),
            ),
        )
        assertEquals(listOf("s1", "s2"), dao.getSteps("t").map { it.id })

        dao.saveWithSteps(task, listOf(TaskStepEntity(id = "s3", taskId = "t", title = "neu", position = 0)))
        assertEquals(listOf("s3"), dao.getSteps("t").map { it.id })
    }

    @Test
    fun deletingATaskDeletesItsSteps() = runTest {
        val dao = db.taskDao()
        dao.saveWithSteps(TaskEntity(id = "t", title = "x"), listOf(TaskStepEntity(id = "s", taskId = "t", title = "y")))
        dao.delete("t")
        assertNull(dao.get("t"))
        assertTrue(dao.getAllSteps().isEmpty())
    }

    @Test
    fun stepProgressCountsDoneAndTotal() = runTest {
        val dao = db.taskDao()
        dao.saveWithSteps(
            TaskEntity(id = "t", title = "x"),
            listOf(
                TaskStepEntity(id = "a", taskId = "t", title = "a", isDone = true),
                TaskStepEntity(id = "b", taskId = "t", title = "b"),
                TaskStepEntity(id = "c", taskId = "t", title = "c", isDone = true),
            ),
        )
        val progress = dao.observeStepProgress().first().single { it.taskId == "t" }
        assertEquals(3, progress.total)
        assertEquals(2, progress.done)
        dao.setStepDone("b", true)
        assertEquals(3, dao.observeStepProgress().first().single { it.taskId == "t" }.done)
    }

    @Test
    fun openTasksExcludeDoneOnes() = runTest {
        val dao = db.taskDao()
        dao.upsert(TaskEntity(id = "open", title = "a"))
        dao.upsert(TaskEntity(id = "done", title = "b", status = TaskStatus.DONE))
        assertEquals(listOf("open"), dao.getOpen().map { it.id })
    }

    // ------------------------------------------------------------- Erinnerungen

    @Test
    fun notDoneRemindersExcludeConfirmedOnes() = runTest {
        val dao = db.reminderDao()
        dao.upsert(ReminderEntity(id = "p", ownerType = OwnerType.TASK, ownerId = "t", fixedAt = 1))
        dao.upsert(ReminderEntity(id = "f", ownerType = OwnerType.TASK, ownerId = "t", fixedAt = 2, state = ReminderState.FIRED))
        dao.upsert(ReminderEntity(id = "d", ownerType = OwnerType.TASK, ownerId = "t", fixedAt = 3, state = ReminderState.DONE))
        assertEquals(setOf("p", "f"), dao.getNotDone().map { it.id }.toSet())
        assertEquals(listOf("f"), dao.observeFired().first().map { it.id })
    }

    @Test
    fun deleteForOwnerOnlyRemovesThatOwnersReminders() = runTest {
        val dao = db.reminderDao()
        dao.upsert(ReminderEntity(id = "a", ownerType = OwnerType.TASK, ownerId = "t1", fixedAt = 1))
        dao.upsert(ReminderEntity(id = "b", ownerType = OwnerType.TASK, ownerId = "t2", fixedAt = 1))
        dao.upsert(ReminderEntity(id = "c", ownerType = OwnerType.EVENT, ownerId = "t1", fixedAt = 1))
        dao.deleteForOwner(OwnerType.TASK, "t1")
        assertEquals(setOf("b", "c"), dao.getAll().map { it.id }.toSet())
        assertEquals(listOf("c"), dao.getForOwner(OwnerType.EVENT, "t1").map { it.id })
    }

    // ------------------------------------------------------------- Kalender

    private fun event(id: String, start: Long, end: Long, rule: RecurrenceRule = RecurrenceRule.NONE) =
        CalendarEventEntity(id = id, title = id, startAt = start, endAt = end, recurrence = rule)

    @Test
    fun calendarQueryFindsSingleEventsTouchingTheRange() = runTest {
        val dao = db.calendarEventDao()
        dao.upsert(event("inside", 1_000, 2_000))
        dao.upsert(event("overlapStart", 500, 1_500))
        dao.upsert(event("before", 100, 900))
        dao.upsert(event("after", 3_000, 4_000))
        val ids = dao.observeBetween(1_000, 3_000).first().map { it.id }.toSet()
        assertEquals(setOf("inside", "overlapStart"), ids)
    }

    @Test
    fun calendarQueryKeepsRunningSeriesAndDropsEndedOnes() = runTest {
        val dao = db.calendarEventDao()
        dao.upsert(event("running", 100, 200, RecurrenceRule(Frequency.DAILY)))
        dao.upsert(event("ended", 100, 200, RecurrenceRule(Frequency.DAILY, until = 900)))
        dao.upsert(event("notStarted", 5_000, 5_100, RecurrenceRule(Frequency.WEEKLY)))
        val ids = dao.observeBetween(1_000, 3_000).first().map { it.id }.toSet()
        assertEquals(setOf("running"), ids)
    }

    @Test
    fun recurrenceRoundTripsThroughTheDatabase() = runTest {
        val dao = db.calendarEventDao()
        val rule = RecurrenceRule(Frequency.MONTHLY, interval = 2, until = 123_456)
        dao.upsert(event("e", 1, 2, rule))
        assertEquals(rule, dao.get("e")?.recurrence)
    }

    // ------------------------------------------------------------- Sonstiges

    @Test
    fun onlyOneMoodPerDayAndTheLatestWins() = runTest {
        val dao = db.moodDao()
        dao.insert(MoodEntryEntity(mood = Mood.GOOD, epochDay = 20_000))
        dao.insert(MoodEntryEntity(mood = Mood.OVERWHELMED, epochDay = 20_000))
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(Mood.OVERWHELMED, all[0].mood)
        assertNotNull(dao.observeForDay(20_000).first())
        dao.deleteForDay(20_000)
        assertNull(dao.observeForDay(20_000).first())
    }

    @Test
    fun draftsAreStoredPerKindAndCanBeDeleted() = runTest {
        val dao = db.draftDao()
        dao.upsert(DraftEntity(kind = "BRAIN_DUMP", text = "eins"))
        dao.upsert(DraftEntity(kind = "BRAIN_DUMP", text = "zwei"))
        assertEquals("zwei", dao.get("BRAIN_DUMP")?.text)
        assertEquals(1, dao.getAll().size)
        dao.delete("BRAIN_DUMP")
        assertNull(dao.get("BRAIN_DUMP"))
    }
}
