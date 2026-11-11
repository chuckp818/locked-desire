package com.lockeddesire.app.data

import android.content.Context
import androidx.room.withTransaction
import com.lockeddesire.app.ai.AiContext
import com.lockeddesire.app.ai.AiEngine
import com.lockeddesire.app.ai.Intent
import com.lockeddesire.app.ai.IntentDetector
import com.lockeddesire.app.ai.RemoteEngine
import com.lockeddesire.app.ai.RuleBasedEngine
import com.lockeddesire.app.ai.Safety
import com.lockeddesire.app.engine.ChallengeEngine
import com.lockeddesire.app.engine.DungeonPlanner
import com.lockeddesire.app.engine.EngineContext
import com.lockeddesire.app.engine.Leveling
import com.lockeddesire.app.engine.Proposal
import com.lockeddesire.app.engine.Verdict
import com.lockeddesire.app.security.Crypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class UiEvent(val kind: String, val text: String)

fun Task.toProposal() = Proposal(
    title = title, description = description, categoryKey = categoryKey, difficulty = difficulty,
    durationMin = durationMin, xp = xp, tag = fetishKey, physicalLoad = physicalLoad, jointLoad = jointLoad,
    reps = reps, partnerRequired = partnerRequired, taskId = id
)

object TimeUtil {
    const val DAY = 86_400_000L
    fun minuteOfDay(t: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
    fun startOfDay(t: Long = now()): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun dayNumber(t: Long = now()): Long = startOfDay(t) / DAY
    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val d = s / 86400; val h = (s % 86400) / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (d > 0) "%dT %02d:%02d:%02d".format(d, h, m, sec) else "%02d:%02d:%02d".format(h, m, sec)
    }
    fun date(t: Long): String = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date(t))
    fun dateTime(t: Long): String = java.text.SimpleDateFormat("dd.MM.yy HH:mm", java.util.Locale.GERMANY).format(java.util.Date(t))
    fun clock(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}

class Repo(private val context: Context) {
    val db: AppDatabase get() = AppDatabase.get(context)
    val secure = SecurePrefs(context)
    val rules = RuleBasedEngine()
    private val seedMutex = Mutex()
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    private val p get() = db.profileDao()
    private val ai get() = db.aiDao()
    private val t get() = db.taskDao()
    private val d get() = db.dungeonDao()
    private val c get() = db.chastityDao()
    private val f get() = db.fetishDao()
    private val s get() = db.statsDao()

    fun emit(kind: String, text: String) { _events.tryEmit(UiEvent(kind, text)) }

    // ---------------- Startdaten ----------------
    suspend fun ensureSeeded() = seedMutex.withLock {
        withContext(Dispatchers.IO) {
            if (p.profileNow() == null) p.save(UserProfile())
            if (p.prefsNow() == null) p.save(UserPreferences())
            if (p.consentNow() == null) p.save(ConsentSettings())
            if (p.notifNow() == null) p.save(NotificationSettings())
            t.insertCategories(Seed.CATEGORIES)
            if (t.taskCount() == 0) t.insertAll(Seed.tasks(context))
            f.insertAll(Seed.FETISHES)
            s.insertCounters(Seed.COUNTERS)
            s.insertAchievements(Seed.ACHIEVEMENTS)
            if (ai.personasNow().isEmpty()) Seed.PERSONAS.forEach { ai.save(it) }
        }
    }

    suspend fun profile(): UserProfile = p.profileNow() ?: UserProfile()
    suspend fun prefs(): UserPreferences = p.prefsNow() ?: UserPreferences()
    suspend fun consent(): ConsentSettings = p.consentNow() ?: ConsentSettings()
    suspend fun notif(): NotificationSettings = p.notifNow() ?: NotificationSettings()
    suspend fun activePersona(): AiPersona {
        val all = ai.personasNow()
        return all.firstOrNull { it.id == prefs().activePersonaId } ?: all.firstOrNull() ?: Seed.PERSONAS.first()
    }

    // ---------------- Challenge-Engine-Kontext ----------------
    suspend fun excludedTags(): Set<String> {
        val cats = f.categoriesNow()
        val prefs = f.prefsNow().associateBy { it.fetishId }
        val out = HashSet<String>()
        cats.forEach { if (prefs[it.id]?.level == "EXCLUDED") out += it.key }
        cats.groupBy { it.groupName }.forEach { (group, list) ->
            val tag = Seed.GROUP_TAG[group]
            if (tag != null && list.all { prefs[it.id]?.level == "EXCLUDED" }) out += tag
        }
        return out
    }

