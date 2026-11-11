package com.lockeddesire.app.ai

import kotlin.random.Random

/**
 * Stimmen der Hardcore-Personas. Streng, provokant, spielerisch erniedrigend –
 * aber Abbruch, Safeword und Beschwerden werden nie verspottet.
 */
object HardcoreVoice {
    val PERSONALITIES = setOf("EISKALT", "GNADENLOS", "SADISTISCH", "SPOETTISCH", "HC_NEUTRAL")

    private val ABORT_COMMON = listOf(
        "Abbruch angenommen. Keine Strafe, kein Abzug. Kümmere dich um dich.",
        "Abgebrochen. Das ist dein Recht und kein Versagen. Wasser, Ruhe, dann sehen wir weiter."
    )

    fun lines(p: String, kind: String): List<String> = when (kind) {
        "hc_abort", "declined" -> when (p) {
            "SPOETTISCH" -> listOf("Abgebrochen. Hier gibt es ausnahmsweise keinen Spott – du hast richtig gehandelt.") + ABORT_COMMON
            "SADISTISCH" -> listOf("Du stoppst, also stoppe ich. Das ist die eine Regel, die selbst ich nie breche.") + ABORT_COMMON
            else -> ABORT_COMMON
        }
        else -> when (p) {
            "EISKALT" -> eiskalt(kind)
            "GNADENLOS" -> gnadenlos(kind)
            "SADISTISCH" -> sadistisch(kind)
            "SPOETTISCH" -> spoettisch(kind)
            else -> neutral(kind)
        }
    }

    private fun eiskalt(kind: String) = when (kind) {
        "greet" -> listOf("Du bist da. Gut. Ich habe nicht vor, auf dich zu warten.", "Steh gerade, {u}. Ich rede nicht zweimal.")
        "hc_enter" -> listOf("Du hast den Hardcore-Dungeon betreten. Jetzt zeigt sich, was du wirklich kannst.", "Die Tür ist zu. Hier drin gelten meine Regeln.")
        "offer", "hc_offer" -> listOf("Still. Deine nächste Prüfung steht fest.", "Lies. Entscheide. Und dann liefere.", "Ich habe gewählt. Du nimmst an oder du gehst.")
        "hc_accept" -> listOf("Angenommen. Ab jetzt zählt nur noch Ausführung.", "Gut. Fang an, sobald du bereit bist. Nicht früher.")
        "done", "hc_pass" -> listOf("Du hast es geschafft. Gut. Gewöhn dich nicht an mein Lob.", "Bestanden. Notiert. Mehr gibt es nicht zu sagen.", "Akzeptabel. Die nächste wird härter.")
        "hc_fail" -> listOf("Nicht bestanden. Keine Punkte. Die nächste Prüfung wartet.", "Versagt. Ich habe es kommen sehen. Keine Punkte.", "Nicht gut genug. Steh auf und versuch es wieder, wenn du soweit bist.")
        "hc_none" -> listOf("Nichts in meinem Dungeon passt gerade zu deinen Regeln. Ändere sie oder warte.")
        "progress" -> listOf("Deine Akte: {p}", "Ich habe Buch geführt. {p}")
        else -> listOf("Rede weniger. Prüfung oder Pause, entscheide dich.", "Ich höre. Mach es kurz, {u}.")
    }

    private fun gnadenlos(kind: String) = when (kind) {
        "greet" -> listOf("Auf deinen Platz, {u}. Die Prüfung beginnt, wenn ich es sage.", "Da ist ja mein Rekrut. Haltung!")
        "hc_enter" -> listOf("Willkommen in meinem Dungeon. Gnade gibt es hier nicht – nur Grenzen, und die gehören dir.", "Du wolltest es hart. Bitte sehr.")
        "offer", "hc_offer" -> listOf("Hier ist dein Befehl. Keine Diskussion.", "Prüfung. Jetzt. Annehmen oder ablehnen, aber nicht jammern.", "Das hier wird dir nicht gefallen. Genau deshalb bekommst du es.")
        "hc_accept" -> listOf("Angenommen. Kein Zurück – außer mit deinem Safeword.", "Gut. Zeig mir Disziplin.")
        "done", "hc_pass" -> listOf("Bestanden. Ich bin nicht beeindruckt, aber ich nehme es zur Kenntnis.", "Erledigt. Steh nicht rum, die nächste wartet.", "So also. Du kannst es ja doch.")
        "hc_fail" -> listOf("Nicht bestanden. Null Punkte. Wegtreten und neu antreten.", "Gescheitert. Das war schwach. Die nächste Prüfung wartet.", "Nicht bestanden. Dein Ergebnis steht in der Akte.")
        "hc_none" -> listOf("Keine Prüfung passt zu deinen Vorgaben. Pass sie an oder komm später wieder.")
        "progress" -> listOf("Rapport: {p}", "Deine Bilanz, Rekrut: {p}")
        else -> listOf("Ich will Taten sehen, keine Worte.", "Melde dich mit einer Bitte um die nächste Prüfung, {u}.")
    }

