package com.lockeddesire.app.data

import android.content.Context

object Seed {
    val CATEGORIES = listOf(
        TaskCategory(key = "fitness", name = "Fitness", emoji = "💪"),
        TaskCategory(key = "selfcontrol", name = "Selbstkontrolle", emoji = "🧘"),
        TaskCategory(key = "chastity", name = "Keuschheit", emoji = "🔒"),
        TaskCategory(key = "roleplay", name = "BDSM-Rollenspiel", emoji = "⛓"),
        TaskCategory(key = "petplay", name = "Petplay", emoji = "🐾"),
        TaskCategory(key = "sensual", name = "Sinnlichkeit", emoji = "🕯"),
        TaskCategory(key = "dungeon", name = "Dungeon-Prüfungen", emoji = "🏰"),
        TaskCategory(key = "surprise", name = "Überraschungen", emoji = "🎲"),
        TaskCategory(key = "partner", name = "Partner-Challenges", emoji = "💞"),
        TaskCategory(key = "custom", name = "Eigene Aufgaben", emoji = "✍")
    )
    val CATEGORY_KEYS = CATEGORIES.map { it.key }.toSet()

    private val CAUTION_TAGS = setOf("spanking", "wax", "temperature", "cuffs", "blindfold")

    fun parseTasks(text: String, custom: Boolean = false): List<Task> = text.lines().mapNotNull { line ->
        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
        val f = line.split("|")
        if (f.size < 12) return@mapNotNull null
        try {
            val tag = f[9].trim()
            Task(
                title = f[10].trim(), description = f.subList(11, f.size).joinToString("|").trim(),
                categoryKey = f[0].trim(), difficulty = f[1].trim().toInt().coerceIn(1, 6),
                durationMin = f[2].trim().toInt(), xp = f[3].trim().toInt(),
                hasTimer = f[4].trim() == "1", physicalLoad = f[5].trim().toIntOrNull() ?: 0,
                jointLoad = f[6].trim() == "1", reps = f[7].trim().toIntOrNull() ?: 0,
                partnerRequired = f[8].trim() == "1", fetishKey = tag,
                safetyTag = if (tag in CAUTION_TAGS) "CAUTION" else "SAFE", custom = custom
            )
        } catch (_: Exception) { null }
    }

    fun tasks(context: Context): List<Task> =
        parseTasks(context.assets.open("tasks.txt").bufferedReader().use { it.readText() })

    const val G_DS = "Dominanz und Unterwerfung"
    const val G_CH = "Keuschheit und Orgasmuskontrolle"
    const val G_IM = "Impact Play und Schmerzvorlieben"
    const val G_BO = "Bondage"
    const val G_PE = "Petplay"
    const val G_OT = "Weitere Vorlieben"
    val GROUP_TAG = mapOf(G_DS to "ds", G_CH to "chastity", G_PE to "petplay")

    private fun f(key: String, group: String, name: String, risky: Boolean = false) =
        FetishCategory(key = key, groupName = group, name = name, risky = risky)

    /** risky = nur Vorliebenverwaltung und Dokumentation, nie als Aufgabe. */
    val FETISHES = listOf(
        f("femdom", G_DS, "Femdom"), f("maledom", G_DS, "Maledom"), f("neutraldom", G_DS, "Geschlechtsneutrale Dominanz"),
        f("submissive", G_DS, "Submissive Rollen"), f("switch", G_DS, "Switch"), f("obedience", G_DS, "Gehorsamsrituale"),
        f("service", G_DS, "Serviceorientierte Rollenspiele"), f("discipline", G_DS, "Freiwillige Disziplin"),
        f("chastity", G_CH, "Keuschheit"), f("keyholder", G_CH, "Keyholder-Rollenspiele"),
        f("denial", G_CH, "Freiwilliger Orgasmusverzicht"), f("edging", G_CH, "Edging als persönliche Vorliebe"),
        f("patience", G_CH, "Selbstkontrolle"), f("chastitygoals", G_CH, "Persönliche Keuschheitsziele"),
        f("spanking", G_IM, "Spanking"), f("paddling", G_IM, "Paddling", true), f("flogging", G_IM, "Flogging", true),
        f("caning", G_IM, "Caning", true), f("cbt", G_IM, "Cock and Ball Torture (CBT)", true), f("nipple", G_IM, "Nipple Play", true),
        f("rope", G_BO, "Rope Bondage", true), f("shibari", G_BO, "Shibari", true), f("cuffs", G_BO, "Cuffs"),
        f("blindfold", G_BO, "Blindfolds"), f("sensorydep", G_BO, "Sensorische Einschränkungen", true),
        f("puppyplay", G_PE, "Puppyplay"), f("kittenplay", G_PE, "Kittenplay"), f("ponyplay", G_PE, "Ponyplay"),
        f("bunnyplay", G_PE, "Bunnyplay"), f("foxplay", G_PE, "Foxplay"),
        f("feet", G_OT, "Foot Fetish"), f("sensory", G_OT, "Sensory Play"), f("wax", G_OT, "Wax Play"),
        f("temperature", G_OT, "Temperature Play"), f("medical", G_OT, "Medical Roleplay"), f("latex", G_OT, "Latex"),
        f("leather", G_OT, "Leder"), f("fetishwear", G_OT, "Fetischkleidung"), f("uniform", G_OT, "Uniform-Fetische"),
        f("cosplay", G_OT, "Cosplay"), f("exhib", G_OT, "Exhibitionistische Fantasien", true),
        f("voyeur", G_OT, "Voyeuristische Fantasien", true)
    )

