package com.lockeddesire.app.engine

import com.lockeddesire.app.data.HardcoreRun
import com.lockeddesire.app.data.HardcoreTask
import kotlin.random.Random

/**
 * Hardcore-Dungeon: Kategorien, Zustände, Prüfregeln und Zusammenstellung von Prüfungsserien.
 * Reine Logik ohne Android-Abhängigkeiten – vollständig per Unit-Test prüfbar.
 */
object Hardcore {
    data class Category(
        val key: String,
        val name: String,
        val emoji: String,
        /** Fetisch-Schlüssel: Ist einer davon ausgeschlossen, ist die Kategorie gesperrt (bei blockAll erst, wenn alle ausgeschlossen sind). */
        val fetishKeys: Set<String>,
        val blockAll: Boolean = false,
        /** Nur Session-Rahmen nach eigener Liste, keine detaillierten Körperanweisungen. */
        val sessionOnly: Boolean = false,
        val safetyNote: String
    )

    val CATEGORIES = listOf(
        Category("hc_ds", "Dominanz & Unterwerfung", "👑", setOf("obedience"), safetyNote = "Kissen für die Knie, Haltungen jederzeit lockern."),
        Category("hc_humiliation", "Humiliation", "🎭", setOf("humiliation"), safetyNote = "Nur privat und innerhalb eurer vereinbarten Worte. Nie vor Unbeteiligten."),
        Category("hc_chastity", "Chastity", "🔒", setOf("chastity"), safetyNote = "Notfallschlüssel immer erreichbar. Bei Druck, Taubheit oder Verfärbung sofort öffnen."),
        Category("hc_orgasm", "Orgasm Control", "⏳", setOf("edging"), safetyNote = "Pausen und Wasser jederzeit. Bei Schmerz sofort aufhören."),
        Category("hc_anal", "Anal Play", "🍑", setOf("analplay"), safetyNote = "Nur Toys mit breitem Fuß, viel Gleitmittel, langsam. Bei Schmerz sofort entfernen. Maximal 30 Minuten."),
        Category("hc_toy", "Toy Play", "🎁", setOf("toyplay"), safetyNote = "Saubere Toys, passendes Gleitmittel, dein Tempo."),
        Category("hc_cbt", "CBT", "⚠", setOf("cbt"), sessionOnly = true, safetyNote = "Nur Session-Rahmen nach deiner eigenen Liste. Keine Schläge auf die Hoden, nichts Einschnürendes, kein starker Druck."),
        Category("hc_pussy", "Pussy Play", "🌹", setOf("pussyplay"), sessionOnly = true, safetyNote = "Nur Session-Rahmen nach deiner eigenen Liste. Kein starker Druck, keine Schläge."),
        Category("hc_nipple", "Nipple Play", "📌", setOf("nipple"), safetyNote = "Klammern vorher am Finger testen, höchstens fünf Minuten am Stück, langsam lösen."),
        Category("hc_bondage", "Bondage", "⛓", setOf("cuffs", "rope"), blockAll = true, safetyNote = "Echte Fesselung nur mit anwesender Partnerperson und Sicherheitsschere. Nie allein gefesselt, nie am Hals."),
        Category("hc_impact", "Impact Play", "✋", setOf("spanking"), safetyNote = "Nur Gesäß und Oberschenkelrückseite. Nie Rücken, Nieren, Gelenke oder Genitalien."),
        Category("hc_pet", "Petplay", "🐾", setOf("puppyplay", "kittenplay", "ponyplay", "bunnyplay", "foxplay"), blockAll = true, safetyNote = "Knieschoner oder Decke. Bei Gelenkbeschwerden aufrecht bleiben.")
    )
    val KEYS: Set<String> = CATEGORIES.map { it.key }.toSet()
    fun category(key: String): Category? = CATEGORIES.firstOrNull { it.key == key }

    val DIFFICULTY_NAMES = listOf("Streng", "Dominant", "Grausam", "Gnadenlos", "Extrem", "Albtraum")
    val DIFFICULTY_TEXT = listOf(
        "Einstieg in den Hardcore-Dungeon",
        "Anspruchsvollere Prüfungen",
        "Komplexe Rollenspielprüfungen, erste Serien",
        "Mehrstufige Herausforderungen",
        "Schwierige Prüfungsserien",
        "Höchste spielerische Herausforderung"
    )

    const val AVAILABLE = "AVAILABLE"
    const val ACCEPTED = "ACCEPTED"
    const val ACTIVE = "ACTIVE"
    const val PASSED = "PASSED"
    const val FAILED = "FAILED"
    const val ABORTED = "ABORTED"
    val OPEN_STATES = setOf(AVAILABLE, ACCEPTED, ACTIVE)

