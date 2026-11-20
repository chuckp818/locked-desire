package de.kopffrei.core.common.text

/** Freundliche, druckfreie Begrüßung und kurze Hinweise für die Startseite. */
object Greeting {
    fun headline(hour: Int): String = when (hour) {
        in 5..10 -> "Guten Morgen! Was ist heute wichtig?"
        in 11..16 -> "Hallo! Was braucht gerade Platz?"
        in 17..21 -> "Guten Abend! Was darf aus deinem Kopf raus?"
        else -> "Noch wach? Schreib auf, was dich beschäftigt."
    }

    private val LINES = listOf(
        "Ein kleiner Schritt reicht für heute.",
        "Du musst nicht alles heute schaffen.",
        "Aufschreiben entlastet den Kopf.",
        "Fünf Minuten sind auch ein Anfang.",
        "Pausen gehören zum Plan.",
        "Heute zählt, was machbar ist.",
        "Dein Tempo ist in Ordnung.",
        "Gedanken dürfen erst mal nur geparkt werden.",
        "Weniger gleichzeitig, mehr nacheinander.",
        "Gut genug ist oft genug.",
        "Auch ein halber Schritt ist ein Schritt.",
        "Erst atmen, dann entscheiden.",
    )

    fun motivation(epochDay: Long): String = LINES[Math.floorMod(epochDay, LINES.size.toLong()).toInt()]
}
