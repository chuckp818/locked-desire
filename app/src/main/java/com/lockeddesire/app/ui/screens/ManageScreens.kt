package com.lockeddesire.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockeddesire.app.MainActivity
import com.lockeddesire.app.data.AiMemory
import com.lockeddesire.app.data.PartnerProfile
import com.lockeddesire.app.data.Seed
import com.lockeddesire.app.data.SessionEntry
import com.lockeddesire.app.data.Task
import com.lockeddesire.app.data.TimeUtil
import com.lockeddesire.app.data.now
import com.lockeddesire.app.data.toProposal
import com.lockeddesire.app.engine.ChallengeEngine
import com.lockeddesire.app.engine.Leveling
import com.lockeddesire.app.engine.Verdict
import com.lockeddesire.app.notify.Notifier
import com.lockeddesire.app.ui.components.AvatarBubble
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
import com.lockeddesire.app.ui.components.StatTile
import com.lockeddesire.app.ui.components.XpBar
import com.lockeddesire.app.ui.components.wheelFor
import com.lockeddesire.app.ui.theme.LD
import com.lockeddesire.app.ui.theme.LocalAccent
import com.lockeddesire.app.ui.theme.accentName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PartnerScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val partners by vm.partners.collectAsState()
    val profile by vm.profile.collectAsState()
    val completions by vm.completions.collectAsState()
    var editing by remember { mutableStateOf<PartnerProfile?>(null) }
    var delete by remember { mutableStateOf<PartnerProfile?>(null) }
    var selfConsent by remember { mutableStateOf(false) }
    var partnerConsent by remember { mutableStateOf(false) }
    val ready = partners.any { it.consentGiven }
    Screen("Partner-Modus", onBack = { nav("back") }) {
        GothicCard {
            Text("Für zwei einvernehmliche Erwachsene. Beide stimmen unabhängig zu, beide können jederzeit abbrechen.")
            Hint("Gemeinsam vorher besprechen: Wünsche, Grenzen, Safeword. Danach Aftercare.")
        }
        val e = editing
        if (e != null) {
            LdField(e.name, "Name der Partnerperson") { editing = e.copy(name = it.take(40)) }
            ChoiceChips(listOf("Dominant", "Submissiv", "Switch").map { it to it }, e.role) { editing = e.copy(role = it) }
            LdField(e.pronouns, "Pronomen") { editing = e.copy(pronouns = it.take(30)) }
            LdField(e.limits, "Grenzen der Partnerperson (Begriffe, durch Komma)", singleLine = false) { editing = e.copy(limits = it.take(500)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton("Speichern", Modifier.weight(1f), enabled = e.name.isNotBlank()) {
                    vm.run {
                        db.profileDao().save(e)
                        // Partnergrenzen gelten in der Challenge-Engine wie eigene Grenzen
                        if (e.limits.isNotBlank()) {
                            val p = profile(); val merged = (p.limitsText.split(",") + e.limits.split(",")).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                            db.profileDao().save(p.copy(limitsText = merged.joinToString(", ")))
                        }
                    }
                    editing = null
                }
                GhostButton("Abbrechen", Modifier.weight(1f)) { editing = null }
            }
        } else {
            partners.forEach { p ->
                GothicCard(highlight = p.consentGiven) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBubble("💞", 46); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge, color = LD.Gold)
                            Text("${p.role}" + if (p.pronouns.isNotBlank()) " · ${p.pronouns}" else "", color = LD.Dim)
                        }
                        Badge(if (p.consentGiven) "Zugestimmt" else "Keine Zustimmung", if (p.consentGiven) LocalAccent.current else LD.Dim)
                    }
                    if (p.limits.isNotBlank()) Text("Grenzen: ${p.limits}", style = MaterialTheme.typography.bodySmall)
                    if (!p.consentGiven) {
                        LabeledSwitch("Ich (${profile.username.ifBlank { "Person 1" }}) stimme zu", selfConsent) { selfConsent = it }
                        LabeledSwitch("${p.name} stimmt zu", partnerConsent) { partnerConsent = it }
                        GoldButton("Session freigeben", Modifier.fillMaxWidth(), enabled = selfConsent && partnerConsent) {
                            vm.run { db.profileDao().save(p.copy(consentGiven = true)) }; selfConsent = false; partnerConsent = false
                        }
                    } else {
                        GhostButton("Abbrechen / Zustimmung zurückziehen", Modifier.fillMaxWidth()) {
                            vm.run {
                                db.profileDao().save(p.copy(consentGiven = false))
                                db.statsDao().save(SessionEntry(kind = "AFTERCARE", title = "Partner-Session beendet", note = "Aftercare: Wasser, Decke, Nähe, Reden."))
                                emit("INFO", "Session beendet. Zeit für Aftercare 🫖")
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Bearbeiten", Modifier.weight(1f)) { editing = p }
                        GhostButton("Löschen", Modifier.weight(1f)) { delete = p }
                    }
                }
            }
            GoldButton("＋ Partnerprofil anlegen", Modifier.fillMaxWidth()) { editing = PartnerProfile(name = "") }
        }
        if (ready) {
            SectionTitle("Gemeinsames Roulette")
            Hint("Jede Aufgabe muss von beiden bestätigt werden.")
            RoulettePanel(
                idleLabels = listOf("Check-in", "Massage", "Rollenwahl", "Blickkontakt", "Aftercare", "Drehbuch"),
                emptyText = "Keine Partner-Aufgabe passt zu euren Grenzen.",
                spin = {
                    val p = vm.repo.pickTask(categories = setOf("partner", "sensual", "roleplay"), tags = null, partner = true) ?: return@RoulettePanel null
                    val (labels, idx) = wheelFor(vm.tasks.value.filter { it.categoryKey == "partner" }.map { it.title }, p.title)
                    Triple(labels, idx, p)
                }
            ) { p, reroll -> PartnerProposal(p, reroll) }
        }
        SectionTitle("Gemeinsame Statistik")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(completions.count { it.status == "DONE" && it.categoryKey == "partner" }.toString(), "Partner-Challenges", Modifier.weight(1f), "💞")
            StatTile(completions.count { it.source == "PARTNER" && it.status == "DONE" }.toString(), "Gemeinsame Aufgaben", Modifier.weight(1f), "🤝")
        }
        completions.filter { it.source == "PARTNER" }.take(10).forEach {
            Text("• ${TimeUtil.dateTime(it.createdAt)} – ${it.taskTitle} (${statusText(it.status)})", style = MaterialTheme.typography.bodySmall)
        }
    }
    delete?.let { p -> Confirm("Partnerprofil löschen", "„${p.name}“ löschen?", "Löschen", { delete = null }) { vm.run { db.profileDao().delete(p) } } }
}