    fun statusLabel(s: String) = when (s) {
        AVAILABLE -> "VERFÜGBAR"; ACCEPTED -> "ANGENOMMEN"; ACTIVE -> "AKTIV"
        PASSED -> "BESTANDEN"; FAILED -> "NICHT BESTANDEN"; ABORTED -> "ABGEBROCHEN"; else -> s
    }

    val ANATOMY = listOf("PENIS" to "Penis/Hoden", "VULVA" to "Vulva")

    val SAFETY_CLASSES = mapOf(
        "A" to "Rollenspiel, kein Körperkontakt",
        "B" to "Leichter Körperbezug",
        "C" to "Vorsicht: Impact, Toys oder Klammern",
        "S" to "Session-Rahmen nach eigener Liste"
    )

    /** Feste Sperrliste zusätzlich zur Challenge-Engine. */
    val HC_BLOCK = listOf(
        "hoden schlagen", "auf die hoden schlagen", "tritt", "quetsch", "zusammendrück", "gewicht an", "gewichte an",
        "stunden tragen", "über nacht", "die ganze nacht", "am hals fesseln", "um den hals", "ohne gleitmittel"
    )

    /** Anzahl der Teilaufgaben je Stufe. */
    fun stepsFor(difficulty: Int): Int = listOf(1, 1, 2, 2, 3, 3)[difficulty.coerceIn(1, 6) - 1]

    /** XP-Faktor je Stufe. */
    fun xpFactor(difficulty: Int): Double = 1.0 + 0.2 * (difficulty.coerceIn(1, 6) - 1)

    /** Nichtkörperliche Spielphasen werden mit der Stufe länger. */
    fun scaledMinutes(base: Int, physical: Boolean, difficulty: Int): Int =
        if (physical || base <= 0) base else (base * (1.0 + 0.25 * (difficulty.coerceIn(1, 6) - 1))).toInt().coerceAtMost(ChallengeEngine.MAX_ANY_MIN)

    /** Höchstdauer für körperliche Aufgaben je Kategorie. */
    fun maxPhysicalMinutes(cat: String): Int = when (cat) {
        "hc_cbt", "hc_pussy" -> 30
        "hc_anal", "hc_toy", "hc_nipple", "hc_impact" -> 35
        else -> ChallengeEngine.MAX_PHYSICAL_MIN
    }

    fun roleCode(profileRole: String): String = when (profileRole.trim().lowercase()) {
        "dominant" -> "DOM"; "submissiv" -> "SUB"; "switch" -> "SWITCH"; "solo" -> "SOLO"; else -> ""
    }

    /** Wie oft am Tag der Hardcore-Dungeon von sich aus eine Prüfung ankündigen darf. */
    fun spontaneousPerDay(difficulty: Int) = difficulty.coerceIn(1, 6)

    fun ranks() = listOf(0 to "Novize", 5 to "Geprüft", 15 to "Gehorsam", 30 to "Gezeichnet", 60 to "Unterworfen", 100 to "Dungeon-Eigentum", 200 to "Albtraum-Bezwinger")
    fun rankFor(passed: Int): String = ranks().last { it.first <= passed }.second
    fun nextRank(passed: Int): Pair<Int, String>? = ranks().firstOrNull { it.first > passed }

    fun toProposal(t: HardcoreTask, difficulty: Int = t.difficulty) = Proposal(
        title = t.title, description = t.description, categoryKey = t.categoryKey, difficulty = t.difficulty,
        durationMin = scaledMinutes(t.durationMin, t.physical, difficulty), xp = t.xp,
        tag = if (t.categoryKey == "hc_impact") "spanking" else "",
        physicalLoad = if (t.physical) 1 else 0, jointLoad = t.jointLoad, partnerRequired = t.partnerRequired, taskId = t.id
    )
}

data class HcContext(
    val enabled: Set<String>,
    val maxDifficulty: Int,
    val anatomy: Set<String> = emptySet(),
    val role: String = "",
    val partnerAvailable: Boolean = false,
    val toyCategories: Set<String> = emptySet(),
    val excludedFetishes: Set<String> = emptySet(),
    val base: EngineContext = EngineContext(allowedCategories = Hardcore.KEYS)
)

data class Composed(
    val steps: List<HardcoreTask>,
    val title: String,
    val description: String,
    val categoryKey: String,
    val categories: List<String>,
    val difficulty: Int,
    val xp: Int,
    val durationMin: Int,
    val toyCategory: String
)

