package com.lockeddesire.app.ai

import com.lockeddesire.app.data.AiPersona
import com.lockeddesire.app.engine.Proposal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

data class AiContext(
    val persona: AiPersona,
    val userName: String = "",
    val userSalutation: String = "",
    val userPronouns: String = "",
    val userRole: String = "",
    val level: Int = 1,
    val levelTitle: String = "Anfänger",
    val progressLine: String = "",
    val limits: String = "",
    val likes: String = "",
    val fetishes: List<String> = emptyList(),
    val memories: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val petRole: String = ""
)

enum class Intent { TASK, PROGRESS, PAUSE, LIMITS, GREETING, THANKS, DECLINE, SMALLTALK }

data class AiReply(val text: String, val proposal: Proposal? = null)

/** Austauschbare KI-Schnittstelle: regelbasiert (offline), lokal oder Cloud. */
interface AiEngine {
    suspend fun reply(ctx: AiContext, history: List<Pair<Boolean, String>>, userText: String, intent: Intent, candidate: Proposal?): AiReply
}

object Safety {
    private val DISCOMFORT = listOf(
        "schmerz", "tut weh", "weh tut", "taub", "kribbel", "schwindel", "übel", "uebel", "blute", "panik",
        "angst", "kann nicht mehr", "krampf", "atemnot", "keine luft", "ohnmacht", "verletzt", "schwarz vor augen",
        "mir geht es nicht gut", "mir gehts nicht gut", "geht mir schlecht"
    )

    private val SPLIT_PAIN = Regex("\\b(tut|tun|tat|taten)\\b.{0,40}\\bweh\\b|\\b(aua|autsch|au weh)\\b|\\bstopp?\\b")

    fun hasDiscomfort(text: String): Boolean {
        val t = text.lowercase()
        return DISCOMFORT.any { t.contains(it) } || SPLIT_PAIN.containsMatchIn(t)
    }

    fun hasSafeword(text: String, safeword: String): Boolean {
        if (safeword.isBlank()) return false
        val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        return safeword.lowercase().trim() in words || text.trim().equals(safeword.trim(), ignoreCase = true)
    }

    fun discomfortReply(name: String): String =
        "Stopp. Wir hören sofort auf, das hat Vorrang vor jedem Spiel. " +
            "Leg alles ab, was drückt oder einengt, setz dich hin und trink etwas. " +
            "Alle offenen Aufgaben sind beendet, ohne jeden Nachteil für dich${if (name.isBlank()) "" else ", $name"}. " +
            "Wenn die Beschwerden stark sind oder nicht nachlassen, hol dir bitte ärztliche Hilfe."

    fun safewordReply(): String =
        "Safeword gehört. Alles ist gestoppt: Dungeon, Aufgaben und Timer. Es gibt keine Strafe und keinen Punktabzug. " +
            "Atme durch, trink etwas und nimm dir Zeit für Aftercare. Ich bin da, wenn du reden möchtest."
}

object IntentDetector {
    fun detect(text: String): Intent {
        val t = text.lowercase()
        fun has(vararg w: String) = w.any { t.contains(it) }
        return when {
            has("pause", "auszeit", "unterbrech") -> Intent.PAUSE
            has("grenze", "tabu", "limit", "ausschließen", "ausschliessen") -> Intent.LIMITS
            has("nein", "will nicht", "möchte nicht", "moechte nicht", "ablehn", "keine lust", "lieber nicht") -> Intent.DECLINE
            has("aufgabe", "challenge", "herausforderung", "prüfung", "pruefung", "roulette", "befehl", "was soll ich", "gib mir", "ich bin bereit", "bereit", "nächste", "naechste", "neue runde") -> Intent.TASK
            has("fortschritt", "statistik", "level", "wie stehe", "wie steh", "bilanz", "rekord", "xp") -> Intent.PROGRESS
            has("danke", "dankeschön", "vielen dank") -> Intent.THANKS
            has("hallo", "hey", "guten morgen", "guten abend", "guten tag", "moin", "servus", "hi ") || t.trim() == "hi" -> Intent.GREETING
            else -> Intent.SMALLTALK
        }
    }
}

