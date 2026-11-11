package com.lockeddesire.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

fun now(): Long = System.currentTimeMillis()

@Entity
data class UserProfile(
    @PrimaryKey val id: Long = 1,
    val username: String = "",
    val avatar: String = "🔒",
    val ageConfirmed: Boolean = false,
    val gender: String = "",
    val pronouns: String = "",
    val salutation: String = "",
    val orientation: String = "",
    val role: String = "Solo",
    val likes: String = "",
    val limitsText: String = "",
    val difficulty: Int = 1,
    val dungeonLevel: Int = 1,
    val xp: Int = 0,
    val goals: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now()
)

@Entity
data class UserPreferences(
    @PrimaryKey val id: Long = 1,
    val effectsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val ttsEnabled: Boolean = false,
    val activePersonaId: Long = 1,
    val safeword: String = "Rot",
    val fitnessMaxReps: Int = 40,
    val fitnessMaxMinutes: Int = 30,
    val jointFriendly: Boolean = false,
    val excludedCategories: String = "",
    val accent: String = "blood",
    val aiMode: String = "RULES",
    val aiBaseUrl: String = "",
    val aiModel: String = "",
    val updatedAt: Long = now()
)

@Entity
data class ConsentSettings(
    @PrimaryKey val id: Long = 1,
    val aiEnabled: Boolean = true,
    val cloudAiConsent: Boolean = false,
    val shareProfile: Boolean = false,
    val shareLimits: Boolean = true,
    val shareFetishes: Boolean = false,
    val shareProgress: Boolean = false,
    val memoryEnabled: Boolean = true,
    val updatedAt: Long = now()
)

@Entity
data class AiPersona(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String = "👑",
    val type: String = "FEMALE",
    val pronouns: String = "sie/ihr",
    val salutation: String = "Herrin",
    val personality: String = "FRECH",
    val strictness: Int = 5,
    val voicePitch: Float = 1.0f,
    val voiceRate: Float = 1.0f,
    val greeting: String = "",
    val notifyStyle: String = "",
    val builtIn: Boolean = false,
    val createdAt: Long = now()
)

@Entity(indices = [Index("personaId")])
data class AiConversation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaId: Long,
    val title: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now()
)

@Entity(
    foreignKeys = [ForeignKey(entity = AiConversation::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("conversationId")]
)
data class AiMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val fromUser: Boolean,
    val text: String,
    val offerTitle: String = "",
    val offerDescription: String = "",
    val offerCategory: String = "",
    val offerDuration: Int = 0,
    val offerXp: Int = 0,
    val offerTaskId: Long = 0,
    val offerState: String = "",
    val createdAt: Long = now()
)

@Entity
data class AiMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val content: String,
    val createdAt: Long = now(),
    val updatedAt: Long = now()
)

@Entity(indices = [Index(value = ["key"], unique = true)])
data class TaskCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val name: String,
    val emoji: String,
    val rouletteEnabled: Boolean = true
)

@Entity(indices = [Index("categoryKey")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val categoryKey: String,
    val difficulty: Int = 1,
    val durationMin: Int = 5,
    val xp: Int = 10,
    val hasTimer: Boolean = false,
    val requirements: String = "",
    val safetyTag: String = "SAFE",
    val enabled: Boolean = true,
    val partnerRequired: Boolean = false,
    val rouletteEnabled: Boolean = true,
    val dungeonEnabled: Boolean = true,
    val custom: Boolean = false,
    val fetishKey: String = "",
    val physicalLoad: Int = 0,
    val jointLoad: Boolean = false,
    val reps: Int = 0,
    val limitsNote: String = "",
    val createdAt: Long = now()
)

@Entity(indices = [Index("createdAt"), Index("categoryKey")])
data class TaskCompletion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long = 0,
    val taskTitle: String,
    val categoryKey: String,
    val status: String,
    val xp: Int = 0,
    val source: String = "ROULETTE",
    val note: String = "",
    val createdAt: Long = now()
)

@Entity
data class DungeonSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long = now(),
    val endsAt: Long,
    val difficulty: Int,
    val status: String = "ACTIVE",
    val personaId: Long = 1,
    val pausedAt: Long = 0
)

