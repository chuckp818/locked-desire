package de.kopffrei.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kopffrei.core.common.model.Frequency
import de.kopffrei.core.common.model.RecurrenceRule
import de.kopffrei.core.common.text.RecurrenceText
import de.kopffrei.core.common.time.TimeUtils

private val INTERVALS = (1..12).toList()

/** Bearbeitung einer einfachen Wiederholungsregel (täglich … jährlich, Intervall, Enddatum). */
@Composable
fun RecurrenceEditor(
    rule: RecurrenceRule,
    onChange: (RecurrenceRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { TimeUtils.systemZone() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KfDropdownField(
            label = "Wiederholung",
            selected = rule.frequency,
            options = Frequency.entries.toList(),
            optionLabel = { RecurrenceText.frequencyLabel(it) },
            onSelect = { frequency ->
                onChange(
                    if (frequency == Frequency.NONE) {
                        RecurrenceRule.NONE
                    } else {
                        rule.copy(frequency = frequency)
                    },
                )
            },
        )
        if (rule.isRecurring) {
            KfDropdownField(
                label = "Rhythmus",
                selected = rule.interval,
                options = INTERVALS,
                optionLabel = { RecurrenceText.describe(RecurrenceRule(rule.frequency, it)) },
                onSelect = { onChange(rule.copy(interval = it)) },
            )
            DatePickerField(
                label = "Bis",
                dateMillis = rule.until?.let { TimeUtils.startOfDay(it, zone) },
                onChange = { picked ->
                    // „Bis“ gilt einschließlich des gewählten Tages.
                    val until = picked?.let {
                        TimeUtils.startOfDay(TimeUtils.toLocalDate(it, zone).plusDays(1), zone) - 1
                    }
                    onChange(rule.copy(until = until))
                },
            )
        }
    }
}
