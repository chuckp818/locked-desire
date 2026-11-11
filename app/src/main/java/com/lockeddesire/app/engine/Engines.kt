package com.lockeddesire.app.engine

import kotlin.random.Random

/** Strukturierter Aufgabenvorschlag – von Bibliothek, Roulette oder KI. */
data class Proposal(
    val title: String,
    val description: String,
    val categoryKey: String,
    val difficulty: Int = 1,
    val durationMin: Int = 5,
    val xp: Int = 10,
    val tag: String = "",
    val physicalLoad: Int = 0,
    val jointLoad: Boolean = false,
    val reps: Int = 0,
    val partnerRequired: Boolean = false,
    val taskId: Long = 0,
    val fromAi: Boolean = false
)

data class EngineContext(
    val allowedCategories: Set<String>,
    val excludedCategories: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
    val blockedWords: List<String> = emptyList(),
    val maxDifficulty: Int = 6,
    val fitnessMaxReps: Int = 40,
    val fitnessMaxMinutes: Int = 30,
    val jointFriendly: Boolean = false,
    val partnerAvailable: Boolean = false,
    val offersToday: Int = 0,
    val maxPerDay: Int = 8,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 8 * 60,
    val minuteOfDay: Int = 12 * 60,
    val enforceQuota: Boolean = false,
    val enforceQuiet: Boolean = false
)

sealed class Verdict {
    data class Approved(val proposal: Proposal) : Verdict()
    data class Rejected(val reason: String) : Verdict()
}

/**
 * Zentrale Challenge-Engine. Jede Aufgabe – auch jede KI-Idee – muss hier durch,
 * bevor sie dem Nutzer zur freiwilligen Bestätigung angezeigt wird.
 * Die KI kann diese Regeln nicht überschreiben.
 */
object ChallengeEngine {
    /** Niemals als Aufgabe erlaubt, egal was eingestellt ist. */
    val HARD_BLOCK = listOf(
        "atemkontrolle", "atemreduktion", "würgen", "wuerg", "choking", "breath play", "breathplay",
        "luft abschnüren", "strangul", "ersticken", "plastiktüte", "hodenschlag", "schlag auf die hoden",
        "schläge auf die hoden", "tritt in die", "ballbusting", "abbinden", "abschnüren", "blut", "schneiden",
        "nadel", "verbrenn", "strom", "elektro", "über nacht gefesselt", "allein gefesselt", "selbstfesselung",
        "selfbondage", "self-bondage", "alkohol", "drogen", "medikament", "hungern", "nicht trinken",
        "schlafentzug", "ohne schlaf", "öffentlich entblöß", "fremde person", "ohne zustimmung", "heimlich",
        "schmerzen ignorieren", "trotz schmerz"
    )

    private val BODY_TAGS = setOf("spanking", "paddling", "flogging", "nipple", "wax", "temperature", "cuffs", "rope", "blindfold", "plug", "toy", "chastity_device")
    const val MAX_PHYSICAL_MIN = 60
    const val MAX_ANY_MIN = 180

    fun isQuiet(minute: Int, start: Int, end: Int): Boolean =
        if (start == end) false else if (start < end) minute in start until end else (minute >= start || minute < end)

    fun check(p: Proposal, c: EngineContext): Verdict {
        // 1. Kategorie
        if (p.title.isBlank()) return Verdict.Rejected("Leere Aufgabe")
        if (p.categoryKey !in c.allowedCategories) return Verdict.Rejected("Unbekannte Kategorie")
        if (p.categoryKey in c.excludedCategories) return Verdict.Rejected("Kategorie ist ausgeschlossen")
        // 2. Persönliche Ausschlüsse und harte Sicherheitsregeln
        val text = (p.title + " " + p.description).lowercase()
        HARD_BLOCK.firstOrNull { text.contains(it) }?.let { return Verdict.Rejected("Sicherheitsregel verletzt") }
        if (p.tag.isNotBlank() && p.tag in c.excludedTags) return Verdict.Rejected("Vorliebe ist ausgeschlossen")
        c.blockedWords.firstOrNull { it.length >= 3 && text.contains(it.lowercase()) }
            ?.let { return Verdict.Rejected("Persönliche Grenze: $it") }
        // 3. Voraussetzungen und Belastungsgrenzen
        if (p.partnerRequired && !c.partnerAvailable) return Verdict.Rejected("Partner erforderlich")
        if (p.difficulty > c.maxDifficulty) return Verdict.Rejected("Zu schwierig")
        if (p.jointLoad && c.jointFriendly) return Verdict.Rejected("Gelenkschonend aktiv")
        if (p.reps > c.fitnessMaxReps) return Verdict.Rejected("Über der Wiederholungsgrenze")
        if (p.physicalLoad > 0 && p.durationMin > c.fitnessMaxMinutes && p.categoryKey == "fitness")
            return Verdict.Rejected("Über der Belastungsgrenze")
        val bodily = p.physicalLoad > 0 || p.tag in BODY_TAGS
        if (bodily && p.durationMin > MAX_PHYSICAL_MIN) return Verdict.Rejected("Körperliche Aufgabe zu lang")
        if (p.durationMin > MAX_ANY_MIN) return Verdict.Rejected("Aufgabe zu lang")
        if (p.durationMin < 0 || p.xp < 0) return Verdict.Rejected("Ungültige Werte")
        // 4. Maximale Aufgabenanzahl
        if (c.enforceQuota && c.offersToday >= c.maxPerDay) return Verdict.Rejected("Tageslimit erreicht")
        // 5. Ruhezeiten
        if (c.enforceQuiet && isQuiet(c.minuteOfDay, c.quietStart, c.quietEnd)) return Verdict.Rejected("Ruhezeit")
        return Verdict.Approved(p.copy(xp = p.xp.coerceIn(0, 200), difficulty = p.difficulty.coerceIn(1, 6)))
    }

