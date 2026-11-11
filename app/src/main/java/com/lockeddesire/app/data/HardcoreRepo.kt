package com.lockeddesire.app.data

import android.content.Context
import com.lockeddesire.app.ai.HardcoreVoice
import com.lockeddesire.app.engine.Composed
import com.lockeddesire.app.engine.Hardcore
import com.lockeddesire.app.engine.HardcoreEngine
import com.lockeddesire.app.engine.HcContext
import com.lockeddesire.app.engine.HcStats
import com.lockeddesire.app.engine.Proposal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ergebnis einer abgeschlossenen Prüfung für den Ergebnisbildschirm. */
data class HcResult(val run: HardcoreRun, val status: String, val xp: Int, val verdict: String)

/**
 * Repository des Hardcore-Dungeons. Jede Prüfung entsteht ausschließlich aus Aufgaben,
 * die die Hardcore-Regeln und die zentrale Challenge-Engine bestanden haben.
 */
class HardcoreRepo(private val repo: Repo, private val context: Context) {
    private val dao get() = repo.db.hardcoreDao()

    suspend fun seed() = withContext(Dispatchers.IO) {
        if (dao.settingsNow() == null) dao.save(HardcoreSettings())
        val parsed = HardcoreEngine.parse(context.assets.open("hardcore_tasks.txt").bufferedReader().use { it.readText() })
        if (dao.taskCount() < parsed.size) dao.insertTasks(parsed)
    }

    suspend fun settings(): HardcoreSettings = dao.settingsNow() ?: HardcoreSettings()
    suspend fun saveSettings(s: HardcoreSettings) = dao.save(s.copy(updatedAt = now()))

    suspend fun persona(): AiPersona {
        val s = settings()
        val all = repo.db.aiDao().personasNow()
        return all.firstOrNull { it.id == s.personaId }
            ?: all.firstOrNull { it.personality in HardcoreVoice.PERSONALITIES }
            ?: repo.activePersona()
    }

    private suspend fun voice(kind: String, persona: AiPersona? = null): String {
        val p = persona ?: persona()
        val prof = repo.profile()
        val st = stats()
        return HardcoreVoice.line(p.personality, kind, prof.salutation.ifBlank { prof.username },
            "${st.passed} bestanden, ${st.failed} nicht bestanden, ${st.aborted} abgebrochen, höchste Stufe ${st.highestPassed}.")
    }

    suspend fun enterLine(): String = voice("hc_enter")

    private suspend fun excludedFetishes(): Set<String> {
        val f = repo.db.fetishDao()
        val prefs = f.prefsNow().associateBy { it.fetishId }
        return f.categoriesNow().filter { prefs[it.id]?.level == "EXCLUDED" }.map { it.key }.toSet()
    }

    suspend fun partnerAvailable(): Boolean = repo.db.profileDao().partnersNow().any { it.consentGiven }

    suspend fun context(difficulty: Int? = null, partner: Boolean? = null): HcContext {
        val s = settings()
        val toys = repo.db.toyDao().toysNow().filter { it.rouletteEnabled }
        val p = partner ?: partnerAvailable()
        val base = repo.engineContext(partner = p)
        return HcContext(
            enabled = s.categories.split(",").filter { it.isNotBlank() }.toSet(),
            maxDifficulty = difficulty ?: s.difficulty,
            anatomy = s.anatomy.split(",").filter { it.isNotBlank() }.toSet(),
            role = repo.profile().role,
            partnerAvailable = p,
            toyCategories = toys.map { it.category }.toSet(),
            excludedFetishes = excludedFetishes(),
            base = base.copy(allowedCategories = base.allowedCategories + Hardcore.KEYS, maxDifficulty = 6)
        )
    }

    private suspend fun pickToy(category: String): Toy? {
        if (category.isBlank()) return null
        val toys = repo.db.toyDao().toysNow().filter { it.rouletteEnabled && it.category == category }
        if (toys.isEmpty()) return null
        val fav = toys.filter { it.favorite }
        return (if (fav.isNotEmpty() && Math.random() < 0.5) fav else toys).random()
    }

    private suspend fun recentCodes(): Set<String> =
        dao.runsNow().take(30).flatMap { it.code.split(",") }.toSet()

    private suspend fun store(c: Composed, source: String): HardcoreRun {
        val toy = pickToy(c.toyCategory)
        val persona = persona()
        val run = HardcoreRun(
            taskIds = c.steps.joinToString(",") { it.id.toString() },
            code = c.steps.joinToString(",") { it.code },
            title = HardcoreEngine.withToy(c.title, toy?.name ?: ""),
            description = HardcoreEngine.withToy(c.description, toy?.name ?: ""),
            categoryKey = c.categoryKey,
            categories = c.categories.joinToString(","),
            difficulty = c.difficulty, xp = c.xp, durationMin = c.durationMin,
            stepsTotal = c.steps.size, toyId = toy?.id ?: 0, toyName = toy?.name ?: "",
            partner = c.steps.any { it.partnerRequired }, personaId = persona.id, source = source
        )
        val id = dao.insert(run)
        return run.copy(id = id)
    }