    suspend fun blockedWords(): List<String> {
        val profile = profile()
        val fetishLimits = f.prefsNow().map { it.limits }
        return (listOf(profile.limitsText) + fetishLimits)
            .flatMap { it.split(",", ";", "\n") }.map { it.trim() }.filter { it.length >= 3 }
    }

    suspend fun offersToday(): Int = ai.offersSince(TimeUtil.startOfDay())

    suspend fun engineContext(enforceQuota: Boolean = false, enforceQuiet: Boolean = false, partner: Boolean? = null): EngineContext {
        val prefs = prefs(); val n = notif(); val profile = profile()
        return EngineContext(
            allowedCategories = Seed.CATEGORY_KEYS,
            excludedCategories = prefs.excludedCategories.split(",").filter { it.isNotBlank() }.toSet(),
            excludedTags = excludedTags(),
            blockedWords = blockedWords(),
            maxDifficulty = 6,
            fitnessMaxReps = prefs.fitnessMaxReps,
            fitnessMaxMinutes = prefs.fitnessMaxMinutes,
            jointFriendly = prefs.jointFriendly,
            partnerAvailable = partner ?: false,
            offersToday = offersToday(),
            maxPerDay = n.maxPerDay,
            quietStart = n.quietStart, quietEnd = n.quietEnd,
            minuteOfDay = TimeUtil.minuteOfDay(now()),
            enforceQuota = enforceQuota, enforceQuiet = enforceQuiet
        ).let { if (profile.difficulty in 1..6) it.copy(maxDifficulty = maxOf(profile.difficulty + 1, 2).coerceAtMost(6)) else it }
    }

    /** Wählt eine Aufgabe aus der Bibliothek, die alle Prüfungen der Challenge-Engine besteht. */
    suspend fun pickTask(
        categories: Set<String>? = null, tags: Set<String>? = null, partner: Boolean = false,
        maxDifficulty: Int? = null, forRoulette: Boolean = true, enforceQuota: Boolean = false, enforceQuiet: Boolean = false
    ): Proposal? {
        var ctx = engineContext(enforceQuota, enforceQuiet, partner)
        if (maxDifficulty != null) ctx = ctx.copy(maxDifficulty = maxDifficulty)
        val recent = t.completionsSince(now() - 3 * TimeUtil.DAY).take(25).map { it.taskTitle }.toSet()
        val pool = t.tasksNow().filter { task ->
            task.enabled && (if (forRoulette) task.rouletteEnabled else task.dungeonEnabled) &&
                (categories == null || task.categoryKey in categories) &&
                (tags == null || task.fetishKey in tags || ("@partner" in tags && task.categoryKey == "partner")) &&
                (partner || !task.partnerRequired)
        }.map { it.toProposal() }.filter { ChallengeEngine.approved(it, ctx) }
        if (pool.isEmpty()) return null
        val fresh = pool.filter { it.title !in recent }
        return (if (fresh.isNotEmpty()) fresh else pool).random()
    }

    suspend fun check(proposal: Proposal, partner: Boolean = false): Verdict =
        ChallengeEngine.check(proposal, engineContext(partner = partner))

    // ---------------- XP, Aufgaben, Erfolge ----------------
    suspend fun grantXp(amount: Int, reason: String = "") {
        if (amount <= 0) return
        val profile = profile()
        val old = Leveling.levelFor(profile.xp)
        val newXp = profile.xp + amount
        val lvl = Leveling.levelFor(newXp)
        p.save(profile.copy(xp = newXp, dungeonLevel = lvl, updatedAt = now()))
        emit("XP", "+$amount XP" + if (reason.isBlank()) "" else " · $reason")
        if (lvl > old) {
            emit("LEVEL", "Level $lvl erreicht: ${Leveling.titleFor(lvl)}")
            checkAchievements()
        }
    }

    suspend fun recordTask(title: String, category: String, xp: Int, status: String, source: String, taskId: Long = 0, note: String = "") {
        t.insert(TaskCompletion(taskId = taskId, taskTitle = title, categoryKey = category, status = status, xp = if (status == "DONE") xp else 0, source = source, note = note))
        if (status != "DONE") return
        when {
            category == "fitness" -> bumpCounter("fitness_challenges", 1)
            category == "petplay" -> {
                bumpCounter("pet_sessions", 1)
                db.petDao().petsNow().firstOrNull { it.active }?.let { pet ->
                    db.petDao().insert(PetSession(petId = pet.id, taskTitle = title))
                    val nx = pet.xp + xp
                    db.petDao().save(pet.copy(xp = nx, level = 1 + nx / 100))
                }
            }
        }
        if (source == "DUNGEON") bumpCounter("dungeon_challenges", 1)
        if (consent().memoryEnabled) rememberFavorite()
        grantXp(xp, title)
        checkAchievements()
    }

