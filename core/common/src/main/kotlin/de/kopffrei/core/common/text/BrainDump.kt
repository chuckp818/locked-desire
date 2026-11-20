package de.kopffrei.core.common.text

/**
 * „Kopf ausschütten“: Der Nutzer kippt alles ungeordnet in ein Textfeld. Hier wird daraus eine Liste
 * einzelner Gedanken. Jede nicht leere Zeile ist ein Gedanke; Aufzählungszeichen und Nummerierung
 * am Zeilenanfang werden entfernt, damit sie nicht im Titel landen.
 */
object BrainDump {
    private val LEADING_MARKER = Regex("""^\s*(?:[-*•–—]+|\d{1,3}[.)])\s+""")

    fun splitLines(text: String): List<String> =
        text.lineSequence()
            .map { LEADING_MARKER.replace(it, "").trim() }
            .filter { it.isNotEmpty() }
            .toList()
}
