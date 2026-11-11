package com.lockeddesire.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lockeddesire.app.LockedDesireApp
import com.lockeddesire.app.ai.Speech
import com.lockeddesire.app.data.AiPersona
import com.lockeddesire.app.data.ConsentSettings
import com.lockeddesire.app.data.LockState
import com.lockeddesire.app.data.NotificationSettings
import com.lockeddesire.app.data.Repo
import com.lockeddesire.app.data.UserPreferences
import com.lockeddesire.app.data.UserProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo: Repo = (app as LockedDesireApp).repo
    val db get() = repo.db
    private val speech = Speech(app)

    val ready = MutableStateFlow(false)
    val unlocked = MutableStateFlow(false)

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    val profile = db.profileDao().profile().map { it ?: UserProfile() }.state(UserProfile())
    val prefs = db.profileDao().prefs().map { it ?: UserPreferences() }.state(UserPreferences())
    val consent = db.profileDao().consent().map { it ?: ConsentSettings() }.state(ConsentSettings())
    val notif = db.profileDao().notif().map { it ?: NotificationSettings() }.state(NotificationSettings())
    val personas = db.aiDao().personas().state(emptyList())
    val lock = repo.secure.lock.state(LockState())
    val persona: StateFlow<AiPersona?> = combine(personas, prefs) { list, p ->
        list.firstOrNull { it.id == p.activePersonaId } ?: list.firstOrNull()
    }.state(null)

    val categories = db.taskDao().categories().state(emptyList())
    val tasks = db.taskDao().tasks().state(emptyList())
    val completions = db.taskDao().completions().state(emptyList())
    val chastity = db.chastityDao().active().state(null)
    val chastityHistory = db.chastityDao().all().state(emptyList())
    val dungeon = db.dungeonDao().active().state(null)
    val fetishes = db.fetishDao().categories().state(emptyList())
    val fetishPrefs = db.fetishDao().prefs().state(emptyList())
    val pets = db.petDao().pets().state(emptyList())
    val petSessions = db.petDao().sessions().state(emptyList())
    val toys = db.toyDao().toys().state(emptyList())
    val counters = db.statsDao().counters().state(emptyList())
    val counterEntries = db.statsDao().entries().state(emptyList())
    val fitness = db.statsDao().fitness().state(emptyList())
    val achievements = db.statsDao().achievements().state(emptyList())
    val unlockedAchievements = db.statsDao().unlocked().state(emptyList())
    val journal = db.statsDao().journal().state(emptyList())
    val partners = db.profileDao().partners().state(emptyList())
    val memories = db.aiDao().memories().state(emptyList())

    init {
        viewModelScope.launch {
            try { repo.ensureSeeded() } catch (e: Exception) { repo.emit("INFO", "Startfehler: ${e.message}") }
            ready.value = true
        }
    }

    /** Führt eine Repository-Aktion aus und meldet Fehler als Hinweis statt abzustürzen. */
    fun run(block: suspend Repo.() -> Unit): Job = viewModelScope.launch {
        try { repo.block() } catch (e: Exception) { repo.emit("INFO", "Das hat nicht geklappt: ${e.message ?: e.javaClass.simpleName}") }
    }

    fun speak(text: String, force: Boolean = false) {
        if (!force && !prefs.value.ttsEnabled) return
        val p = persona.value
        speech.speak(text, p?.voicePitch ?: 1f, p?.voiceRate ?: 1f)
    }

    fun stopSpeaking() = speech.stop()

    fun saveProfile(p: UserProfile) = run { db.profileDao().save(p.copy(updatedAt = com.lockeddesire.app.data.now())) }
    fun savePrefs(p: UserPreferences) = run { db.profileDao().save(p) }
    fun saveConsent(c: ConsentSettings) = run { db.profileDao().save(c) }
    fun saveNotif(n: NotificationSettings) = run { db.profileDao().save(n) }

    override fun onCleared() { speech.shutdown() }
}