    private suspend fun rememberFavorite() {
        val done = t.completionsSince(now() - 60 * TimeUtil.DAY).filter { it.status == "DONE" }
        if (done.size < 3) return
        val favCat = done.groupingBy { it.categoryKey }.eachCount().maxByOrNull { it.value }?.key ?: return
        val name = Seed.CATEGORIES.firstOrNull { it.key == favCat }?.name ?: favCat
        val favTask = done.groupingBy { it.taskTitle }.eachCount().maxByOrNull { it.value }?.key
        upsertMemory("AUTO_FAVCAT", "Lieblingskategorie: $name")
        if (favTask != null) upsertMemory("AUTO_FAVTASK", "Häufigste Aufgabe: $favTask")
    }

    suspend fun upsertMemory(kind: String, content: String) {
        val existing = ai.memoriesNow().firstOrNull { it.kind == kind }
        if (existing == null) ai.save(AiMemory(kind = kind, content = content))
        else if (existing.content != content) ai.save(existing.copy(content = content, updatedAt = now()))
    }

    suspend fun bumpCounter(key: String, delta: Int, note: String = "") {
        val counter = s.counter(key) ?: return
        s.save(counter.copy(value = (counter.value + delta).coerceAtLeast(0)))
        s.insert(CounterEntry(counterId = counter.id, delta = delta, note = note))
    }

    suspend fun checkAchievements() {
        val all = s.achievementsNow()
        val have = s.unlockedNow().map { it.achievementId }.toSet()
        val done = t.doneCount()
        val level = Leveling.levelFor(profile().xp)
        val best = bestChastityMs()
        val conditions = mapOf(
            "first_task" to (done >= 1), "ten_tasks" to (done >= 10), "fifty_tasks" to (done >= 50), "hundred_tasks" to (done >= 100),
            "first_dungeon" to (d.completedCount() >= 1),
            "chastity_1d" to (best >= TimeUtil.DAY), "chastity_7d" to (best >= 7 * TimeUtil.DAY), "chastity_30d" to (best >= 30 * TimeUtil.DAY),
            "fitness_10" to (s.fitnessNow().size >= 10),
            "pet_first" to (db.petDao().sessionCount() >= 1),
            "toy_first" to (db.toyDao().toysNow().isNotEmpty()),
            "partner_first" to (t.doneCount("partner") >= 1),
            "custom_task" to (t.tasksNow().any { it.custom }),
            "fetish_profile" to (f.prefsNow().count { it.level != "NONE" } >= 5),
            "level_5" to (level >= 5), "level_10" to (level >= 10), "level_20" to (level >= 20), "level_50" to (level >= 50)
        )
        var bonus = 0
        all.forEach { a ->
            if (a.id !in have && conditions[a.key] == true) {
                s.insert(UserAchievement(achievementId = a.id))
                emit("ACH", "${a.emoji} Erfolg freigeschaltet: ${a.name}")
                bonus += a.xpReward
            }
        }
        if (bonus > 0) {
            val profile = profile()
            val nx = profile.xp + bonus
            p.save(profile.copy(xp = nx, dungeonLevel = Leveling.levelFor(nx)))
        }
    }

    // ---------------- Keuschheit ----------------
    fun chastityElapsed(cs: ChastitySession, at: Long = now()): Long {
        val end = when {
            cs.endedAt > 0 -> cs.endedAt
            cs.pausedAt > 0 -> cs.pausedAt
            else -> at
        }
        return (end - cs.startedAt - cs.pausedTotalMs).coerceAtLeast(0)
    }

    suspend fun bestChastityMs(): Long = c.allNow().maxOfOrNull { chastityElapsed(it) } ?: 0L

    suspend fun startChastity(goalDays: Int, goalHours: Int, note: String) {
        if (c.activeNow() != null) return
        val goalMs = goalDays * TimeUtil.DAY + goalHours * 3_600_000L
        c.insert(ChastitySession(goalAt = if (goalMs > 0) now() + goalMs else 0, note = note))
    }