@Entity(
    foreignKeys = [ForeignKey(entity = DungeonSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")]
)
data class DungeonPhase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val idx: Int,
    val name: String,
    val startHour: Int,
    val endHour: Int,
    val completed: Boolean = false,
    val notified: Boolean = false
)

@Entity(
    foreignKeys = [ForeignKey(entity = DungeonSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sessionId")]
)
data class DungeonChallenge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val phaseIdx: Int,
    val taskId: Long,
    val title: String,
    val description: String,
    val categoryKey: String,
    val durationMin: Int,
    val xp: Int,
    val scheduledAt: Long,
    val status: String = "PENDING",
    val notified: Boolean = false
)

@Entity
data class ChastitySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long = now(),
    val goalAt: Long = 0,
    val endedAt: Long = 0,
    val pausedAt: Long = 0,
    val pausedTotalMs: Long = 0,
    val note: String = "",
    val status: String = "ACTIVE",
    val goalNotified: Boolean = false
)

@Entity(indices = [Index(value = ["key"], unique = true)])
data class FetishCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val groupName: String,
    val name: String,
    val risky: Boolean = false,
    val custom: Boolean = false
)

@Entity(
    foreignKeys = [ForeignKey(entity = FetishCategory::class, parentColumns = ["id"], childColumns = ["fetishId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["fetishId"], unique = true)]
)
data class UserFetishPreference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fetishId: Long,
    val level: String = "NONE",
    val note: String = "",
    val limits: String = "",
    val allowedTasks: String = "",
    val rouletteEnabled: Boolean = false,
    val dungeonEnabled: Boolean = false,
    val updatedAt: Long = now()
)

@Entity
data class PetProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatar: String = "🐶",
    val role: String = "Puppy",
    val salutation: String = "",
    val limits: String = "",
    val favorites: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    val jointFriendly: Boolean = false,
    val active: Boolean = true,
    val createdAt: Long = now()
)

@Entity(indices = [Index("petId")])
data class PetSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val petId: Long,
    val taskTitle: String,
    val durationMin: Int = 0,
    val note: String = "",
    val createdAt: Long = now()
)

@Entity
data class Toy(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "Sonstiges",
    val sizeClass: String = "M",
    val material: String = "",
    val measures: String = "",
    val note: String = "",
    val favorite: Boolean = false,
    val rouletteEnabled: Boolean = true,
    val photoFile: String = "",
    val lastUsedAt: Long = 0,
    val createdAt: Long = now()
)

@Entity(indices = [Index("toyId")])
data class ToySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toyId: Long,
    val note: String = "",
    val durationMin: Int = 0,
    val createdAt: Long = now()
)

@Entity(indices = [Index(value = ["key"], unique = true)])
data class PersonalCounter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val name: String,
    val value: Int = 0,
    val builtIn: Boolean = true
)

@Entity(
    foreignKeys = [ForeignKey(entity = PersonalCounter::class, parentColumns = ["id"], childColumns = ["counterId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("counterId"), Index("createdAt")]
)
data class CounterEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterId: Long,
    val delta: Int,
    val note: String = "",
    val createdAt: Long = now()
)

@Entity(indices = [Index("createdAt")])
data class FitnessActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val reps: Int = 0,
    val durationSec: Int = 0,
    val note: String = "",
    val createdAt: Long = now()
)

@Entity(indices = [Index(value = ["key"], unique = true)])
data class Achievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val name: String,
    val description: String,
    val emoji: String,
    val xpReward: Int = 50
)

@Entity(
    foreignKeys = [ForeignKey(entity = Achievement::class, parentColumns = ["id"], childColumns = ["achievementId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["achievementId"], unique = true)]
)
data class UserAchievement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val achievementId: Long,
    val unlockedAt: Long = now()
)

@Entity
data class PartnerProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val role: String = "Switch",
    val pronouns: String = "",
    val limits: String = "",
    val consentGiven: Boolean = false,
    val createdAt: Long = now()
)

@Entity
data class NotificationSettings(
    @PrimaryKey val id: Long = 1,
    val enabled: Boolean = true,
    val spontaneous: Boolean = false,
    val frequency: Int = 0,
    val customPerDay: Int = 2,
    val maxPerDay: Int = 8,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 8 * 60,
    val discreet: Boolean = true,
    val vibration: Boolean = true,
    val sound: Boolean = true,
    val tts: Boolean = false,
    val surprise: Boolean = false,
    val taskTypes: String = "fitness,selfcontrol,chastity",
    val nChallenge: Boolean = true,
    val nPhase: Boolean = true,
    val nChastity: Boolean = true,
    val nDaily: Boolean = true,
    val nFitness: Boolean = false,
    val nRecord: Boolean = true,
    val nXp: Boolean = true,
    val nPause: Boolean = true,
    val lastDailyDay: Long = 0,
    val lastFitnessDay: Long = 0
)

/** Session-Tagebuch: Zeitgenerator, Partner-Sessions, freie Einträge. */
@Entity(indices = [Index("createdAt")])
data class SessionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val title: String,
    val durationMin: Int = 0,
    val note: String = "",
    val createdAt: Long = now()
)
