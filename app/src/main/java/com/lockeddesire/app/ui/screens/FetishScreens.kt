package com.lockeddesire.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lockeddesire.app.data.FetishCategory
import com.lockeddesire.app.data.Seed
import com.lockeddesire.app.data.UserFetishPreference
import com.lockeddesire.app.data.now
import com.lockeddesire.app.ui.components.Badge
import com.lockeddesire.app.ui.components.Chips
import com.lockeddesire.app.ui.components.ChoiceChips
import com.lockeddesire.app.ui.components.Confirm
import com.lockeddesire.app.ui.components.GhostButton
import com.lockeddesire.app.ui.components.GoldButton
import com.lockeddesire.app.ui.components.GothicCard
import com.lockeddesire.app.ui.components.Hint
import com.lockeddesire.app.ui.components.LabeledSlider
import com.lockeddesire.app.ui.components.LabeledSwitch
import com.lockeddesire.app.ui.components.LdField
import com.lockeddesire.app.ui.components.LocalNav
import com.lockeddesire.app.ui.components.LocalVm
import com.lockeddesire.app.ui.components.RoulettePanel
import com.lockeddesire.app.ui.components.Screen
import com.lockeddesire.app.ui.components.SectionTitle
import com.lockeddesire.app.ui.components.wheelFor
import com.lockeddesire.app.ui.theme.LD
import com.lockeddesire.app.ui.theme.LocalAccent

val LEVELS = listOf(
    "NONE" to "Keine Angabe", "INTERESTED" to "Interessiert mich", "WANT_TRY" to "Möchte ich ausprobieren",
    "LIKE" to "Mag ich", "LOVE" to "Mag ich besonders", "EXCLUDED" to "Ausgeschlossen"
)
fun levelName(key: String) = LEVELS.firstOrNull { it.first == key }?.second ?: "Keine Angabe"
private val POSITIVE = setOf("INTERESTED", "WANT_TRY", "LIKE", "LOVE")

@Composable
fun FetishUniverseScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val cats by vm.fetishes.collectAsState()
    val prefs by vm.fetishPrefs.collectAsState()
    val byId = prefs.associateBy { it.fetishId }
    var editing by remember { mutableStateOf<FetishCategory?>(null) }
    var newName by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<FetishCategory?>(null) }
    Screen("Fetish Universe", onBack = { nav("back") }) {
        GothicCard {
            Text("Deine Bibliothek für Vorlieben, Kommunikation und freiwillige Dokumentation.")
            Hint("Eine Vorliebe ist nie automatisch eine Zustimmung zu einer konkreten Handlung. Die KI nutzt nur, was du ausdrücklich für Roulette oder Dungeon freigibst.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Mein Profil", Modifier.weight(1f)) { nav("fetishProfile") }
                GhostButton("Roulette", Modifier.weight(1f)) { nav("fetishRoulette") }
            }
        }
        GothicCard(onClick = { nav("hardcore") }) {
            Text("⛓ Hardcore-Dungeon", color = LD.Blood, style = MaterialTheme.typography.titleMedium)
            Text("CBT, Pussy Play, Nipple Play, Anal Play, Toy Play, Chastity, Orgasm Control, Bondage, Impact, Petplay, Humiliation und D/s als Prüfungskategorien aktivieren.")
            Hint("Was du hier als „Ausgeschlossen“ markierst, sperrt die passende Hardcore-Kategorie automatisch.")
        }
        cats.groupBy { it.groupName }.forEach { (group, list) ->
            SectionTitle(group)
            list.forEach { c ->
                val p = byId[c.id]
                GothicCard(onClick = { editing = c }, highlight = p?.level in POSITIVE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Badge(levelName(p?.level ?: "NONE"), if (p?.level == "EXCLUDED") LD.Dim else if (p?.level in POSITIVE) LocalAccent.current else LD.Gold)
                    }
                    if (c.risky) Hint("Nur Vorliebenverwaltung – wird nie als KI-Pflichtaufgabe erzeugt.")
                    if (p != null && (p.rouletteEnabled || p.dungeonEnabled)) Hint(listOfNotNull(if (p.rouletteEnabled) "Roulette" else null, if (p.dungeonEnabled) "Dungeon" else null).joinToString(" · ") + " freigegeben")
                    if (c.custom) GhostButton("Kategorie löschen", Modifier.fillMaxWidth()) { delete = c }
                }
            }
        }
        if (cats.none { it.custom }) SectionTitle("Eigene Kategorien")
        LdField(newName, "Neue eigene Kategorie") { newName = it.take(40) }
        GoldButton("＋ Hinzufügen", Modifier.fillMaxWidth(), enabled = newName.isNotBlank()) {
            val n = newName.trim(); newName = ""
            vm.run { db.fetishDao().insert(FetishCategory(key = "custom_" + now(), groupName = "Eigene Kategorien", name = n, custom = true)) }
        }
    }
    editing?.let { c -> FetishEditDialog(c, byId[c.id]) { editing = null } }
    delete?.let { c -> Confirm("Kategorie löschen", "„${c.name}“ löschen?", "Löschen", { delete = null }) { vm.run { db.fetishDao().delete(c) } } }
}