data class HcStats(
    val started: Int,
    val passed: Int,
    val failed: Int,
    val aborted: Int,
    val successRate: Int,
    val highestPassed: Int,
    val favorites: List<Pair<String, Int>>,
    val xp: Int,
    val bestStreak: Int,
    val bestDay: Int,
    val longestSeries: Int
)

object HardcoreEngine {

    fun categoryBlocked(cat: Hardcore.Category, excluded: Set<String>): Boolean =
        if (cat.blockAll) cat.fetishKeys.all { it in excluded } else cat.fetishKeys.any { it in excluded }

    /** Prüft eine Bibliotheksaufgabe. Liefert null, wenn sie erlaubt ist, sonst den Grund. */
    fun reject(t: HardcoreTask, c: HcContext, difficulty: Int = c.maxDifficulty): String? {
        if (t.status != "ACTIVE") return "Deaktiviert"
        val cat = Hardcore.category(t.categoryKey) ?: return "Unbekannte Kategorie"
        if (t.categoryKey !in c.enabled) return "Kategorie nicht aktiviert"
        if (categoryBlocked(cat, c.excludedFetishes)) return "Im Fetisch-Profil ausgeschlossen"
        if (t.difficulty > c.maxDifficulty) return "Zu schwierig"
        if (t.anatomy.isNotBlank() && t.anatomy !in c.anatomy) return "Anatomische Angabe fehlt"
        val code = Hardcore.roleCode(c.role)
        if (code.isNotBlank() && t.roles.isNotBlank() && code !in t.roles.split(",")) return "Passt nicht zu deiner Rolle"
        if (t.partnerRequired && !c.partnerAvailable) return "Partner erforderlich"
        if (t.toyCategory.isNotBlank() && t.toyCategory !in c.toyCategories) return "Kein passendes Toy in der Galerie"
        val text = (t.title + " " + t.description).lowercase()
        if (Hardcore.HC_BLOCK.any { text.contains(it) }) return "Sicherheitsregel verletzt"
        if (t.physical && t.durationMin > Hardcore.maxPhysicalMinutes(t.categoryKey)) return "Körperliche Aufgabe zu lang"
        val base = c.base.copy(allowedCategories = c.base.allowedCategories + Hardcore.KEYS, maxDifficulty = 6, partnerAvailable = c.partnerAvailable, enforceQuota = false, enforceQuiet = false)
        return when (val v = ChallengeEngine.check(Hardcore.toProposal(t, difficulty), base)) {
            is Verdict.Approved -> null
            is Verdict.Rejected -> v.reason
        }
    }

    fun allowed(t: HardcoreTask, c: HcContext): Boolean = reject(t, c) == null

    /**
     * Stellt eine Prüfung zusammen. Höhere Stufen kombinieren mehrere freigegebene Aufgaben
     * aus verschiedenen Kategorien. Die körperliche Gesamtdauer bleibt begrenzt.
     */
    fun compose(
        pool: List<HardcoreTask>,
        c: HcContext,
        difficulty: Int = c.maxDifficulty,
        recentCodes: Set<String> = emptySet(),
        preferCategory: String? = null,
        random: Random = Random.Default
    ): Composed? {
        val d = difficulty.coerceIn(1, 6)
        val ok = pool.filter { reject(it, c.copy(maxDifficulty = d), d) == null }
        if (ok.isEmpty()) return null
        val fresh = ok.filter { it.code !in recentCodes }.ifEmpty { ok }
        val mainPool = fresh.filter { preferCategory == null || it.categoryKey == preferCategory }.ifEmpty { fresh }
        // Bevorzugt Aufgaben nahe der gewählten Stufe
        val near = mainPool.filter { it.difficulty >= d - 1 }.ifEmpty { mainPool }
        val main = near[random.nextInt(near.size)]
        val steps = arrayListOf(main)
        val wanted = Hardcore.stepsFor(d)
        var physicalTotal = if (main.physical) main.durationMin else 0
        var toyCategory = main.toyCategory
        val others = fresh.filter { it.id != main.id }.shuffled(random)
            .sortedBy { if (it.categoryKey == main.categoryKey) 1 else 0 }
        for (o in others) {
            if (steps.size >= wanted) break
            if (steps.any { it.categoryKey == o.categoryKey } && others.any { x -> steps.none { it.categoryKey == x.categoryKey } }) continue
            if (o.physical && physicalTotal + o.durationMin > ChallengeEngine.MAX_PHYSICAL_MIN) continue
            if (o.toyCategory.isNotBlank() && toyCategory.isNotBlank() && o.toyCategory != toyCategory) continue
            if (o.partnerRequired != main.partnerRequired && o.partnerRequired) continue
            steps += o
            if (o.physical) physicalTotal += o.durationMin
            if (toyCategory.isBlank()) toyCategory = o.toyCategory
        }
        val minutes = steps.sumOf { Hardcore.scaledMinutes(it.durationMin, it.physical, d) }.coerceAtMost(ChallengeEngine.MAX_ANY_MIN)
        val xp = (steps.sumOf { it.xp } * Hardcore.xpFactor(d)).toInt().coerceIn(10, 400)
        val title = if (steps.size == 1) main.title else "Prüfungsserie: " + steps.joinToString(" · ") { it.title }
        val desc = if (steps.size == 1) fill(main.description, main, d)
        else steps.mapIndexed { i, s -> "${i + 1}. ${s.title}: ${fill(s.description, s, d)}" }.joinToString("\n")
        return Composed(steps, title.take(140), desc, main.categoryKey, steps.map { it.categoryKey }.distinct(), d, xp, minutes, toyCategory)
    }

