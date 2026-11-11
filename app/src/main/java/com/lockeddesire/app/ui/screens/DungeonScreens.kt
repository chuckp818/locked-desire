package com.lockeddesire.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockeddesire.app.data.DungeonChallenge
import com.lockeddesire.app.data.DungeonPhase
import com.lockeddesire.app.data.Seed
import com.lockeddesire.app.data.TimeUtil
import com.lockeddesire.app.engine.DungeonPlanner
import com.lockeddesire.app.engine.Proposal
import com.lockeddesire.app.ui.components.Badge
import com.lockeddesire.app.ui.components.Chips
import com.lockeddesire.app.ui.components.Confirm
import com.lockeddesire.app.ui.components.CountdownTimer
import com.lockeddesire.app.ui.components.GhostButton
import com.lockeddesire.app.ui.components.GoldButton
import com.lockeddesire.app.ui.components.GothicCard
import com.lockeddesire.app.ui.components.Hint
import com.lockeddesire.app.ui.components.LabeledSlider
import com.lockeddesire.app.ui.components.LocalNav
import com.lockeddesire.app.ui.components.LocalVm
import com.lockeddesire.app.ui.components.RoulettePanel
import com.lockeddesire.app.ui.components.Screen
import com.lockeddesire.app.ui.components.SectionTitle
import com.lockeddesire.app.ui.components.XpBar
import com.lockeddesire.app.ui.components.wheelFor
import com.lockeddesire.app.ui.theme.LD
import com.lockeddesire.app.ui.theme.LocalAccent
import kotlinx.coroutines.flow.flowOf

@Composable
fun TaskRouletteScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val partners by vm.partners.collectAsState()
    var selected by remember { mutableStateOf(Seed.CATEGORY_KEYS - "partner") }
    val partnerReady = partners.any { it.consentGiven }
    Screen("Aufgabenroulette", onBack = { nav("back") }) {
        SectionTitle("Kategorien")
        Chips(Seed.CATEGORIES.map { it.key to it.emoji + " " + it.name }, selected) {
            selected = if (it in selected) selected - it else selected + it
        }
        if ("partner" in selected && !partnerReady) Hint("Partner-Challenges erscheinen erst, wenn im Partner-Modus eine Person zugestimmt hat.")
        RoulettePanel(
            idleLabels = Seed.CATEGORIES.filter { it.key in selected }.map { it.name },
            emptyText = "Keine Aufgabe passt zu deiner Auswahl, deinen Grenzen und Belastungsgrenzen. Wähle mehr Kategorien oder passe den Grenzen-Manager an.",
            spin = {
                val p = vm.repo.pickTask(categories = selected, partner = partnerReady) ?: return@RoulettePanel null
                val comment = vm.repo.rules.comment(vm.repo.aiContext(), "offer")
                val (labels, idx) = wheelFor(Seed.CATEGORIES.filter { it.key in selected }.map { it.name }, Seed.CATEGORIES.first { it.key == p.categoryKey }.name)
                Triple(labels, idx, p to comment)
            }
        ) { (p, comment), reroll ->
            ProposalFlow(p, comment, "ROULETTE", reroll)
        }
        GhostButton("Eigene Aufgaben verwalten", Modifier.fillMaxWidth()) { nav("taskEditor") }
        GhostButton("⛓ Hardcore-Prüfung ziehen", Modifier.fillMaxWidth()) { nav("hardcore") }
    }
}

