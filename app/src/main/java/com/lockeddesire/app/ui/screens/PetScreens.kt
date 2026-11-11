package com.lockeddesire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockeddesire.app.data.PetProfile
import com.lockeddesire.app.data.Seed
import com.lockeddesire.app.data.TimeUtil
import com.lockeddesire.app.engine.Proposal
import com.lockeddesire.app.ui.components.AvatarBubble
import com.lockeddesire.app.ui.components.ChoiceChips
import com.lockeddesire.app.ui.components.Confirm
import com.lockeddesire.app.ui.components.GhostButton
import com.lockeddesire.app.ui.components.GoldButton
import com.lockeddesire.app.ui.components.GothicCard
import com.lockeddesire.app.ui.components.Hint
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

private val PET_TAGS = setOf("petplay", "puppyplay", "kittenplay", "ponyplay", "bunnyplay", "foxplay")

private fun roleTag(role: String) = when (role) {
    "Puppy" -> "puppyplay"; "Kitten" -> "kittenplay"; "Pony" -> "ponyplay"; "Bunny" -> "bunnyplay"; "Fox" -> "foxplay"; else -> "petplay"
}

suspend fun com.lockeddesire.app.data.Repo.petTask(pet: PetProfile?): Proposal? {
    val tags = if (pet == null) PET_TAGS else setOf("petplay", roleTag(pet.role))
    val base = pickTask(categories = setOf("petplay"), tags = tags) ?: pickTask(categories = setOf("petplay")) ?: return null
    if (pet != null && pet.jointFriendly && base.jointLoad) {
        return pickTask(categories = setOf("petplay"))?.takeIf { !it.jointLoad }
    }
    return base
}

fun petComment(pet: PetProfile?, base: String): String {
    if (pet == null) return base
    val call = pet.salutation.ifBlank { pet.name }
    return "$call, " + base.replaceFirstChar { it.lowercase() }
}

