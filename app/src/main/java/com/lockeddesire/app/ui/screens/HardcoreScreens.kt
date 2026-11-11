package com.lockeddesire.app.ui.screens

import android.app.Application
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lockeddesire.app.LockedDesireApp
import com.lockeddesire.app.ai.HardcoreVoice
import com.lockeddesire.app.data.AiPersona
import com.lockeddesire.app.data.HardcoreRun
import com.lockeddesire.app.data.HardcoreSettings
import com.lockeddesire.app.data.HcResult
import com.lockeddesire.app.data.TimeUtil
import com.lockeddesire.app.engine.Hardcore
import com.lockeddesire.app.engine.HardcoreEngine
import com.lockeddesire.app.ui.components.AvatarBubble
import com.lockeddesire.app.ui.components.Badge
import com.lockeddesire.app.ui.components.Chips
import com.lockeddesire.app.ui.components.ChoiceChips
import com.lockeddesire.app.ui.components.Confirm
import com.lockeddesire.app.ui.components.CountdownTimer
import com.lockeddesire.app.ui.components.Fx
import com.lockeddesire.app.ui.components.GhostButton
import com.lockeddesire.app.ui.components.GoldButton
import com.lockeddesire.app.ui.components.GothicCard
import com.lockeddesire.app.ui.components.Hint
import com.lockeddesire.app.ui.components.LabeledSlider
import com.lockeddesire.app.ui.components.LabeledSwitch
import com.lockeddesire.app.ui.components.LocalNav
import com.lockeddesire.app.ui.components.LocalVm
import com.lockeddesire.app.ui.components.RouletteWheel
import com.lockeddesire.app.ui.components.Screen
import com.lockeddesire.app.ui.components.SectionTitle
import com.lockeddesire.app.ui.components.StatTile
import com.lockeddesire.app.ui.components.XpBar
import com.lockeddesire.app.ui.components.wheelFor
import com.lockeddesire.app.ui.theme.LD
import com.lockeddesire.app.ui.theme.LocalEffects
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sin

/** ViewModel des Hardcore-Dungeons. */
class HardcoreViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as LockedDesireApp).repo
    private val dao = repo.db.hardcoreDao()

    val settings: StateFlow<HardcoreSettings> = dao.settings().map { it ?: HardcoreSettings() }.stateIn(viewModelScope, SharingStarted.Eagerly, HardcoreSettings())
    val open: StateFlow<HardcoreRun?> = dao.open().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val runs: StateFlow<List<HardcoreRun>> = dao.runs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val personas: StateFlow<List<AiPersona>> = repo.db.aiDao().personas().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tasks: StateFlow<List<com.lockeddesire.app.data.HardcoreTask>> = dao.tasks().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val line = MutableStateFlow("")
    val result = MutableStateFlow<HcResult?>(null)
    val empty = MutableStateFlow(false)
    val busy = MutableStateFlow(false)

    private fun go(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { repo.emit("INFO", "Das hat nicht geklappt: ${e.message ?: e.javaClass.simpleName}") }
    }

    fun enter() = go { line.value = repo.hc.enterLine() }

    fun save(s: HardcoreSettings) = go { repo.hc.saveSettings(s) }

    /** Wählt eine neue Prüfung; das Rad zeigt danach die Kategorie. */
    fun newTrial(onReady: (HardcoreRun?) -> Unit = {}) = go {
        busy.value = true
        val r = repo.hc.newTrial()
        empty.value = r == null
        line.value = if (r == null) repo.hc.noneLine() else repo.hc.offerLine()
        busy.value = false
        onReady(r)
    }

    fun toggle(t: com.lockeddesire.app.data.HardcoreTask) = go { dao.update(t.copy(status = if (t.status == "ACTIVE") "DISABLED" else "ACTIVE")) }

    fun accept(id: Long) = go { val t = repo.hc.accept(id); if (t.isNotBlank()) line.value = t }
    fun start(id: Long) = go { repo.hc.start(id) }
    fun step(id: Long, done: Boolean) = go { repo.hc.step(id, done) }
    fun finish(id: Long, status: String) = go {
        val r = repo.hc.finish(id, status)
        if (r != null) { result.value = r; line.value = r.verdict }
    }
}