@Composable
private fun FetishEditDialog(c: FetishCategory, existing: UserFetishPreference?, onDone: () -> Unit) {
    val vm = LocalVm.current
    var p by remember(c.id) { mutableStateOf(existing ?: UserFetishPreference(fetishId = c.id)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text(c.name) },
        text = {
            androidx.compose.foundation.layout.Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChoiceChips(LEVELS, p.level) { p = p.copy(level = it, rouletteEnabled = if (it == "EXCLUDED") false else p.rouletteEnabled, dungeonEnabled = if (it == "EXCLUDED") false else p.dungeonEnabled) }
                LdField(p.note, "Persönliche Notizen", singleLine = false) { p = p.copy(note = it.take(500)) }
                LdField(p.limits, "Grenzen (Begriffe, durch Komma)", singleLine = false) { p = p.copy(limits = it.take(400)) }
                LdField(p.allowedTasks, "Erlaubte Rollenspielaufgaben", singleLine = false) { p = p.copy(allowedTasks = it.take(400)) }
                if (!c.risky && p.level in POSITIVE) {
                    LabeledSwitch("Für Roulette freigeben", p.rouletteEnabled) { p = p.copy(rouletteEnabled = it) }
                    LabeledSwitch("Für Dungeon freigeben", p.dungeonEnabled) { p = p.copy(dungeonEnabled = it) }
                } else if (c.risky) Hint("Diese Kategorie bleibt reine Dokumentation und wird nie als Aufgabe vorgeschlagen.")
            }
        },
        confirmButton = { GoldButton("Speichern") { vm.run { db.fetishDao().save(p.copy(updatedAt = now())); checkAchievements() }; onDone() } },
        dismissButton = { GhostButton("Abbrechen", onClick = onDone) }
    )
}


@Composable
fun FetishProfileScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val cats by vm.fetishes.collectAsState()
    val prefs by vm.fetishPrefs.collectAsState()
    val byFetish = prefs.associateBy { it.fetishId }
    Screen("Fetisch-Profil", onBack = { nav("back") }) {
        LEVELS.filter { it.first != "NONE" }.forEach { (key, label) ->
            val list = cats.filter { byFetish[it.id]?.level == key }
            SectionTitle("$label (${list.size})")
            if (list.isEmpty()) Hint("Noch nichts eingeordnet.")
            list.forEach { c ->
                val p = byFetish[c.id]!!
                GothicCard {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, color = LD.Gold)
                    if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall)
                    if (p.limits.isNotBlank()) Text("Grenzen: ${p.limits}", style = MaterialTheme.typography.bodySmall)
                    if (p.allowedTasks.isNotBlank()) Text("Erlaubt: ${p.allowedTasks}", style = MaterialTheme.typography.bodySmall)
                    if (p.rouletteEnabled || p.dungeonEnabled) Hint(listOfNotNull(if (p.rouletteEnabled) "Roulette" else null, if (p.dungeonEnabled) "Dungeon" else null).joinToString(" · ") + " freigegeben")
                }
            }
        }
        GoldButton("Vorlieben bearbeiten", Modifier.fillMaxWidth()) { nav("fetishUniverse") }
    }
}

@Composable
fun FetishRouletteScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val cats by vm.fetishes.collectAsState()
    val prefs by vm.fetishPrefs.collectAsState()
    val partners by vm.partners.collectAsState()
    val byFetish = prefs.associateBy { it.fetishId }
    val released = cats.filter { !it.risky && byFetish[it.id]?.rouletteEnabled == true && byFetish[it.id]?.level in POSITIVE }
    var maxD by remember { mutableStateOf(3) }
    Screen("Fetisch-Roulette", onBack = { nav("back") }) {
        if (released.isEmpty()) {
            GothicCard {
                Text("Noch keine Kategorien freigegeben.", style = MaterialTheme.typography.titleMedium)
                Text("Markiere im Fetish Universe eine Vorliebe und aktiviere „Für Roulette freigeben“.")
                GoldButton("Zum Fetish Universe", Modifier.fillMaxWidth()) { nav("fetishUniverse") }
            }
        } else {
            Chips(released.map { it.key to it.name }, released.map { it.key }.toSet()) { }
            LabeledSlider("Max. Schwierigkeit", maxD, 1..6, "$maxD · ${Seed.DIFFICULTY_NAMES[maxD - 1]}") { maxD = it }
            RoulettePanel(
                idleLabels = released.map { it.name },
                emptyText = "Für deine freigegebenen Kategorien gibt es gerade keine sichere Aufgabe. Eigene Ideen kannst du im Aufgaben-Editor anlegen.",
                spin = {
                    val tags = released.map { it.key }.toSet()
                    val p = vm.repo.pickTask(tags = tags, partner = partners.any { it.consentGiven }, maxDifficulty = maxD) ?: return@RoulettePanel null
                    val cat = released.firstOrNull { it.key == p.tag }?.name ?: released.first().name
                    val (labels, idx) = wheelFor(released.map { it.name }, cat)
                    Triple(labels, idx, Triple(p, vm.repo.rules.comment(vm.repo.aiContext(), "offer"), cat))
                }
            ) { (p, c, cat), reroll ->
                Text("Kategorie: $cat", color = LD.Gold)
                ProposalFlow(p, c, "FETISH", reroll)
            }
        }
    }
}