    suspend fun pauseChastity() { c.activeNow()?.let { if (it.pausedAt == 0L) c.update(it.copy(pausedAt = now(), status = "PAUSED")) } }

    suspend fun resumeChastity() {
        c.activeNow()?.let {
            if (it.pausedAt > 0) {
                val gap = now() - it.pausedAt
                c.update(it.copy(pausedAt = 0, pausedTotalMs = it.pausedTotalMs + gap, goalAt = if (it.goalAt > 0) it.goalAt + gap else 0, status = "ACTIVE"))
            }
        }
    }

    /** Beenden ist jederzeit möglich und wird nie bestraft. */
    suspend fun endChastity() {
        val cs = c.activeNow() ?: return
        val previousBest = c.allNow().filter { it.id != cs.id }.maxOfOrNull { chastityElapsed(it) } ?: 0L
        val ended = cs.copy(endedAt = now(), status = "ENDED")
        c.update(ended)
        val elapsed = chastityElapsed(ended)
        val days = (elapsed / TimeUtil.DAY).toInt()
        if (days > 0) bumpCounter("chastity_days", days, "Keuschheitsperiode")
        grantXp((days * 20 + (elapsed % TimeUtil.DAY / 3_600_000L).toInt()).coerceAtMost(2000), "Keuschheitsperiode")
        if (elapsed > previousBest && previousBest > 0) { bumpCounter("records", 1, "Keuschheitsrekord"); emit("RECORD", "Neuer Keuschheitsrekord: ${TimeUtil.duration(elapsed)}") }
        checkAchievements()
    }

    /** Nur auf ausdrücklichen Wunsch des Nutzers – die KI darf das nie selbst auslösen. */
    suspend fun extendChastityGoal(hours: Int) {
        c.activeNow()?.let { cs -> c.update(cs.copy(goalAt = (if (cs.goalAt > 0) cs.goalAt else now()) + hours * 3_600_000L, goalNotified = false)) }
    }

    // ---------------- 24-Stunden-Dungeon ----------------
    suspend fun startDungeon(difficulty: Int, categories: Set<String>, totalTasks: Int): Int {
        if (d.activeNow() != null) return 0
        val ctx = engineContext().copy(maxDifficulty = difficulty)
        val pool = t.tasksNow().filter { it.enabled && it.dungeonEnabled && it.categoryKey in categories && !it.partnerRequired }.map { it.toProposal() }
        val start = now()
        val plan = DungeonPlanner.plan(pool, start, difficulty, totalTasks, ctx, { TimeUtil.minuteOfDay(it) })
        if (plan.isEmpty()) return 0
        db.withTransaction {
            val sid = d.insert(DungeonSession(startedAt = start, endsAt = start + 24 * DungeonPlanner.HOUR, difficulty = difficulty, personaId = prefs().activePersonaId))
            d.insertPhases(DungeonPlanner.PHASES.mapIndexed { i, name -> DungeonPhase(sessionId = sid, idx = i, name = name, startHour = i * 6, endHour = (i + 1) * 6) })
            d.insertChallenges(plan.map {
                DungeonChallenge(sessionId = sid, phaseIdx = it.phaseIdx, taskId = it.proposal.taskId, title = it.proposal.title, description = it.proposal.description,
                    categoryKey = it.proposal.categoryKey, durationMin = it.proposal.durationMin, xp = it.proposal.xp, scheduledAt = it.scheduledAt)
            })
        }
        return plan.size
    }

    suspend fun dungeonChallenge(ch: DungeonChallenge, status: String) {
        d.update(ch.copy(status = status))
        recordTask(ch.title, ch.categoryKey, ch.xp, status, "DUNGEON", ch.taskId)
    }

    suspend fun pauseDungeon() { d.activeNow()?.let { if (it.pausedAt == 0L) d.update(it.copy(pausedAt = now())) } }

    suspend fun resumeDungeon() {
        val ds = d.activeNow() ?: return
        if (ds.pausedAt == 0L) return
        val gap = now() - ds.pausedAt
        d.update(ds.copy(pausedAt = 0, startedAt = ds.startedAt + gap, endsAt = ds.endsAt + gap))
        d.shift(ds.id, gap)
    }

    /** Abbruch ohne Strafe. Bereits verdiente XP bleiben erhalten. */
    suspend fun abortDungeon() { d.activeNow()?.let { d.update(it.copy(status = "ABORTED", pausedAt = 0)) } }

