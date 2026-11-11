package com.lockeddesire.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lockeddesire.app.data.FitnessActivity
import com.lockeddesire.app.data.PersonalCounter
import com.lockeddesire.app.data.Seed
import com.lockeddesire.app.data.SessionEntry
import com.lockeddesire.app.data.TimeUtil
import com.lockeddesire.app.data.now
import com.lockeddesire.app.engine.Roulette
import com.lockeddesire.app.ui.components.BarChart
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
import java.util.Calendar

@Composable
fun TimeGeneratorScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    var range by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var finished by remember { mutableStateOf(false) }
    Screen("Zeitgenerator", onBack = { nav("back") }) {
        Hint("Zufällige Dauer für geeignete Herausforderungen. Bereiche über eine Stunde sind nur für nichtkörperliche Aufgaben gedacht, etwa Verzicht, Fokus oder Schweigen.")
        ChoiceChips(Roulette.TIME_RANGES.mapIndexed { i, r -> i.toString() to (if (r.second >= 60 && r.first >= 60) "1–3 Std. (nichtkörperlich)" else "${r.first}–${r.second} Min") }, range.toString()) { range = it.toInt(); minutes = 0; finished = false }
        LdField(title, "Wofür? (optional)") { title = it.take(60) }
        GoldButton("🎲 Zeit würfeln", Modifier.fillMaxWidth()) { minutes = Roulette.randomMinutes(Roulette.TIME_RANGES[range]); finished = false }
        if (minutes > 0) {
            GothicCard(highlight = true) {
                Text("$minutes Minuten", style = MaterialTheme.typography.headlineMedium, color = LD.Gold)
                CountdownTimer(minutes * 60) { finished = true }
                LdField(note, "Notiz für das Tagebuch", singleLine = false) { note = it.take(400) }
                GhostButton(if (finished) "Ins Tagebuch eintragen" else "Vorzeitig beenden und eintragen", Modifier.fillMaxWidth()) {
                    val e = SessionEntry(kind = "TIMER", title = title.ifBlank { "Zeitgenerator" }, durationMin = minutes, note = note)
                    vm.run { db.statsDao().save(e); emit("INFO", "Im Tagebuch gespeichert") }
                    minutes = 0; note = ""
                }
            }
        }
    }
}

@Composable
fun JournalScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val entries by vm.journal.collectAsState()
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("NOTE") }
    var delete by remember { mutableStateOf<SessionEntry?>(null) }
    Screen("Session-Tagebuch", onBack = { nav("back") }) {
        GothicCard {
            ChoiceChips(listOf("NOTE" to "Notiz", "SESSION" to "Session", "AFTERCARE" to "Aftercare", "REPORT" to "Bericht"), kind) { kind = it }
            LdField(title, "Titel") { title = it.take(80) }
            LdField(minutes, "Dauer in Minuten (optional)", number = true) { minutes = it.filter { c -> c.isDigit() }.take(4) }
            LdField(note, "Eintrag", singleLine = false) { note = it.take(3000) }
            GoldButton("Eintrag speichern", Modifier.fillMaxWidth(), enabled = title.isNotBlank() || note.isNotBlank()) {
                val e = SessionEntry(kind = kind, title = title.ifBlank { "Eintrag" }, durationMin = minutes.toIntOrNull() ?: 0, note = note)
                vm.run { db.statsDao().save(e); if (kind == "SESSION") bumpCounter("bdsm_sessions", 1, e.title) }
                title = ""; note = ""; minutes = ""
            }
            Hint("Session-Einträge erhöhen den Zähler „BDSM-Sessions“.")
        }
        SectionTitle("Einträge (${entries.size})")
        entries.forEach { e ->
            GothicCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, color = LD.Gold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { delete = e }) { Text("✕") }
                }
                Text(TimeUtil.dateTime(e.createdAt) + " · " + when (e.kind) { "TIMER" -> "Zeitgenerator"; "SESSION" -> "Session"; "AFTERCARE" -> "Aftercare"; "REPORT" -> "Bericht"; "SAFEWORD" -> "Safeword"; "PARTNER" -> "Partner"; else -> "Notiz" } + if (e.durationMin > 0) " · ${e.durationMin} Min" else "", style = MaterialTheme.typography.labelSmall, color = LD.Dim)
                if (e.note.isNotBlank()) Text(e.note)
            }
        }
    }
    delete?.let { e -> Confirm("Eintrag löschen", "„${e.title}“ löschen?", "Löschen", { delete = null }) { vm.run { db.statsDao().delete(e) } } }
}

