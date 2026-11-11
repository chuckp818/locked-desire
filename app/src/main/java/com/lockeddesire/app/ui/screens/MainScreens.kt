package com.lockeddesire.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockeddesire.app.MainActivity
import com.lockeddesire.app.data.AiMessage
import com.lockeddesire.app.data.AiPersona
import com.lockeddesire.app.data.DungeonChallenge
import com.lockeddesire.app.data.Seed
import com.lockeddesire.app.data.TimeUtil
import com.lockeddesire.app.engine.DungeonPlanner
import com.lockeddesire.app.engine.Leveling
import com.lockeddesire.app.ui.components.AvatarBubble
import com.lockeddesire.app.ui.components.Badge
import com.lockeddesire.app.ui.components.ChainDivider
import com.lockeddesire.app.ui.components.Chips
import com.lockeddesire.app.ui.components.ChoiceChips
import com.lockeddesire.app.ui.components.Confirm
import com.lockeddesire.app.ui.components.CountdownTimer
import com.lockeddesire.app.ui.components.GhostButton
import com.lockeddesire.app.ui.components.GoldButton
import com.lockeddesire.app.ui.components.GothicCard
import com.lockeddesire.app.ui.components.Hint
import com.lockeddesire.app.ui.components.LabeledSlider
import com.lockeddesire.app.ui.components.LdField
import com.lockeddesire.app.ui.components.LocalNav
import com.lockeddesire.app.ui.components.LocalVm
import com.lockeddesire.app.ui.components.Screen
import com.lockeddesire.app.ui.components.SectionTitle
import com.lockeddesire.app.ui.components.StatTile
import com.lockeddesire.app.ui.components.XpBar
import com.lockeddesire.app.ui.theme.LD
import com.lockeddesire.app.ui.theme.LocalAccent
import kotlinx.coroutines.flow.flowOf

private val AREAS = listOf(
    Triple("⛓", "Hardcore-Dungeon", "hardcore"), Triple("🎲", "Aufgabenroulette", "taskRoulette"), Triple("💬", "KI-Chat", "chat"), Triple("🏰", "24h-Dungeon", "dungeon24"),
    Triple("⛓", "BDSM-Dungeon", "bdsmDungeon"), Triple("🔒", "Keuschheit", "chastity"), Triple("🐾", "Petplay", "petDungeon"),
    Triple("📖", "Fetish Universe", "fetishUniverse"), Triple("🎯", "Fetisch-Roulette", "fetishRoulette"), Triple("🚫", "Grenzen", "limits"),
    Triple("🎁", "Toy-Galerie", "toys"), Triple("🎰", "Toy-Roulette", "toyRoulette"), Triple("⏳", "Zeitgenerator", "timeGen"),
    Triple("📓", "Tagebuch", "journal"), Triple("💪", "Fitness", "fitness"), Triple("📊", "Statistiken", "stats"),
    Triple("💞", "Partner-Modus", "partner"), Triple("🏆", "Level & Erfolge", "levels"), Triple("✍", "Aufgaben-Editor", "taskEditor"),
    Triple("👑", "KI-Personas", "personas"), Triple("🙂", "Profil", "profile"), Triple("🔔", "Benachrichtigungen", "notifSettings"),
    Triple("🛡", "Datenschutz", "privacy"), Triple("⚙", "Einstellungen", "settings"), Triple("🐶", "Pet-Profil", "petProfile")
)

