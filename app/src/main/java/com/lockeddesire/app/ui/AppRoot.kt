package com.lockeddesire.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lockeddesire.app.MainActivity
import com.lockeddesire.app.ui.components.ChainDivider
import com.lockeddesire.app.ui.components.Fx
import com.lockeddesire.app.ui.components.GhostButton
import com.lockeddesire.app.ui.components.GoldButton
import com.lockeddesire.app.ui.components.GothicBackground
import com.lockeddesire.app.ui.components.GothicCard
import com.lockeddesire.app.ui.components.LdField
import com.lockeddesire.app.ui.components.LocalNav
import com.lockeddesire.app.ui.components.LocalVm
import com.lockeddesire.app.ui.screens.*
import com.lockeddesire.app.ui.theme.LD
import com.lockeddesire.app.ui.theme.LocalEffects
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ROUTES = setOf(
    "dashboard", "profile", "personas", "chat", "taskRoulette", "bdsmDungeon", "dungeon24", "chastity", "petDungeon",
    "petProfile", "petRoulette", "fetishUniverse", "fetishProfile", "fetishRoulette", "limits", "toys", "toyRoulette",
    "timeGen", "journal", "fitness", "stats", "partner", "levels", "taskEditor", "notifSettings", "privacy", "settings"
)

@Composable
fun AppRoot(activity: MainActivity) {
    val vm = LocalVm.current
    val ready by vm.ready.collectAsState()
    val profile by vm.profile.collectAsState()
    val lock by vm.lock.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    var splashDone by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    var celebration by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vm.repo.events.collect { e ->
            when (e.kind) {
                "LEVEL", "ACH", "RECORD" -> { celebration = e.text; Fx.feedback(context, vm.prefs.value, strong = true) }
                else -> launch { snackbar.showSnackbar(e.text) }
            }
        }
    }
    // Solange die App offen ist, Dungeon-Phasen und -Ende laufend prüfen
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        while (true) { try { vm.repo.tickDungeon() } catch (_: Exception) { }; delay(30_000) }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            !splashDone || !ready -> Splash { splashDone = true }
            !profile.ageConfirmed -> AgeGate(activity)
            lock.pinSet && !unlocked -> LockScreen(activity)
            else -> MainNav(activity)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(12.dp)) { data ->
            Snackbar(containerColor = LD.Bordeaux, contentColor = LD.Grey) { Text(data.visuals.message) }
        }
        celebration?.let { text -> Celebration(text) { celebration = null } }
    }
}

@Composable
private fun Splash(onDone: () -> Unit) {
    val effects = LocalEffects.current
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.8f) }
    LaunchedEffect(Unit) {
        if (effects) {
            launch { scale.animateTo(1f, tween(1400)) }
            alpha.animateTo(1f, tween(1200))
            delay(700)
        }
        onDone()
    }
    GothicBackground {
        Column(Modifier.fillMaxSize().alpha(if (effects) alpha.value else 1f).scale(if (effects) scale.value else 1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔒", fontSize = 84.sp)
            Spacer(Modifier.height(16.dp))
            Text("LOCKED DESIRE", style = MaterialTheme.typography.displaySmall, color = LD.Gold)
            Spacer(Modifier.height(8.dp))
            ChainDivider()
            Spacer(Modifier.height(8.dp))
            Text("Dein Dungeon erwartet dich", color = LD.Grey)
        }
    }
}

@Composable
private fun AgeGate(activity: MainActivity) {
    val vm = LocalVm.current
    val profile by vm.profile.collectAsState()
    GothicBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔞", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text("NUR FÜR ERWACHSENE", style = MaterialTheme.typography.headlineMedium, color = LD.Gold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            GothicCard {
                Text("Diese App enthält Inhalte zu BDSM, Keuschheit und erotischem Rollenspiel. Sie ist ausschließlich für Personen ab 18 Jahren bestimmt.")
                Text("Alles hier ist freiwillig. Du kannst jede Aufgabe ablehnen, jederzeit pausieren und alles mit dem Safeword-Knopf 🛑 sofort stoppen. Pausen und Abbrüche werden nie bestraft.")
                Text("Bei Schmerzen, Taubheit oder Unwohlsein gilt immer: sofort aufhören.", color = LD.Gold)
            }
            Spacer(Modifier.height(20.dp))
            GoldButton("Ich bin mindestens 18 Jahre alt", Modifier.fillMaxWidth()) {
                vm.saveProfile(profile.copy(ageConfirmed = true))
                activity.askNotificationPermission()
            }
            Spacer(Modifier.height(10.dp))
            GhostButton("Ich bin jünger – App schließen", Modifier.fillMaxWidth()) { activity.finishAffinity() }
        }
    }
}

