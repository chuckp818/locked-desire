package com.lockeddesire.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM UserProfile WHERE id = 1") fun profile(): Flow<UserProfile?>
    @Query("SELECT * FROM UserProfile WHERE id = 1") suspend fun profileNow(): UserProfile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: UserProfile)

    @Query("SELECT * FROM UserPreferences WHERE id = 1") fun prefs(): Flow<UserPreferences?>
    @Query("SELECT * FROM UserPreferences WHERE id = 1") suspend fun prefsNow(): UserPreferences?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: UserPreferences)

    @Query("SELECT * FROM ConsentSettings WHERE id = 1") fun consent(): Flow<ConsentSettings?>
    @Query("SELECT * FROM ConsentSettings WHERE id = 1") suspend fun consentNow(): ConsentSettings?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(c: ConsentSettings)

    @Query("SELECT * FROM NotificationSettings WHERE id = 1") fun notif(): Flow<NotificationSettings?>
    @Query("SELECT * FROM NotificationSettings WHERE id = 1") suspend fun notifNow(): NotificationSettings?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(n: NotificationSettings)

    @Query("SELECT * FROM PartnerProfile ORDER BY id") fun partners(): Flow<List<PartnerProfile>>
    @Query("SELECT * FROM PartnerProfile ORDER BY id") suspend fun partnersNow(): List<PartnerProfile>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: PartnerProfile): Long
    @Delete suspend fun delete(p: PartnerProfile)
}

@Dao
interface AiDao {
    @Query("SELECT * FROM AiPersona ORDER BY id") fun personas(): Flow<List<AiPersona>>
    @Query("SELECT * FROM AiPersona ORDER BY id") suspend fun personasNow(): List<AiPersona>
    @Query("SELECT * FROM AiPersona WHERE id = :id") suspend fun persona(id: Long): AiPersona?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: AiPersona): Long
    @Delete suspend fun delete(p: AiPersona)

    @Query("SELECT * FROM AiConversation WHERE personaId = :personaId ORDER BY id DESC LIMIT 1")
    suspend fun conversationFor(personaId: Long): AiConversation?
    @Insert suspend fun insert(c: AiConversation): Long
    @Query("DELETE FROM AiConversation WHERE personaId = :personaId") suspend fun clearConversations(personaId: Long)
    @Query("DELETE FROM AiConversation") suspend fun clearAllConversations()

    @Query("SELECT * FROM AiMessage WHERE conversationId = :cid ORDER BY id") fun messages(cid: Long): Flow<List<AiMessage>>
    @Query("SELECT * FROM AiMessage WHERE conversationId = :cid ORDER BY id DESC LIMIT :n") suspend fun lastMessages(cid: Long, n: Int): List<AiMessage>
    @Query("SELECT * FROM AiMessage WHERE id = :id") suspend fun message(id: Long): AiMessage?
    @Insert suspend fun insert(m: AiMessage): Long
    @Update suspend fun update(m: AiMessage)
    @Query("SELECT COUNT(*) FROM AiMessage WHERE fromUser = 0 AND offerState != '' AND createdAt >= :since") suspend fun offersSince(since: Long): Int
    @Query("UPDATE AiMessage SET offerState = 'EXPIRED' WHERE offerState = 'OPEN'") suspend fun expireOffers()

    @Query("SELECT * FROM AiMemory ORDER BY id DESC") fun memories(): Flow<List<AiMemory>>
    @Query("SELECT * FROM AiMemory ORDER BY id DESC") suspend fun memoriesNow(): List<AiMemory>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(m: AiMemory): Long
    @Delete suspend fun delete(m: AiMemory)
    @Query("DELETE FROM AiMemory") suspend fun clearMemories()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM TaskCategory ORDER BY id") fun categories(): Flow<List<TaskCategory>>
    @Query("SELECT * FROM TaskCategory ORDER BY id") suspend fun categoriesNow(): List<TaskCategory>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(c: List<TaskCategory>)
    @Update suspend fun update(c: TaskCategory)

    @Query("SELECT * FROM Task ORDER BY custom DESC, categoryKey, difficulty, id") fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM Task") suspend fun tasksNow(): List<Task>
    @Query("SELECT * FROM Task WHERE id = :id") suspend fun task(id: Long): Task?
    @Query("SELECT COUNT(*) FROM Task") suspend fun taskCount(): Int
    @Insert suspend fun insert(t: Task): Long
    @Insert suspend fun insertAll(t: List<Task>)
    @Update suspend fun update(t: Task)
    @Delete suspend fun delete(t: Task)

    @Query("SELECT * FROM TaskCompletion ORDER BY createdAt DESC LIMIT 500") fun completions(): Flow<List<TaskCompletion>>
    @Query("SELECT * FROM TaskCompletion WHERE createdAt >= :since ORDER BY createdAt DESC") suspend fun completionsSince(since: Long): List<TaskCompletion>
    @Query("SELECT COUNT(*) FROM TaskCompletion WHERE status = 'DONE'") suspend fun doneCount(): Int
    @Query("SELECT COUNT(*) FROM TaskCompletion WHERE status = 'DONE' AND categoryKey = :cat") suspend fun doneCount(cat: String): Int
    @Query("SELECT COUNT(*) FROM TaskCompletion WHERE status = 'DONE' AND createdAt >= :since") suspend fun doneSince(since: Long): Int
    @Insert suspend fun insert(c: TaskCompletion): Long
}