@Composable
fun DashboardScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val profile by vm.profile.collectAsState()
    val persona by vm.persona.collectAsState()
    val chastity by vm.chastity.collectAsState()
    val dungeon by vm.dungeon.collectAsState()
    val completions by vm.completions.collectAsState()
    val counters by vm.counters.collectAsState()
    val fitness by vm.fitness.collectAsState()
    val nowMs by rememberNow()
    var greeting by remember { mutableStateOf("") }
    LaunchedEffect(persona?.id, dungeon?.id, chastity?.id) {
        greeting = try { vm.repo.dashboardGreeting() } catch (_: Exception) { "" }
    }
    val sid = dungeon?.id
    val challenges by remember(sid) { if (sid != null) vm.db.dungeonDao().challenges(sid) else flowOf(emptyList<DungeonChallenge>()) }.collectAsState(emptyList())
    val level = Leveling.levelFor(profile.xp)

    Screen("Locked Desire") {
        GothicCard(onClick = { nav("profile") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBubble(profile.avatar, 64)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.username.ifBlank { "Namenlos" }, style = MaterialTheme.typography.headlineSmall, color = LD.Grey)
                    Text("Level $level · ${Leveling.titleFor(level)}", color = LD.Gold, fontWeight = FontWeight.SemiBold)
                }
            }
            XpBar(Leveling.progress(profile.xp), "${profile.xp} XP · noch ${Leveling.xpToNext(profile.xp)} bis Level ${level + 1}")
        }

        persona?.let { p ->
            GothicCard(onClick = { nav("chat") }, highlight = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBubble(p.avatar, 52)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, color = LD.Gold)
                        Text(Seed.PERSONALITIES.firstOrNull { it.first == p.personality }?.second.orEmpty() + " · Strenge ${p.strictness}/10", style = MaterialTheme.typography.labelMedium, color = LD.Dim)
                    }
                }
                if (greeting.isNotBlank()) Text("„$greeting“", style = MaterialTheme.typography.bodyLarge)
                Hint("Tippen, um den Chat zu öffnen")
            }
        }

        GoldButton("🎲  Roulette drehen", Modifier.fillMaxWidth()) { nav("taskRoulette") }

        SectionTitle("Aktuelle Challenge")
        val next = challenges.firstOrNull { it.status == "PENDING" && it.scheduledAt <= nowMs } ?: challenges.firstOrNull { it.status == "PENDING" }
        GothicCard(onClick = { nav(if (dungeon != null) "dungeon24" else "taskRoulette") }) {
            if (dungeon != null && next != null) {
                Text(next.title, style = MaterialTheme.typography.titleMedium, color = LD.Gold)
                Text(if (next.scheduledAt <= nowMs) "Jetzt verfügbar im Dungeon" else "Verfügbar in ${TimeUtil.duration(next.scheduledAt - nowMs)}")
            } else {
                Text("Keine aktive Challenge", style = MaterialTheme.typography.titleMedium)
                Hint("Dreh das Roulette oder bitte deine KI-Persona um eine Aufgabe.")
            }
        }

        SectionTitle("Status")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GothicCard(Modifier.weight(1f), onClick = { nav("chastity") }) {
                Text("🔒 Keuschheit", style = MaterialTheme.typography.titleSmall, color = LD.Gold)
                val cs = chastity
                Text(if (cs == null) "Nicht aktiv" else TimeUtil.duration(vm.repo.chastityElapsed(cs, nowMs)), style = MaterialTheme.typography.titleLarge)
                if (cs != null) Hint(if (cs.pausedAt > 0) "Pausiert" else if (cs.goalAt > 0 && nowMs >= cs.goalAt) "Ziel erreicht" else if (cs.goalAt > 0) "Ziel in ${TimeUtil.duration(cs.goalAt - nowMs)}" else "Ohne Ziel")
            }
            GothicCard(Modifier.weight(1f), onClick = { nav("dungeon24") }) {
                Text("🏰 Dungeon", style = MaterialTheme.typography.titleSmall, color = LD.Gold)
                val ds = dungeon
                if (ds == null) { Text("Geschlossen", style = MaterialTheme.typography.titleLarge); Hint("Bereit für 24 Stunden?") }
                else {
                    val ref = if (ds.pausedAt > 0) ds.pausedAt else nowMs
                    Text(DungeonPlanner.PHASES[DungeonPlanner.phaseAt(ds.startedAt, ref)], style = MaterialTheme.typography.titleLarge)
                    Hint(if (ds.pausedAt > 0) "Pausiert" else "Noch ${TimeUtil.duration(ds.endsAt - nowMs)}")
                }
            }
        }
        HardcoreSummaryCard(compact = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(completions.count { it.status == "DONE" }.toString(), "Aufgaben erledigt", Modifier.weight(1f), "✅")
            StatTile((counters.firstOrNull { it.key == "records" }?.value ?: 0).toString(), "Rekorde", Modifier.weight(1f), "🏅")
            StatTile(fitness.count { it.createdAt >= nowMs - 7 * TimeUtil.DAY }.toString(), "Fitness (7 Tage)", Modifier.weight(1f), "💪")
        }

        SectionTitle("Alle Bereiche")
        AREAS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (emoji, label, route) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(LD.Leather)
                            .border(1.dp, LD.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable { nav(route) }.padding(vertical = 14.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(emoji, fontSize = 26.sp)
                        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        ChainDivider()
    }
}