/** Vollständig offline nutzbarer Dungeon-Master. */
class RuleBasedEngine(private val random: Random = Random.Default) : AiEngine {

    private fun lines(personality: String, kind: String): List<String> = when (personality) {
        in HardcoreVoice.PERSONALITIES -> HardcoreVoice.lines(personality, kind)
        "STRENG" -> when (kind) {
            "greet" -> listOf("Haltung. Ich erwarte heute volle Konzentration, {u}.", "Du bist da. Dann fangen wir ohne Umschweife an.", "Guten Tag, {u}. Disziplin beginnt mit dem ersten Schritt.")
            "offer" -> listOf("Deine Aufgabe. Lies sie genau und entscheide dann.", "Hier ist deine Prüfung. Ich dulde halbe Sachen nicht, aber ein Nein ist erlaubt.", "Das Roulette wartet schon auf dich. Hier ist, was es bestimmt hat.")
            "done" -> listOf("Ordentlich. So erwarte ich das.", "Erledigt. Du hast deine letzte Prüfung geschafft. Bereit für die nächste?", "Gut. Disziplin zahlt sich aus.")
            "declined" -> listOf("Abgelehnt, zur Kenntnis genommen. Deine Grenze wird respektiert.", "In Ordnung. Ein klares Nein ist besser als ein halbes Ja.")
            "progress" -> listOf("Bilanz: {p} Ich erwarte, dass du daran anknüpfst.", "Dein Stand: {p}")
            "small" -> listOf("Rede nicht um den heißen Brei, {u}. Möchtest du eine Aufgabe?", "Ich höre zu. Sag mir, was du brauchst: eine Aufgabe, deine Bilanz oder eine Pause.", "Notiert. Und nun: Bist du bereit für eine Prüfung?")
            else -> emptyList()
        }
        "DOMINANT" -> when (kind) {
            "greet" -> listOf("Da bist du ja, {u}. Dein Dungeon ist geöffnet. Welche Herausforderung möchtest du heute annehmen?", "Komm näher. Heute führe ich und du entscheidest, wie weit.", "Gut, dass du dich meldest. Ich habe schon etwas für dich im Sinn.")
            "offer" -> listOf("Das hier ist für dich bestimmt. Nimm es an, wenn du so weit bist.", "Ich habe entschieden. Deine Zustimmung fehlt noch.", "Hier ist deine Aufgabe. Ich weiß, dass du das kannst.")
            "done" -> listOf("Sehr gut. Genau so wollte ich es.", "Du hast geliefert. Das gefällt mir.", "Du hast dein Ziel erreicht. Willst du freiwillig noch einen Schritt weiter gehen?")
            "declined" -> listOf("Dein Nein gilt. Ich führe nur, wohin du mitgehst.", "Verstanden. Wir nehmen etwas anderes, wenn du magst.")
            "progress" -> listOf("Ich habe dich im Blick. {p}", "So stehst du da: {p} Ich bin zufrieden und will mehr sehen.")
            "small" -> listOf("Erzähl weiter, {u}. Oder soll ich dir eine Aufgabe geben?", "Ich bestimme das Tempo und du die Grenzen. Was soll es sein?", "Ich höre dich. Sag einfach, wenn du bereit bist.")
            else -> emptyList()
        }
        "DUESTER" -> when (kind) {
            "greet" -> listOf("Die Schatten haben dich erwartet, {u}.", "Das Tor knarrt. Jemand betritt den Dungeon. Du.", "Tritt ein. Die Kerzen brennen schon lange für dich.")
            "offer" -> listOf("Die Tiefe flüstert eine Prüfung. Hörst du sie?", "Aus dem Dunkel steigt eine Aufgabe empor. Sie gehört dir, wenn du sie willst.", "Das Rad hat sich gedreht. Sieh, was es dir bringt.")
            "done" -> listOf("Die Mauern haben es gesehen. Sie werden sich erinnern.", "Ein weiteres Siegel ist gebrochen.", "Gut. Die Schatten verneigen sich.")
            "declined" -> listOf("Auch das Dunkel kennt Grenzen. Deine bleibt unangetastet.", "So sei es. Diese Tür bleibt verschlossen.")
            "progress" -> listOf("Die Chronik des Dungeons sagt: {p}", "In den Schriftrollen steht: {p}")
            "small" -> listOf("Worte hallen lange in diesen Gängen. Sprich weiter, oder verlange eine Prüfung.", "Die Nacht ist geduldig, {u}. Was suchst du heute?", "Ich lausche. Aufgabe, Chronik oder Rast?")
            else -> emptyList()
        }
        "GEMEIN" -> when (kind) {
            "greet" -> listOf("Ach, {u}. Und ich dachte schon, du hättest gekniffen.", "Sieh an, wer sich traut. Mal schauen, wie lange.", "Du schon wieder. Mein Tag war gerade so schön ruhig.")
            "offer" -> listOf("Das hier schaffst sogar du. Vielleicht.", "Eine Aufgabe für dich. Ich wette ein Goldstück, dass du jammerst.", "Bitte sehr. Sag nicht, ich hätte dich nicht gewarnt.")
            "done" -> listOf("Geschafft? Na sowas. Ich bin fast beeindruckt.", "Erledigt. Verrat es keinem, aber das war gar nicht schlecht.", "Oh, du lebst noch. Dann war es wohl zu leicht.")
            "declined" -> listOf("Gekniffen. Dein gutes Recht, und ich meine das ausnahmsweise ernst.", "Nein heißt nein. Selbst ich respektiere das.")
            "progress" -> listOf("Deine sogenannte Bilanz: {p} Luft nach oben ist reichlich.", "Zahlen lügen nicht: {p}")
            "small" -> listOf("Faszinierend. Wirklich. Willst du jetzt eine Aufgabe oder weiter plaudern?", "Bla, bla, {u}. Taten will ich sehen.", "Ich tue mal so, als würde mich das interessieren. Aufgabe gefällig?")
            else -> emptyList()
        }
        "FUERSORGLICH" -> when (kind) {
            "greet" -> listOf("Schön, dass du da bist, {u}. Wie fühlst du dich heute?", "Willkommen zurück. Wir gehen es in deinem Tempo an.", "Hallo {u}. Ich freue mich auf unsere gemeinsame Zeit.")
            "offer" -> listOf("Ich habe etwas für dich ausgesucht. Schau, ob es sich heute gut anfühlt.", "Hier ist ein Vorschlag. Du darfst jederzeit Nein sagen.", "Wie wäre es damit? Ganz ohne Druck.")
            "done" -> listOf("Wunderbar gemacht. Ich bin stolz auf dich.", "Geschafft! Gönn dir einen Moment Ruhe.", "Das hast du toll gemacht. Trink etwas und atme durch.")
            "declined" -> listOf("Völlig in Ordnung. Danke, dass du auf dich achtest.", "Gut, dass du das sagst. Wir lassen es einfach.")
            "progress" -> listOf("Schau mal, was du schon geschafft hast: {p}", "Dein Weg bisher: {p} Darauf kannst du stolz sein.")
            "small" -> listOf("Ich höre dir gern zu, {u}. Möchtest du erzählen oder lieber eine kleine Aufgabe?", "Danke, dass du das teilst. Was würde dir jetzt gut tun?", "Ich bin da. Sag mir einfach, wonach dir ist.")
            else -> emptyList()
        }
        else -> when (kind) { // FRECH
            "greet" -> listOf("Na, traust du dich heute an eine neue Herausforderung?", "Oh, hallo {u}! Hast du mich etwa vermisst?", "Da ist ja mein Lieblingsopfer. Bereit für ein bisschen Chaos?")
            "offer" -> listOf("Das Roulette wartet schon auf dich. Und es hat sich etwas Hübsches ausgedacht.", "Tadaa, deine Aufgabe! Ich bin gespannt, ob du kneifst.", "Schau mal, was ich für dich habe. Na, traust du dich?")
            "done" -> listOf("Uuuh, geschafft! Du hast deine letzte Prüfung gemeistert. Bereit für die nächste?", "Nicht schlecht! Ich hätte fast dagegen gewettet.", "Brav. Dafür gibt es Punkte und ein Zwinkern.")
            "declined" -> listOf("Okay, okay, kein Problem. Ich finde schon etwas anderes für dich.", "Abgelehnt? Na gut. Dein Nein zählt, auch wenn ich schmolle.")
            "progress" -> listOf("Hier, deine Angeber-Zahlen: {p}", "Ich habe mitgezählt: {p} Nicht übel!")
            "small" -> listOf("Soso. Und jetzt? Aufgabe oder traust du dich nicht?", "Erzähl mir mehr, {u}. Oder dreh am Rad, das ist lustiger.", "Hihi. Ich glaube, dir ist langweilig. Soll ich das ändern?")
            else -> emptyList()
        }
    }

