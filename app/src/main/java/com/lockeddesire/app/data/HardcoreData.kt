package com.lockeddesire.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Aufgabe der Hardcore-Bibliothek. */
@Entity(indices = [Index(value = ["code"], unique = true), Index("categoryKey")])
data class HardcoreTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val title: String,
    val categoryKey: String,
    val description: String,
    val difficulty: Int = 1,
    val durationMin: Int = 0,
    val xp: Int = 20,
    val physical: Boolean = false,
    val jointLoad: Boolean = false,
    val partnerRequired: Boolean = false,
    val toyCategory: String = "",
    val anatomy: String = "",
    val roles: String = "SUB,SWITCH,SOLO",
    val requirements: String = "",
    val safetyClass: String = "A",
    val status: String = "ACTIVE",
    val custom: Boolean = false,
    val createdAt: Long = now()
)

/** Eine konkrete Prüfung mit Zustand VERFÜGBAR → ANGENOMMEN → AKTIV → Ergebnis. */
@Entity(indices = [Index("status"), Index("createdAt")])
data class HardcoreRun(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskIds: String,
    val code: String,
    val title: String,
    val description: String,
    val categoryKey: String,
    val categories: String,
    val difficulty: Int,
    val xp: Int,
    val durationMin: Int = 0,
    val stepsTotal: Int = 1,
    val stepsDone: Int = 0,
    val toyId: Long = 0,
    val toyName: String = "",
    val partner: Boolean = false,
    val personaId: Long = 0,
    val source: String = "HARDCORE",
    val status: String = "AVAILABLE",
    val verdict: String = "",
    val createdAt: Long = now(),
    val acceptedAt: Long = 0,
    val startedAt: Long = 0,
    val endedAt: Long = 0
)

@Entity
data class HardcoreSettings(
    @PrimaryKey val id: Long = 1,
    val confirmed: Boolean = false,
    val difficulty: Int = 1,
    val categories: String = "hc_ds,hc_humiliation,hc_chastity,hc_orgasm,hc_bondage,hc_impact,hc_pet",
    val personaId: Long = 0,
    val anatomy: String = "",
    val includeIn24h: Boolean = false,
    val notify: Boolean = false,
    val chains: Boolean = true,
    val updatedAt: Long = now()
)

@Dao
interface HardcoreDao {
    @Query("SELECT * FROM HardcoreTask ORDER BY categoryKey, difficulty, code") fun tasks(): Flow<List<HardcoreTask>>
    @Query("SELECT * FROM HardcoreTask") suspend fun tasksNow(): List<HardcoreTask>
    @Query("SELECT COUNT(*) FROM HardcoreTask") suspend fun taskCount(): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTasks(t: List<HardcoreTask>)
    @Update suspend fun update(t: HardcoreTask)

    @Query("SELECT * FROM HardcoreRun ORDER BY id DESC LIMIT 500") fun runs(): Flow<List<HardcoreRun>>
    @Query("SELECT * FROM HardcoreRun ORDER BY id DESC") suspend fun runsNow(): List<HardcoreRun>
    @Query("SELECT * FROM HardcoreRun WHERE status IN ('AVAILABLE','ACCEPTED','ACTIVE') ORDER BY id DESC LIMIT 1") fun open(): Flow<HardcoreRun?>
    @Query("SELECT * FROM HardcoreRun WHERE status IN ('AVAILABLE','ACCEPTED','ACTIVE') ORDER BY id DESC LIMIT 1") suspend fun openNow(): HardcoreRun?
    @Query("SELECT * FROM HardcoreRun WHERE id = :id") suspend fun run(id: Long): HardcoreRun?
    @Query("SELECT * FROM HardcoreRun WHERE id = :id") fun runFlow(id: Long): Flow<HardcoreRun?>
    @Query("SELECT COUNT(*) FROM HardcoreRun WHERE source = :source AND createdAt >= :since") suspend fun countSince(source: String, since: Long): Int
    @Query("SELECT COUNT(*) FROM HardcoreRun WHERE status = 'PASSED'") suspend fun passedCount(): Int
    @Query("SELECT COUNT(*) FROM HardcoreRun WHERE status = 'PASSED' AND difficulty >= :d") suspend fun passedAtLeast(d: Int): Int
    @Insert suspend fun insert(r: HardcoreRun): Long
    @Update suspend fun update(r: HardcoreRun)
    @Query("DELETE FROM HardcoreRun WHERE id = :id AND status = 'AVAILABLE'") suspend fun discardAvailable(id: Long)
    @Query("UPDATE HardcoreRun SET status = 'ABORTED', endedAt = :at, verdict = :verdict WHERE status IN ('ACCEPTED','ACTIVE')") suspend fun abortAllOpen(at: Long, verdict: String)
    @Query("DELETE FROM HardcoreRun WHERE status = 'AVAILABLE'") suspend fun discardAllAvailable()

    @Query("SELECT * FROM HardcoreSettings WHERE id = 1") fun settings(): Flow<HardcoreSettings?>
    @Query("SELECT * FROM HardcoreSettings WHERE id = 1") suspend fun settingsNow(): HardcoreSettings?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(s: HardcoreSettings)
}