    /** Prüft Phasen und Ende. Liefert Meldungen für Benachrichtigungen. */
    suspend fun tickDungeon(): List<Pair<String, String>> {
        val ds = d.activeNow() ?: return emptyList()
        if (ds.pausedAt > 0) return emptyList()
        val out = ArrayList<Pair<String, String>>()
        val t0 = now()
        d.phasesNow(ds.id).forEach { ph ->
            val end = ds.startedAt + ph.endHour * DungeonPlanner.HOUR
            if (!ph.completed && t0 >= end) {
                d.update(ph.copy(completed = true, notified = true))
                out += "PHASE" to "Phase „${ph.name}“ abgeschlossen"
            }
        }
        d.challengesNow(ds.id).forEach { ch ->
            if (ch.status == "PENDING" && !ch.notified && t0 >= ch.scheduledAt) {
                d.update(ch.copy(notified = true))
                out += "CHALLENGE" to "Dungeon-Aufgabe verfügbar: ${ch.title}"
            }
        }
        if (t0 >= ds.endsAt) {
            d.update(ds.copy(status = "COMPLETED"))
            val doneCount = d.challengesNow(ds.id).count { it.status == "DONE" }
            grantXp(50 + ds.difficulty * 10 + doneCount * 5, "Dungeon abgeschlossen")
            checkAchievements()
            out += "PHASE" to "Dein 24-Stunden-Dungeon ist abgeschlossen"
        }
        return out
    }

    // ---------------- Safeword ----------------
    suspend fun safewordStop() {
        abortDungeon()
        ai.expireOffers()
        pauseChastity()
        db.statsDao().save(SessionEntry(kind = "SAFEWORD", title = "Safeword benutzt", note = "Alles gestoppt. Keine Strafe."))
        emit("SAFEWORD", "Alles gestoppt. Nimm dir Zeit für Aftercare.")
    }

    // ---------------- KI ----------------
    suspend fun aiContext(persona: AiPersona? = null): AiContext {
        val per = persona ?: activePersona()
        val profile = profile(); val cons = consent()
        val level = Leveling.levelFor(profile.xp)
        val done = t.doneCount()
        val week = t.doneSince(now() - 7 * TimeUtil.DAY)
        val cs = c.activeNow()
        val progress = buildString {
            append("Level $level (${Leveling.titleFor(level)}), $done Aufgaben insgesamt, $week in den letzten 7 Tagen.")
            if (cs != null) append(" Keuschheit läuft seit ${TimeUtil.duration(chastityElapsed(cs))}.")
        }
        val fetishNames = if (cons.shareFetishes) {
            val prefs = f.prefsNow().associateBy { it.fetishId }
            f.categoriesNow().filter { !it.risky && prefs[it.id]?.level in setOf("LIKE", "LOVE", "WANT_TRY", "INTERESTED") && (prefs[it.id]?.dungeonEnabled == true || prefs[it.id]?.rouletteEnabled == true) }.map { it.name }
        } else emptyList()
        val pet = db.petDao().petsNow().firstOrNull { it.active }
        return AiContext(
            persona = per,
            userName = profile.username,
            userSalutation = profile.salutation,
            userPronouns = if (cons.shareProfile) profile.pronouns else "",
            userRole = if (cons.shareProfile) profile.role else "",
            level = level, levelTitle = Leveling.titleFor(level),
            progressLine = progress,
            limits = if (cons.shareLimits) blockedWords().joinToString(", ") else "",
            likes = if (cons.shareProfile) profile.likes else "",
            fetishes = fetishNames,
            memories = if (cons.memoryEnabled) ai.memoriesNow().take(12).map { it.content } else emptyList(),
            categories = Seed.CATEGORIES.map { it.key }.filter { it !in prefs().excludedCategories.split(",") },
            petRole = pet?.let { "${it.name} (${it.role})" } ?: ""
        )
    }

    /** Für externe Server: nur ausdrücklich freigegebene Informationen. */
    private suspend fun remoteContext(ctx: AiContext): AiContext {
        val cons = consent()
        return ctx.copy(
            userName = if (cons.shareProfile) ctx.userName else "",
            progressLine = if (cons.shareProgress) ctx.progressLine else "",
            petRole = if (cons.shareProfile) ctx.petRole else ""
        )
    }

    private fun isLocalHost(url: String): Boolean {
        val host = try { java.net.URI(url).host ?: "" } catch (_: Exception) { "" }
        return host == "localhost" || host == "127.0.0.1" || host == "::1"
    }

