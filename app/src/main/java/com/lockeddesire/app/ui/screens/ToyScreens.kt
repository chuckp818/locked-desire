package com.lockeddesire.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lockeddesire.app.data.Seed
import com.lockeddesire.app.data.TimeUtil
import com.lockeddesire.app.data.Toy
import com.lockeddesire.app.data.ToySession
import com.lockeddesire.app.data.now
import com.lockeddesire.app.security.Crypto
import com.lockeddesire.app.ui.components.Badge
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
import com.lockeddesire.app.ui.components.VaultImage
import com.lockeddesire.app.ui.components.wheelFor
import com.lockeddesire.app.ui.theme.LD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ToyGalleryScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val toys by vm.toys.collectAsState()
    var filter by remember { mutableStateOf("ALL") }
    val shown = toys.filter { filter == "ALL" || (filter == "FAV" && it.favorite) || it.category == filter }
    Screen("Toy-Galerie", onBack = { nav("back") }) {
        Hint("Alle Fotos bleiben verschlüsselt auf diesem Gerät.")
        ChoiceChips(listOf("ALL" to "Alle", "FAV" to "★ Favoriten") + Seed.TOY_CATEGORIES.map { it to it }, filter) { filter = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GoldButton("＋ Toy hinzufügen", Modifier.weight(1f)) { nav("toyDetail/0") }
            GhostButton("🎰 Roulette", Modifier.weight(1f)) { nav("toyRoulette") }
        }
        if (shown.isEmpty()) Hint("Noch keine Toys in dieser Ansicht.")
        shown.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    GothicCard(Modifier.weight(1f), onClick = { nav("toyDetail/${t.id}") }) {
                        VaultImage(t.photoFile, Modifier.fillMaxWidth().aspectRatio(1f))
                        Text((if (t.favorite) "★ " else "") + t.name, style = MaterialTheme.typography.titleSmall, color = LD.Gold, maxLines = 1)
                        Text("${t.category} · ${t.sizeClass}", style = MaterialTheme.typography.labelSmall, color = LD.Dim)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ToyDetailScreen(id: Long) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored by remember(id) { vm.db.toyDao().toy(id) }.collectAsState(null)
    val sessions by remember(id) { vm.db.toyDao().sessions(id) }.collectAsState(emptyList())
    var t by remember(stored?.id) { mutableStateOf(stored ?: Toy(name = "")) }
    var delete by remember { mutableStateOf(false) }
    var sessionNote by remember { mutableStateOf("") }
    val oldPhoto = stored?.photoFile ?: ""

    fun storePhoto(uri: Uri) = scope.launch {
        val name = withContext(Dispatchers.IO) {
            try { context.contentResolver.openInputStream(uri)?.use { Crypto.savePhoto(context, it.readBytes()) } } catch (_: Exception) { null }
        }
        if (name != null) { if (t.photoFile.isNotBlank() && t.photoFile != oldPhoto) Crypto.deletePhoto(context, t.photoFile); t = t.copy(photoFile = name) }
        else vm.repo.emit("INFO", "Foto konnte nicht gespeichert werden")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) storePhoto(uri) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) scope.launch {
            storePhoto(u).join()
            File(context.cacheDir, "camera").listFiles()?.forEach { it.delete() }
        }
    }

    Screen(if (id == 0L) "Neues Toy" else "Toy-Details", onBack = { nav("back") }) {
        VaultImage(t.photoFile, Modifier.fillMaxWidth().aspectRatio(1.2f))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("📷 Kamera", Modifier.weight(1f)) {
                try {
                    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                    val f = File(dir, "cap_${now()}.jpg")
                    val u = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                    camUri = u; vm.externalLaunch = true; camera.launch(u)
                } catch (_: Exception) { vm.repo.emit("INFO", "Keine Kamera-App gefunden") }
            }
            GhostButton("🖼 Foto wählen", Modifier.weight(1f)) {
                vm.externalLaunch = true; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        LdField(t.name, "Name") { t = t.copy(name = it.take(50)) }
        SectionTitle("Kategorie")
        ChoiceChips(Seed.TOY_CATEGORIES.map { it to it }, t.category) { t = t.copy(category = it) }
        SectionTitle("Größenklasse")
        ChoiceChips(listOf("XS", "S", "M", "L", "XL").map { it to it }, t.sizeClass) { t = t.copy(sizeClass = it) }
        LdField(t.material, "Material") { t = t.copy(material = it.take(50)) }
        LdField(t.measures, "Maße (optional)") { t = t.copy(measures = it.take(60)) }
        LdField(t.note, "Persönliche Notizen", singleLine = false) { t = t.copy(note = it.take(500)) }
        LabeledSwitch("Favorit", t.favorite) { t = t.copy(favorite = it) }
        LabeledSwitch("Für Roulette freigeben", t.rouletteEnabled) { t = t.copy(rouletteEnabled = it) }
        if (t.lastUsedAt > 0) Hint("Zuletzt verwendet: ${TimeUtil.dateTime(t.lastUsedAt)}")
        GoldButton("Speichern", Modifier.fillMaxWidth(), enabled = t.name.isNotBlank()) {
            val toSave = t
            vm.run {
                if (oldPhoto.isNotBlank() && oldPhoto != toSave.photoFile) Crypto.deletePhoto(context, oldPhoto)
                db.toyDao().save(toSave); checkAchievements()
            }
            nav("back")
        }
        if (id != 0L && stored != null) {
            SectionTitle("Freiwillige Verwendung")
            LdField(sessionNote, "Notiz (optional)") { sessionNote = it.take(200) }
            GhostButton("Verwendung eintragen", Modifier.fillMaxWidth()) {
                val note = sessionNote; sessionNote = ""
                vm.run {
                    db.toyDao().insert(ToySession(toyId = id, note = note))
                    db.toyDao().save(t.copy(lastUsedAt = now()))
                    bumpCounter("toys_used", 1, t.name)
                    emit("INFO", "Eingetragen")
                }
            }
            sessions.take(10).forEach { s -> Text("• ${TimeUtil.dateTime(s.createdAt)}" + if (s.note.isNotBlank()) " – ${s.note}" else "", style = MaterialTheme.typography.bodySmall) }
            GhostButton("Toy löschen", Modifier.fillMaxWidth()) { delete = true }
        }
    }
    if (delete) Confirm("Toy löschen", "„${t.name}“ und sein Foto endgültig löschen?", "Löschen", { delete = false }) {
        val s = stored
        if (s != null) vm.run { Crypto.deletePhoto(context, s.photoFile); db.toyDao().deleteSessions(s.id); db.toyDao().delete(s) }
        nav("back")
    }
}