private val DIFF_COLORS = listOf(Color(0xFF8E8791), Color(0xFFB59662), Color(0xFFB51230), Color(0xFFD01A3A), Color(0xFFFF2D55), Color(0xFFFF0033))

@Composable
fun HardcoreScreen() {
    val app = LocalVm.current
    val nav = LocalNav.current
    val vm: HardcoreViewModel = viewModel()
    val s by vm.settings.collectAsState()
    Screen("Hardcore-Dungeon", onBack = { nav("back") }, hardcore = true) {
        if (s.chains) ChainBanner()
        if (!s.confirmed) HardcoreGate(vm, s) else HardcoreMain(vm, s)
    }
    val result by vm.result.collectAsState()
    result?.let { r -> ResultOverlay(r, onNew = { vm.result.value = null; vm.newTrial() }, onClose = { vm.result.value = null }, onJournal = { vm.result.value = null; nav("journal") }) }
    LaunchedEffect(Unit) { app.repo.ensureSeeded() }
}

/** Animierte Kette mit Schloss. */
@Composable
fun ChainBanner() {
    val effects = LocalEffects.current
    val shift by if (effects) rememberInfiniteTransition(label = "chain").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "s"
    ) else remember { mutableStateOf(0f) }
    Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val link = 34f
            val y = size.height / 2f
            val offset = shift * link * 2
            var x = -link * 2 + offset
            var i = 0
            val metal = Brush.verticalGradient(listOf(Color(0xFFE6E1E8), Color(0xFF6E6873), Color(0xFFB59662)), startY = y - 14f, endY = y + 14f)
            while (x < size.width + link) {
                val sag = sin((x / size.width) * Math.PI).toFloat() * 8f
                if (i % 2 == 0) drawOval(metal, Offset(x, y - 9f + sag), Size(link * 1.25f, 18f), style = Stroke(5f))
                else drawOval(metal, Offset(x + 4f, y - 3f + sag), Size(link * 1.1f, 6f), style = Stroke(4f))
                x += link; i++
            }
        }
        Text("🔒", fontSize = 26.sp)
    }
}