@Composable
fun PetDungeonScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val pets by vm.pets.collectAsState()
    val sessions by vm.petSessions.collectAsState()
    val pet = pets.firstOrNull { it.active } ?: pets.firstOrNull()
    var current by remember { mutableStateOf<Pair<Proposal, String>?>(null) }
    var empty by remember { mutableStateOf(false) }
    fun draw() = vm.run {
        val p = petTask(pet)
        empty = p == null
        current = p?.let { it to petComment(pet, rules.comment(aiContext(), "offer")) }
    }
    Screen("Petplay-Dungeon", onBack = { nav("back") }) {
        if (pet == null) {
            GothicCard {
                Text("Noch kein Pet-Profil", style = MaterialTheme.typography.titleLarge, color = LD.Gold)
                Text("Lege dein Pet an: Name, Rolle, Anrede und Grenzen.")
                GoldButton("Pet-Profil anlegen", Modifier.fillMaxWidth()) { nav("petProfile") }
            }
        } else {
            GothicCard(onClick = { nav("petProfile") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBubble(pet.avatar, 60)
                    Spacer(Modifier.width(12.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(pet.name, style = MaterialTheme.typography.headlineSmall, color = LD.Gold)
                        Text("${pet.role} · Pet-Level ${pet.level}", color = LD.Dim)
                    }
                }
                XpBar((pet.xp % 100) / 100f, "${pet.xp} Pet-XP")
                if (pet.jointFriendly) Hint("Gelenkschonender Modus: keine Aufgaben auf allen Vieren.")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldButton("Aufgabe ziehen", Modifier.weight(1f)) { draw() }
            GhostButton("Pet-Roulette", Modifier.weight(1f)) { nav("petRoulette") }
        }
        if (empty) GothicCard { Text("Gerade passt keine Pet-Aufgabe zu deinen Grenzen.") }
        current?.let { (p, c) -> ProposalFlow(p, c, "PET", { draw() }, "Andere Aufgabe") }
        GothicCard {
            Text("Alternativen bei Beschwerden", style = MaterialTheme.typography.titleMedium, color = LD.Gold)
            Text("Knie: dicke Decke oder Knieschoner, oder stattdessen sitzend spielen. Handgelenke: auf Fäusten oder Unterarmen abstützen, oder aufrecht kniend. Rücken: Rollenspiel im Sitzen oder Liegen statt auf allen Vieren.", style = MaterialTheme.typography.bodySmall)
        }
        SectionTitle("Pet-Statistik")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(sessions.size.toString(), "Sessions", Modifier.weight(1f), "🐾")
            StatTile(sessions.count { it.createdAt >= System.currentTimeMillis() - 7 * TimeUtil.DAY }.toString(), "Diese Woche", Modifier.weight(1f), "📅")
        }
        sessions.take(10).forEach { s -> Text("• ${TimeUtil.dateTime(s.createdAt)} – ${s.taskTitle}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun PetProfileScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val pets by vm.pets.collectAsState()
    var editing by remember { mutableStateOf<PetProfile?>(null) }
    var delete by remember { mutableStateOf<PetProfile?>(null) }
    Screen("Pet-Profil", onBack = { nav("back") }) {
        val e = editing
        if (e != null) {
            GothicCard {
                Row(verticalAlignment = Alignment.CenterVertically) { AvatarBubble(e.avatar, 56); Spacer(Modifier.width(12.dp)); Text(e.name.ifBlank { "Neues Pet" }, style = MaterialTheme.typography.headlineSmall, color = LD.Gold) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("🐶", "🐱", "🐴", "🐰", "🦊", "🐾", "🐺", "🐈‍⬛", "🦄", "🐹").forEach { a ->
                        Text(a, fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (a == e.avatar) LocalAccent.current.copy(alpha = 0.5f) else LD.Black).clickable { editing = e.copy(avatar = a) }.padding(8.dp))
                    }
                }
            }
            LdField(e.name, "Pet-Name") { editing = e.copy(name = it.take(30)) }
            SectionTitle("Pet-Rolle")
            val roles = Seed.PET_ROLES.map { it.first }
            ChoiceChips(Seed.PET_ROLES.map { it.first to it.second + " " + it.first }, if (e.role in roles) e.role else "Eigenes Pet") {
                editing = e.copy(role = it, avatar = Seed.PET_ROLES.first { r -> r.first == it }.second)
            }
            if (e.role !in roles || e.role == "Eigenes Pet") LdField(if (e.role == "Eigenes Pet") "" else e.role, "Eigene Pet-Rolle") { editing = e.copy(role = it.ifBlank { "Eigenes Pet" }.take(30)) }
            LdField(e.salutation, "Bevorzugte Anrede") { editing = e.copy(salutation = it.take(30)) }
            LdField(e.limits, "Individuelle Grenzen", singleLine = false) { editing = e.copy(limits = it.take(400)) }
            LdField(e.favorites, "Lieblingsaufgaben", singleLine = false) { editing = e.copy(favorites = it.take(400)) }
            LabeledSwitch("Gelenkschonend", e.jointFriendly, "Keine Aufgaben auf allen Vieren (Knie, Handgelenke, Rücken)") { editing = e.copy(jointFriendly = it) }
            LabeledSwitch("Aktives Pet", e.active) { editing = e.copy(active = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoldButton("Speichern", Modifier.weight(1f), enabled = e.name.isNotBlank()) {
                    vm.run {
                        if (e.active) db.petDao().petsNow().filter { it.id != e.id && it.active }.forEach { db.petDao().save(it.copy(active = false)) }
                        db.petDao().save(e)
                        if (e.limits.isNotBlank()) upsertMemory("PET_LIMITS_${e.name}", "Grenzen von ${e.name}: ${e.limits}")
                        upsertMemory("PET_ROLE", "Bevorzugte Pet-Rolle: ${e.role} (${e.name})")
                    }
                    editing = null
                }
                GhostButton("Abbrechen", Modifier.weight(1f)) { editing = null }
            }
        } else {
            pets.forEach { p ->
                GothicCard(highlight = p.active) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarBubble(p.avatar, 52); Spacer(Modifier.width(12.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleLarge, color = LD.Gold)
                            Text("${p.role} · Level ${p.level} · ${p.xp} XP" + if (p.active) " · aktiv" else "", color = LD.Dim)
                        }
                    }
                    if (p.salutation.isNotBlank()) Text("Anrede: ${p.salutation}")
                    if (p.limits.isNotBlank()) Text("Grenzen: ${p.limits}", style = MaterialTheme.typography.bodySmall)
                    if (p.favorites.isNotBlank()) Text("Lieblingsaufgaben: ${p.favorites}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton("Bearbeiten", Modifier.weight(1f)) { editing = p }
                        GhostButton("Löschen", Modifier.weight(1f)) { delete = p }
                    }
                }
            }
            GoldButton("＋ Pet anlegen", Modifier.fillMaxWidth()) { editing = PetProfile(name = "", active = pets.none { it.active }) }
        }
    }
    delete?.let { p -> Confirm("Pet löschen", "„${p.name}“ wirklich löschen?", "Löschen", { delete = null }) { vm.run { db.petDao().delete(p) } } }
}

@Composable
fun PetRouletteScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val pets by vm.pets.collectAsState()
    val pet = pets.firstOrNull { it.active } ?: pets.firstOrNull()
    val tasks by vm.tasks.collectAsState()
    val petTitles = tasks.filter { it.categoryKey == "petplay" && it.enabled }.map { it.title }
    Screen("Pet-Roulette", onBack = { nav("back") }) {
        if (pet != null) Hint("Für ${pet.name} (${pet.role})") else Hint("Tipp: Mit einem Pet-Profil werden Rolle und Anrede berücksichtigt.")
        RoulettePanel(
            idleLabels = petTitles.take(10),
            emptyText = "Keine Pet-Aufgabe passt gerade zu deinen Grenzen.",
            spin = {
                val p = vm.repo.petTask(pet) ?: return@RoulettePanel null
                val (labels, idx) = wheelFor(petTitles, p.title)
                Triple(labels, idx, p to petComment(pet, vm.repo.rules.comment(vm.repo.aiContext(), "offer")))
            }
        ) { (p, c), reroll -> ProposalFlow(p, c, "PET", reroll) }
        GhostButton("Zum Petplay-Dungeon", Modifier.fillMaxWidth()) { nav("petDungeon") }
    }
}