@Composable
fun BdsmDungeonScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val partners by vm.partners.collectAsState()
    var difficulty by remember { mutableIntStateOf(2) }
    var room by remember { mutableStateOf<Int?>(null) }
    var current by remember { mutableStateOf<Pair<Proposal, String>?>(null) }
    var empty by remember { mutableStateOf(false) }
    val partnerReady = partners.any { it.consentGiven }
    fun draw(i: Int) {
        room = i; empty = false
        vm.run {
            val tags = Seed.DUNGEON_ROOMS[i].third
            val p = pickTask(tags = tags, partner = partnerReady, maxDifficulty = difficulty, forRoulette = false)
            if (p == null) { current = null; empty = true } else current = p to rules.comment(aiContext(), "offer")
        }
    }
    Screen("BDSM-Dungeon", onBack = { nav("back") }) {
        GothicCard {
            Text("Einvernehmliches Rollenspiel, Session-Planung und ungefährliche Herausforderungen.", style = MaterialTheme.typography.bodyMedium)
            Hint("Höhere Stufen erhöhen Komplexität und Kombinationen, nie die körperliche Gefahr.")
        }
        LabeledSlider("Schwierigkeit", difficulty, 1..6, "${difficulty} · ${Seed.DIFFICULTY_NAMES[difficulty - 1]}") { difficulty = it }
        SectionTitle("Kammern")
        Seed.DUNGEON_ROOMS.chunked(2).forEachIndexed { ri, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { ci, (name, emoji, _) ->
                    val idx = ri * 2 + ci
                    GothicCard(Modifier.weight(1f), onClick = { draw(idx) }, highlight = room == idx) {
                        Text(emoji, fontSize = 26.sp)
                        Text(name, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (empty) GothicCard { Text("In dieser Kammer passt gerade keine Aufgabe zu deinen Grenzen, der Stufe oder dem Partner-Status.") }
        current?.let { (p, c) -> ProposalFlow(p, c, "DUNGEON_ROOM", { room?.let { draw(it) } }, "Andere Aufgabe") }
        if (room == Seed.DUNGEON_ROOMS.lastIndex) GothicCard {
            Text("Aftercare", style = MaterialTheme.typography.titleMedium, color = LD.Gold)
            Text("Wasser trinken, etwas Warmes anziehen, zudecken, durchatmen. Sprich über das Erlebte oder schreib es ins Tagebuch.")
            GhostButton("Ins Tagebuch", Modifier.fillMaxWidth()) { nav("journal") }
        }
        HardcoreSummaryCard()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("24h-Dungeon", Modifier.weight(1f)) { nav("dungeon24") }
            GhostButton("Grenzen", Modifier.weight(1f)) { nav("limits") }
        }
    }
}

@Composable
fun Dungeon24Screen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val dungeon by vm.dungeon.collectAsState()
    val nowMs by rememberNow()
    var abort by remember { mutableStateOf(false) }
    Screen("24-Stunden-Dungeon", onBack = { nav("back") }) {
        val ds = dungeon
        if (ds == null) {
            Dungeon24Setup()
        } else {
            val phases by remember(ds.id) { vm.db.dungeonDao().phases(ds.id) }.collectAsState(emptyList<DungeonPhase>())
            val challenges by remember(ds.id) { vm.db.dungeonDao().challenges(ds.id) }.collectAsState(emptyList<DungeonChallenge>())
            val ref = if (ds.pausedAt > 0) ds.pausedAt else nowMs
            val phaseIdx = DungeonPlanner.phaseAt(ds.startedAt, ref)
            GothicCard(highlight = true) {
                Text("Phase ${phaseIdx + 1}: ${DungeonPlanner.PHASES[phaseIdx]}", style = MaterialTheme.typography.headlineSmall, color = LD.Gold)
                Text("Stufe ${ds.difficulty} · ${Seed.DIFFICULTY_NAMES[ds.difficulty - 1]}")
                XpBar(((ref - ds.startedAt).toFloat() / (ds.endsAt - ds.startedAt)).coerceIn(0f, 1f),
                    if (ds.pausedAt > 0) "Pausiert" else "Noch ${TimeUtil.duration(ds.endsAt - nowMs)}")
                val done = challenges.count { it.status == "DONE" }
                Text("$done von ${challenges.size} Aufgaben erledigt", color = LD.Dim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ds.pausedAt > 0) GoldButton("Fortsetzen", Modifier.weight(1f)) { vm.run { resumeDungeon() } }
                    else GhostButton("Pausieren", Modifier.weight(1f)) { vm.run { pauseDungeon() } }
                    GhostButton("Beenden", Modifier.weight(1f)) { abort = true }
                }
                Hint("Pausen und Abbrüche werden nie bestraft. Verdiente XP bleiben.")
            }
            phases.forEach { ph ->
                SectionTitle("${ph.idx + 1}. ${ph.name} (Stunde ${ph.startHour}–${ph.endHour})" + if (ph.completed) " ✔" else "")
                challenges.filter { it.phaseIdx == ph.idx }.forEach { ch -> DungeonChallengeCard(ch, nowMs, ds.pausedAt > 0) }
            }
        }
    }
    if (abort) Confirm("Dungeon beenden", "Den 24-Stunden-Dungeon jetzt beenden? Es gibt keine Strafe, deine XP bleiben erhalten.", "Beenden", { abort = false }) {
        vm.run { abortDungeon() }
    }
}

