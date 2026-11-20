package de.kopffrei.core.common.model

/** Art einer Notiz: normale Notiz oder spontaner Gedanke aus dem Gedanken-Parkplatz. */
enum class NoteType { NOTE, THOUGHT }

/** Besitzer von Anhängen und Erinnerungen. */
enum class OwnerType { NOTE, JOURNAL, EVENT, TASK, STANDALONE }

enum class TaskStatus { OPEN, DONE, PAUSED }

/**
 * Zustand einer Erinnerung.
 * PENDING = wartet auf ihren Zeitpunkt, FIRED = wurde angezeigt und wartet auf Bestätigung,
 * DONE = bestätigt/erledigt.
 */
enum class ReminderState { PENDING, FIRED, DONE }

enum class Frequency { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

enum class Mood(val label: String, val emoji: String) {
    VERY_GOOD("Sehr gut", "😄"),
    GOOD("Gut", "🙂"),
    NEUTRAL("Neutral", "😐"),
    STRESSED("Gestresst", "😣"),
    OVERWHELMED("Überfordert", "😵"),
    SAD("Traurig", "😢"),
    EXHAUSTED("Erschöpft", "😴"),
}

enum class ThemeMode(val label: String) {
    DARK("Dunkel"),
    LIGHT("Hell"),
    LOW_STIMULATION("Reizarm"),
    HIGH_CONTRAST("Hoher Kontrast"),
}

enum class AccentPreset(val label: String) {
    TURQUOISE("Türkis"),
    VIOLET("Violett"),
    BLUE("Blau"),
}

/** Frei konfigurierbare Schnellaktion (dauerhaft erreichbarer Knopf). */
enum class QuickAction(val label: String) {
    NOTE("Notiz"),
    VOICE("Einsprechen"),
    TASK("Aufgabe"),
    EVENT("Termin"),
    BRAIN_DUMP("Kopf ausschütten"),
}

/** Karten der Startseite „Mein Kopf heute“. */
enum class DashboardCard(val label: String) {
    QUICK_ACTIONS("Schnellaktionen"),
    MOOD("Stimmung des Tages"),
    NEXT_EVENT("Nächster Termin"),
    TOP_TASKS("Drei wichtigste Aufgaben"),
    REMINDERS("Aktive Erinnerungen"),
    QUICK_THOUGHT("Gedanke festhalten"),
    FOCUS("Fokus-Timer"),
    SHORTCUTS("Meine Schnellzugriffe"),
}

/** Aufgaben-Prioritäten als Zahl 0..3 (sortierbar in SQL). */
object Priority {
    const val LOW = 0
    const val NORMAL = 1
    const val HIGH = 2
    const val URGENT = 3

    fun label(priority: Int): String = when (priority) {
        LOW -> "Niedrig"
        NORMAL -> "Normal"
        HIGH -> "Hoch"
        else -> "Dringend"
    }
}

/**
 * Einträge des „Mehr“-Bereichs. [phase] sagt, in welcher Ausbaustufe der Eintrag fertig wird;
 * nur Phase 1 ist in dieser Version nutzbar, alle anderen sind als „kommt später“ gekennzeichnet.
 */
enum class MoreEntry(val label: String, val phase: Int) {
    JOURNAL("Tagebuch", 1),
    ROUTINES("Routinen", 2),
    AI("KI-Assistent", 2),
    COMMUNITY("Community", 3),
    TASKS("Aufgabenübersicht", 1),
    STATS("Statistiken", 2),
    SETTINGS("Einstellungen", 1),
    PARTNER("Partner", 4),
    ;

    val isAvailable: Boolean get() = phase == 1
}