    /** BDSM-Dungeon-Kategorien → Aufgaben-Tags. */
    val DUNGEON_ROOMS = listOf(
        Triple("Dominanz und Unterwerfung", "👑", setOf("ds", "service")),
        Triple("Spanking", "✋", setOf("spanking")),
        Triple("Keuschheit", "🔒", setOf("chastity")),
        Triple("Bondage", "⛓", setOf("cuffs", "blindfold")),
        Triple("Petplay", "🐾", setOf("petplay", "puppyplay", "kittenplay", "ponyplay", "bunnyplay", "foxplay")),
        Triple("Sinneswahrnehmung", "🕯", setOf("sensory", "temperature", "wax", "feet")),
        Triple("Rollenspiele", "🎭", setOf("roleplay", "uniform", "medical", "latex", "leather")),
        Triple("Disziplin", "📜", setOf("discipline")),
        Triple("Geduld", "⏳", setOf("patience")),
        Triple("Partner-Challenges", "💞", setOf("@partner")),
        Triple("Aftercare", "🫖", setOf("aftercare"))
    )

    val DIFFICULTY_NAMES = listOf("Sanft", "Frech", "Streng", "Gemein", "Dungeon-Master", "Albtraum")

    val PERSONAS = listOf(
        AiPersona(name = "Lady Morgana", avatar = "👸", type = "FEMALE", pronouns = "sie/ihr", salutation = "Herrin", personality = "STRENG", strictness = 7, voicePitch = 0.9f, builtIn = true, greeting = "Du bist pünktlich. Gut. Zeig mir, dass du heute Disziplin hast."),
        AiPersona(name = "Master Kain", avatar = "🤴", type = "MALE", pronouns = "er/ihm", salutation = "Herr", personality = "DOMINANT", strictness = 6, voicePitch = 0.7f, builtIn = true, greeting = "Da bist du ja. Dein Dungeon ist geöffnet. Welche Herausforderung nimmst du heute an?"),
        AiPersona(name = "Nyx", avatar = "🦇", type = "NEUTRAL", pronouns = "keine", salutation = "Hoheit", personality = "DUESTER", strictness = 5, voicePitch = 0.8f, voiceRate = 0.9f, builtIn = true, greeting = "Die Schatten haben dich erwartet. Tritt näher."),
        AiPersona(name = "Vex", avatar = "😈", type = "FEMALE", pronouns = "sie/ihr", salutation = "Miss", personality = "FRECH", strictness = 4, voicePitch = 1.2f, voiceRate = 1.1f, builtIn = true, greeting = "Na, traust du dich heute an eine neue Herausforderung?"),
        AiPersona(name = "Sir Malice", avatar = "🎩", type = "MALE", pronouns = "er/ihm", salutation = "Sir", personality = "GEMEIN", strictness = 8, voicePitch = 0.8f, builtIn = true, greeting = "Ach, du schon wieder. Mal sehen, ob du diesmal länger durchhältst als mein Kaffee warm bleibt."),
        AiPersona(name = "Sera", avatar = "🕊", type = "NEUTRAL", pronouns = "keine", salutation = "Sera", personality = "FUERSORGLICH", strictness = 2, voicePitch = 1.1f, voiceRate = 0.95f, builtIn = true, greeting = "Schön, dass du da bist. Wie fühlst du dich heute?")
    )