    /** NEUE PRÜFUNG: verwirft eine nur angebotene Prüfung und wählt eine neue. Laufende Prüfungen bleiben. */
    suspend fun newTrial(source: String = "HARDCORE", preferCategory: String? = null, difficulty: Int? = null): HardcoreRun? {
        dao.openNow()?.let { open ->
            if (open.status != Hardcore.AVAILABLE) return open
            dao.discardAvailable(open.id)
        }
        val ctx = context(difficulty)
        val c = HardcoreEngine.compose(dao.tasksNow(), ctx, ctx.maxDifficulty, recentCodes(), preferCategory) ?: return null
        return store(c, source)
    }

    suspend fun offerLine(): String = voice("hc_offer")
    suspend fun noneLine(): String = voice("hc_none")

    suspend fun accept(id: Long): String {
        val r = dao.run(id) ?: return ""
        if (r.status != Hardcore.AVAILABLE) return ""
        dao.update(r.copy(status = Hardcore.ACCEPTED, acceptedAt = now()))
        return voice("hc_accept")
    }

    suspend fun start(id: Long) {
        val r = dao.run(id) ?: return
        if (r.status == Hardcore.ACCEPTED) dao.update(r.copy(status = Hardcore.ACTIVE, startedAt = now()))
    }

    suspend fun step(id: Long, done: Boolean) {
        val r = dao.run(id) ?: return
        if (r.status != Hardcore.ACTIVE) return
        dao.update(r.copy(stepsDone = (r.stepsDone + if (done) 1 else -1).coerceIn(0, r.stepsTotal)))
    }

    /** BESTANDEN, NICHT BESTANDEN oder ABGEBROCHEN. Nur BESTANDEN bringt XP, nie gibt es Abzüge. */
    suspend fun finish(id: Long, status: String): HcResult? {
        val r = dao.run(id) ?: return null
        if (status !in setOf(Hardcore.PASSED, Hardcore.FAILED, Hardcore.ABORTED)) return null
        if (r.status !in setOf(Hardcore.ACCEPTED, Hardcore.ACTIVE)) return null
        if (status != Hardcore.ABORTED && r.status != Hardcore.ACTIVE) return null
        val persona = repo.db.aiDao().persona(r.personaId) ?: persona()
        val verdict = voice(when (status) { Hardcore.PASSED -> "hc_pass"; Hardcore.FAILED -> "hc_fail"; else -> "hc_abort" }, persona)
        val ended = r.copy(status = status, endedAt = now(), verdict = verdict,
            stepsDone = if (status == Hardcore.PASSED) r.stepsTotal else r.stepsDone)
        dao.update(ended)
        val xp = HardcoreEngine.xpFor(status, ended)
        repo.db.taskDao().insert(TaskCompletion(taskTitle = ended.title.take(120), categoryKey = ended.categoryKey,
            status = when (status) { Hardcore.PASSED -> "DONE"; Hardcore.FAILED -> "FAILED"; else -> "ABORTED" }, xp = xp, source = "HARDCORE"))
        if (status == Hardcore.PASSED) {
            repo.bumpCounter("bdsm_sessions", 1, "Hardcore: ${ended.title.take(40)}")
            if ("hc_impact" in ended.categories) repo.bumpCounter("spanking_sessions", 1)
            if ("hc_pet" in ended.categories) repo.bumpCounter("pet_sessions", 1)
            repo.bumpCounter("sub_roleplay", 1)
            if (ended.toyId > 0) {
                repo.db.toyDao().insert(ToySession(toyId = ended.toyId, note = "Hardcore: ${ended.title.take(60)}", durationMin = ended.durationMin))
                repo.db.toyDao().toysNow().firstOrNull { it.id == ended.toyId }?.let { repo.db.toyDao().save(it.copy(lastUsedAt = now())) }
                repo.bumpCounter("toys_used", 1)
            }
            repo.grantXp(xp, "Prüfung bestanden")
        }
        repo.checkAchievements()
        // Die Persona kommentiert das Ergebnis auch im Chat
        try {
            val conv = repo.conversation(persona.id)
            repo.db.aiDao().insert(AiMessage(conversationId = conv.id, fromUser = false, text = "⛓ ${Hardcore.statusLabel(status)}: ${ended.title.take(80)}. $verdict"))
        } catch (_: Exception) { }
        return HcResult(ended, status, xp, verdict)
    }

    /** Safeword, Beschwerden: alles Offene sofort beenden, ohne Strafe. */
    suspend fun stopAll() {
        dao.abortAllOpen(now(), "Gestoppt. Keine Strafe, kein Abzug.")
        dao.discardAllAvailable()
    }

    suspend fun stats(): HcStats = HardcoreEngine.stats(dao.runsNow()) { TimeUtil.dayNumber(it) }