@Composable
fun ProfileScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val saved by vm.profile.collectAsState()
    var p by remember(saved.updatedAt) { mutableStateOf(saved) }
    val level = Leveling.levelFor(saved.xp)
    Screen("Profil", onBack = { nav("back") }) {
        GothicCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBubble(p.avatar, 64)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Level $level · ${Leveling.titleFor(level)}", color = LD.Gold, fontWeight = FontWeight.Bold)
                    Text("${saved.xp} XP", color = LD.Dim)
                }
            }
            Text("Avatar", style = MaterialTheme.typography.labelMedium, color = LD.Dim)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AVATARS.forEach { a ->
                    Text(a, fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (a == p.avatar) LocalAccent.current.copy(alpha = 0.5f) else LD.Black)
                        .clickable { p = p.copy(avatar = a) }.padding(8.dp))
                }
            }
        }
        Hint("Alle Angaben sind freiwillig. Aus Geschlecht oder Orientierung werden keine Vorlieben abgeleitet.")
        LdField(p.username, "Benutzername") { p = p.copy(username = it.take(40)) }
        LdField(p.salutation, "Bevorzugte Anrede (so nennt dich die KI)") { p = p.copy(salutation = it.take(40)) }
        LdField(p.pronouns, "Pronomen") { p = p.copy(pronouns = it.take(40)) }
        LdField(p.gender, "Geschlecht (optional)") { p = p.copy(gender = it.take(40)) }
        LdField(p.orientation, "Sexuelle Orientierung (optional)") { p = p.copy(orientation = it.take(40)) }
        SectionTitle("BDSM-Rolle")
        val roles = listOf("Dominant", "Submissiv", "Switch", "Solo")
        ChoiceChips(roles.map { it to it } + ("__custom" to "Frei definierbar"), if (p.role in roles) p.role else "__custom") {
            p = p.copy(role = if (it == "__custom") "" else it)
        }
        if (p.role !in roles) LdField(p.role, "Eigene Rollenbezeichnung") { p = p.copy(role = it.take(40)) }
        SectionTitle("Schwierigkeitsgrad")
        LabeledSlider("Gewünschte Stufe", p.difficulty, 1..6, Seed.DIFFICULTY_NAMES[p.difficulty - 1]) { p = p.copy(difficulty = it) }
        Hint("Steuert Tonfall und spielerische Komplexität, nie gefährliche körperliche Belastung.")
        LdField(p.likes, "Persönliche Vorlieben", singleLine = false) { p = p.copy(likes = it.take(600)) }
        LdField(p.limitsText, "Persönliche Grenzen (Begriffe, durch Komma getrennt)", singleLine = false) { p = p.copy(limitsText = it.take(600)) }
        Hint("Aufgaben, die einen dieser Begriffe enthalten, werden von der Challenge-Engine blockiert. Mehr im Grenzen-Manager.")
        LdField(p.goals, "Persönliche Ziele", singleLine = false) { p = p.copy(goals = it.take(600)) }
        SectionTitle("Anatomie (freiwillig)")
        val hcs by remember { vm.db.hardcoreDao().settings() }.collectAsState(null)
        val anat = hcs?.anatomy.orEmpty().split(",").filter { it.isNotBlank() }.toSet()
        Chips(com.lockeddesire.app.engine.Hardcore.ANATOMY, anat) { k ->
            val cur = hcs ?: com.lockeddesire.app.data.HardcoreSettings()
            val next = if (k in anat) anat - k else anat + k
            vm.run { hc.saveSettings(cur.copy(anatomy = next.joinToString(","))) }
        }
        Hint("Nur für die Auswahl von Hardcore-Aufgaben. Wird nie aus Geschlecht oder Pronomen abgeleitet und nie an eine Cloud-KI gesendet.")
        HardcoreSummaryCard()
        GoldButton("Profil speichern", Modifier.fillMaxWidth()) { vm.saveProfile(p); vm.repo.emit("INFO", "Profil gespeichert") }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Statistiken", Modifier.weight(1f)) { nav("stats") }
            GhostButton("Erfolge", Modifier.weight(1f)) { nav("levels") }
        }
    }
}