@Composable
private fun LockScreen(activity: MainActivity) {
    val vm = LocalVm.current
    val lock by vm.lock.collectAsState()
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(lock.biometric) { if (lock.biometric) activity.showBiometric { vm.unlocked.value = true } }
    GothicBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔐", fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text("GESPERRT", style = MaterialTheme.typography.headlineMedium, color = LD.Gold)
            Spacer(Modifier.height(16.dp))
            LdField(pin, "PIN", number = true) { pin = it.filter { c -> c.isDigit() }.take(12); error = false }
            if (error) Text("Falsche PIN", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            GoldButton("Entsperren", Modifier.fillMaxWidth(), enabled = pin.length >= 4) {
                scope.launch { if (vm.repo.secure.checkPin(pin)) { pin = ""; vm.unlocked.value = true } else error = true }
            }
            if (lock.biometric && activity.biometricAvailable()) {
                Spacer(Modifier.height(10.dp))
                GhostButton("Biometrisch entsperren", Modifier.fillMaxWidth()) { activity.showBiometric { vm.unlocked.value = true } }
            }
        }
    }
}

@Composable
private fun Celebration(text: String, onDone: () -> Unit) {
    val effects = LocalEffects.current
    val scale = remember(text) { Animatable(0.4f) }
    LaunchedEffect(text) {
        if (effects) scale.animateTo(1f, tween(500))
        delay(2600)
        onDone()
    }
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        GothicCard(Modifier.scale(if (effects) scale.value else 1f), onClick = onDone, highlight = true) {
            Text("✨ 🗝 ✨", fontSize = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(text, style = MaterialTheme.typography.headlineSmall, color = LD.Gold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MainNav(activity: MainActivity) {
    val nav = rememberNavController()
    val go: (String) -> Unit = { route ->
        if (route == "back") nav.popBackStack() else nav.navigate(route) { launchSingleTop = true }
    }
    val pending by activity.pendingRoute
    LaunchedEffect(pending) {
        val r = pending
        if (r == "back") nav.popBackStack()
        else if (r != null && r != "dashboard" && (r in ROUTES || r.startsWith("personaEdit/") || r.startsWith("toyDetail/"))) nav.navigate(r) { launchSingleTop = true }
        if (r != null) activity.pendingRoute.value = null
    }
    CompositionLocalProvider(LocalNav provides go) {
        NavHost(nav, startDestination = "dashboard") {
            composable("dashboard") { DashboardScreen() }
            composable("profile") { ProfileScreen() }
            composable("personas") { PersonaGalleryScreen() }
            composable("personaEdit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PersonaEditScreen(it.arguments?.getLong("id") ?: 0L)
            }
            composable("chat") { ChatScreen(activity) }
            composable("taskRoulette") { TaskRouletteScreen() }
            composable("bdsmDungeon") { BdsmDungeonScreen() }
            composable("dungeon24") { Dungeon24Screen() }
            composable("chastity") { ChastityScreen() }
            composable("petDungeon") { PetDungeonScreen() }
            composable("petProfile") { PetProfileScreen() }
            composable("petRoulette") { PetRouletteScreen() }
            composable("fetishUniverse") { FetishUniverseScreen() }
            composable("fetishProfile") { FetishProfileScreen() }
            composable("fetishRoulette") { FetishRouletteScreen() }
            composable("limits") { LimitsScreen() }
            composable("toys") { ToyGalleryScreen() }
            composable("toyDetail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ToyDetailScreen(it.arguments?.getLong("id") ?: 0L)
            }
            composable("toyRoulette") { ToyRouletteScreen() }
            composable("timeGen") { TimeGeneratorScreen() }
            composable("journal") { JournalScreen() }
            composable("fitness") { FitnessScreen() }
            composable("stats") { StatsScreen() }
            composable("partner") { PartnerScreen() }
            composable("levels") { LevelsScreen() }
            composable("taskEditor") { TaskEditorScreen() }
            composable("notifSettings") { NotificationSettingsScreen(activity) }
            composable("privacy") { PrivacyScreen(activity) }
            composable("settings") { SettingsScreen() }
        }
    }
}