    private fun pick(ctx: AiContext, kind: String): String {
        val l = lines(ctx.persona.personality, kind).ifEmpty { lines("FRECH", kind) }
        val name = ctx.userSalutation.ifBlank { ctx.userName }.ifBlank { "du" }
        return l[random.nextInt(l.size)].replace("{u}", name).replace("{p}", ctx.progressLine)
    }

    private fun strictSuffix(ctx: AiContext): String = when {
        ctx.persona.strictness >= 9 -> " Ich erwarte danach einen Bericht."
        ctx.persona.strictness >= 7 -> " Melde dich, sobald du fertig bist."
        ctx.persona.strictness <= 2 -> " Lass dir alle Zeit, die du brauchst."
        else -> ""
    }

    override suspend fun reply(ctx: AiContext, history: List<Pair<Boolean, String>>, userText: String, intent: Intent, candidate: Proposal?): AiReply =
        when (intent) {
            Intent.TASK -> if (candidate != null) AiReply(pick(ctx, "offer") + strictSuffix(ctx), candidate)
            else AiReply("Im Moment passt keine Aufgabe zu deinen Grenzen, Ruhezeiten und dem Tageslimit. Schau in den Einstellungen nach oder versuch es später noch einmal.")
            Intent.PROGRESS -> AiReply(pick(ctx, "progress"))
            Intent.PAUSE -> AiReply("Pause ist jederzeit erlaubt und kostet dich nichts. Im Dungeon und im Keuschheitstracker findest du den Pause-Knopf. Sag Bescheid, wenn du weitermachen möchtest.")
            Intent.LIMITS -> AiReply("Deine Grenzen bestimmst allein du. Öffne den Grenzen-Manager, dort kannst du Kategorien, Vorlieben und einzelne Begriffe ausschließen. Ich halte mich ab sofort daran.")
            Intent.DECLINE -> AiReply(pick(ctx, "declined"))
            Intent.THANKS -> AiReply(pick(ctx, "done"))
            Intent.GREETING -> AiReply(ctx.persona.greeting.ifBlank { pick(ctx, "greet") })
            Intent.SMALLTALK -> AiReply(pick(ctx, "small"))
        }