@Composable
fun PersonaGalleryScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val personas by vm.personas.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var delete by remember { mutableStateOf<AiPersona?>(null) }
    Screen("KI-Personas", onBack = { nav("back") }) {
        Hint("Wähle, wer dich durch den Dungeon begleitet. Jede Persona behält ihre eigenen Einstellungen und ihren eigenen Chat.")
        personas.forEach { p ->
            val active = p.id == prefs.activePersonaId
            GothicCard(highlight = active) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBubble(p.avatar, 56)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, color = LD.Gold)
                        Text(when (p.type) { "FEMALE" -> "Herrin"; "MALE" -> "Herr"; "NEUTRAL" -> "Geschlechtsneutral"; else -> "Eigener Charakter" } + " · Anrede: ${p.salutation}", style = MaterialTheme.typography.labelMedium, color = LD.Dim)
                        Text(Seed.PERSONALITIES.firstOrNull { it.first == p.personality }?.second.orEmpty() + " · Strenge ${p.strictness}/10", style = MaterialTheme.typography.labelMedium, color = LD.Dim)
                    }
                    if (active) Badge("Aktiv", LocalAccent.current)
                }
                if (p.greeting.isNotBlank()) Text("„${p.greeting}“", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!active) GoldButton("Auswählen", Modifier.weight(1f)) { vm.savePrefs(prefs.copy(activePersonaId = p.id)) }
                    GhostButton("Anpassen", Modifier.weight(1f)) { nav("personaEdit/${p.id}") }
                    if (!p.builtIn && !active) GhostButton("Löschen", Modifier.weight(1f)) { delete = p }
                }
            }
        }
        GoldButton("＋ Neuen Charakter anlegen", Modifier.fillMaxWidth()) { nav("personaEdit/0") }
    }
    delete?.let { p ->
        Confirm("Charakter löschen", "„${p.name}“ und den zugehörigen Chat wirklich löschen?", "Löschen", { delete = null }) {
            vm.run { db.aiDao().clearConversations(p.id); db.aiDao().delete(p) }
        }
    }
}