@Composable
fun ToyRouletteScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val toys by vm.toys.collectAsState()
    val released = toys.filter { it.rouletteEnabled }
    Screen("Toy-Roulette", onBack = { nav("back") }) {
        if (released.isEmpty()) {
            GothicCard {
                Text("Keine Toys freigegeben.", style = MaterialTheme.typography.titleMedium)
                GoldButton("Zur Toy-Galerie", Modifier.fillMaxWidth()) { nav("toys") }
            }
        } else {
            Hint("Das Rad wählt nur aus. Ob, wann und wie du etwas verwendest, entscheidest allein du.")
            RoulettePanel(
                idleLabels = released.map { it.name },
                emptyText = "Keine Toys freigegeben.",
                spin = {
                    val toy = released.random()
                    val (labels, idx) = wheelFor(released.map { it.name }, toy.name)
                    Triple(labels, idx, toy to vm.repo.rules.toyComment(vm.repo.aiContext(), toy.name))
                }
            ) { (toy, comment), reroll ->
                GothicCard(highlight = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VaultImage(toy.photoFile, Modifier.size(110.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(toy.name, style = MaterialTheme.typography.headlineSmall, color = LD.Gold)
                            Badge(toy.category); Badge("Größe ${toy.sizeClass}")
                        }
                    }
                    Text("„$comment“", color = com.lockeddesire.app.ui.theme.LocalAccent.current)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton("Bestätigen", Modifier.weight(1f)) { vm.repo.emit("INFO", "Auswahl bestätigt: ${toy.name}") }
                        GhostButton("Neu würfeln", Modifier.weight(1f), onClick = reroll)
                    }
                    GhostButton("Details öffnen", Modifier.fillMaxWidth()) { nav("toyDetail/${toy.id}") }
                }
            }
        }
    }
}
