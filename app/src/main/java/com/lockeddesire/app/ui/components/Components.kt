package com.lockeddesire.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lockeddesire.app.data.UserPreferences
import com.lockeddesire.app.engine.Proposal
import com.lockeddesire.app.security.Crypto
import com.lockeddesire.app.ui.AppViewModel
import com.lockeddesire.app.ui.theme.LD
import com.lockeddesire.app.ui.theme.LocalAccent
import com.lockeddesire.app.ui.theme.LocalEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

val LocalVm = staticCompositionLocalOf<AppViewModel> { error("Kein ViewModel") }
val LocalNav = staticCompositionLocalOf<(String) -> Unit> { {} }

object Fx {
    fun feedback(context: Context, prefs: UserPreferences, strong: Boolean = false) {
        if (prefs.vibrationEnabled) try {
            val v = context.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(if (strong) 180L else 60L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) { }
        if (prefs.soundEnabled) try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
            tg.startTone(if (strong) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP, 150)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ try { tg.release() } catch (_: Exception) { } }, 400)
        } catch (_: Exception) { }
    }
}

@Composable
fun GothicBackground(hardcore: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val effects = LocalEffects.current
    val accent = LocalAccent.current
    Box(
        Modifier.fillMaxSize().background(
            if (hardcore) Brush.verticalGradient(listOf(Color.Black, LD.Black, LD.DarkRed.copy(alpha = 0.35f), Color.Black))
            else Brush.verticalGradient(listOf(LD.Black, LD.Violet.copy(alpha = 0.55f), LD.Black, LD.Bordeaux.copy(alpha = 0.45f)))
        )
    ) {
        if (effects) {
            val t by rememberInfiniteTransition(label = "smoke").animateFloat(
                0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "t"
            )
            Canvas(Modifier.fillMaxSize()) {
                for (i in 0 until 14) {
                    val phase = (t + i / 14f) % 1f
                    val x = size.width * ((i * 0.37f + 0.1f) % 1f) + sin((phase * 6.28f) + i) * 22f
                    val y = size.height * (1f - phase)
                    val r = 2f + (i % 4) * 1.6f
                    drawCircle(if (i % 3 == 0) LD.Gold.copy(alpha = 0.22f * (1f - phase)) else accent.copy(alpha = 0.28f * (1f - phase)), r, Offset(x, y))
                }
                for (i in 0 until 3) {
                    val phase = (t * (0.6f + i * 0.2f) + i * 0.33f) % 1f
                    drawCircle(
                        Brush.radialGradient(listOf(LD.Grey.copy(alpha = 0.035f), Color.Transparent)),
                        radius = size.width * 0.55f,
                        center = Offset(size.width * (0.2f + 0.3f * i) + cos(phase * 6.28f) * 60f, size.height * (0.9f - phase * 0.8f))
                    )
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides LD.Grey) { content() }
    }
}

@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
    safeword: Boolean = true,
    hardcore: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val vm = LocalVm.current
    var ask by remember { mutableStateOf(false) }
    GothicBackground(hardcore) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = LD.Grey,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, color = LD.Gold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (onBack != null) TextButton(onClick = onBack) { Text("‹", fontSize = 30.sp, color = LD.Gold) }
                    },
                    actions = {
                        actions()
                        if (safeword) TextButton(onClick = { ask = true }) { Text("🛑", fontSize = 22.sp) }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LD.Black.copy(alpha = 0.85f))
                )
            }
        ) { pad ->
            val base = Modifier.fillMaxSize().padding(pad).imePadding()
            CompositionLocalProvider(LocalContentColor provides LD.Grey) {
                Column(
                    (if (scroll) base.verticalScroll(rememberScrollState()) else base).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )
            }
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Safeword") },
        text = { Text("Alles sofort stoppen? Der Dungeon wird beendet, offene Aufgaben verfallen und die Keuschheitsuhr pausiert. Es gibt keine Strafe und keinen Punktabzug.") },
        confirmButton = { Button(onClick = { ask = false; vm.run { safewordStop() } }) { Text("Alles stoppen") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("Zurück") } }
    )
}

@Composable
fun GothicCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(14.dp)
    val border = Brush.linearGradient(
        if (highlight) listOf(accent, LD.Gold, accent) else listOf(LD.Gold.copy(alpha = 0.75f), LD.Gold.copy(alpha = 0.15f), LD.Gold.copy(alpha = 0.6f))
    )
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(LD.LeatherLight, LD.Leather, LD.Black.copy(alpha = 0.9f))))
            .border(BorderStroke(1.2.dp, border), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { CompositionLocalProvider(LocalContentColor provides LD.Grey) { content() } }
}

@Composable
fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("⛓", fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.titleMedium, color = LD.Gold)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(LD.Gold.copy(alpha = 0.7f), Color.Transparent))))
    }
}