@Composable
fun PersonaEditScreen(id: Long) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val personas by vm.personas.collectAsState()
    val existing = personas.firstOrNull { it.id == id }
    var p by remember(existing?.id) { mutableStateOf(existing ?: AiPersona(name = "", type = "CUSTOM", salutation = "", greeting = "")) }
    Screen(if (id == 0L) "Neuer Charakter" else "KI-Personalisierung", onBack = { nav("back") }) {
        GothicCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarBubble(p.avatar, 60)
                Spacer(Modifier.width(12.dp))
                Text(p.name.ifBlank { "Unbenannt" }, style = MaterialTheme.typography.headlineSmall, color = LD.Gold)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("👸", "🤴", "🦇", "😈", "🎩", "🕊", "👑", "🖤", "🌹", "🐺", "💀", "🔥", "🧛", "🧙", "🎭", "🌙").forEach { a ->
                    Text(a, fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (a == p.avatar) LocalAccent.current.copy(alpha = 0.5f) else LD.Black)
                        .clickable { p = p.copy(avatar = a) }.padding(8.dp))
                }
            }
        }
        LdField(p.name, "Name") { p = p.copy(name = it.take(40)) }
        SectionTitle("Charaktertyp")
        ChoiceChips(listOf("FEMALE" to "Weibliche Herrin", "MALE" to "Männlicher Herr", "NEUTRAL" to "Geschlechtsneutral", "CUSTOM" to "Frei konfigurierbar"), p.type) { p = p.copy(type = it) }
        LdField(p.pronouns, "Pronomen") { p = p.copy(pronouns = it.take(30)) }
        LdField(p.salutation, "Anrede (so sprichst du die Persona an)") { p = p.copy(salutation = it.take(30)) }
        SectionTitle("Persönlichkeit")
        ChoiceChips(Seed.PERSONALITIES, p.personality) { p = p.copy(personality = it) }
        Hint(when (p.personality) {
            "STRENG" -> "Direkt, diszipliniert und anspruchsvoll."
            "DOMINANT" -> "Selbstbewusst und bestimmend innerhalb vereinbarter Grenzen."
            "DUESTER" -> "Mysteriös, geheimnisvoll und dramatisch."
            "GEMEIN" -> "Spöttisch, provokant und besonders herausfordernd."
            "FUERSORGLICH" -> "Ruhig, motivierend und unterstützend."
            else -> "Verspielt, neckisch und herausfordernd."
        })
        LabeledSlider("Strenge", p.strictness, 1..10, "${p.strictness}/10") { p = p.copy(strictness = it) }
        Hint("Die Strenge verändert nur den Tonfall, nie die körperliche Belastung.")
        SectionTitle("Stimme")
        LabeledSlider("Tonhöhe", (p.voicePitch * 10).toInt(), 5..20, "%.1f".format(p.voicePitch)) { p = p.copy(voicePitch = it / 10f) }
        LabeledSlider("Sprechtempo", (p.voiceRate * 10).toInt(), 5..20, "%.1f".format(p.voiceRate)) { p = p.copy(voiceRate = it / 10f) }
        LdField(p.greeting, "Eigene Begrüßung", singleLine = false) { p = p.copy(greeting = it.take(300)) }
        GhostButton("🔊 Stimme testen", Modifier.fillMaxWidth()) {
            vm.speak(p.greeting.ifBlank { "Dein Dungeon ist geöffnet." }, force = true)
        }
        GoldButton("Speichern", Modifier.fillMaxWidth(), enabled = p.name.isNotBlank() && p.salutation.isNotBlank()) {
            vm.run { db.aiDao().save(p) }
            nav("back")
        }
    }
}