@Composable
private fun PartnerProposal(p: com.lockeddesire.app.engine.Proposal, reroll: () -> Unit) {
    val vm = LocalVm.current
    var a by remember(p) { mutableStateOf(false) }
    var b by remember(p) { mutableStateOf(false) }
    var stage by remember(p) { mutableStateOf("OFFER") }
    com.lockeddesire.app.ui.components.TaskCard(p, "", categoryName(p.categoryKey)) {
        when (stage) {
            "OFFER" -> {
                LabeledSwitch("Person 1 bestätigt", a) { a = it }
                LabeledSwitch("Person 2 bestätigt", b) { b = it }
                GoldButton("Gemeinsam starten", Modifier.fillMaxWidth(), enabled = a && b) { stage = "ACTIVE" }
                GhostButton("Neu würfeln", Modifier.fillMaxWidth(), onClick = reroll)
            }
            "ACTIVE" -> {
                if (p.durationMin > 0) com.lockeddesire.app.ui.components.CountdownTimer(p.durationMin * 60)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton("Erledigt", Modifier.weight(1f)) {
                        vm.run {
                            recordTask(p.title, p.categoryKey, p.xp, "DONE", "PARTNER", p.taskId)
                            db.statsDao().save(SessionEntry(kind = "PARTNER", title = p.title, durationMin = p.durationMin))
                        }
                        stage = "DONE"
                    }
                    GhostButton("Abbrechen", Modifier.weight(1f)) { vm.run { recordTask(p.title, p.categoryKey, 0, "ABORTED", "PARTNER", p.taskId) }; stage = "DONE" }
                }
                Hint("Jede Person kann jederzeit abbrechen.")
            }
            else -> {
                Text("🫖 Aftercare-Erinnerung: Wasser, Decke, Nähe und ein kurzes Gespräch.", color = LD.Gold)
                GoldButton("Nächste Runde", Modifier.fillMaxWidth(), onClick = reroll)
            }
        }
    }
}