@Composable
private fun Dungeon24Setup() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val notif by vm.notif.collectAsState()
    val persona by vm.persona.collectAsState()
    var difficulty by remember { mutableIntStateOf(2) }
    var count by remember { mutableIntStateOf(12) }
    var cats by remember { mutableStateOf(setOf("fitness", "selfcontrol", "dungeon", "roleplay", "sensual")) }
    GothicCard {
        Text("Vier Phasen, 24 Stunden", style = MaterialTheme.typography.titleLarge, color = LD.Gold)
        DungeonPlanner.PHASES.forEachIndexed { i, n ->
            Text("${i + 1}. $n · Stunde ${i * 6}–${(i + 1) * 6}", style = MaterialTheme.typography.bodyMedium)
        }
        Hint("Die Schwierigkeit steigt im Verlauf, Aufgaben in Ruhezeiten werden übersprungen. Jede Aufgabe bleibt freiwillig.")
    }
    LabeledSlider("Schwierigkeitsgrad", difficulty, 1..6, "$difficulty · ${Seed.DIFFICULTY_NAMES[difficulty - 1]}") { difficulty = it }
    LabeledSlider("Aufgabenanzahl", count, 4..32, count.toString()) { count = it }
    SectionTitle("Kategorien")
    Chips(Seed.CATEGORIES.filter { it.key != "partner" && it.key != "custom" }.map { it.key to it.emoji + " " + it.name } + ("custom" to "✍ Eigene"), cats) {
        cats = if (it in cats) cats - it else cats + it
    }
    val hcs by remember { vm.db.hardcoreDao().settings() }.collectAsState(null)
    GothicCard {
        val h = hcs
        if (h != null && h.confirmed) {
            com.lockeddesire.app.ui.components.LabeledSwitch("⛓ Hardcore-Prüfungen einplanen", h.includeIn24h, "Bis Stufe ${h.difficulty} · ${com.lockeddesire.app.engine.Hardcore.DIFFICULTY_NAMES[h.difficulty - 1]}") {
                vm.run { hc.saveSettings(h.copy(includeIn24h = it)) }
            }
            Hint("Hardcore-Aufgaben können bestanden, nicht bestanden oder abgebrochen werden. Nur Bestanden bringt XP.")
        } else {
            Text("⛓ Hardcore-Prüfungen")
            Hint("Öffne zuerst einmal den Hardcore-Dungeon, um sie hier einplanen zu können.")
            GhostButton("Hardcore-Dungeon öffnen", Modifier.fillMaxWidth()) { nav("hardcore") }
        }
    }
    GothicCard {
        Text("Ruhezeiten: ${TimeUtil.clock(notif.quietStart)} – ${TimeUtil.clock(notif.quietEnd)}")
        Text("KI-Begleitung: ${persona?.name ?: "-"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Ruhezeiten", Modifier.weight(1f)) { nav("notifSettings") }
            GhostButton("Grenzen", Modifier.weight(1f)) { nav("limits") }
        }
        GhostButton("KI-Persona wählen", Modifier.fillMaxWidth()) { nav("personas") }
    }
    GoldButton("🏰 Dungeon öffnen", Modifier.fillMaxWidth(), enabled = cats.isNotEmpty()) {
        vm.run {
            val n = startDungeon(difficulty, cats, count)
            emit("INFO", if (n > 0) "Dein Dungeon ist geöffnet: $n Aufgaben geplant." else "Keine passenden Aufgaben gefunden. Wähle mehr Kategorien oder prüfe deine Grenzen.")
        }
    }
}

