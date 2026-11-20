package de.kopffrei.core.common.checklist

data class ChecklistItem(val text: String, val checked: Boolean = false)

/**
 * Checklisten werden als einfacher Text gespeichert (eine Zeile je Punkt, „[ ] “ oder „[x] “ als
 * Präfix). So bleiben sie durchsuchbar und exportierbar, ohne eigene Tabelle.
 */
object Checklist {
    private const val CHECKED = "[x] "
    private const val UNCHECKED = "[ ] "

    fun parse(body: String): List<ChecklistItem> =
        body.lines()
            .filter { it.isNotBlank() }
            .map { line ->
                when {
                    line.startsWith(CHECKED, ignoreCase = true) ->
                        ChecklistItem(line.substring(CHECKED.length).trim(), true)
                    line.startsWith(UNCHECKED) ->
                        ChecklistItem(line.substring(UNCHECKED.length).trim(), false)
                    else -> ChecklistItem(line.trim(), false)
                }
            }
            .filter { it.text.isNotEmpty() }

    fun serialize(items: List<ChecklistItem>): String =
        items.filter { it.text.isNotBlank() }
            .joinToString("\n") { (if (it.checked) CHECKED else UNCHECKED) + it.text.trim() }

    /** Normaler Text → Checkliste (jede nicht leere Zeile wird ein Punkt). */
    fun fromPlainText(text: String): List<ChecklistItem> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { ChecklistItem(it) }

    /** Checkliste → normaler Text (Haken gehen verloren). */
    fun toPlainText(items: List<ChecklistItem>): String =
        items.filter { it.text.isNotBlank() }.joinToString("\n") { it.text.trim() }

    /** (erledigt, gesamt) */
    fun progress(items: List<ChecklistItem>): Pair<Int, Int> =
        items.count { it.checked } to items.size

    /** Lesbare Vorschau für Listen: „☐ Milch  ☑ Brot“ als Zeilen. */
    fun preview(body: String, maxItems: Int = 4): String =
        parse(body).take(maxItems).joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
}