@Composable
fun LimitsScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val prefs by vm.prefs.collectAsState()
    val profile by vm.profile.collectAsState()
    val cats by vm.fetishes.collectAsState()
    val fprefs by vm.fetishPrefs.collectAsState()
    var limits by remember(profile.updatedAt) { mutableStateOf(profile.limitsText) }
    var safeword by remember(prefs.safeword) { mutableStateOf(prefs.safeword) }
    val excluded = prefs.excludedCategories.split(",").filter { it.isNotBlank() }.toSet()
    Screen("Grenzen-Manager", onBack = { nav("back") }) {
        GothicCard {
            Text("Deine Grenzen gelten überall: Roulette, Dungeon, KI-Chat und spontane Aufgaben. Die KI kann sie nicht überschreiben.")
        }
        SectionTitle("Safeword")
        LdField(safeword, "Safeword") { safeword = it.take(30) }
        Hint("Schreibst du es im Chat, wird sofort alles gestoppt. Der 🛑-Knopf oben rechts wirkt genauso.")
        GoldButton("Safeword speichern", Modifier.fillMaxWidth(), enabled = safeword.isNotBlank()) { vm.savePrefs(prefs.copy(safeword = safeword.trim())) }
        SectionTitle("Ausgeschlossene Aufgabenkategorien")
        Chips(Seed.CATEGORIES.map { it.key to it.emoji + " " + it.name }, excluded) {
            val n = if (it in excluded) excluded - it else excluded + it
            vm.savePrefs(prefs.copy(excludedCategories = n.joinToString(",")))
        }
        SectionTitle("Verbotene Begriffe")
        LdField(limits, "Begriffe, durch Komma getrennt", singleLine = false) { limits = it.take(600) }
        Hint("Beispiel: Halsband, Knien, Eis. Jede Aufgabe mit einem dieser Wörter wird blockiert.")
        GoldButton("Begriffe speichern", Modifier.fillMaxWidth()) { vm.saveProfile(profile.copy(limitsText = limits)) }
        SectionTitle("Belastungsgrenzen")
        LabeledSlider("Max. Wiederholungen", prefs.fitnessMaxReps, 5..100, "${prefs.fitnessMaxReps}") { vm.savePrefs(prefs.copy(fitnessMaxReps = it)) }
        LabeledSlider("Max. Trainingsdauer", prefs.fitnessMaxMinutes, 5..60, "${prefs.fitnessMaxMinutes} Min") { vm.savePrefs(prefs.copy(fitnessMaxMinutes = it)) }
        LabeledSwitch("Gelenkschonend", prefs.jointFriendly, "Keine Aufgaben mit Belastung für Knie, Handgelenke oder Rücken") { vm.savePrefs(prefs.copy(jointFriendly = it)) }
        SectionTitle("Ausgeschlossene Vorlieben")
        val ex = cats.filter { c -> fprefs.any { it.fetishId == c.id && it.level == "EXCLUDED" } }
        if (ex.isEmpty()) Hint("Keine. Im Fetish Universe kannst du Vorlieben ausschließen.")
        else Text(ex.joinToString(", ") { it.name })
        GhostButton("Fetish Universe öffnen", Modifier.fillMaxWidth()) { nav("fetishUniverse") }
        SectionTitle("Immer gesperrt")
        GothicCard {
            Text("Atemkontrolle, Würgen, Schläge auf Genitalien, starke Kompression, Abbinden, Verletzungen, Substanzen, Schlaf- und Nahrungsentzug, Selbstfesselung ohne Aufsicht, lange Tragezeiten und Handlungen mit Unbeteiligten werden nie als Aufgabe erzeugt.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
