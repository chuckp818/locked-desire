package de.kopffrei.core.common.text

object TextUtils {
    /**
     * SQL-LIKE-Muster für die Suche. Sonderzeichen (% _ \) werden maskiert; im SQL muss
     * `ESCAPE '\'` angegeben werden. Leere Suche → „%“ (alles).
     */
    fun likePattern(query: String): String {
        val q = query.trim()
        if (q.isEmpty()) return "%"
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return "%$escaped%"
    }

    fun firstLine(text: String): String =
        text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""

    fun truncate(text: String, max: Int): String =
        if (text.length <= max) text else text.take((max - 1).coerceAtLeast(0)).trimEnd() + "…"

    /** Kommagetrennte Tags → bereinigte Liste (ohne Duplikate, ohne führendes „#“). */
    fun parseTags(raw: String): List<String> =
        raw.split(',', ';')
            .map { it.trim().removePrefix("#").trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun joinTags(tags: List<String>): String = tags.joinToString(", ")

    /** Lesbare Angabe für Erinnerungsstufen: „30 Min. vorher“, „1 Tag vorher“, „Zur Startzeit“. */
    fun formatOffset(minutes: Int): String = when {
        minutes <= 0 -> "Zur Startzeit"
        minutes == 1440 -> "1 Tag vorher"
        minutes % 1440 == 0 -> "${minutes / 1440} Tage vorher"
        minutes % 60 == 0 -> "${minutes / 60} Std. vorher"
        minutes > 60 -> "${minutes / 60} Std. ${minutes % 60} Min. vorher"
        else -> "$minutes Min. vorher"
    }

    /** Dauer in Minuten lesbar: „25 Min.“, „1 Std.“, „1 Std. 30 Min.“. */
    fun formatMinutes(minutes: Int): String = when {
        minutes < 60 -> "$minutes Min."
        minutes % 60 == 0 -> "${minutes / 60} Std."
        else -> "${minutes / 60} Std. ${minutes % 60} Min."
    }
}
