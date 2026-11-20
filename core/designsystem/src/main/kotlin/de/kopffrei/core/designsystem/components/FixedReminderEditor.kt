package de.kopffrei.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.kopffrei.core.common.reminder.ReminderDraft
import de.kopffrei.core.common.time.TimeUtils
import java.time.LocalTime

private const val DEFAULT_REPEAT_MINUTES = 10
private const val DEFAULT_REMINDER_MINUTES_OF_DAY = 9 * 60

/**
 * Einzelne Erinnerung zu einem festen Zeitpunkt (Datum + Uhrzeit), z. B. für Notizen.
 * [draft] = null bedeutet „keine Erinnerung“.
 */
@Composable
fun FixedReminderEditor(
    draft: ReminderDraft?,
    onChange: (ReminderDraft?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { TimeUtils.systemZone() }
    val fixedAt = draft?.fixedAt
    val dateStart = fixedAt?.let { TimeUtils.startOfDay(it, zone) }
    val minutes = fixedAt?.let { TimeUtils.minutesOfDay(it, zone) }

    fun combine(dayStart: Long, minutesOfDay: Int): Long {
        val date = TimeUtils.toLocalDate(dayStart, zone)
        return TimeUtils.millisOf(date, LocalTime.of(minutesOfDay / 60, minutesOfDay % 60), zone)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Erinnerung")
        DatePickerField(
            label = "Datum",
            dateMillis = dateStart,
            onChange = { picked ->
                if (picked == null) {
                    onChange(null)
                } else {
                    val base = draft ?: ReminderDraft()
                    onChange(
                        base.copy(
                            fixedAt = combine(picked, minutes ?: DEFAULT_REMINDER_MINUTES_OF_DAY),
                            offsetMinutes = null,
                        ),
                    )
                }
            },
        )
        if (draft != null && dateStart != null) {
            TimePickerField(
                label = "Uhrzeit",
                minutesOfDay = minutes,
                onChange = { picked ->
                    onChange(draft.copy(fixedAt = combine(dateStart, picked ?: DEFAULT_REMINDER_MINUTES_OF_DAY)))
                },
            )
            KfSwitchRow(
                label = "Erneut erinnern, bis ich bestätige",
                checked = draft.repeatEveryMinutes != null,
                onCheckedChange = { checked ->
                    onChange(draft.copy(repeatEveryMinutes = if (checked) DEFAULT_REPEAT_MINUTES else null))
                },
                description = "Alle $DEFAULT_REPEAT_MINUTES Minuten, höchstens 5-mal.",
            )
        }
    }
}