    /** Für den 24-Stunden-Dungeon: freigegebene Hardcore-Aufgaben als Vorschläge. */
    suspend fun dungeonPool(maxDifficulty: Int): List<Proposal> {
        val s = settings()
        if (!s.confirmed || !s.includeIn24h) return emptyList()
        val ctx = context(minOf(maxDifficulty, s.difficulty), partner = false)
        val toys = repo.db.toyDao().toysNow().filter { it.rouletteEnabled }
        return dao.tasksNow().filter { !it.partnerRequired && HardcoreEngine.reject(it, ctx) == null }.map { t ->
            val toy = toys.filter { it.category == t.toyCategory }.randomOrNull()
            Hardcore.toProposal(t, ctx.maxDifficulty).copy(
                description = HardcoreEngine.withToy(HardcoreEngine.fill(t.description, t, ctx.maxDifficulty), toy?.name ?: ""),
                title = "⛓ " + t.title
            )
        }
    }

    /** Ergebnis einer Hardcore-Aufgabe aus dem 24-Stunden-Dungeon. */
    suspend fun recordDungeon(ch: DungeonChallenge, status: String) {
        val mapped = when (status) { "DONE" -> Hardcore.PASSED; "FAILED" -> Hardcore.FAILED; "ABORTED" -> Hardcore.ABORTED; else -> return }
        val task = dao.tasksNow().firstOrNull { it.id == ch.taskId }
        val persona = persona()
        val verdict = HardcoreVoice.line(persona.personality, when (mapped) { Hardcore.PASSED -> "hc_pass"; Hardcore.FAILED -> "hc_fail"; else -> "hc_abort" }, repo.profile().salutation)
        val xp = if (mapped == Hardcore.PASSED) ch.xp else 0
        dao.insert(HardcoreRun(taskIds = ch.taskId.toString(), code = task?.code ?: "", title = ch.title.removePrefix("⛓ "), description = ch.description,
            categoryKey = ch.categoryKey, categories = ch.categoryKey, difficulty = task?.difficulty ?: 1, xp = ch.xp, durationMin = ch.durationMin,
            stepsTotal = 1, stepsDone = if (mapped == Hardcore.PASSED) 1 else 0, personaId = persona.id, source = "DUNGEON24",
            status = mapped, verdict = verdict, acceptedAt = now(), startedAt = now(), endedAt = now()))
        repo.db.taskDao().insert(TaskCompletion(taskId = ch.taskId, taskTitle = ch.title, categoryKey = ch.categoryKey,
            status = if (mapped == Hardcore.PASSED) "DONE" else status, xp = xp, source = "DUNGEON"))
        if (mapped == Hardcore.PASSED) {
            repo.bumpCounter("dungeon_challenges", 1)
            repo.bumpCounter("bdsm_sessions", 1)
            repo.grantXp(xp, ch.title)
        }
        repo.emit("INFO", verdict)
        repo.checkAchievements()
    }

    /** Chat: Die Persona wählt eine Prüfung. Liefert Antworttext oder null, wenn der Modus nicht freigeschaltet ist. */
    suspend fun chatTrial(conversationId: Long): String {
        val s = settings()
        if (!s.confirmed) {
            val t = "Der Hardcore-Dungeon ist noch verschlossen. Öffne ihn einmal selbst über das Dashboard und bestätige seine Regeln."
            repo.db.aiDao().insert(AiMessage(conversationId = conversationId, fromUser = false, text = t)); return t
        }
        val open = dao.openNow()
        val run = if (open != null && open.status != Hardcore.AVAILABLE) open else newTrial("CHAT")
        if (run == null) {
            val t = noneLine(); repo.db.aiDao().insert(AiMessage(conversationId = conversationId, fromUser = false, text = t)); return t
        }
        val t = if (run.status == Hardcore.AVAILABLE) offerLine() else "Du hast noch eine laufende Prüfung. Beende sie zuerst."
        repo.db.aiDao().insert(AiMessage(conversationId = conversationId, fromUser = false, text = t, offerTitle = run.title.take(120),
            offerDescription = run.description.take(600), offerCategory = "hardcore", offerDuration = run.durationMin, offerXp = run.xp,
            offerTaskId = run.id, offerState = "HC"))
        return t
    }

    /** Spontane Ankündigung im Hintergrund. Liefert Benachrichtigungstext oder null. */
    suspend fun spontaneous(): String? {
        val s = settings()
        if (!s.confirmed || !s.notify || !repo.consent().aiEnabled) return null
        if (dao.openNow() != null) return null
        if (dao.countSince("SPONTAN", TimeUtil.startOfDay()) >= Hardcore.spontaneousPerDay(s.difficulty)) return null
        newTrial("SPONTAN") ?: return null
        return if (repo.notif().discreet) "Du hast eine neue Nachricht." else voice("hc_offer")
    }

    suspend fun achievementFlags(): Map<String, Boolean> {
        val st = stats()
        return mapOf(
            "hc_first" to (st.passed >= 1), "hc_ten" to (st.passed >= 10),
            "hc_series" to (st.longestSeries >= 3), "hc_nightmare" to (st.highestPassed >= 6)
        )
    }
}