@Composable
private fun DifficultyPips(d: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..6) Text(if (i <= d) "💀" else "·", fontSize = if (i <= d) 16.sp else 18.sp, color = if (i <= d) DIFF_COLORS[d - 1] else LD.Dim)
        Spacer(Modifier.width(6.dp))
        Text(Hardcore.DIFFICULTY_NAMES[d.coerceIn(1, 6) - 1].uppercase(), color = DIFF_COLORS[d.coerceIn(1, 6) - 1], fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HardcoreGate(vm: HardcoreViewModel, s: HardcoreSettings) {
    var read by remember { mutableStateOf(false) }
    GothicCard(highlight = true) {
        Text("DER HARDCORE-DUNGEON", style = MaterialTheme.typography.headlineSmall, color = LD.Blood, fontWeight = FontWeight.Bold)
        Text("Hier führt eine strenge Persona. Sie wählt deine Prüfungen, bewertet sie und spart nicht mit Spott. Prüfungen können scheitern.")
        Text("Die Regeln, die nie gebrochen werden:", color = LD.Gold)
        Text("• Jede Prüfung musst du annehmen. Ablehnen und Abbrechen sind immer erlaubt.\n• Nicht bestanden und Abgebrochen geben 0 XP – nie einen Abzug, nie eine Ersatzstrafe.\n• Safeword 🛑 oder Beschwerden stoppen sofort alles.\n• Riskante Kategorien wie CBT oder Pussy Play gibt es nur als Session-Rahmen nach deiner eigenen Liste – keine Schläge auf Genitalien, nichts Einschnürendes, keine extremen Tragezeiten.\n• Echte Fesselung nur mit anwesender Partnerperson.")
    }
    HardcoreSettingsBlock(vm, s)
    LabeledSwitch("Ich habe die Regeln gelesen und bin mindestens 18", read) { read = it }
    GoldButton("⛓ DUNGEON BETRETEN", Modifier.fillMaxWidth(), enabled = read && s.categories.isNotBlank()) {
        vm.save(s.copy(confirmed = true)); vm.enter()
    }
}

@Composable
private fun HardcoreSettingsBlock(vm: HardcoreViewModel, s: HardcoreSettings) {
    val nav = LocalNav.current
    val personas by vm.personas.collectAsState()
    val enabled = s.categories.split(",").filter { it.isNotBlank() }.toSet()
    val anatomy = s.anatomy.split(",").filter { it.isNotBlank() }.toSet()
    SectionTitle("Schwierigkeit")
    LabeledSlider("Stufe", s.difficulty, 1..6, "${s.difficulty} · ${Hardcore.DIFFICULTY_NAMES[s.difficulty - 1]}") { vm.save(s.copy(difficulty = it)) }
    Hint(Hardcore.DIFFICULTY_TEXT[s.difficulty - 1] + ". Höhere Stufen: mehr Teilaufgaben, mehr Kombinationen, längere nichtkörperliche Phasen, mehr XP – nie mehr körperliche Gefahr.")
    SectionTitle("Kategorien")
    Chips(Hardcore.CATEGORIES.map { it.key to it.emoji + " " + it.name }, enabled) { k ->
        val next = if (k in enabled) enabled - k else enabled + k
        vm.save(s.copy(categories = next.joinToString(",")))
    }
    if ("hc_cbt" in enabled && "PENIS" !in anatomy || "hc_pussy" in enabled && "VULVA" !in anatomy)
        Hint("CBT und Pussy Play erscheinen nur mit passender anatomischer Angabe. Sie wird nie aus dem Geschlecht abgeleitet.")
    SectionTitle("Anatomie (freiwillig)")
    Chips(Hardcore.ANATOMY, anatomy) { k ->
        val next = if (k in anatomy) anatomy - k else anatomy + k
        vm.save(s.copy(anatomy = next.joinToString(",")))
    }
    SectionTitle("Dungeon-Master")
    val hcFirst = personas.sortedByDescending { it.personality in HardcoreVoice.PERSONALITIES }
    val selected = (personas.firstOrNull { it.id == s.personaId } ?: hcFirst.firstOrNull())?.id ?: 0L
    ChoiceChips(hcFirst.map { it.id.toString() to it.avatar + " " + it.name }, selected.toString()) { vm.save(s.copy(personaId = it.toLong())) }
    GhostButton("Personas bearbeiten", Modifier.fillMaxWidth()) { nav("personas") }
    SectionTitle("Verknüpfungen")
    LabeledSwitch("Prüfungen im 24-Stunden-Dungeon einplanen", s.includeIn24h) { vm.save(s.copy(includeIn24h = it)) }
    LabeledSwitch("Spontane Prüfungen ankündigen", s.notify, "Bis zu ${Hardcore.spontaneousPerDay(s.difficulty)} pro Tag, Ruhezeiten gelten") { vm.save(s.copy(notify = it)) }
    LabeledSwitch("Animierte Ketten", s.chains) { vm.save(s.copy(chains = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostButton("Grenzen", Modifier.weight(1f)) { nav("limits") }
        GhostButton("Fetisch-Profil", Modifier.weight(1f)) { nav("fetishProfile") }
        GhostButton("Toys", Modifier.weight(1f)) { nav("toys") }
    }
}

@Composable
private fun HardcoreMain(vm: HardcoreViewModel, s: HardcoreSettings) {
    val app = LocalVm.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val open by vm.open.collectAsState()
    val runs by vm.runs.collectAsState()
    val personas by vm.personas.collectAsState()
    val line by vm.line.collectAsState()
    val empty by vm.empty.collectAsState()
    val busy by vm.busy.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var spinKey by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(0) }
    var labels by remember { mutableStateOf(Hardcore.CATEGORIES.map { it.name }) }
    var revealed by remember { mutableStateOf(true) }
    var confirmAbort by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) { if (line.isBlank()) vm.enter() }
    LaunchedEffect(line) { if (line.isNotBlank()) app.speak(line) }

    val persona = personas.firstOrNull { it.id == (open?.personaId ?: s.personaId) }
        ?: personas.firstOrNull { it.personality in HardcoreVoice.PERSONALITIES } ?: personas.firstOrNull()
    val stats = remember(runs) { HardcoreEngine.stats(runs) { TimeUtil.dayNumber(it) } }

    // Kopf: Persona, Stimme, Stufe, Rang
    GothicCard(highlight = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBubble(persona?.avatar ?: "🗝", 84)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(persona?.name ?: "Dungeon-Master", style = MaterialTheme.typography.headlineSmall, color = LD.Gold)
                Text("Anrede: ${persona?.salutation ?: "-"}", style = MaterialTheme.typography.labelMedium, color = LD.Dim)
                DifficultyPips(s.difficulty)
            }
        }
        if (line.isNotBlank()) Text("„$line“", style = MaterialTheme.typography.titleMedium, color = LD.Grey, fontWeight = FontWeight.SemiBold)
        val next = Hardcore.nextRank(stats.passed)
        val prevAt = Hardcore.ranks().last { it.first <= stats.passed }.first
        XpBar(if (next == null) 1f else (stats.passed - prevAt).toFloat() / (next.first - prevAt),
            "Rang: ${Hardcore.rankFor(stats.passed)} · ${stats.passed} bestanden" + (next?.let { " · ${it.second} ab ${it.first}" } ?: ""))
    }

    val run = open
    if (run == null || !revealed) {
        RouletteWheel(labels, spinKey, target) {
            revealed = true
            Fx.feedback(context, app.prefs.value, strong = true)
        }
        GoldButton(if (busy || !revealed) "Die Persona wählt …" else "⛓ NEUE PRÜFUNG", Modifier.fillMaxWidth(), enabled = !busy && revealed) {
            vm.newTrial { r ->
                if (r != null) {
                    val names = Hardcore.CATEGORIES.filter { it.key in s.categories.split(",") }.map { it.name }
                    val (l, i) = wheelFor(names, Hardcore.category(r.categoryKey)?.name ?: names.first())
                    labels = l; target = i; revealed = false; spinKey++
                }
            }
        }
        if (empty) GothicCard {
            Text("Keine Prüfung passt gerade zu deinen Kategorien, Grenzen, Toys, deiner Rolle oder dem Partner-Status.")
            Hint("Aktiviere mehr Kategorien, trage passende Toys ein oder prüfe Grenzen und Anatomie-Angaben.")
        }
    } else {
        TrialCard(vm, run, onAbort = { confirmAbort = run.id })
    }

    SectionTitle("Bilanz")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(stats.passed.toString(), "Bestanden", Modifier.weight(1f), "✔")
        StatTile(stats.failed.toString(), "Nicht bestanden", Modifier.weight(1f), "✖")
        StatTile(stats.aborted.toString(), "Abgebrochen", Modifier.weight(1f), "🛑")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("${stats.successRate} %", "Erfolgsquote", Modifier.weight(1f), "📈")
        StatTile(if (stats.highestPassed == 0) "–" else Hardcore.DIFFICULTY_NAMES[stats.highestPassed - 1], "Höchste Stufe", Modifier.weight(1f), "💀")
        StatTile(stats.xp.toString(), "Hardcore-XP", Modifier.weight(1f), "⭐")
    }
    GhostButton("Alle Statistiken", Modifier.fillMaxWidth()) { nav("stats") }

    SectionTitle("Letzte Prüfungen")
    val done = runs.filter { it.status !in Hardcore.OPEN_STATES }.take(8)
    if (done.isEmpty()) Hint("Noch keine Prüfung abgeschlossen.")
    done.forEach { r ->
        GothicCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, color = LD.Gold, modifier = Modifier.weight(1f), maxLines = 2)
                Badge(Hardcore.statusLabel(r.status), statusColor(r.status))
            }
            Text("${TimeUtil.dateTime(r.endedAt.takeIf { it > 0 } ?: r.createdAt)} · Stufe ${r.difficulty} · ${if (r.status == Hardcore.PASSED) "+${r.xp} XP" else "0 XP"}", style = MaterialTheme.typography.labelSmall, color = LD.Dim)
            if (r.verdict.isNotBlank()) Text("„${r.verdict}“", style = MaterialTheme.typography.bodySmall)
        }
    }

    GhostButton(if (showSettings) "Einstellungen schließen" else "⚙ Dungeon-Einstellungen", Modifier.fillMaxWidth()) { showSettings = !showSettings }
    if (showSettings) HardcoreSettingsBlock(vm, s)
    var showLib by remember { mutableStateOf(false) }
    GhostButton(if (showLib) "Bibliothek schließen" else "📜 Aufgabenbibliothek", Modifier.fillMaxWidth()) { showLib = !showLib }
    if (showLib) HardcoreLibrary(vm)

    confirmAbort?.let { id ->
        Confirm("Abbrechen", "Prüfung sofort beenden? 0 XP, kein Abzug, keine Ersatzstrafe.", "Abbrechen", { confirmAbort = null }) {
            vm.finish(id, Hardcore.ABORTED)
        }
    }
}