    suspend fun engine(): Pair<AiEngine, Boolean> {
        val prefs = prefs(); val cons = consent()
        val remoteAllowed = when (prefs.aiMode) {
            "LOCAL" -> prefs.aiBaseUrl.isNotBlank() && (isLocalHost(prefs.aiBaseUrl) || cons.cloudAiConsent)
            "CLOUD" -> prefs.aiBaseUrl.isNotBlank() && cons.cloudAiConsent
            else -> false
        }
        return if (remoteAllowed) RemoteEngine(prefs.aiBaseUrl, prefs.aiModel.ifBlank { "default" }, secure.apiKey(), rules) to true
        else rules to false
    }

    suspend fun conversation(personaId: Long): AiConversation {
        ai.conversationFor(personaId)?.let { return it }
        val persona = ai.persona(personaId) ?: activePersona()
        val id = ai.insert(AiConversation(personaId = personaId, title = persona.name))
        val greeting = persona.greeting.ifBlank { rules.comment(aiContext(persona), "greet") }
        ai.insert(AiMessage(conversationId = id, fromUser = false, text = greeting))
        return ai.conversationFor(personaId)!!
    }

    private suspend fun offerMessage(cid: Long, text: String, pr: Proposal?): Long = ai.insert(
        if (pr == null) AiMessage(conversationId = cid, fromUser = false, text = text)
        else AiMessage(conversationId = cid, fromUser = false, text = text, offerTitle = pr.title, offerDescription = pr.description,
            offerCategory = pr.categoryKey, offerDuration = pr.durationMin, offerXp = pr.xp, offerTaskId = pr.taskId, offerState = "OPEN")
    )

    /** Verarbeitet eine Chatnachricht. Liefert den Antworttext (für Sprachausgabe). */
    suspend fun sendChat(text: String): String {
        val persona = activePersona()
        val conv = conversation(persona.id)
        ai.insert(AiMessage(conversationId = conv.id, fromUser = true, text = text))
        val prefs = prefs(); val cons = consent(); val profile = profile()

        // Sicherheit geht immer vor und wird lokal entschieden – nie von einem KI-Modell.
        if (Safety.hasSafeword(text, prefs.safeword)) {
            safewordStop()
            val r = Safety.safewordReply(); ai.insert(AiMessage(conversationId = conv.id, fromUser = false, text = r)); return r
        }
        if (Safety.hasDiscomfort(text)) {
            ai.expireOffers()
            val r = Safety.discomfortReply(profile.username); ai.insert(AiMessage(conversationId = conv.id, fromUser = false, text = r)); return r
        }
        if (!cons.aiEnabled) {
            val r = "Die KI ist in den Datenschutz-Einstellungen deaktiviert. Roulette, Dungeon und Tracker funktionieren weiterhin."
            ai.insert(AiMessage(conversationId = conv.id, fromUser = false, text = r)); return r
        }
        val lower = text.lowercase()
        if (cons.memoryEnabled && (lower.startsWith("merk dir") || lower.startsWith("merke dir"))) {
            val content = text.substringAfter("dir").trim().trimStart(',', ':').trim()
            if (content.isNotBlank()) ai.save(AiMemory(kind = "USER", content = content))
            val r = "Notiert. Du kannst alle Erinnerungen jederzeit unter Datenschutz ansehen, ändern und löschen."
            ai.insert(AiMessage(conversationId = conv.id, fromUser = false, text = r)); return r
        }
        val intent = IntentDetector.detect(text)
        val candidate = if (intent == Intent.TASK) pickTask(enforceQuota = true) else null
        val ctx = aiContext(persona)
        val (engine, remote) = engine()
        val history = ai.lastMessages(conv.id, 14).reversed().dropLast(1).map { it.fromUser to it.text }
        val reply = engine.reply(if (remote) remoteContext(ctx) else ctx, history, text, intent, candidate)

        // Jede KI-Idee läuft durch die Challenge-Engine, bevor sie angezeigt wird.
        var proposal = reply.proposal
        var replyText = reply.text
        if (proposal != null && proposal.fromAi) {
            when (val v = ChallengeEngine.check(proposal, engineContext(enforceQuota = true))) {
                is Verdict.Approved -> proposal = v.proposal
                is Verdict.Rejected -> {
                    proposal = candidate ?: pickTask(enforceQuota = true)
                    replyText = if (proposal != null) "Mein erster Einfall passte nicht zu deinen Regeln (${v.reason}). Nimm stattdessen diese Aufgabe."
                    else "Mein Einfall passte nicht zu deinen Regeln (${v.reason}), und gerade ist keine andere Aufgabe möglich."
                }
            }
        }
        if (Safety.hasDiscomfort(replyText).not() && ChallengeEngine.HARD_BLOCK.any { replyText.lowercase().contains(it) }) {
            replyText = rules.comment(ctx, "small")
        }
        offerMessage(conv.id, replyText, proposal)
        return replyText
    }