@Composable
fun ChainDivider() {
    Text("⛓ ── 🗝 ── ⛓", color = LD.Gold.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), fontSize = 14.sp)
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LocalAccent.current, contentColor = Color.White),
        border = BorderStroke(1.dp, LD.Gold.copy(alpha = if (enabled) 0.9f else 0.2f))
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, LD.Gold.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LD.Grey)
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
fun XpBar(progress: Float, label: String, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(if (LocalEffects.current) 700 else 0), label = "xp")
    Column(modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { animated }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = LocalAccent.current, trackColor = LD.Black
        )
        if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelSmall, color = LD.Dim, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, sub: String = "", enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (enabled) Modifier.clickable { onChange(!checked) } else Modifier), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = LD.Dim)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun LabeledSlider(label: String, value: Int, range: IntRange, valueText: String = value.toString(), onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, color = LD.Gold, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(), onValueChange = { onChange(Math.round(it).coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = if (range.last - range.first > 12) 0 else (range.last - range.first - 1).coerceAtLeast(0)
        )
    }
}

@Composable
fun LdField(value: String, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun Chips(options: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = key in selected, onClick = { onToggle(key) }, label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAccent.current.copy(alpha = 0.55f), selectedLabelColor = Color.White)
            )
        }
    }
}

@Composable
fun ChoiceChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) =
    Chips(options, setOf(selected)) { onSelect(it) }

@Composable
fun Confirm(title: String, text: String, confirm: String = "Ja", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { Button(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LD.Dim)
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, emoji: String = "") {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(LD.Black.copy(alpha = 0.6f))
            .border(1.dp, LD.Gold.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (emoji.isNotBlank()) Text(emoji, fontSize = 20.sp)
        Text(value, style = MaterialTheme.typography.titleLarge, color = LocalAccent.current, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LD.Dim, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Animiertes Roulette-Rad. Bleibt mit dem Zielsegment unter dem Zeiger stehen. */
@Composable
fun RouletteWheel(labels: List<String>, spinKey: Int, target: Int, modifier: Modifier = Modifier, onFinished: () -> Unit) {
    val effects = LocalEffects.current
    val accent = LocalAccent.current
    val rotation = remember { Animatable(0f) }
    val n = labels.size.coerceAtLeast(1)
    LaunchedEffect(spinKey) {
        if (spinKey == 0) return@LaunchedEffect
        val sweep = 360f / n
        val desired = ((-(target + 0.5f) * sweep) % 360f + 360f) % 360f
        val current = ((rotation.value % 360f) + 360f) % 360f
        val end = rotation.value - current + 360f * 5 + desired
        if (effects) rotation.animateTo(end, tween(3400, easing = FastOutSlowInEasing)) else rotation.snapTo(end)
        onFinished()
    }
    val paint = remember {
        android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true }
    }
    Box(modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val sweep = 360f / n
            val arc = (2f * Math.PI.toFloat() * r * 0.62f) / n
            paint.textSize = minOf(r * 0.085f, arc * 0.5f).coerceAtLeast(14f)
            val maxWidth = r * 0.66f
            for (i in 0 until n) {
                val start = -90f + rotation.value + i * sweep
                val color = when (i % 3) { 0 -> LD.DarkRed; 1 -> LD.Bordeaux; else -> LD.Violet }
                drawArc(color, start, sweep, true, Offset(center.x - r, center.y - r), Size(r * 2, r * 2))
                drawArc(LD.Gold.copy(alpha = 0.8f), start, sweep, true, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(2f))
                var label = labels.getOrElse(i) { "" }
                if (paint.measureText(label) > maxWidth) {
                    while (label.length > 2 && paint.measureText("$label…") > maxWidth) label = label.dropLast(1)
                    label = label.trimEnd() + "…"
                }
                val c = drawContext.canvas.nativeCanvas
                c.save()
                c.rotate(start + sweep / 2f, center.x, center.y)
                c.drawText(label, center.x + r * 0.58f, center.y + paint.textSize / 3f, paint)
                c.restore()
            }
            drawCircle(LD.Gold, r, center, style = Stroke(6f))
            drawCircle(accent.copy(alpha = 0.5f), r + 6f, center, style = Stroke(3f))
            drawCircle(LD.Black, r * 0.18f, center)
            drawCircle(LD.Gold, r * 0.18f, center, style = Stroke(4f))
            val p = Path().apply {
                moveTo(center.x, center.y - r + r * 0.16f)
                lineTo(center.x - r * 0.07f, center.y - r - 6f)
                lineTo(center.x + r * 0.07f, center.y - r - 6f)
                close()
            }
            drawPath(p, LD.Gold)
            drawPath(p, LD.Black, style = Stroke(2f))
        }
        Text("🔒", fontSize = 22.sp)
    }
}

/**
 * Roulette mit Rad, Dreh-Knopf und Ergebnisbereich.
 * spin liefert die Radbeschriftungen, den Zielindex und das Ergebnis – oder null, wenn nichts passt.
 */
@Composable
fun <T> RoulettePanel(
    idleLabels: List<String>,
    spinText: String = "Roulette drehen",
    emptyText: String,
    spin: suspend () -> Triple<List<String>, Int, T>?,
    result: @Composable (T, () -> Unit) -> Unit
) {
    val vm = LocalVm.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var labels by remember { mutableStateOf(idleLabels) }
    var target by remember { mutableIntStateOf(0) }
    var spinKey by remember { mutableIntStateOf(0) }
    var spinning by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<T?>(null) }
    var shown by remember { mutableStateOf<T?>(null) }
    var empty by remember { mutableStateOf(false) }
    LaunchedEffect(idleLabels) { if (!spinning && shown == null) labels = idleLabels }

    val doSpin: () -> Unit = {
        if (!spinning) scope.launch {
            val r = try { spin() } catch (e: Exception) { null }
            if (r == null) { empty = true; shown = null } else {
                empty = false; shown = null; pending = r.third
                labels = r.first; target = r.second; spinning = true; spinKey++
            }
        }
    }
    RouletteWheel(labels.ifEmpty { listOf("?") }, spinKey, target) {
        spinning = false; shown = pending
        Fx.feedback(context, vm.prefs.value, strong = true)
    }
    if (shown == null) GoldButton(if (spinning) "Das Rad dreht sich …" else spinText, Modifier.fillMaxWidth(), enabled = !spinning, onClick = doSpin)
    if (empty) GothicCard { Text(emptyText) }
    shown?.let { result(it, doSpin) }
}

/** Baut bis zu 12 Radsegmente, die das gewählte Label sicher enthalten. */
fun wheelFor(all: List<String>, chosen: String): Pair<List<String>, Int> {
    val others = all.filter { it != chosen }.distinct().shuffled().take(11)
    val list = (others + chosen).shuffled()
    val padded = if (list.size < 4) (list + list + list + list).take(maxOf(4, list.size * 2)) else list
    return padded to padded.indexOf(chosen)
}

@Composable
fun CountdownTimer(totalSeconds: Int, onFinished: () -> Unit = {}) {
    val vm = LocalVm.current
    val context = LocalContext.current
    var left by remember(totalSeconds) { mutableIntStateOf(totalSeconds) }
    var running by remember(totalSeconds) { mutableStateOf(false) }
    LaunchedEffect(running, totalSeconds) {
        while (running && left > 0) { delay(1000); left-- }
        if (running && left <= 0) { running = false; Fx.feedback(context, vm.prefs.value, strong = true); onFinished() }
    }
    val progress = if (totalSeconds > 0) 1f - left.toFloat() / totalSeconds else 0f
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("%02d:%02d:%02d".format(left / 3600, (left % 3600) / 60, left % 60), style = MaterialTheme.typography.displaySmall, color = if (left <= 10 && running) LD.Neon else LD.Grey)
        XpBar(progress, "")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton(if (running) "Pause" else if (left == totalSeconds) "Timer starten" else "Weiter", Modifier.weight(1f), enabled = left > 0) { running = !running }
            GhostButton("Zurücksetzen", Modifier.weight(1f)) { running = false; left = totalSeconds }
        }
    }
}