@Composable
fun FitnessScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val log by vm.fitness.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var exercise by remember { mutableStateOf(Seed.FITNESS_PRESETS.first().first) }
    var reps by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(60) }
    var goal by remember { mutableIntStateOf(5) }
    var delete by remember { mutableStateOf<FitnessActivity?>(null) }
    val countsReps = Seed.FITNESS_PRESETS.firstOrNull { it.first == exercise }?.second ?: true
    val weekStart = now() - 7 * TimeUtil.DAY
    val week = log.filter { it.createdAt >= weekStart }
    Screen("Fitness", onBack = { nav("back") }) {
        GothicCard {
            Text("Wochenziel: ${week.size} von $goal Einheiten", style = MaterialTheme.typography.titleMedium, color = LD.Gold)
            XpBar(week.size.toFloat() / goal, "")
            LabeledSlider("Wochenziel", goal, 1..21, "$goal") { goal = it }
        }
        SectionTitle("Training")
        ChoiceChips(Seed.FITNESS_PRESETS.map { it.first to it.first }, exercise) { exercise = it; reps = 0 }
        if (countsReps) {
            GothicCard(highlight = true) {
                Text("$reps", style = MaterialTheme.typography.displaySmall, color = LD.Gold, modifier = Modifier.fillMaxWidth())
                Text("Wiederholungen (Grenze ${prefs.fitnessMaxReps})", color = LD.Dim)
                GoldButton("+1", Modifier.fillMaxWidth()) { if (reps < prefs.fitnessMaxReps) reps++ }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("+5", Modifier.weight(1f)) { reps = (reps + 5).coerceAtMost(prefs.fitnessMaxReps) }
                    GhostButton("−1", Modifier.weight(1f)) { reps = (reps - 1).coerceAtLeast(0) }
                    GhostButton("0", Modifier.weight(1f)) { reps = 0 }
                }
                if (reps >= prefs.fitnessMaxReps) Hint("Deine persönliche Belastungsgrenze ist erreicht.")
            }
        } else {
            LabeledSlider("Dauer", seconds / 60, 1..prefs.fitnessMaxMinutes, "${seconds / 60} Min") { seconds = it * 60 }
            GothicCard { CountdownTimer(seconds) }
        }
        GoldButton("Einheit eintragen", Modifier.fillMaxWidth(), enabled = !countsReps || reps > 0) {
            val r = reps; val s = if (countsReps) 0 else seconds; val name = exercise
            vm.run {
                val best = db.statsDao().fitnessNow().filter { it.name == name }.maxOfOrNull { if (r > 0) it.reps else it.durationSec } ?: 0
                db.statsDao().insert(FitnessActivity(name = name, reps = r, durationSec = s))
                bumpCounter("fitness_challenges", 1, name)
                grantXp(10 + r / 5 + s / 120, name)
                val value = if (r > 0) r else s
                if (best in 1 until value) { bumpCounter("records", 1, name); emit("RECORD", "Neuer Rekord: $name") }
                checkAchievements()
            }
            reps = 0
        }
        Hint("Bei Schmerzen, Schwindel oder Atemnot sofort aufhören.")
        SectionTitle("Persönliche Rekorde")
        val records = log.groupBy { it.name }.map { (n, l) -> n to (l.maxOfOrNull { it.reps }.takeIf { (it ?: 0) > 0 }?.let { "$it Wdh." } ?: "${(l.maxOf { it.durationSec }) / 60} Min") }
        if (records.isEmpty()) Hint("Noch keine Einträge.")
        records.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (n, v) -> StatTile(v, n, Modifier.weight(1f)) }
                repeat(3 - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
        SectionTitle("Letzte 7 Tage")
        GothicCard { BarChart(lastDays(7) { start, end -> log.count { it.createdAt in start until end } }) }
        SectionTitle("Historie")
        log.take(30).forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${TimeUtil.dateTime(a.createdAt)} · ${a.name} · " + if (a.reps > 0) "${a.reps} Wdh." else "${a.durationSec / 60} Min", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { delete = a }) { Text("✕") }
            }
        }
        GhostButton("Fitness-Challenge im Roulette", Modifier.fillMaxWidth()) { nav("taskRoulette") }
    }
    delete?.let { a -> Confirm("Eintrag löschen", "Diesen Eintrag löschen?", "Löschen", { delete = null }) { vm.run { db.statsDao().delete(a) } } }
}

fun lastDays(n: Int, count: (Long, Long) -> Int): List<Pair<String, Int>> {
    val today = TimeUtil.startOfDay()
    val names = listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")
    return (n - 1 downTo 0).map { i ->
        val start = today - i * TimeUtil.DAY
        val dow = Calendar.getInstance().apply { timeInMillis = start + 3_600_000 }.get(Calendar.DAY_OF_WEEK) - 1
        names[dow] to count(start, start + TimeUtil.DAY)
    }
}