@Composable
fun LevelsScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val profile by vm.profile.collectAsState()
    val achievements by vm.achievements.collectAsState()
    val unlocked by vm.unlockedAchievements.collectAsState()
    val completions by vm.completions.collectAsState()
    val level = Leveling.levelFor(profile.xp)
    val have = unlocked.associateBy { it.achievementId }
    Screen("Level & Erfolge", onBack = { nav("back") }) {
        GothicCard(highlight = true) {
            Text("Level $level", style = MaterialTheme.typography.displaySmall, color = LD.Gold)
            Text(Leveling.titleFor(level), style = MaterialTheme.typography.titleLarge)
            XpBar(Leveling.progress(profile.xp), "${profile.xp} XP · noch ${Leveling.xpToNext(profile.xp)} bis Level ${level + 1}")
            Hint("Pausen und Abbrüche werden nie bestraft.")
        }
        SectionTitle("Ränge")
        Leveling.TITLES.forEach { (l, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (level >= l) "🔓" else "🔒", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text("Level $l · $t", color = if (level >= l) LD.Gold else LD.Dim)
            }
        }
        SectionTitle("Tägliche Challenge")
        val today = completions.count { it.status == "DONE" && it.createdAt >= TimeUtil.startOfDay() }
        GothicCard {
            Text("Erledige heute 3 Aufgaben: $today/3", color = LD.Gold)
            XpBar(today / 3f, if (today >= 3) "Geschafft!" else "")
        }
        SectionTitle("Wöchentliche Mission")
        val week = completions.filter { it.status == "DONE" && it.createdAt >= now() - 7 * TimeUtil.DAY }
        GothicCard {
            Text("15 Aufgaben aus mindestens 3 Kategorien: ${week.size}/15 · ${week.map { it.categoryKey }.toSet().size}/3", color = LD.Gold)
            XpBar(minOf(week.size / 15f, week.map { it.categoryKey }.toSet().size / 3f), "")
        }
        SectionTitle("Freischaltbare Designs")
        Leveling.ACCENTS.forEach { (key, l) -> Text((if (level >= l) "🔓 " else "🔒 ") + accentName(key) + " · ab Level $l", color = if (level >= l) LD.Grey else LD.Dim) }
        GhostButton("Design wählen", Modifier.fillMaxWidth()) { nav("settings") }
        SectionTitle("Erfolge (${unlocked.size}/${achievements.size})")
        achievements.forEach { a ->
            val u = have[a.id]
            GothicCard(highlight = u != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (u != null) a.emoji else "🔒", fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleMedium, color = if (u != null) LD.Gold else LD.Dim)
                        Text(a.description + if (a.xpReward > 0) " · +${a.xpReward} XP" else "", style = MaterialTheme.typography.bodySmall)
                        if (u != null) Text("Freigeschaltet am ${TimeUtil.date(u.unlockedAt)}", style = MaterialTheme.typography.labelSmall, color = LD.Dim)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskEditorScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by vm.tasks.collectAsState()
    var editing by remember { mutableStateOf<Task?>(null) }
    var filter by remember { mutableStateOf("custom") }
    var delete by remember { mutableStateOf<Task?>(null) }
    var verdict by remember { mutableStateOf("") }
    var exportCustomOnly by remember { mutableStateOf(true) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = vm.repo.exportTasks(exportCustomOnly)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                vm.repo.emit("INFO", "Aufgabenpaket exportiert")
            } catch (e: Exception) { vm.repo.emit("INFO", "Export fehlgeschlagen") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: ""
                val (ok, rejected) = vm.repo.importTasks(text)
                vm.repo.emit("INFO", "$ok Aufgaben importiert" + if (rejected > 0) ", $rejected abgelehnt (Duplikat oder Sicherheitsregel)" else "")
            } catch (e: Exception) { vm.repo.emit("INFO", "Keine gültige Aufgabendatei") }
        }
    }

    Screen("Aufgaben-Editor", onBack = { nav("back") }) {
        val e = editing
        if (e != null) {
            LdField(e.title, "Titel") { editing = e.copy(title = it.take(80)) }
            LdField(e.description, "Beschreibung", singleLine = false) { editing = e.copy(description = it.take(800)) }
            SectionTitle("Kategorie")
            ChoiceChips(Seed.CATEGORIES.map { it.key to it.emoji + " " + it.name }, e.categoryKey) { editing = e.copy(categoryKey = it) }
            LabeledSlider("Schwierigkeit", e.difficulty, 1..6, "${e.difficulty} · ${Seed.DIFFICULTY_NAMES[e.difficulty - 1]}") { editing = e.copy(difficulty = it) }
            LabeledSlider("Dauer", e.durationMin, 0..180, "${e.durationMin} Min") { editing = e.copy(durationMin = it) }
            LabeledSlider("XP-Belohnung", e.xp, 5..100, "${e.xp}") { editing = e.copy(xp = it) }
            LabeledSlider("Körperliche Belastung", e.physicalLoad, 0..3, listOf("Keine", "Leicht", "Mittel", "Hoch")[e.physicalLoad]) { editing = e.copy(physicalLoad = it) }
            LabeledSlider("Wiederholungen (0 = keine)", e.reps, 0..100, "${e.reps}") { editing = e.copy(reps = it) }
            LabeledSwitch("Timer", e.hasTimer) { editing = e.copy(hasTimer = it) }
            LabeledSwitch("Belastet Knie, Handgelenke oder Rücken", e.jointLoad) { editing = e.copy(jointLoad = it) }
            LabeledSwitch("Partner erforderlich", e.partnerRequired) { editing = e.copy(partnerRequired = it) }
            LabeledSwitch("Roulette-Freigabe", e.rouletteEnabled) { editing = e.copy(rouletteEnabled = it) }
            LabeledSwitch("Dungeon-Freigabe", e.dungeonEnabled) { editing = e.copy(dungeonEnabled = it) }
            LabeledSwitch("Aktiv", e.enabled) { editing = e.copy(enabled = it) }
            LdField(e.limitsNote, "Grenzen / Hinweise", singleLine = false) { editing = e.copy(limitsNote = it.take(300)) }
            SectionTitle("Sicherheitskennzeichnung")
            ChoiceChips(listOf("SAFE" to "Unbedenklich", "CAUTION" to "Mit Vorsicht", "PARTNER_ONLY" to "Nur mit Partner"), e.safetyTag) { editing = e.copy(safetyTag = it) }
            if (verdict.isNotBlank()) Text(verdict, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton("Speichern", Modifier.weight(1f), enabled = e.title.isNotBlank()) {
                    val t = e.copy(partnerRequired = e.partnerRequired || e.safetyTag == "PARTNER_ONLY", hasTimer = e.hasTimer)
                    val text = (t.title + " " + t.description).lowercase()
                    val blocked = ChallengeEngine.HARD_BLOCK.firstOrNull { text.contains(it) }
                    val tooLong = (t.physicalLoad > 0 && t.durationMin > ChallengeEngine.MAX_PHYSICAL_MIN)
                    when {
                        blocked != null -> verdict = "Gesperrt: Diese Aufgabe verstößt gegen eine feste Sicherheitsregel."
                        tooLong -> verdict = "Körperliche Aufgaben dürfen höchstens ${ChallengeEngine.MAX_PHYSICAL_MIN} Minuten dauern."
                        else -> {
                            verdict = ""
                            vm.run { if (t.id == 0L) db.taskDao().insert(t) else db.taskDao().update(t); checkAchievements() }
                            editing = null
                        }
                    }
                }
                GhostButton("Abbrechen", Modifier.weight(1f)) { editing = null; verdict = "" }
            }
        } else {
            GoldButton("＋ Neue Aufgabe", Modifier.fillMaxWidth()) { editing = Task(title = "", description = "", categoryKey = "custom", custom = true, hasTimer = true) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Import", Modifier.weight(1f)) { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }
                GhostButton("Export", Modifier.weight(1f)) { exporter.launch("locked-desire-aufgaben.json") }
            }
            LabeledSwitch("Nur eigene Aufgaben exportieren", exportCustomOnly) { exportCustomOnly = it }
            ChoiceChips(listOf("custom" to "Eigene", "all" to "Alle") + Seed.CATEGORIES.filter { it.key != "custom" }.map { it.key to it.emoji }, filter) { filter = it }
            val shown = tasks.filter { when (filter) { "custom" -> it.custom; "all" -> true; else -> it.categoryKey == filter } }
            Hint("${shown.size} Aufgaben · ausgeschaltete Aufgaben erscheinen nirgends")
            shown.take(300).forEach { t ->
                GothicCard(onClick = { editing = t }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.title, style = MaterialTheme.typography.titleSmall, color = if (t.enabled) LD.Gold else LD.Dim, modifier = Modifier.weight(1f))
                        if (t.custom) TextButton(onClick = { delete = t }) { Text("✕") }
                    }
                    Text("${categoryName(t.categoryKey)} · Stufe ${t.difficulty} · ${t.durationMin} Min · ${t.xp} XP" + if (!t.enabled) " · aus" else "", style = MaterialTheme.typography.labelSmall, color = LD.Dim)
                }
            }
        }
    }
    delete?.let { t -> Confirm("Aufgabe löschen", "„${t.title}“ löschen?", "Löschen", { delete = null }) { vm.run { db.taskDao().delete(t) } } }
}