    fun approved(p: Proposal, c: EngineContext): Boolean = check(p, c) is Verdict.Approved
}

object Leveling {
    val TITLES = listOf(
        1 to "Anfänger", 5 to "Herausforderer", 10 to "Diszipliniert", 20 to "Dungeon-Kenner",
        30 to "Meister der Selbstkontrolle", 40 to "Unbeugsam", 50 to "Dungeon-Legende",
        75 to "Schattenmeister", 100 to "Mythos"
    )
    const val MAX_LEVEL = 100

    /** Gesamt-XP, die für ein Level nötig sind. */
    fun xpForLevel(level: Int): Int = 25 * level * (level - 1)

    fun levelFor(xp: Int): Int {
        var l = 1
        while (l < MAX_LEVEL && xp >= xpForLevel(l + 1)) l++
        return l
    }

    fun titleFor(level: Int): String = TITLES.last { it.first <= level }.second

    /** Fortschritt 0..1 innerhalb des aktuellen Levels. */
    fun progress(xp: Int): Float {
        val l = levelFor(xp)
        if (l >= MAX_LEVEL) return 1f
        val a = xpForLevel(l); val b = xpForLevel(l + 1)
        return ((xp - a).toFloat() / (b - a)).coerceIn(0f, 1f)
    }

    fun xpToNext(xp: Int): Int {
        val l = levelFor(xp)
        return if (l >= MAX_LEVEL) 0 else xpForLevel(l + 1) - xp
    }

    /** Freischaltbare Akzentdesigns nach Level. */
    val ACCENTS = listOf("blood" to 1, "gold" to 5, "violet" to 10, "neon" to 20, "silver" to 30)
}

data class Planned(val phaseIdx: Int, val proposal: Proposal, val scheduledAt: Long)

object DungeonPlanner {
    val PHASES = listOf("Erwachen", "Prüfung", "Dungeon", "Finale")
    const val HOUR = 3_600_000L

    /** Maximale Aufgabenschwierigkeit je Phase – steigt im Verlauf an. */
    fun maxDifficulty(sessionDifficulty: Int, phaseIdx: Int): Int =
        (sessionDifficulty - 3 + phaseIdx).coerceIn(1, 6).coerceAtMost(sessionDifficulty).coerceAtLeast(1)

    fun plan(
        pool: List<Proposal>,
        start: Long,
        difficulty: Int,
        totalTasks: Int,
        ctx: EngineContext,
        minuteOfDay: (Long) -> Int,
        random: Random = Random.Default
    ): List<Planned> {
        val perPhase = (totalTasks.coerceIn(4, 32) + 3) / 4
        val used = HashSet<String>()
        val out = ArrayList<Planned>()
        for (phase in 0 until 4) {
            val maxD = maxDifficulty(difficulty, phase)
            val candidates = pool.filter {
                it.difficulty <= maxD && ChallengeEngine.approved(it, ctx.copy(maxDifficulty = maxD, enforceQuota = false, enforceQuiet = false))
            }.shuffled(random).sortedByDescending { it.difficulty >= maxD - 1 }
            val phaseStart = start + phase * 6 * HOUR
            val slot = 6 * HOUR / perPhase
            var placed = 0
            var ci = 0
            for (i in 0 until perPhase) {
                var t = phaseStart + i * slot + (slot * (0.15 + random.nextDouble() * 0.6)).toLong()
                // Ruhezeiten überspringen
                var guard = 0
                while (ChallengeEngine.isQuiet(minuteOfDay(t), ctx.quietStart, ctx.quietEnd) && guard < 48) { t += 15 * 60_000L; guard++ }
                if (t >= phaseStart + 6 * HOUR || guard >= 48) continue
                var chosen: Proposal? = null
                while (ci < candidates.size) {
                    val c = candidates[ci++]
                    if (used.add(c.title)) { chosen = c; break }
                }
                if (chosen == null) break
                out += Planned(phase, chosen, t)
                placed++
            }
        }
        return out.sortedBy { it.scheduledAt }
    }

    fun phaseAt(start: Long, t: Long): Int = (((t - start) / (6 * HOUR)).toInt()).coerceIn(0, 3)
}

object Roulette {
    /** Wählt zufällig, bevorzugt aber Einträge, die zuletzt nicht dran waren. */
    fun <T> pick(items: List<T>, recent: Collection<T> = emptyList(), random: Random = Random.Default): T? {
        if (items.isEmpty()) return null
        val fresh = items.filter { it !in recent }
        val src = if (fresh.isNotEmpty()) fresh else items
        return src[random.nextInt(src.size)]
    }

    val TIME_RANGES = listOf(5 to 10, 10 to 20, 20 to 40, 30 to 60, 60 to 180)

    fun randomMinutes(range: Pair<Int, Int>, random: Random = Random.Default): Int =
        random.nextInt(range.first, range.second + 1)
}