@Composable
fun TaskCard(p: Proposal, comment: String = "", categoryName: String = p.categoryKey, extra: @Composable ColumnScope.() -> Unit = {}) {
    GothicCard(highlight = true) {
        Text(p.title, style = MaterialTheme.typography.headlineSmall, color = LD.Gold)
        Text(p.description, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(categoryName)
            Badge("Stufe ${p.difficulty}")
            if (p.durationMin > 0) Badge("${p.durationMin} Min")
            Badge("+${p.xp} XP")
            if (p.partnerRequired) Badge("Partner")
        }
        if (comment.isNotBlank()) Text("„$comment“", style = MaterialTheme.typography.bodyMedium, color = LocalAccent.current, fontWeight = FontWeight.SemiBold)
        extra()
    }
}

@Composable
fun Badge(text: String, color: Color = LD.Gold) {
    Text(
        text, style = MaterialTheme.typography.labelSmall, color = color,
        modifier = Modifier.padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun AvatarBubble(emoji: String, size: Int = 56) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brush.radialGradient(listOf(LD.DarkRed, LD.Black)))
            .border(2.dp, LD.Gold, CircleShape), contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = (size * 0.5f).sp) }
}

/** Zeigt ein verschlüsselt gespeichertes Foto. */
@Composable
fun VaultImage(name: String, modifier: Modifier = Modifier, placeholder: String = "📷") {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            try {
                Crypto.loadPhoto(context, name)?.let { bytes ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    var sample = 1
                    while (opts.outWidth / sample > 1400 || opts.outHeight / sample > 1400) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(LD.Black).border(1.dp, LD.Gold.copy(alpha = 0.4f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(placeholder, fontSize = 34.sp)
    }
}

@Composable
fun BarChart(values: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val max = (values.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (v > 0) Text(v.toString(), style = MaterialTheme.typography.labelSmall, color = LD.Gold, maxLines = 1)
                Box(
                    Modifier.fillMaxWidth().height((110f * v / max).coerceAtLeast(2f).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(Brush.verticalGradient(listOf(accent, LD.DarkRed)))
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = LD.Dim, maxLines = 1, fontSize = 10.sp)
            }
        }
    }
}