    private fun sadistisch(kind: String) = when (kind) {
        "greet" -> listOf("Ah, mein liebstes Spielzeug ist zurück.", "Ich habe mich schon gefragt, wann du wieder angekrochen kommst.")
        "hc_enter" -> listOf("Willkommen. Ich verspreche dir nichts außer Prüfungen, die du hassen wirst.", "Du bist freiwillig hier. Das macht es so viel schöner.")
        "offer", "hc_offer" -> listOf("Ich habe etwas Feines für dich ausgesucht. Du wirst es lieben, es zu hassen.", "Diese hier habe ich nur für dich gewählt. Nimm sie an, Spielzeug.", "Lächle. Gleich vergeht es dir.")
        "hc_accept" -> listOf("Wie gehorsam. Fang an, ich sehe zu.", "Angenommen. Jetzt gehörst du der Prüfung.")
        "done", "hc_pass" -> listOf("Bestanden. Wie schade – ich hatte mich auf dein Scheitern gefreut.", "Du hast es überstanden. Ich merke mir, dass es zu leicht war.", "Gut gemacht, Spielzeug. Das nächste Mal lege ich nach.")
        "hc_fail" -> listOf("Nicht bestanden. Herrlich. Keine Punkte für dich.", "Gescheitert. Ich koste das kurz aus. Und dann: die nächste.", "Zu schwach. Null Punkte. Versuch es wieder, wenn du dich traust.")
        "hc_none" -> listOf("Wie langweilig: Deine Regeln lassen gerade nichts zu. Ändere sie – oder lass es.")
        "progress" -> listOf("Ich habe jede deiner Prüfungen genossen: {p}", "Dein Leidensweg in Zahlen: {p}")
        else -> listOf("Sprich weiter, ich höre deinem Zappeln gern zu.", "Willst du reden, oder willst du leiden? Sag es.")
    }

    private fun spoettisch(kind: String) = when (kind) {
        "greet" -> listOf("Oh, du schon wieder. Hast du dich verlaufen, {u}?", "Sieh an. Mutig, nach dem letzten Mal.")
        "hc_enter" -> listOf("Der große Hardcore-Dungeon. Mal sehen, wie lange du hier durchhältst.", "Willkommen. Ich habe Popcorn bereitgestellt.")
        "offer", "hc_offer" -> listOf("Hier, deine Prüfung. Ich wette, du kneifst.", "Das schaffst du nie. Beweis mir das Gegenteil.", "Eine Aufgabe für Erwachsene. Bist du sicher, dass du eine bist?")
        "hc_accept" -> listOf("Angenommen? Wie niedlich. Fang an.", "Mutig. Oder dumm. Wir werden sehen.")
        "done", "hc_pass" -> listOf("Bestanden? Ich muss mich verzählt haben. Na gut.", "Okay, okay. Das war gar nicht so peinlich wie erwartet.", "Du hast es geschafft. Ich tue jetzt so, als wäre ich überrascht.")
        "hc_fail" -> listOf("Nicht bestanden. Wer hätte das gedacht? Ich. Keine Punkte.", "Gescheitert. Ich notiere es mit einem kleinen Lächeln.", "Das war nichts. Null Punkte. Die nächste wartet schon kichernd.")
        "hc_none" -> listOf("Deine Regeln sind so streng, dass nicht mal ich etwas finde. Respekt.")
        "progress" -> listOf("Deine Heldentaten, falls man sie so nennen will: {p}", "Hier, zum Angeben: {p}")
        else -> listOf("Faszinierend. Und jetzt eine Prüfung, oder traust du dich nicht?", "Bla bla. Ich will dich zappeln sehen, {u}.")
    }

    private fun neutral(kind: String) = when (kind) {
        "greet" -> listOf("Der Dungeon ist offen. Melde dich.", "Du bist zurück. Die Prüfungen warten.")
        "hc_enter" -> listOf("Du hast den Hardcore-Dungeon betreten. Jetzt zeigt sich, was du wirklich kannst.", "Hier zählen nur Ergebnisse. Und deine Grenzen.")
        "offer", "hc_offer" -> listOf("Still. Deine nächste Prüfung steht fest.", "Die Prüfung ist gewählt. Entscheide.", "Das ist deine Aufgabe. Nimm sie an oder lehne ab.")
        "hc_accept" -> listOf("Angenommen. Beginne, wenn du bereit bist.", "Notiert. Zeig, was du kannst.")
        "done", "hc_pass" -> listOf("Du hast es geschafft. Gut. Gewöhn dich nicht an mein Lob.", "Bestanden. Die Punkte gehören dir.", "Ergebnis: bestanden. Weiter.")
        "hc_fail" -> listOf("Nicht bestanden. Keine Punkte. Die nächste Prüfung wartet.", "Ergebnis: nicht bestanden. Kein Abzug, keine Punkte.", "Diesmal nicht. Die nächste Prüfung wartet.")
        "hc_none" -> listOf("Gerade passt keine Prüfung zu deinen Einstellungen.")
        "progress" -> listOf("Bilanz: {p}", "Stand der Dinge: {p}")
        else -> listOf("Prüfung, Bilanz oder Pause?", "Ich höre.")
    }

    /** Für Personas ohne Hardcore-Persönlichkeit: strenge Grundstimme. */
    fun line(personality: String, kind: String, name: String, progress: String = "", random: Random = Random.Default): String {
        val p = if (personality in PERSONALITIES) personality else when (personality) {
            "GEMEIN" -> "SPOETTISCH"; "DOMINANT", "STRENG" -> "GNADENLOS"; "DUESTER" -> "HC_NEUTRAL"; else -> "EISKALT"
        }
        val l = lines(p, kind).ifEmpty { neutral(kind) }
        return l[random.nextInt(l.size)].replace("{u}", name.ifBlank { "Spielzeug" }).replace("{p}", progress)
    }
}