@Composable
fun NotificationSettingsScreen(activity: MainActivity) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val n by vm.notif.collectAsState()
    val context = LocalContext.current
    Screen("Benachrichtigungen", onBack = { nav("back") }) {
        if (!Notifier.canNotify(context)) GothicCard {
            Text("Benachrichtigungen sind für diese App nicht erlaubt.")
            GoldButton("Erlauben", Modifier.fillMaxWidth()) { activity.askNotificationPermission() }
        }
        LabeledSwitch("Benachrichtigungen aktiv", n.enabled) { vm.saveNotif(n.copy(enabled = it)) }
        LabeledSwitch("Diskrete Texte", n.discreet, "Zeigt nur „LD“ und neutrale Hinweise, auch auf dem Sperrbildschirm") { vm.saveNotif(n.copy(discreet = it)) }
        LabeledSwitch("Ton", n.sound) { vm.saveNotif(n.copy(sound = it)) }
        LabeledSwitch("Vibration", n.vibration) { vm.saveNotif(n.copy(vibration = it)) }
        LabeledSwitch("KI-Sprachausgabe im Chat", vm.prefs.collectAsState().value.ttsEnabled) { vm.savePrefs(vm.prefs.value.copy(ttsEnabled = it)) }
        SectionTitle("Ruhezeiten")
        LabeledSlider("Beginn", n.quietStart / 30, 0..47, TimeUtil.clock(n.quietStart)) { vm.saveNotif(n.copy(quietStart = it * 30)) }
        LabeledSlider("Ende", n.quietEnd / 30, 0..47, TimeUtil.clock(n.quietEnd)) { vm.saveNotif(n.copy(quietEnd = it * 30)) }
        Hint("In Ruhezeiten gibt es keine Benachrichtigungen und keine Aufgaben.")
        SectionTitle("Spontane KI-Aufgaben")
        LabeledSwitch("Spontane Aufgaben aktivieren", n.spontaneous, "Die KI bietet zu zufälligen erlaubten Zeiten Aufgaben an. Jede muss angenommen werden.") { vm.saveNotif(n.copy(spontaneous = it)) }
        ChoiceChips(listOf("0" to "Gelegentlich (1–2)", "1" to "Regelmäßig (3–5)", "2" to "Intensiv (6–8)", "3" to "Individuell"), n.frequency.toString()) { vm.saveNotif(n.copy(frequency = it.toInt())) }
        if (n.frequency == 3) LabeledSlider("Aufgaben pro Tag", n.customPerDay, 1..12, "${n.customPerDay}") { vm.saveNotif(n.copy(customPerDay = it)) }
        LabeledSlider("Maximale Aufgaben pro Tag", n.maxPerDay, 1..12, "${n.maxPerDay}") { vm.saveNotif(n.copy(maxPerDay = it)) }
        LabeledSwitch("Überraschungsmodus", n.surprise) { vm.saveNotif(n.copy(surprise = it)) }
        Text("Aufgabenarten", style = MaterialTheme.typography.labelLarge)
        val types = n.taskTypes.split(",").filter { it.isNotBlank() }.toSet()
        Chips(Seed.CATEGORIES.filter { it.key != "partner" }.map { it.key to it.emoji + " " + it.name }, types) {
            vm.saveNotif(n.copy(taskTypes = (if (it in types) types - it else types + it).joinToString(",")))
        }
        GhostButton("KI-Tonfall: Persona wählen", Modifier.fillMaxWidth()) { nav("personas") }
        SectionTitle("Arten")
        LabeledSwitch("Neue KI-Challenge", n.nChallenge) { vm.saveNotif(n.copy(nChallenge = it)) }
        LabeledSwitch("Dungeon-Phase abgeschlossen", n.nPhase) { vm.saveNotif(n.copy(nPhase = it)) }
        LabeledSwitch("Keuschheitsziel erreicht", n.nChastity) { vm.saveNotif(n.copy(nChastity = it)) }
        LabeledSwitch("Tagesaufgabe verfügbar", n.nDaily) { vm.saveNotif(n.copy(nDaily = it)) }
        LabeledSwitch("Fitness-Erinnerung (ab 17 Uhr)", n.nFitness) { vm.saveNotif(n.copy(nFitness = it)) }
        LabeledSwitch("Persönlicher Rekord", n.nRecord) { vm.saveNotif(n.copy(nRecord = it)) }
        LabeledSwitch("XP-Belohnung", n.nXp) { vm.saveNotif(n.copy(nXp = it)) }
        LabeledSwitch("Session-Pause", n.nPause) { vm.saveNotif(n.copy(nPause = it)) }
        GhostButton("Testbenachrichtigung", Modifier.fillMaxWidth()) { Notifier.post(context, n, "TEST", "So sieht eine Benachrichtigung aus.", ignoreQuiet = true) }
        Hint("Android prüft Hintergrundaufgaben etwa alle 15 Minuten. Bei aktivem Akkusparen kann es später werden.")
    }
}