@Dao
interface DungeonDao {
    @Query("SELECT * FROM DungeonSession WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1") fun active(): Flow<DungeonSession?>
    @Query("SELECT * FROM DungeonSession WHERE status = 'ACTIVE' ORDER BY id DESC LIMIT 1") suspend fun activeNow(): DungeonSession?
    @Query("SELECT * FROM DungeonSession ORDER BY id DESC LIMIT 50") fun sessions(): Flow<List<DungeonSession>>
    @Query("SELECT COUNT(*) FROM DungeonSession WHERE status = 'COMPLETED'") suspend fun completedCount(): Int
    @Insert suspend fun insert(s: DungeonSession): Long
    @Update suspend fun update(s: DungeonSession)

    @Query("SELECT * FROM DungeonPhase WHERE sessionId = :sid ORDER BY idx") fun phases(sid: Long): Flow<List<DungeonPhase>>
    @Query("SELECT * FROM DungeonPhase WHERE sessionId = :sid ORDER BY idx") suspend fun phasesNow(sid: Long): List<DungeonPhase>
    @Insert suspend fun insertPhases(p: List<DungeonPhase>)
    @Update suspend fun update(p: DungeonPhase)

    @Query("SELECT * FROM DungeonChallenge WHERE sessionId = :sid ORDER BY scheduledAt") fun challenges(sid: Long): Flow<List<DungeonChallenge>>
    @Query("SELECT * FROM DungeonChallenge WHERE sessionId = :sid ORDER BY scheduledAt") suspend fun challengesNow(sid: Long): List<DungeonChallenge>
    @Insert suspend fun insertChallenges(c: List<DungeonChallenge>)
    @Update suspend fun update(c: DungeonChallenge)
    @Query("UPDATE DungeonChallenge SET scheduledAt = scheduledAt + :delta WHERE sessionId = :sid AND status = 'PENDING'") suspend fun shift(sid: Long, delta: Long)
}

@Dao
interface ChastityDao {
    @Query("SELECT * FROM ChastitySession WHERE status != 'ENDED' ORDER BY id DESC LIMIT 1") fun active(): Flow<ChastitySession?>
    @Query("SELECT * FROM ChastitySession WHERE status != 'ENDED' ORDER BY id DESC LIMIT 1") suspend fun activeNow(): ChastitySession?
    @Query("SELECT * FROM ChastitySession ORDER BY id DESC") fun all(): Flow<List<ChastitySession>>
    @Query("SELECT * FROM ChastitySession ORDER BY id DESC") suspend fun allNow(): List<ChastitySession>
    @Insert suspend fun insert(s: ChastitySession): Long
    @Update suspend fun update(s: ChastitySession)
    @Delete suspend fun delete(s: ChastitySession)
}