    val COUNTERS = listOf(
        "oral_given" to "Oralsex gegeben", "oral_received" to "Oralsex erhalten", "anal" to "Analverkehr",
        "encounters" to "Weitere sexuelle Begegnungen", "bdsm_sessions" to "BDSM-Sessions",
        "spanking_sessions" to "Spanking-Sessions", "dom_roleplay" to "Dominante Rollenspiele",
        "sub_roleplay" to "Submissive Rollenspiele", "chastity_days" to "Keuschheitstage",
        "pet_sessions" to "Petplay-Sessions", "toys_used" to "Verwendete Toys",
        "fitness_challenges" to "Fitness-Challenges", "dungeon_challenges" to "Dungeon-Challenges",
        "records" to "Persönliche Rekorde"
    ).map { PersonalCounter(key = it.first, name = it.second) }

    val ACHIEVEMENTS = listOf(
        Achievement(key = "first_task", name = "Erster Schritt", description = "Erste Aufgabe abgeschlossen", emoji = "🗝", xpReward = 20),
        Achievement(key = "ten_tasks", name = "Zehnfach geprüft", description = "10 Aufgaben abgeschlossen", emoji = "⛓", xpReward = 50),
        Achievement(key = "fifty_tasks", name = "Ausdauernd", description = "50 Aufgaben abgeschlossen", emoji = "🔥", xpReward = 100),
        Achievement(key = "hundred_tasks", name = "Hundertschaft", description = "100 Aufgaben abgeschlossen", emoji = "🏆", xpReward = 200),
        Achievement(key = "first_dungeon", name = "Dungeon bezwungen", description = "Einen 24-Stunden-Dungeon abgeschlossen", emoji = "🏰", xpReward = 150),
        Achievement(key = "chastity_1d", name = "Ein Tag verschlossen", description = "24 Stunden Keuschheit am Stück", emoji = "🔒", xpReward = 40),
        Achievement(key = "chastity_7d", name = "Eine Woche standhaft", description = "7 Tage Keuschheit am Stück", emoji = "🔐", xpReward = 150),
        Achievement(key = "chastity_30d", name = "Eiserner Wille", description = "30 Tage Keuschheit am Stück", emoji = "👑", xpReward = 500),
        Achievement(key = "fitness_10", name = "In Form", description = "10 Fitness-Einheiten eingetragen", emoji = "💪", xpReward = 60),
        Achievement(key = "pet_first", name = "Braves Pet", description = "Erste Petplay-Session", emoji = "🐾", xpReward = 30),
        Achievement(key = "toy_first", name = "Sammler", description = "Erstes Toy in der Galerie", emoji = "🎁", xpReward = 20),
        Achievement(key = "partner_first", name = "Zu zweit", description = "Erste Partner-Challenge", emoji = "💞", xpReward = 40),
        Achievement(key = "custom_task", name = "Eigene Regeln", description = "Erste eigene Aufgabe erstellt", emoji = "✍", xpReward = 20),
        Achievement(key = "fetish_profile", name = "Selbstkenntnis", description = "5 Vorlieben eingeordnet", emoji = "📖", xpReward = 30),
        Achievement(key = "level_5", name = "Herausforderer", description = "Level 5 erreicht", emoji = "⭐", xpReward = 0),
        Achievement(key = "level_10", name = "Diszipliniert", description = "Level 10 erreicht", emoji = "🌟", xpReward = 0),
        Achievement(key = "level_20", name = "Dungeon-Kenner", description = "Level 20 erreicht", emoji = "💫", xpReward = 0),
        Achievement(key = "level_50", name = "Dungeon-Legende", description = "Level 50 erreicht", emoji = "☄", xpReward = 0)
    )

    val FITNESS_PRESETS = listOf(
        "Liegestütze" to true, "Kniebeugen" to true, "Hampelmänner" to true, "Ausfallschritte" to true,
        "Plank" to false, "Spazieren" to false, "Laufen" to false, "Mobility-Training" to false, "Ganzkörpertraining" to false
    )
    val TOY_CATEGORIES = listOf("Dildos", "Analplugs", "Keuschheitszubehör", "BDSM-Zubehör", "Sonstiges")
    val PET_ROLES = listOf("Puppy" to "🐶", "Kitten" to "🐱", "Pony" to "🐴", "Bunny" to "🐰", "Fox" to "🦊", "Eigenes Pet" to "🐾")
    val PERSONALITIES = listOf(
        "FRECH" to "Frech", "STRENG" to "Streng", "DOMINANT" to "Dominant",
        "DUESTER" to "Düster", "GEMEIN" to "Gemein", "FUERSORGLICH" to "Fürsorglich"
    )
}