@Composable
fun PrivacyScreen(activity: MainActivity) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val consent by vm.consent.collectAsState()
    val prefs by vm.prefs.collectAsState()
    val lock by vm.lock.collectAsState()
    val memories by vm.memories.collectAsState()
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var baseUrl by remember(prefs.aiBaseUrl) { mutableStateOf(prefs.aiBaseUrl) }
    var model by remember(prefs.aiModel) { mutableStateOf(prefs.aiModel) }
    var apiKey by remember { mutableStateOf("") }
    var exportPw by remember { mutableStateOf("") }
    var wipe by remember { mutableIntStateOf(0) }
    var editMemory by remember { mutableStateOf<AiMemory?>(null) }
    var newMemory by remember { mutableStateOf("") }
    var cloudAsk by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = exportPw
        if (uri != null) scope.launch {
            try {
                val bytes = vm.repo.exportAll(pw)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                vm.repo.emit("INFO", "Verschlüsselter Export gespeichert")
            } catch (e: Exception) { vm.repo.emit("INFO", "Export fehlgeschlagen") }
            exportPw = ""
        }
    }

    Screen("Datenschutz & Sicherheit", onBack = { nav("back") }) {
        GothicCard {
            Text("Alle Daten liegen verschlüsselt auf diesem Gerät (SQLCipher-Datenbank, Schlüssel im Android Keystore). Fotos sind einzeln verschlüsselt. Screenshots der App sind gesperrt.", style = MaterialTheme.typography.bodySmall)
        }
        SectionTitle("App-Sperre")
        if (!lock.pinSet) {
            LdField(pin, "Neue PIN (mind. 4 Ziffern)", number = true) { pin = it.filter { c -> c.isDigit() }.take(12) }
            LdField(pin2, "PIN wiederholen", number = true) { pin2 = it.filter { c -> c.isDigit() }.take(12) }
            GoldButton("PIN-Sperre aktivieren", Modifier.fillMaxWidth(), enabled = pin.length >= 4 && pin == pin2) {
                val p = pin; pin = ""; pin2 = ""
                scope.launch { vm.repo.secure.setPin(p); vm.unlocked.value = true; vm.repo.emit("INFO", "PIN-Sperre aktiv") }
            }
        } else {
            Text("PIN-Sperre ist aktiv. Die App sperrt sich, sobald du sie verlässt.", color = LD.Gold)
            LabeledSwitch("Biometrische Entsperrung", lock.biometric, if (activity.biometricAvailable()) "Fingerabdruck oder Gesicht" else "Auf diesem Gerät nicht eingerichtet", enabled = activity.biometricAvailable()) {
                scope.launch { vm.repo.secure.setBiometric(it) }
            }
            GhostButton("PIN-Sperre entfernen", Modifier.fillMaxWidth()) { scope.launch { vm.repo.secure.clearPin() } }
        }

        SectionTitle("KI und Einwilligungen")
        LabeledSwitch("KI aktiv", consent.aiEnabled, "Ausgeschaltet bleiben Roulette, Dungeon und Tracker voll nutzbar") { vm.saveConsent(consent.copy(aiEnabled = it)) }
        LabeledSwitch("KI-Gedächtnis", consent.memoryEnabled, "Lieblingsaufgaben, Pet-Rolle und mit „Merk dir …“ notierte Dinge") { vm.saveConsent(consent.copy(memoryEnabled = it)) }
        Text("Was die KI verwenden darf:", style = MaterialTheme.typography.labelLarge)
        LabeledSwitch("Grenzen", consent.shareLimits, "Empfohlen, damit die KI sie beachtet") { vm.saveConsent(consent.copy(shareLimits = it)) }
        LabeledSwitch("Profil (Name, Pronomen, Rolle, Vorlieben)", consent.shareProfile) { vm.saveConsent(consent.copy(shareProfile = it)) }
        LabeledSwitch("Freigegebene Fetischkategorien", consent.shareFetishes) { vm.saveConsent(consent.copy(shareFetishes = it)) }
        LabeledSwitch("Fortschritt (Level, Statistik)", consent.shareProgress, "Gilt für externe KI-Server") { vm.saveConsent(consent.copy(shareProgress = it)) }

        SectionTitle("KI-Modus")
        ChoiceChips(listOf("RULES" to "Offline-Dungeon-Master", "LOCAL" to "Lokales Modell", "CLOUD" to "Externer Anbieter"), prefs.aiMode) {
            if (it == "CLOUD" && !consent.cloudAiConsent) cloudAsk = true
            vm.savePrefs(prefs.copy(aiMode = it))
        }
        when (prefs.aiMode) {
            "RULES" -> Hint("Läuft komplett offline, ohne Datenübertragung.")
            "LOCAL" -> Hint("Ein lokales Modell auf diesem Gerät, etwa per Termux mit llama.cpp oder Ollama, das eine OpenAI-kompatible Schnittstelle anbietet, z. B. http://127.0.0.1:8080/v1. Ein Modell direkt in der APK mitzuliefern würde sie um mehrere Gigabyte vergrößern, deshalb wird es über diese Schnittstelle angebunden. Adressen außerhalb des Geräts brauchen die Cloud-Einwilligung.")
            else -> Hint("Jeder OpenAI-kompatible Dienst. Übertragen werden nur die oben freigegebenen Informationen und der aktuelle Chatverlauf.")
        }
        if (prefs.aiMode != "RULES") {
            LdField(baseUrl, "Server-Adresse (…/v1)") { baseUrl = it.trim().take(200) }
            LdField(model, "Modellname") { model = it.trim().take(80) }
            LdField(apiKey, "API-Schlüssel (wird verschlüsselt gespeichert)") { apiKey = it.trim() }
            GoldButton("KI-Verbindung speichern", Modifier.fillMaxWidth()) {
                vm.savePrefs(prefs.copy(aiBaseUrl = baseUrl, aiModel = model))
                val k = apiKey; apiKey = ""
                scope.launch { if (k.isNotBlank()) vm.repo.secure.setApiKey(k); vm.repo.emit("INFO", "Gespeichert") }
            }
            LabeledSwitch("Einwilligung: Daten an externe KI senden", consent.cloudAiConsent) { if (it) cloudAsk = true else vm.saveConsent(consent.copy(cloudAiConsent = false)) }
            GhostButton("API-Schlüssel löschen", Modifier.fillMaxWidth()) { scope.launch { vm.repo.secure.setApiKey(""); vm.repo.emit("INFO", "Schlüssel gelöscht") } }
        }

        SectionTitle("KI-Erinnerungen (${memories.size})")
        memories.forEach { m ->
            GothicCard {
                Text(m.content)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Bearbeiten", Modifier.weight(1f)) { editMemory = m }
                    GhostButton("Löschen", Modifier.weight(1f)) { vm.run { db.aiDao().delete(m) } }
                }
            }
        }
        LdField(newMemory, "Neue Erinnerung") { newMemory = it.take(300) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Hinzufügen", Modifier.weight(1f), enabled = newMemory.isNotBlank()) { val c = newMemory; newMemory = ""; vm.run { db.aiDao().save(AiMemory(kind = "USER", content = c)) } }
            GhostButton("Alle löschen", Modifier.weight(1f)) { vm.run { db.aiDao().clearMemories() } }
        }
        GhostButton("Alle Chatverläufe löschen", Modifier.fillMaxWidth()) { vm.run { db.aiDao().clearAllConversations(); emit("INFO", "Chats gelöscht") } }

        SectionTitle("Verschlüsselter Export")
        LdField(exportPw, "Passwort für die Exportdatei (mind. 8 Zeichen)") { exportPw = it.take(64) }
        GoldButton("Daten exportieren", Modifier.fillMaxWidth(), enabled = exportPw.length >= 8) { exporter.launch("locked-desire-${TimeUtil.date(now()).replace('.', '-')}.ldx") }
        Hint("AES-256-verschlüsselt. Ohne Passwort ist die Datei nicht lesbar. Fotos werden nicht exportiert.")

        SectionTitle("Vollständige Datenlöschung")
        GhostButton("Alle Daten löschen …", Modifier.fillMaxWidth()) { wipe = 1 }
    }
    if (cloudAsk) Confirm(
        "Einwilligung",
        "Bei aktivierter externer KI werden deine Chatnachrichten und nur die oben freigegebenen Informationen an den eingetragenen Server gesendet. Sicherheitsentscheidungen (Safeword, Beschwerden, Grenzen) trifft immer die App selbst. Einverstanden?",
        "Einwilligen", { cloudAsk = false }
    ) { vm.saveConsent(consent.copy(cloudAiConsent = true)) }
    if (wipe == 1) Confirm("Alles löschen?", "Profil, Statistiken, Toys, Fotos, Chats, Erinnerungen und eigene Aufgaben werden unwiderruflich gelöscht.", "Weiter", { wipe = 0 }) { wipe = 2 }
    if (wipe == 2) Confirm("Wirklich endgültig?", "Dies kann nicht rückgängig gemacht werden.", "Endgültig löschen", { wipe = 0 }) {
        vm.run { wipeEverything(); vm.unlocked.value = false; emit("INFO", "Alle Daten wurden gelöscht") }
    }
    editMemory?.let { m ->
        var text by remember(m.id) { mutableStateOf(m.content) }
        AlertDialog(
            onDismissRequest = { editMemory = null },
            title = { Text("Erinnerung bearbeiten") },
            text = { LdField(text, "Inhalt", singleLine = false) { text = it.take(300) } },
            confirmButton = { GoldButton("Speichern") { vm.run { db.aiDao().save(m.copy(content = text, updatedAt = now())) }; editMemory = null } },
            dismissButton = { GhostButton("Abbrechen") { editMemory = null } }
        )
    }
}

