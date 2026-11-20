package de.kopffrei.core.common.journal

import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.common.text.DateText
import de.kopffrei.core.common.text.TextUtils
import de.kopffrei.core.common.time.TimeUtils
import java.time.ZoneId

/** Ein Tagebucheintrag für Export und Filter (unabhängig von der Datenbank). */
data class JournalItem(
    val title: String,
    val text: String,
    val mood: Mood?,
    val tags: List<String>,
    val createdAt: Long,
)

/** Reine Logik für das Tagebuch: Filter, Tag-Liste und Textexport. */
object JournalLogic {
    /** Stimmungs- und Tag-Filter; `null` bedeutet „kein Filter“. Tags werden ohne Beachtung der Schreibweise verglichen. */
    fun matchesFilter(mood: Mood?, tagsRaw: String, moodFilter: Mood?, tagFilter: String?): Boolean {
        if (moodFilter != null && mood != moodFilter) return false
        if (tagFilter != null && TextUtils.parseTags(tagsRaw).none { it.equals(tagFilter, ignoreCase = true) }) return false
        return true
    }

    /** Alle vorkommenden Tags, ohne Doppelte (Groß-/Kleinschreibung egal), alphabetisch. */
    fun collectTags(tagsRaw: List<String>): List<String> =
        tagsRaw.flatMap { TextUtils.parseTags(it) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /** Titel für die Liste: der Titel, sonst die erste Textzeile, sonst „Ohne Titel“. */
    fun displayTitle(title: String, text: String): String =
        title.trim().ifBlank { TextUtils.truncate(TextUtils.firstLine(text), 80) }.ifBlank { "Ohne Titel" }

    /** Lesbarer Textexport (ältester Eintrag zuerst), z. B. zum Sichern oder Weitergeben. */
    fun exportText(items: List<JournalItem>, zone: ZoneId, nowMillis: Long): String {
        val sb = StringBuilder()
        sb.appendLine("KopfFrei – Tagebuch")
        sb.appendLine("Exportiert am ${stamp(nowMillis, zone)}")
        sb.appendLine("Einträge: ${items.size}")
        sb.appendLine("==============================")
        if (items.isEmpty()) {
            sb.appendLine()
            sb.appendLine("Keine Einträge.")
            return sb.toString()
        }
        items.sortedBy { it.createdAt }.forEach { item ->
            sb.appendLine()
            val heading = item.title.trim()
            sb.appendLine(if (heading.isEmpty()) stamp(item.createdAt, zone) else "${stamp(item.createdAt, zone)} – $heading")
            item.mood?.let { sb.appendLine("Stimmung: ${it.emoji} ${it.label}") }
            if (item.tags.isNotEmpty()) sb.appendLine("Tags: ${TextUtils.joinTags(item.tags)}")
            if (item.text.isNotBlank()) {
                sb.appendLine()
                sb.appendLine(item.text.trimEnd())
            }
            sb.appendLine()
            sb.appendLine("------------------------------")
        }
        return sb.toString()
    }

    private fun stamp(millis: Long, zone: ZoneId): String =
        "${DateText.dateLong(TimeUtils.toLocalDate(millis, zone))}, ${DateText.clock(millis, zone)}"
}