@Composable
private fun DungeonChallengeCard(ch: DungeonChallenge, nowMs: Long, paused: Boolean) {
    val vm = LocalVm.current
    var active by remember(ch.id) { mutableStateOf(false) }
    val available = ch.scheduledAt <= nowMs && !paused
    GothicCard(highlight = available && ch.status == "PENDING") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ch.title, style = MaterialTheme.typography.titleMedium, color = if (ch.status == "PENDING") LD.Gold else LD.Dim, modifier = Modifier.weight(1f))
            Badge(if (ch.status == "PENDING") (if (available) "Jetzt" else TimeUtil.dateTime(ch.scheduledAt).takeLast(5)) else statusText(ch.status),
                if (ch.status == "DONE") LocalAccent.current else LD.Gold)
        }
        if (ch.status == "PENDING" && available) {
            Text(ch.description)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(categoryName(ch.categoryKey)); if (ch.durationMin > 0) Badge("${ch.durationMin} Min"); Badge("+${ch.xp} XP")
            }
            if (!active) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton("Annehmen", Modifier.weight(1f)) { active = true }
                GhostButton("Überspringen", Modifier.weight(1f)) { vm.run { dungeonChallenge(ch, "SKIPPED") } }
            } else {
                if (ch.durationMin > 0) CountdownTimer(ch.durationMin * 60)
                if (ch.categoryKey in com.lockeddesire.app.engine.Hardcore.KEYS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton("Bestanden", Modifier.weight(1f)) { vm.run { dungeonChallenge(ch, "DONE") } }
                        GhostButton("Nicht bestanden", Modifier.weight(1f)) { vm.run { dungeonChallenge(ch, "FAILED") } }
                    }
                    GhostButton("Abbrechen", Modifier.fillMaxWidth()) { vm.run { dungeonChallenge(ch, "ABORTED") } }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton("Erledigt", Modifier.weight(1f)) { vm.run { dungeonChallenge(ch, "DONE") } }
                    GhostButton("Abbrechen", Modifier.weight(1f)) { vm.run { dungeonChallenge(ch, "ABORTED") } }
                }
            }
        }
    }
}