@Composable
fun SettingsScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val prefs by vm.prefs.collectAsState()
    val profile by vm.profile.collectAsState()
    val level = Leveling.levelFor(profile.xp)
    Screen("Einstellungen", onBack = { nav("back") }) {
        SectionTitle("Darstellung")
        LabeledSwitch("Visuelle Effekte", prefs.effectsEnabled, "Rauch, Partikel und Animationen") { vm.savePrefs(prefs.copy(effectsEnabled = it)) }
        Text("Akzentfarbe", style = MaterialTheme.typography.labelLarge)
        ChoiceChips(Leveling.ACCENTS.filter { level >= it.second }.map { it.first to accentName(it.first) }, prefs.accent) { vm.savePrefs(prefs.copy(accent = it)) }
        Hint("Weitere Farben werden mit höheren Leveln freigeschaltet.")
        SectionTitle("Audio")
        LabeledSwitch("Soundeffekte", prefs.soundEnabled) { vm.savePrefs(prefs.copy(soundEnabled = it)) }
        LabeledSwitch("Vibration", prefs.vibrationEnabled) { vm.savePrefs(prefs.copy(vibrationEnabled = it)) }
        LabeledSwitch("KI-Sprachausgabe", prefs.ttsEnabled, "Die Persona liest ihre Chatantworten vor") { vm.savePrefs(prefs.copy(ttsEnabled = it)) }
        SectionTitle("Weitere Einstellungen")
        GhostButton("Profil", Modifier.fillMaxWidth()) { nav("profile") }
        GhostButton("Grenzen-Manager", Modifier.fillMaxWidth()) { nav("limits") }
        GhostButton("Benachrichtigungen", Modifier.fillMaxWidth()) { nav("notifSettings") }
        GhostButton("Datenschutz & Sicherheit", Modifier.fillMaxWidth()) { nav("privacy") }
        GhostButton("KI-Personas", Modifier.fillMaxWidth()) { nav("personas") }
        SectionTitle("Über")
        GothicCard {
            Text("Locked Desire", style = MaterialTheme.typography.titleLarge, color = LD.Gold)
            Text("Nur für Erwachsene ab 18. Alles ist freiwillig, jederzeit abbrechbar und ohne Strafen.", style = MaterialTheme.typography.bodySmall)
            Text("Bei Schmerzen, Taubheit, Schwindel oder Unwohlsein: sofort aufhören und bei Bedarf ärztliche Hilfe holen.", style = MaterialTheme.typography.bodySmall, color = LD.Gold)
        }
    }
}