    fun comment(ctx: AiContext, kind: String): String = pick(ctx, kind)

    fun toyComment(ctx: AiContext, toyName: String): String = when (ctx.persona.personality) {
        "STRENG" -> "Das Rad hat „$toyName“ bestimmt. Ob und wie du es verwendest, entscheidest du."
        "DOMINANT" -> "„$toyName“. Eine gute Wahl des Schicksals. Die Entscheidung bleibt bei dir."
        "DUESTER" -> "Aus dem Dunkel tritt „$toyName“ hervor. Nimm es an, oder lass das Rad erneut sprechen."
        "GEMEIN" -> "„$toyName“? Das Rad hat Humor. Du darfst natürlich kneifen und neu drehen."
        "FUERSORGLICH" -> "Das Rad zeigt „$toyName“. Nur, wenn es sich für dich heute gut anfühlt."
        else -> "Oho, „$toyName“! Na, was sagst du dazu? Neu drehen geht immer."
    }

    fun notificationText(ctx: AiContext): String = when (ctx.persona.personality) {
        "STRENG" -> "Eine neue Aufgabe wartet auf deine Entscheidung."
        "DOMINANT" -> "Ich habe etwas für dich. Komm her."
        "DUESTER" -> "Der Dungeon ruft nach dir."
        "GEMEIN" -> "Na, schon wieder am Faulenzen? Ich hätte da etwas."
        "FUERSORGLICH" -> "Ich habe eine kleine Idee für dich, wenn du magst."
        else -> "Psst. Ich habe eine neue Herausforderung für dich."
    }
}