private fun statusColor(s: String) = when (s) {
    Hardcore.PASSED -> LD.Gold; Hardcore.FAILED -> LD.Blood; Hardcore.ABORTED -> LD.Dim; else -> LD.Grey
}

@Composable
private fun TrialCard(vm: HardcoreViewModel, r: HardcoreRun, onAbort: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color.Black, LD.Bordeaux.copy(alpha = 0.55f), Color.Black)))
            .border(2.dp, Brush.linearGradient(listOf(Color(0xFFE6E1E8), LD.Blood, Color(0xFF6E6873), LD.Gold)), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PRÜFUNG", style = MaterialTheme.typography.labelLarge, color = LD.Blood, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Badge(Hardcore.statusLabel(r.status), if (r.status == Hardcore.ACTIVE) LD.Neon else LD.Gold)
        }
        Text(r.title, style = MaterialTheme.typography.headlineSmall, color = LD.Gold, fontWeight = FontWeight.Bold)
        DifficultyPips(r.difficulty)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.categories.split(",").filter { it.isNotBlank() }.forEach { Badge(categoryName(it)) }
            Badge("+${r.xp} XP")
            if (r.durationMin > 0) Badge("${r.durationMin} Min")
            if (r.stepsTotal > 1) Badge("${r.stepsTotal} Teilaufgaben")
            if (r.toyName.isNotBlank()) Badge("🎁 ${r.toyName}")
            if (r.partner) Badge("💞 Partner")
        }
        val steps = r.description.split("\n").filter { it.isNotBlank() }
        if (r.status == Hardcore.ACTIVE && steps.size > 1) {
            steps.forEachIndexed { i, st ->
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = i < r.stepsDone, onCheckedChange = { vm.step(r.id, it) },
                        colors = CheckboxDefaults.colors(checkedColor = LD.Blood, uncheckedColor = LD.Gold))
                    Text(st, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 10.dp))
                }
            }
            XpBar(r.stepsDone.toFloat() / r.stepsTotal, "${r.stepsDone} von ${r.stepsTotal} Teilaufgaben")
        } else Text(r.description, style = MaterialTheme.typography.bodyLarge)

        r.categories.split(",").mapNotNull { Hardcore.category(it) }.forEach {
            Text("⚠ ${it.safetyNote}", style = MaterialTheme.typography.bodySmall, color = LD.Dim)
        }
        when (r.status) {
            Hardcore.AVAILABLE -> {
                GoldButton("PRÜFUNG ANNEHMEN", Modifier.fillMaxWidth()) { vm.accept(r.id) }
                GhostButton("NEUE PRÜFUNG", Modifier.fillMaxWidth()) { vm.newTrial() }
                Hint("Ablehnen kostet nichts. Eine neue Prüfung zählt nicht als gestartet.")
            }
            Hardcore.ACCEPTED -> {
                GoldButton("▶ PRÜFUNG STARTEN", Modifier.fillMaxWidth()) { vm.start(r.id) }
                GhostButton("ABBRECHEN", Modifier.fillMaxWidth(), onClick = onAbort)
            }
            Hardcore.ACTIVE -> {
                if (r.durationMin > 0) CountdownTimer(r.durationMin * 60)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton("BESTANDEN", Modifier.weight(1f)) { vm.finish(r.id, Hardcore.PASSED) }
                    GhostButton("NICHT BESTANDEN", Modifier.weight(1f)) { vm.finish(r.id, Hardcore.FAILED) }
                }
                GhostButton("ABBRECHEN", Modifier.fillMaxWidth(), onClick = onAbort)
                Hint("Bei Schmerz, Taubheit oder Unwohlsein: sofort abbrechen. Das zählt nie gegen dich.")
            }
        }
    }
}