@Composable
fun ChatScreen(activity: MainActivity) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val persona by vm.persona.collectAsState()
    val consent by vm.consent.collectAsState()
    var conversationId by remember { mutableStateOf(0L) }
    LaunchedEffect(persona?.id) {
        persona?.let { conversationId = try { vm.repo.conversation(it.id).id } catch (_: Exception) { 0L } }
    }
    val messages by remember(conversationId) { vm.db.aiDao().messages(conversationId) }.collectAsState(emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, busy) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (busy) 1 else 0) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isBlank() || busy) return
        input = ""; busy = true
        vm.run { try { val r = sendChat(t); vm.speak(r) } finally { busy = false } }
    }
    val stt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input = it }
        }
    }

    Screen(persona?.name ?: "KI-Chat", onBack = { nav("back") }, scroll = false, actions = {
        TextButton(onClick = { nav("personas") }) { Text("👑", fontSize = 20.sp) }
    }) {
        if (!consent.aiEnabled) Hint("Die KI ist deaktiviert. Du kannst sie unter Datenschutz wieder einschalten.")
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages, key = { it.id }) { m -> MessageBubble(m, persona) }
            if (busy) item { Text("${persona?.name ?: "KI"} schreibt …", color = LD.Dim, style = MaterialTheme.typography.bodySmall) }
        }
        Chips(listOf("task" to "Neue Challenge", "hc" to "⛓ Hardcore-Prüfung", "progress" to "Fortschritt", "pause" to "Pause", "limits" to "Grenzen ändern"), emptySet()) {
            when (it) {
                "task" -> send("Ich bin bereit für eine neue Challenge.")
                "hc" -> send("Gib mir eine Hardcore-Prüfung.")
                "progress" -> send("Wie ist mein Fortschritt?")
                "pause" -> send("Ich brauche eine Pause.")
                "limits" -> nav("limits")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LdField(input, "Nachricht", Modifier.weight(1f), singleLine = false) { input = it.take(1000) }
            TextButton(onClick = {
                try {
                    vm.externalLaunch = true; stt.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                    })
                } catch (_: Exception) { vm.repo.emit("INFO", "Spracheingabe ist auf diesem Gerät nicht verfügbar") }
            }) { Text("🎤", fontSize = 22.sp) }
            GoldButton("➤", enabled = input.isNotBlank() && !busy) { send(input) }
        }
    }
}

@Composable
private fun MessageBubble(m: AiMessage, persona: AiPersona?) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(14.dp))
                .background(if (m.fromUser) accent.copy(alpha = 0.35f) else LD.LeatherLight)
                .border(1.dp, if (m.fromUser) accent.copy(alpha = 0.6f) else LD.Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!m.fromUser) Row(verticalAlignment = Alignment.CenterVertically) {
                Text((persona?.avatar ?: "👑") + " " + (persona?.name ?: ""), style = MaterialTheme.typography.labelMedium, color = LD.Gold, modifier = Modifier.weight(1f, fill = false))
                Text("  🔊", modifier = Modifier.clickable { vm.speak(m.text, force = true) })
            }
            Text(m.text, style = MaterialTheme.typography.bodyLarge)
            if (m.offerState.isNotBlank()) {
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(LD.Black.copy(alpha = 0.7f)).border(1.dp, LD.Gold.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(m.offerTitle, style = MaterialTheme.typography.titleMedium, color = LD.Gold)
                    Text(m.offerDescription, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge(categoryName(m.offerCategory))
                        if (m.offerDuration > 0) Badge("${m.offerDuration} Min")
                        Badge("+${m.offerXp} XP")
                    }
                    when (m.offerState) {
                        "OPEN" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GoldButton("Annehmen", Modifier.weight(1f)) { vm.run { respondToOffer(m.id, "ACCEPT") } }
                            GhostButton("Ablehnen", Modifier.weight(1f)) { vm.run { vm.speak(respondToOffer(m.id, "DECLINE")) } }
                        }
                        "ACCEPTED" -> {
                            if (m.offerDuration > 0) CountdownTimer(m.offerDuration * 60)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoldButton("Erledigt", Modifier.weight(1f)) { vm.run { vm.speak(respondToOffer(m.id, "DONE")) } }
                                GhostButton("Abbrechen", Modifier.weight(1f)) { vm.run { vm.speak(respondToOffer(m.id, "ABORT")) } }
                            }
                            Hint("Abbrechen ist jederzeit erlaubt und wird nicht bestraft.")
                        }
                        "HC" -> GoldButton("⛓ Zur Prüfung", Modifier.fillMaxWidth()) { nav("hardcore") }
                        else -> Badge(statusText(m.offerState), LD.Dim)
                    }
                }
            }
        }
    }
}