    /** Setzt die skalierte Dauer ein. Das Toy wird erst beim Speichern eingesetzt. */
    fun fill(text: String, t: HardcoreTask, difficulty: Int): String =
        text.replace("{min}", Hardcore.scaledMinutes(t.durationMin, t.physical, difficulty).toString())

    fun withToy(text: String, toyName: String): String = text.replace("{toy}", toyName.ifBlank { "dein Toy" })

    /** Nur BESTANDEN bringt XP. NICHT BESTANDEN und ABGEBROCHEN: 0 XP, nie ein Abzug. */
    fun xpFor(status: String, run: HardcoreRun): Int = if (status == Hardcore.PASSED) run.xp else 0

    fun stats(runs: List<HardcoreRun>, dayOf: (Long) -> Long): HcStats {
        val started = runs.filter { it.status != Hardcore.AVAILABLE }
        val passed = runs.filter { it.status == Hardcore.PASSED }
        val failed = runs.count { it.status == Hardcore.FAILED }
        val aborted = runs.count { it.status == Hardcore.ABORTED }
        val judged = passed.size + failed
        val rate = if (judged == 0) 0 else (passed.size * 100.0 / judged).toInt()
        val fav = passed.flatMap { it.categories.split(",").filter { c -> c.isNotBlank() } }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(5)
        var streak = 0; var best = 0
        runs.filter { it.status == Hardcore.PASSED || it.status == Hardcore.FAILED }.sortedBy { it.endedAt.takeIf { e -> e > 0 } ?: it.createdAt }.forEach {
            if (it.status == Hardcore.PASSED) { streak++; best = maxOf(best, streak) } else streak = 0
        }
        val bestDay = passed.groupingBy { dayOf(if (it.endedAt > 0) it.endedAt else it.createdAt) }.eachCount().values.maxOrNull() ?: 0
        return HcStats(
            started = started.size, passed = passed.size, failed = failed, aborted = aborted, successRate = rate,
            highestPassed = passed.maxOfOrNull { it.difficulty } ?: 0, favorites = fav, xp = passed.sumOf { it.xp },
            bestStreak = best, bestDay = bestDay, longestSeries = passed.maxOfOrNull { it.stepsTotal } ?: 0
        )
    }

    /** Liest die Aufgabenbibliothek aus dem Textformat. */
    fun parse(text: String): List<HardcoreTask> = text.lines().mapNotNull { line ->
        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
        val f = line.split("|")
        if (f.size < 14) return@mapNotNull null
        try {
            HardcoreTask(
                code = f[0].trim(), categoryKey = f[1].trim(), difficulty = f[2].trim().toInt().coerceIn(1, 6),
                durationMin = f[3].trim().toInt(), xp = f[4].trim().toInt(), physical = f[5].trim() == "1",
                jointLoad = f[6].trim() == "1", partnerRequired = f[7].trim() == "1", toyCategory = f[8].trim(),
                anatomy = f[9].trim(), roles = f[10].trim(), safetyClass = f[11].trim(), title = f[12].trim(),
                description = f.subList(13, f.size).joinToString("|").trim(),
                requirements = buildList {
                    if (f[7].trim() == "1") add("Partnerperson anwesend")
                    if (f[8].isNotBlank()) add("Toy: " + f[8].trim())
                    if (f[9].isNotBlank()) add("Anatomie: " + (Hardcore.ANATOMY.firstOrNull { it.first == f[9].trim() }?.second ?: f[9].trim()))
                    if (f[6].trim() == "1") add("Belastet Knie oder Handgelenke")
                }.joinToString(", ")
            )
        } catch (_: Exception) { null }
    }
}