@Composable
fun ChastityScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val active by vm.chastity.collectAsState()
    val history by vm.chastityHistory.collectAsState()
    val nowMs by rememberNow()
    var goalDays by remember { mutableIntStateOf(1) }
    var goalHours by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    var end by remember { mutableStateOf(false) }
    var extend by remember { mutableStateOf(false) }
    Screen("Keuschheit", onBack = { nav("back") }) {
        GothicCard {
            Text("⚠ Ein körperliches Keuschheitsgerät muss jederzeit unabhängig von der App entfernt werden können. Bei Druck, Taubheit, Verfärbung oder Schmerzen sofort ablegen.", style = MaterialTheme.typography.bodySmall, color = LD.Gold)
        }
        val cs = active
        if (cs == null) {
            SectionTitle("Neue Periode")
            LabeledSlider("Ziel: Tage", goalDays, 0..60, "$goalDays") { goalDays = it }
            LabeledSlider("Ziel: Stunden", goalHours, 0..23, "$goalHours") { goalHours = it }
            Hint(if (goalDays == 0 && goalHours == 0) "Ohne Ziel – offene Periode." else "Ziel: $goalDays Tage, $goalHours Stunden")
            com.lockeddesire.app.ui.components.LdField(note, "Notiz (optional)", singleLine = false) { note = it.take(300) }
            GoldButton("🔒 Periode starten", Modifier.fillMaxWidth()) { vm.run { startChastity(goalDays, goalHours, note) }; note = "" }
        } else {
            val elapsed = vm.repo.chastityElapsed(cs, nowMs)
            GothicCard(highlight = true) {
                Text(TimeUtil.duration(elapsed), style = MaterialTheme.typography.displaySmall, color = LD.Gold, modifier = Modifier.fillMaxWidth())
                if (cs.goalAt > 0) {
                    val total = (cs.goalAt - cs.startedAt - cs.pausedTotalMs).coerceAtLeast(1)
                    XpBar(elapsed.toFloat() / total, if (nowMs >= cs.goalAt && cs.pausedAt == 0L) "Ziel erreicht!" else "Ziel am ${TimeUtil.dateTime(cs.goalAt)}")
                } else Hint("Offene Periode ohne Ziel")
                if (cs.pausedAt > 0) Text("Pausiert", color = LD.Gold)
                if (cs.note.isNotBlank()) Text("„${cs.note}“", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cs.pausedAt > 0) GoldButton("Fortsetzen", Modifier.weight(1f)) { vm.run { resumeChastity() } }
                    else GhostButton("Pause", Modifier.weight(1f)) { vm.run { pauseChastity() } }
                    GhostButton("Beenden", Modifier.weight(1f)) { end = true }
                }
                if (cs.goalAt > 0 && nowMs >= cs.goalAt) {
                    Text("Du hast dein Ziel erreicht. Willst du freiwillig noch einen Tag versuchen? Nein ist eine völlig gute Antwort.")
                    GhostButton("Freiwillig verlängern …", Modifier.fillMaxWidth()) { extend = true }
                }
            }
        }
        SectionTitle("Rekorde")
        val ended = history.filter { it.status == "ENDED" }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val best = history.maxOfOrNull { vm.repo.chastityElapsed(it, nowMs) } ?: 0L
            com.lockeddesire.app.ui.components.StatTile("${best / TimeUtil.DAY}T ${(best % TimeUtil.DAY) / 3_600_000L}h", "Längste Periode", Modifier.weight(1f), "🏅")
            com.lockeddesire.app.ui.components.StatTile(ended.size.toString(), "Perioden", Modifier.weight(1f), "🔒")
        }
        SectionTitle("Kalender")
        GothicCard {
            val days = HashSet<Long>()
            history.forEach { s ->
                val endT = if (s.endedAt > 0) s.endedAt else nowMs
                var t = s.startedAt
                while (t <= endT) { days += TimeUtil.dayNumber(t); t += TimeUtil.DAY }
                days += TimeUtil.dayNumber(endT)
            }
            MonthCalendar(days)
        }
        SectionTitle("Historie")
        ended.take(30).forEach { s ->
            GothicCard {
                Text("${TimeUtil.date(s.startedAt)} – ${TimeUtil.date(s.endedAt)}", color = LD.Gold)
                Text("Dauer: ${TimeUtil.duration(vm.repo.chastityElapsed(s))}")
                if (s.note.isNotBlank()) Hint(s.note)
            }
        }
        if (ended.isEmpty()) Hint("Noch keine abgeschlossenen Perioden.")
    }
    if (end) Confirm("Periode beenden", "Jetzt beenden? Beenden ist jederzeit erlaubt und wird nicht bestraft.", "Beenden", { end = false }) { vm.run { endChastity() } }
    if (extend) {
        var hours by remember { mutableIntStateOf(24) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { extend = false },
            title = { Text("Freiwillig verlängern") },
            text = { Column { Text("Nur wenn du das wirklich möchtest und dich gut fühlst."); LabeledSlider("Stunden", hours, 1..72, "$hours") { hours = it } } },
            confirmButton = { GoldButton("Verlängern") { extend = false; vm.run { extendChastityGoal(hours) } } },
            dismissButton = { GhostButton("Nein, danke") { extend = false } }
        )
    }
}