@Composable
fun StatsScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val counters by vm.counters.collectAsState()
    val entries by vm.counterEntries.collectAsState()
    val completions by vm.completions.collectAsState()
    var period by remember { mutableStateOf("WEEK") }
    var edit by remember { mutableStateOf<PersonalCounter?>(null) }
    var newName by remember { mutableStateOf("") }
    val since = when (period) {
        "DAY" -> TimeUtil.startOfDay(); "WEEK" -> now() - 7 * TimeUtil.DAY; "MONTH" -> now() - 30 * TimeUtil.DAY; else -> now() - 365 * TimeUtil.DAY
    }
    Screen("Statistiken", onBack = { nav("back") }) {
        Hint("Privat und verschlüsselt. Alle Zähler sind manuell bearbeitbar.")
        ChoiceChips(listOf("DAY" to "Tag", "WEEK" to "Woche", "MONTH" to "Monat", "YEAR" to "Jahr"), period) { period = it }
        val done = completions.filter { it.status == "DONE" && it.createdAt >= since }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(done.size.toString(), "Aufgaben", Modifier.weight(1f), "✅")
            StatTile(done.sumOf { it.xp }.toString(), "XP", Modifier.weight(1f), "⭐")
            StatTile(entries.filter { it.createdAt >= since }.sumOf { it.delta.coerceAtLeast(0) }.toString(), "Einträge", Modifier.weight(1f), "📈")
        }
        SectionTitle("Aufgaben – letzte 7 Tage")
        GothicCard { BarChart(lastDays(7) { a, b -> completions.count { it.status == "DONE" && it.createdAt in a until b } }) }
        SectionTitle("Nach Kategorie")
        GothicCard {
            val byCat = done.groupingBy { it.categoryKey }.eachCount().toList().sortedByDescending { it.second }
            if (byCat.isEmpty()) Hint("Keine Aufgaben in diesem Zeitraum.")
            byCat.forEach { (k, v) ->
                Text("${categoryName(k)}: $v")
                XpBar(v.toFloat() / (byCat.first().second), "")
            }
        }
        SectionTitle("Zähler")
        counters.forEach { c ->
            val inPeriod = entries.filter { it.counterId == c.id && it.createdAt >= since }.sumOf { it.delta }
            GothicCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text("Gesamt ${c.value} · Zeitraum ${if (inPeriod >= 0) "+" else ""}$inPeriod", style = MaterialTheme.typography.labelSmall, color = LD.Dim)
                    }
                    TextButton(onClick = { vm.run { bumpCounter(c.key, -1) } }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    TextButton(onClick = { vm.run { bumpCounter(c.key, 1) } }) { Text("+", style = MaterialTheme.typography.titleLarge, color = LD.Gold) }
                    TextButton(onClick = { edit = c }) { Text("✎") }
                }
            }
        }
        SectionTitle("Eigener Zähler")
        LdField(newName, "Name") { newName = it.take(40) }
        GhostButton("＋ Zähler anlegen", Modifier.fillMaxWidth(), enabled = newName.isNotBlank()) {
            val n = newName.trim(); newName = ""
            vm.run { db.statsDao().save(PersonalCounter(key = "c_" + now(), name = n, builtIn = false)) }
        }
        SectionTitle("Kalender der Aktivität")
        GothicCard { MonthCalendar((completions.filter { it.status == "DONE" }.map { it.createdAt } + entries.map { it.createdAt }).map { TimeUtil.dayNumber(it) }.toSet()) }
        GhostButton("Tagebuch & private Notizen", Modifier.fillMaxWidth()) { nav("journal") }
    }
    edit?.let { c ->
        var value by remember(c.id) { mutableStateOf(c.value.toString()) }
        var del by remember(c.id) { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(c.name) },
            text = {
                androidx.compose.foundation.layout.Column {
                    LdField(value, "Wert", number = true) { value = it.filter { ch -> ch.isDigit() }.take(6) }
                    if (!c.builtIn) TextButton(onClick = { del = true }) { Text("Zähler löschen") }
                    if (del) Text("Tippe erneut auf Speichern zum Löschen.", color = LD.Gold)
                }
            },
            confirmButton = {
                GoldButton("Speichern") {
                    val v = value.toIntOrNull() ?: c.value
                    vm.run { if (del) db.statsDao().delete(c) else bumpCounter(c.key, v - c.value, "Manuell") }
                    edit = null
                }
            },
            dismissButton = { GhostButton("Abbrechen") { edit = null } }
        )
    }
}