    suspend fun respondToOffer(messageId: Long, action: String): String {
        val m = ai.message(messageId) ?: return ""
        val ctx = aiContext()
        val text: String
        when (action) {
            "ACCEPT" -> { if (m.offerState != "OPEN") return ""; ai.update(m.copy(offerState = "ACCEPTED")); return "" }
            "DONE" -> {
                if (m.offerState != "ACCEPTED" && m.offerState != "OPEN") return ""
                ai.update(m.copy(offerState = "DONE"))
                recordTask(m.offerTitle, m.offerCategory, m.offerXp, "DONE", "KI", m.offerTaskId)
                text = rules.comment(ctx, "done")
            }
            "DECLINE", "ABORT" -> {
                if (m.offerState != "ACCEPTED" && m.offerState != "OPEN") return ""
                ai.update(m.copy(offerState = if (action == "DECLINE") "DECLINED" else "ABORTED"))
                recordTask(m.offerTitle, m.offerCategory, 0, if (action == "DECLINE") "DECLINED" else "ABORTED", "KI", m.offerTaskId)
                text = rules.comment(ctx, "declined")
            }
            else -> return ""
        }
        ai.insert(AiMessage(conversationId = m.conversationId, fromUser = false, text = text))
        return text
    }

    /** Spontane KI-Aufgabe (Hintergrund). Liefert den Benachrichtigungstext oder null. */
    suspend fun offerSpontaneous(): String? {
        val n = notif(); val cons = consent()
        if (!cons.aiEnabled || !n.spontaneous) return null
        val types = n.taskTypes.split(",").filter { it.isNotBlank() }.toSet()
        if (types.isEmpty()) return null
        val proposal = pickTask(categories = types, enforceQuota = true, enforceQuiet = true) ?: return null
        val persona = activePersona()
        val ctx = aiContext(persona)
        val conv = conversation(persona.id)
        val text = if (n.surprise) "Überraschung! " + rules.comment(ctx, "offer") else rules.comment(ctx, "offer")
        offerMessage(conv.id, text, proposal)
        return if (n.discreet) "Du hast eine neue Nachricht." else rules.notificationText(ctx)
    }

    suspend fun dashboardGreeting(): String {
        val ctx = aiContext()
        val cs = c.activeNow()
        val ds = d.activeNow()
        return when {
            ds != null -> "Dein Dungeon läuft. " + rules.comment(ctx, "small")
            cs != null && cs.goalAt > 0 && now() >= cs.goalAt -> "Du hast dein Ziel erreicht. Willst du freiwillig noch einen Tag versuchen?"
            else -> rules.comment(ctx, "greet")
        }
    }