@Dao
interface FetishDao {
    @Query("SELECT * FROM FetishCategory ORDER BY id") fun categories(): Flow<List<FetishCategory>>
    @Query("SELECT * FROM FetishCategory ORDER BY id") suspend fun categoriesNow(): List<FetishCategory>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(c: List<FetishCategory>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(c: FetishCategory): Long
    @Delete suspend fun delete(c: FetishCategory)
    @Query("SELECT * FROM UserFetishPreference") fun prefs(): Flow<List<UserFetishPreference>>
    @Query("SELECT * FROM UserFetishPreference") suspend fun prefsNow(): List<UserFetishPreference>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: UserFetishPreference): Long
}

@Dao
interface PetDao {
    @Query("SELECT * FROM PetProfile ORDER BY id") fun pets(): Flow<List<PetProfile>>
    @Query("SELECT * FROM PetProfile ORDER BY id") suspend fun petsNow(): List<PetProfile>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: PetProfile): Long
    @Delete suspend fun delete(p: PetProfile)
    @Query("SELECT * FROM PetSession ORDER BY createdAt DESC LIMIT 200") fun sessions(): Flow<List<PetSession>>
    @Query("SELECT COUNT(*) FROM PetSession") suspend fun sessionCount(): Int
    @Insert suspend fun insert(s: PetSession): Long
}

@Dao
interface ToyDao {
    @Query("SELECT * FROM Toy ORDER BY favorite DESC, name") fun toys(): Flow<List<Toy>>
    @Query("SELECT * FROM Toy") suspend fun toysNow(): List<Toy>
    @Query("SELECT * FROM Toy WHERE id = :id") fun toy(id: Long): Flow<Toy?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(t: Toy): Long
    @Delete suspend fun delete(t: Toy)
    @Query("SELECT * FROM ToySession WHERE toyId = :toyId ORDER BY createdAt DESC") fun sessions(toyId: Long): Flow<List<ToySession>>
    @Query("SELECT * FROM ToySession ORDER BY createdAt DESC LIMIT 200") fun allSessions(): Flow<List<ToySession>>
    @Insert suspend fun insert(s: ToySession): Long
    @Query("DELETE FROM ToySession WHERE toyId = :toyId") suspend fun deleteSessions(toyId: Long)
}

@Dao
interface StatsDao {
    @Query("SELECT * FROM PersonalCounter ORDER BY id") fun counters(): Flow<List<PersonalCounter>>
    @Query("SELECT * FROM PersonalCounter ORDER BY id") suspend fun countersNow(): List<PersonalCounter>
    @Query("SELECT * FROM PersonalCounter WHERE `key` = :key") suspend fun counter(key: String): PersonalCounter?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCounters(c: List<PersonalCounter>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(c: PersonalCounter): Long
    @Delete suspend fun delete(c: PersonalCounter)
    @Insert suspend fun insert(e: CounterEntry): Long
    @Query("SELECT * FROM CounterEntry WHERE createdAt >= :since ORDER BY createdAt") suspend fun entriesSince(since: Long): List<CounterEntry>
    @Query("SELECT * FROM CounterEntry ORDER BY createdAt DESC LIMIT 1000") fun entries(): Flow<List<CounterEntry>>

    @Query("SELECT * FROM FitnessActivity ORDER BY createdAt DESC LIMIT 500") fun fitness(): Flow<List<FitnessActivity>>
    @Query("SELECT * FROM FitnessActivity ORDER BY createdAt DESC") suspend fun fitnessNow(): List<FitnessActivity>
    @Insert suspend fun insert(f: FitnessActivity): Long
    @Delete suspend fun delete(f: FitnessActivity)

    @Query("SELECT * FROM Achievement ORDER BY id") fun achievements(): Flow<List<Achievement>>
    @Query("SELECT * FROM Achievement ORDER BY id") suspend fun achievementsNow(): List<Achievement>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAchievements(a: List<Achievement>)
    @Query("SELECT * FROM UserAchievement") fun unlocked(): Flow<List<UserAchievement>>
    @Query("SELECT * FROM UserAchievement") suspend fun unlockedNow(): List<UserAchievement>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(u: UserAchievement): Long

    @Query("SELECT * FROM SessionEntry ORDER BY createdAt DESC LIMIT 500") fun journal(): Flow<List<SessionEntry>>
    @Query("SELECT * FROM SessionEntry ORDER BY createdAt DESC") suspend fun journalNow(): List<SessionEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(e: SessionEntry): Long
    @Delete suspend fun delete(e: SessionEntry)
}
