package de.kopffrei.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.kopffrei.core.common.reminder.ReminderDraft
import de.kopffrei.core.common.text.TextUtils

private val OFFSET_CHOICES = listOf(0, 5, 10, 15, 30, 45, 60, 90, 120, 180, 1440, 2880)
private const val DEFAULT_REPEAT_MINUTES = 10

/**
 * Bearbeitung mehrstufiger Erinnerungen, z. B. beim Arzttermin: 90 Min. vorher „Unterlagen
 * vorbereiten“, 60 Min. vorher „Fertigmachen“, 30 Min. vorher „Losgehen“.
 *
 * @param canUseOffsets false, solange es keinen Bezugszeitpunkt (Datum) gibt.
 */
@Composable
fun ReminderStagesEditor(
    stages: List<ReminderDraft>,
    onChange: (List<ReminderDraft>) -> Unit,
    canUseOffsets: Boolean,
    modifier: Modifier = Modifier,
    onGenerateFromTimes: (() -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Erinnerungen")
        if (!canUseOffsets) {
            Text(
                "Lege zuerst ein Datum fest, dann kannst du Erinnerungen in Stufen planen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        stages.forEachIndexed { index, stage ->
            StageCard(
                stage = stage,
                onChange = { updated -> onChange(stages.toMutableList().also { it[index] = updated }) },
                onDelete = { onChange(stages.filterIndexed { i, _ -> i != index }) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            KfButton(
                text = "Erinnerung hinzufügen",
                onClick = { onChange(stages + ReminderDraft(offsetMinutes = 30)) },
                icon = Icons.Rounded.Add,
                style = KfButtonStyle.Tonal,
            )
            if (onGenerateFromTimes != null) {
                KfButton("Aus Zeiten", onGenerateFromTimes, style = KfButtonStyle.Text)
            }
        }
    }
}

@Composable
private fun StageCard(
    stage: ReminderDraft,
    onChange: (ReminderDraft) -> Unit,
    onDelete: () -> Unit,
) {
    KfCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        KfTextField(
            value = stage.label,
            onValueChange = { onChange(stage.copy(label = it)) },
            label = "Wofür? (z. B. Unterlagen vorbereiten)",
            singleLine = true,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OffsetMenu(
                minutes = stage.offsetMinutes ?: 0,
                onSelect = { onChange(stage.copy(offsetMinutes = it)) },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Erinnerung entfernen")
            }
        }
        val repeating = stage.repeatEveryMinutes != null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minTouchSize())
                .toggleable(
                    value = repeating,
                    role = Role.Checkbox,
                    onValueChange = { checked ->
                        onChange(stage.copy(repeatEveryMinutes = if (checked) DEFAULT_REPEAT_MINUTES else null))
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = repeating, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Erneut erinnern, bis ich bestätige (alle $DEFAULT_REPEAT_MINUTES Min.)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun OffsetMenu(minutes: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val choices = remember(minutes) { (OFFSET_CHOICES + minutes).distinct().sorted() }
    androidx.compose.foundation.layout.Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = minTouchSize()),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(TextUtils.formatOffset(minutes))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(TextUtils.formatOffset(choice)) },
                    onClick = {
                        onSelect(choice)
                        expanded = false
                    },
                )
            }
        }
    }
}