    // ---------------- Export, Import, Löschen ----------------
    suspend fun exportTasks(onlyCustom: Boolean): String = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        t.tasksNow().filter { !onlyCustom || it.custom }.forEach { arr.put(taskJson(it)) }
        JSONObject().put("format", "lockeddesire-tasks").put("version", 1).put("tasks", arr).toString(2)
    }

    private fun taskJson(x: Task) = JSONObject()
        .put("title", x.title).put("description", x.description).put("category", x.categoryKey).put("difficulty", x.difficulty)
        .put("minutes", x.durationMin).put("xp", x.xp).put("timer", x.hasTimer).put("partner", x.partnerRequired)
        .put("roulette", x.rouletteEnabled).put("dungeon", x.dungeonEnabled).put("tag", x.fetishKey).put("load", x.physicalLoad)
        .put("joints", x.jointLoad).put("reps", x.reps).put("limits", x.limitsNote).put("safety", x.safetyTag)

    /** Importiert ein Aufgabenpaket. Jede Aufgabe muss die Sicherheitsprüfung bestehen. */
    suspend fun importTasks(json: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val arr = JSONObject(json).getJSONArray("tasks")
        val existing = t.tasksNow().map { it.title }.toSet()
        var ok = 0; var rejected = 0
        val ctx = engineContext().copy(maxDifficulty = 6, partnerAvailable = true)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val task = Task(
                title = o.optString("title").take(80), description = o.optString("description").take(800),
                categoryKey = o.optString("category", "custom").let { if (it in Seed.CATEGORY_KEYS) it else "custom" },
                difficulty = o.optInt("difficulty", 1).coerceIn(1, 6), durationMin = o.optInt("minutes", 5).coerceIn(0, 180),
                xp = o.optInt("xp", 10).coerceIn(0, 100), hasTimer = o.optBoolean("timer"), partnerRequired = o.optBoolean("partner"),
                rouletteEnabled = o.optBoolean("roulette", true), dungeonEnabled = o.optBoolean("dungeon", true), fetishKey = o.optString("tag"),
                physicalLoad = o.optInt("load", 0), jointLoad = o.optBoolean("joints"), reps = o.optInt("reps", 0),
                limitsNote = o.optString("limits"), safetyTag = o.optString("safety", "SAFE"), custom = true
            )
            val hard = ChallengeEngine.HARD_BLOCK.any { (task.title + " " + task.description).lowercase().contains(it) }
            if (task.title.isBlank() || task.title in existing || hard || !ChallengeEngine.approved(task.toProposal(), ctx.copy(fitnessMaxReps = 1000, fitnessMaxMinutes = 180, jointFriendly = false, excludedTags = emptySet(), blockedWords = emptyList(), excludedCategories = emptySet()))) rejected++
            else { t.insert(task); ok++ }
        }
        checkAchievements()
        ok to rejected
    }

    suspend fun exportAll(password: String): ByteArray = withContext(Dispatchers.IO) {
        val o = JSONObject().put("format", "lockeddesire-export").put("version", 1).put("createdAt", now())
        val pr = profile()
        o.put("profile", JSONObject().put("username", pr.username).put("avatar", pr.avatar).put("gender", pr.gender).put("pronouns", pr.pronouns)
            .put("salutation", pr.salutation).put("orientation", pr.orientation).put("role", pr.role).put("likes", pr.likes)
            .put("limits", pr.limitsText).put("difficulty", pr.difficulty).put("xp", pr.xp).put("goals", pr.goals))
        o.put("tasks", JSONArray().apply { t.tasksNow().filter { it.custom }.forEach { put(taskJson(it)) } })
        o.put("completions", JSONArray().apply { t.completionsSince(0).forEach { put(JSONObject().put("title", it.taskTitle).put("category", it.categoryKey).put("status", it.status).put("xp", it.xp).put("source", it.source).put("at", it.createdAt)) } })
        o.put("chastity", JSONArray().apply { c.allNow().forEach { put(JSONObject().put("start", it.startedAt).put("goal", it.goalAt).put("end", it.endedAt).put("pausedMs", it.pausedTotalMs).put("note", it.note).put("status", it.status)) } })
        o.put("counters", JSONArray().apply { s.countersNow().forEach { put(JSONObject().put("key", it.key).put("name", it.name).put("value", it.value)) } })
        o.put("fitness", JSONArray().apply { s.fitnessNow().forEach { put(JSONObject().put("name", it.name).put("reps", it.reps).put("sec", it.durationSec).put("at", it.createdAt)) } })
        o.put("journal", JSONArray().apply { s.journalNow().forEach { put(JSONObject().put("kind", it.kind).put("title", it.title).put("min", it.durationMin).put("note", it.note).put("at", it.createdAt)) } })
        o.put("memories", JSONArray().apply { ai.memoriesNow().forEach { put(JSONObject().put("kind", it.kind).put("content", it.content)) } })
        o.put("toys", JSONArray().apply { db.toyDao().toysNow().forEach { put(JSONObject().put("name", it.name).put("category", it.category).put("size", it.sizeClass).put("material", it.material).put("measures", it.measures).put("note", it.note).put("favorite", it.favorite)) } })
        val cats = f.categoriesNow().associateBy { it.id }
        o.put("fetishes", JSONArray().apply { f.prefsNow().forEach { put(JSONObject().put("key", cats[it.fetishId]?.key ?: "").put("level", it.level).put("note", it.note).put("limits", it.limits)) } })
        Crypto.encryptWithPassword(o.toString().toByteArray(), password)
    }

    /** Vollständige Datenlöschung: Datenbank, Fotos, Sperre, Schlüssel. */
    suspend fun wipeEverything() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        Crypto.deleteAllPhotos(context)
        secure.clearAll()
        java.io.File(context.cacheDir, "camera").deleteRecursively()
        ensureSeeded()
    }
}
