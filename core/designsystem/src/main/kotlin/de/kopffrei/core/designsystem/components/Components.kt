package de.kopffrei.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.kopffrei.core.common.model.Mood
import de.kopffrei.core.designsystem.theme.KfItemColors
import de.kopffrei.core.designsystem.theme.KfTheme
import de.kopffrei.core.designsystem.theme.LocalBottomBarVisible

/** Mindestgröße für Schaltflächen (48 dp, bei „größere Schaltflächen“ 64 dp). */
@Composable
fun minTouchSize(): Dp = if (KfTheme.ui.largeButtons) 64.dp else 48.dp

// ------------------------------------------------------------------ Seitengerüst

@Composable
fun KfScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    // Ist unten die Hauptnavigation sichtbar, übernimmt sie den unteren Systemrand.
    val insets = if (LocalBottomBarVisible.current) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
    Scaffold(
        modifier = modifier,
        containerColor = background,
        contentWindowInsets = insets,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        content = content,
    )
}

// ------------------------------------------------------------------ Karten

@Composable
fun KfCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    accent: Color? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val border = if (KfTheme.ui.highContrast) BorderStroke(2.dp, MaterialTheme.colorScheme.outline) else null
    val colors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val body: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (accent != null) {
                Box(
                    Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(accent),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = body)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, content = body)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp),
    )
}

// ------------------------------------------------------------------ Schaltflächen

enum class KfButtonStyle { Primary, Tonal, Outlined, Text }

@Composable
fun KfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: KfButtonStyle = KfButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val height = if (KfTheme.ui.largeButtons) 64.dp else 52.dp
    val shape = RoundedCornerShape(16.dp)
    val sized = modifier.heightIn(min = height)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 2, textAlign = TextAlign.Center)
    }
    when (style) {
        KfButtonStyle.Primary -> Button(onClick, sized, enabled, shape = shape, content = content)
        KfButtonStyle.Tonal -> FilledTonalButton(onClick, sized, enabled, shape = shape, content = content)
        KfButtonStyle.Outlined -> OutlinedButton(onClick, sized, enabled, shape = shape, content = content)
        KfButtonStyle.Text -> TextButton(onClick, sized, enabled, shape = shape, content = content)
    }
}

// ------------------------------------------------------------------ Leere Zustände

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            KfButton(actionText, onAction, style = KfButtonStyle.Tonal)
        }
    }
}

// ------------------------------------------------------------------ Auswahl

@Composable
fun KfChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
    )
}

/** Farbpunkt-Auswahl für Notizen und Termine; Index 0 = keine Farbe. */
@Composable
fun ColorPickerRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ColorDot(color = null, selected = selected == 0, name = "Keine Farbe") { onSelect(0) }
        KfItemColors.palette.forEachIndexed { i, color ->
            ColorDot(color = color, selected = selected == i + 1, name = KfItemColors.names[i]) { onSelect(i + 1) }
        }
    }
}

@Composable
private fun ColorDot(color: Color?, selected: Boolean, name: String, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(minTouchSize())
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(color ?: MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (color != null) Color(0xFF07121A) else ring,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Stimmungsauswahl – immer freiwillig; ein zweiter Tipp auf die gewählte Stimmung entfernt sie. */
@Composable
fun MoodSelector(selected: Mood?, onSelect: (Mood?) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Mood.entries.forEach { mood ->
            FilterChip(
                selected = selected == mood,
                onClick = { onSelect(if (selected == mood) null else mood) },
                label = { Text("${mood.emoji} ${mood.label}") },
            )
        }
    }
}

// ------------------------------------------------------------------ Zeitgefühl

/**
 * Kreisförmiger Countdown. [progress] ist der noch verbleibende Anteil (1 = voll, 0 = abgelaufen).
 * Bei „Animationen reduzieren“ springt der Ring ohne Übergang.
 */
@Composable
fun CircularCountdown(
    progress: Float,
    centerText: String,
    modifier: Modifier = Modifier,
    subText: String? = null,
    diameter: Dp = 240.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (KfTheme.ui.reduceAnimations) snap() else tween(durationMillis = 500, easing = LinearEasing),
        label = "countdown",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val arc = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = style)
            if (animated > 0f) {
                drawArc(arc, -90f, 360f * animated, false, topLeft = Offset(inset, inset), size = arcSize, style = style)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, style = MaterialTheme.typography.displaySmall)
            if (subText != null) {
                Text(
                    subText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Fortschrittsbalken-Text wie „2/5“ als kleine Beschriftung. */
@Composable
fun ProgressLabel(done: Int, total: Int, modifier: Modifier = Modifier) {
    Text(
        text = "$done/$total",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