@Composable
private fun ResultOverlay(r: HcResult, onNew: () -> Unit, onClose: () -> Unit, onJournal: () -> Unit) {
    val (label, color) = when (r.status) {
        Hardcore.PASSED -> "BESTANDEN" to LD.Gold
        Hardcore.FAILED -> "NICHT BESTANDEN" to LD.Blood
        else -> "ABGEBROCHEN" to LD.Grey
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)).clickable(enabled = false) {}.padding(24.dp), contentAlignment = Alignment.Center) {
        GothicCard(highlight = true) {
            Text(label, style = MaterialTheme.typography.displaySmall, color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(r.run.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(if (r.xp > 0) "+${r.xp} XP" else "0 XP · kein Abzug", style = MaterialTheme.typography.headlineSmall, color = if (r.xp > 0) LD.Gold else LD.Dim, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text("„${r.verdict}“", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            when (r.status) {
                Hardcore.PASSED -> Hint("Erfolg dokumentiert. Statistik und Dungeon-Fortschritt aktualisiert.")
                Hardcore.FAILED -> Hint("Ergebnis dokumentiert. Keine Punkte, kein Abzug, keine Ersatzstrafe.")
                else -> {
                    Hint("Session beendet. Trink etwas, wärm dich auf, gönn dir Ruhe. Aftercare ist Teil des Spiels.")
                    GhostButton("Ins Tagebuch", Modifier.fillMaxWidth(), onClick = onJournal)
                }
            }
            GoldButton("NEUE PRÜFUNG", Modifier.fillMaxWidth(), onClick = onNew)
            GhostButton("Schließen", Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

/** Kurzübersicht für Dashboard, Profil und Statistik. */
@Composable
fun HardcoreSummaryCard(compact: Boolean = false) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val runs by remember { vm.db.hardcoreDao().runs() }.collectAsState(emptyList())
    val open by remember { vm.db.hardcoreDao().open() }.collectAsState(null)
    val st = remember(runs) { HardcoreEngine.stats(runs) { TimeUtil.dayNumber(it) } }
    GothicCard(onClick = { nav("hardcore") }, highlight = open != null) {
        Text("⛓ Hardcore-Dungeon", style = MaterialTheme.typography.titleMedium, color = LD.Blood, fontWeight = FontWeight.Bold)
        val o = open
        if (o != null) Text("${Hardcore.statusLabel(o.status)}: ${o.title}", color = LD.Gold, maxLines = 2)
        else Text("Rang ${Hardcore.rankFor(st.passed)} · ${st.passed} bestanden · ${st.successRate} % Quote")
        if (!compact) Hint("Gestartet ${st.started} · nicht bestanden ${st.failed} · abgebrochen ${st.aborted}")
    }
}

/** Hardcore-Abschnitt der Statistik. Unterscheidet Abbruch und Nicht bestanden. */
@Composable
fun HardcoreStatsSection() {
    val vm = LocalVm.current
    val runs by remember { vm.db.hardcoreDao().runs() }.collectAsState(emptyList())
    val st = remember(runs) { HardcoreEngine.stats(runs) { TimeUtil.dayNumber(it) } }
    SectionTitle("Hardcore-Dungeon")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(st.started.toString(), "Gestartet", Modifier.weight(1f), "⛓")
        StatTile(st.passed.toString(), "Bestanden", Modifier.weight(1f), "✔")
        StatTile(st.failed.toString(), "Nicht bestanden", Modifier.weight(1f), "✖")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(st.aborted.toString(), "Abgebrochen", Modifier.weight(1f), "🛑")
        StatTile("${st.successRate} %", "Erfolgsquote", Modifier.weight(1f), "📈")
        StatTile(st.xp.toString(), "Erhaltene XP", Modifier.weight(1f), "⭐")
    }
    GothicCard {
        Text("Höchste bestandene Stufe: " + if (st.highestPassed == 0) "–" else "${st.highestPassed} · ${Hardcore.DIFFICULTY_NAMES[st.highestPassed - 1]}")
        Text("Rekorde: ${st.bestStreak} in Folge bestanden · ${st.bestDay} an einem Tag · längste Serie ${st.longestSeries} Teilaufgaben")
        Hint("Erfolgsquote = bestanden ÷ (bestanden + nicht bestanden). Freiwillige Abbrüche zählen nicht dagegen.")
        if (st.favorites.isNotEmpty()) {
            Text("Lieblingskategorien", color = LD.Gold)
            st.favorites.forEach { (k, v) ->
                Text("${categoryName(k)}: $v")
                XpBar(v.toFloat() / st.favorites.first().second, "")
            }
        }
    }
}

/** Bibliothek: alle Aufgaben mit ID, Stufe, Voraussetzungen, Rollen, Sicherheitsklasse und Status. */
@Composable
private fun HardcoreLibrary(vm: HardcoreViewModel) {
    val tasks by vm.tasks.collectAsState()
    var cat by remember { mutableStateOf("hc_ds") }
    Hint("${tasks.size} Aufgaben. Deaktivierte Aufgaben wählt der Dungeon-Master nie aus.")
    ChoiceChips(Hardcore.CATEGORIES.map { it.key to it.emoji + " " + it.name + " (" + tasks.count { t -> t.categoryKey == it.key } + ")" }, cat) { cat = it }
    tasks.filter { it.categoryKey == cat }.forEach { t ->
        GothicCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title, style = MaterialTheme.typography.titleSmall, color = if (t.status == "ACTIVE") LD.Gold else LD.Dim, modifier = Modifier.weight(1f))
                Badge(t.code, LD.Dim)
            }
            Text(t.description, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge("Stufe ${t.difficulty} · ${Hardcore.DIFFICULTY_NAMES[t.difficulty - 1]}")
                Badge("+${t.xp} XP")
                if (t.durationMin > 0) Badge("${t.durationMin} Min")
                Badge("Sicherheit ${t.safetyClass}", if (t.safetyClass == "C" || t.safetyClass == "S") LD.Blood else LD.Gold)
            }
            Text("Sicherheit: " + (Hardcore.SAFETY_CLASSES[t.safetyClass] ?: t.safetyClass), style = MaterialTheme.typography.labelSmall, color = LD.Dim)
            if (t.requirements.isNotBlank()) Text("Voraussetzungen: ${t.requirements}", style = MaterialTheme.typography.labelSmall, color = LD.Dim)
            Text("Rollen: " + t.roles.replace("SUB", "Submissiv").replace("DOM", "Dominant").replace("SWITCH", "Switch").replace("SOLO", "Solo").replace(",", ", "), style = MaterialTheme.typography.labelSmall, color = LD.Dim)
            LabeledSwitch("Aktiv", t.status == "ACTIVE") { vm.toggle(t) }
        }
    }
}
