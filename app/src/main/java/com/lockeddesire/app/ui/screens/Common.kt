package com.lockeddesire.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.lockeddesire.app.data.Seed
import kotlinx.coroutines.delay

/** Aktuelle Zeit als State, jede Sekunde aktualisiert (für laufende Timer). */
@Composable
fun rememberNow(intervalMs: Long = 1000): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) { delay(intervalMs); value = System.currentTimeMillis() }
}

fun categoryName(key: String): String = Seed.CATEGORIES.firstOrNull { it.key == key }?.let { it.emoji + " " + it.name } ?: key

fun statusText(status: String): String = when (status) {
    "DONE" -> "Erledigt"; "SKIPPED" -> "Übersprungen"; "DECLINED" -> "Abgelehnt"; "ABORTED" -> "Abgebrochen"
    "PENDING" -> "Geplant"; "OPEN" -> "Offen"; "ACCEPTED" -> "Angenommen"; "EXPIRED" -> "Verfallen"
    else -> status
}

val AVATARS = listOf("🔒", "🗝", "⛓", "🖤", "🦇", "🌹", "👑", "😈", "🐺", "🦊", "🐱", "🕯", "💀", "🔥", "🌙", "🎭")

// ---------- Gemeinsame Bausteine für Roulette- und Dungeon-Screens ----------

@Composable
fun ProposalFlow(
    p: com.lockeddesire.app.engine.Proposal,
    comment: String,
    source: String,
    reroll: () -> Unit,
    rerollText: String = "Neu würfeln"
) {
    val vm = com.lockeddesire.app.ui.components.LocalVm.current
    var stage by androidx.compose.runtime.remember(p) { androidx.compose.runtime.mutableStateOf("OFFER") }
    com.lockeddesire.app.ui.components.TaskCard(p, comment, categoryName(p.categoryKey)) {
        when (stage) {
            "OFFER" -> {
                com.lockeddesire.app.ui.components.GoldButton("Annehmen", androidx.compose.ui.Modifier.fillMaxWidth()) { stage = "ACTIVE" }
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    com.lockeddesire.app.ui.components.GhostButton("Überspringen", androidx.compose.ui.Modifier.weight(1f)) {
                        vm.run { recordTask(p.title, p.categoryKey, 0, "SKIPPED", source, p.taskId) }
                        stage = "SKIPPED"
                    }
                    com.lockeddesire.app.ui.components.GhostButton(rerollText, androidx.compose.ui.Modifier.weight(1f), onClick = reroll)
                }
                com.lockeddesire.app.ui.components.Hint("Jede Aufgabe ist freiwillig. Überspringen kostet nichts.")
            }
            "ACTIVE" -> {
                if (p.durationMin > 0) com.lockeddesire.app.ui.components.CountdownTimer(p.durationMin * 60)
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    com.lockeddesire.app.ui.components.GoldButton("Erledigt", androidx.compose.ui.Modifier.weight(1f)) {
                        vm.run {
                            recordTask(p.title, p.categoryKey, p.xp, "DONE", source, p.taskId)
                            vm.speak(rules.comment(aiContext(), "done"))
                        }
                        stage = "DONE"
                    }
                    com.lockeddesire.app.ui.components.GhostButton("Abbrechen", androidx.compose.ui.Modifier.weight(1f)) {
                        vm.run { recordTask(p.title, p.categoryKey, 0, "ABORTED", source, p.taskId) }
                        stage = "ABORTED"
                    }
                }
                com.lockeddesire.app.ui.components.Hint("Bei Schmerzen oder Unwohlsein sofort aufhören. Abbrechen wird nie bestraft.")
            }
            else -> {
                androidx.compose.material3.Text(
                    when (stage) { "DONE" -> "✔ Erledigt · +${p.xp} XP"; "SKIPPED" -> "Übersprungen"; else -> "Abgebrochen – völlig in Ordnung." },
                    color = com.lockeddesire.app.ui.theme.LD.Gold
                )
                com.lockeddesire.app.ui.components.GoldButton("Nächste Runde", androidx.compose.ui.Modifier.fillMaxWidth(), onClick = reroll)
            }
        }
    }
}

/** Monatskalender mit markierten Tagen. */
@Composable
fun MonthCalendar(marked: Set<Long>) {
    var offset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MONTH, offset)
        set(java.util.Calendar.HOUR_OF_DAY, 12); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val title = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.GERMANY).format(cal.time)
    val days = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val lead = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
    val today = com.lockeddesire.app.data.TimeUtil.dayNumber()
    val firstNoon = cal.timeInMillis
    val accent = com.lockeddesire.app.ui.theme.LocalAccent.current
    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = { offset-- }) { androidx.compose.material3.Text("‹", fontSize = 22.sp) }
            androidx.compose.material3.Text(title, androidx.compose.ui.Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = com.lockeddesire.app.ui.theme.LD.Gold)
            androidx.compose.material3.TextButton(onClick = { offset++ }) { androidx.compose.material3.Text("›", fontSize = 22.sp) }
        }
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth()) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach {
                androidx.compose.material3.Text(it, androidx.compose.ui.Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = com.lockeddesire.app.ui.theme.LD.Dim, fontSize = 12.sp)
            }
        }
        val cells = (0 until lead).map { 0 } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val dayNo = if (day == 0) -1L else com.lockeddesire.app.data.TimeUtil.dayNumber(firstNoon + (day - 1) * com.lockeddesire.app.data.TimeUtil.DAY)
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.weight(1f).padding(2.dp).height(34.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .background(if (dayNo in marked) accent.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent)
                            .border(1.dp, if (dayNo == today) com.lockeddesire.app.ui.theme.LD.Gold else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { if (day != 0) androidx.compose.material3.Text(day.toString(), fontSize = 13.sp) }
                }
                repeat(7 - week.size) { androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f)) }
            }
        }
    }
}