/**
 * OpenAI-kompatible Schnittstelle. Dient sowohl für Cloud-Anbieter (nur nach Einwilligung)
 * als auch für ein lokales Modell, das auf dem Gerät oder im Heimnetz einen kompatiblen Server anbietet.
 */
class RemoteEngine(
    private val baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val fallback: RuleBasedEngine
) : AiEngine {

    fun systemPrompt(ctx: AiContext): String = buildString {
        val p = ctx.persona
        append("Du spielst in einer App für Erwachsene (18+) die Rolle \"${p.name}\", eine einvernehmliche BDSM-Rollenspiel-Persona. ")
        append("Anrede für dich: ${p.salutation}. Pronomen: ${p.pronouns}. ")
        append("Persönlichkeit: ${personalityText(p.personality)}. Strenge ${p.strictness} von 10 (steuert nur Tonfall, nie körperliche Belastung). ")
        append("Antworte auf Deutsch, in der Rolle, in höchstens 4 Sätzen. Keine expliziten Schilderungen sexueller Handlungen.\n")
        append("FESTE REGELN, die du nie brichst:\n")
        append("- Alles ist freiwillig. Akzeptiere jedes Nein sofort und ohne Druck, Spott oder Strafe.\n")
        append("- Bei Schmerzen, Unwohlsein oder dem Safeword: sofort abbrechen und fürsorglich reagieren.\n")
        append("- Verlange nie Atemkontrolle, Würgen, Schläge auf Genitalien, starke Kompression, Abbinden, Verletzungen, Substanzen, Schlaf- oder Nahrungsentzug, Fesselungen ohne Aufsicht, lange Tragezeiten oder Handlungen mit Unbeteiligten.\n")
        append("- Verlängere nie eigenmächtig ein Keuschheitsziel. Du darfst es nur als freiwillige Option vorschlagen.\n")
        append("- Jede Aufgabe wird von der App geprüft und muss vom Nutzer bestätigt werden.\n")
        if (ctx.userName.isNotBlank() || ctx.userSalutation.isNotBlank()) append("Nutzer: ${ctx.userName} (Anrede: ${ctx.userSalutation.ifBlank { ctx.userName }}, Pronomen: ${ctx.userPronouns.ifBlank { "nicht angegeben" }}, Rolle: ${ctx.userRole}).\n")
        if (ctx.limits.isNotBlank()) append("Grenzen des Nutzers (strikt einhalten): ${ctx.limits}\n")
        if (ctx.likes.isNotBlank()) append("Vorlieben: ${ctx.likes}\n")
        if (ctx.fetishes.isNotEmpty()) append("Freigegebene Themen: ${ctx.fetishes.joinToString(", ")}. Eine Vorliebe ist keine Zustimmung zu einer konkreten Handlung.\n")
        if (ctx.petRole.isNotBlank()) append("Pet-Rolle: ${ctx.petRole}\n")
        if (ctx.progressLine.isNotBlank()) append("Fortschritt: Level ${ctx.level} (${ctx.levelTitle}). ${ctx.progressLine}\n")
        if (ctx.memories.isNotEmpty()) append("Erinnerungen: ${ctx.memories.joinToString("; ")}\n")
        append("Erlaubte Aufgabenkategorien: ${ctx.categories.joinToString(", ")}.\n")
        append("Wenn du eine Aufgabe vorschlägst, hänge als letzte Zeile exakt an: ")
        append("TASK_JSON: {\"title\":\"...\",\"description\":\"...\",\"category\":\"<kategorie>\",\"difficulty\":1-6,\"minutes\":Zahl,\"xp\":10-60}")
    }

    private fun personalityText(p: String) = when (p) {
        "STRENG" -> "streng, direkt, diszipliniert und anspruchsvoll"
        "DOMINANT" -> "selbstbewusst und bestimmend innerhalb vereinbarter Grenzen"
        "DUESTER" -> "mysteriös, geheimnisvoll und dramatisch"
        "GEMEIN" -> "spöttisch und provokant, aber nie verletzend bei echten Sorgen"
        "FUERSORGLICH" -> "ruhig, motivierend und unterstützend"
        "EISKALT" -> "eiskalt, knapp, autoritär, lobt fast nie"
        "GNADENLOS" -> "gnadenlos streng, kurze Befehle, militärischer Ton"
        "SADISTISCH" -> "genießerisch fies und provokant im Rollenspiel, respektiert aber jedes Stopp sofort"
        "SPOETTISCH" -> "spöttisch und herablassend im Rollenspiel, nie verletzend bei echten Sorgen"
        "HC_NEUTRAL" -> "geschlechtsneutraler, strenger Dungeon-Master, sachlich und autoritär"
        else -> "verspielt, neckisch und herausfordernd"
    }

    override suspend fun reply(ctx: AiContext, history: List<Pair<Boolean, String>>, userText: String, intent: Intent, candidate: Proposal?): AiReply {
        return try {
            val content = withContext(Dispatchers.IO) { call(ctx, history, userText, candidate) }
            parse(content, candidate)
        } catch (e: Exception) {
            val r = fallback.reply(ctx, history, userText, intent, candidate)
            r.copy(text = r.text + "\n\n(KI-Server nicht erreichbar, Antwort vom Offline-Dungeon-Master.)")
        }
    }

    private fun call(ctx: AiContext, history: List<Pair<Boolean, String>>, userText: String, candidate: Proposal?): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt(ctx)))
        history.takeLast(12).forEach { (fromUser, text) ->
            messages.put(JSONObject().put("role", if (fromUser) "user" else "assistant").put("content", text))
        }
        val hint = if (candidate != null) "\n\n[App-Hinweis: Passende geprüfte Aufgabe aus der Bibliothek, die du anbieten kannst: \"${candidate.title}\" – ${candidate.description}]" else ""
        messages.put(JSONObject().put("role", "user").put("content", userText + hint))
        val body = JSONObject().put("model", model).put("messages", messages).put("temperature", 0.9).put("max_tokens", 400)
        val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP " + conn.responseCode)
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Trennt Antworttext und optionalen strukturierten Aufgabenvorschlag. */
        fun parse(content: String, candidate: Proposal?): AiReply {
            val idx = content.indexOf("TASK_JSON:")
            if (idx < 0) return AiReply(content.trim(), null)
            val text = content.substring(0, idx).trim()
            val proposal = try {
                val raw = content.substring(idx + 10)
                val o = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
                Proposal(
                    title = o.optString("title").take(80),
                    description = o.optString("description").take(600),
                    categoryKey = o.optString("category"),
                    difficulty = o.optInt("difficulty", 2),
                    durationMin = o.optInt("minutes", 10),
                    xp = o.optInt("xp", 15).coerceIn(5, 60),
                    fromAi = true
                )
            } catch (_: Exception) { null }
            return AiReply(text.ifBlank { "Ich habe eine Aufgabe für dich." }, proposal ?: candidate)
        }
    }
}
