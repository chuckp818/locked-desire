package de.kopffrei.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "KopfFreiReminders"

/**
 * Führt Datenbank-Arbeit außerhalb des Hauptthreads aus, ohne dass das System den Empfänger vorzeitig
 * beendet (goAsync).
 */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Erinnerung konnte nicht verarbeitet werden", e)
        } finally {
            pending.finish()
        }
    }
}

/** Empfängt den Alarm zum Zeitpunkt einer Erinnerung. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: ReminderCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getStringExtra(ReminderIntents.EXTRA_REMINDER_ID) ?: return
        runAsync { coordinator.onAlarm(id) }
    }
}

/** Empfängt die Knöpfe der Benachrichtigung: Gesehen, Erledigt, Später. */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: ReminderCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getStringExtra(ReminderIntents.EXTRA_REMINDER_ID) ?: return
        val action = intent.action
        runAsync {
            when (action) {
                ReminderIntents.ACTION_ACK -> coordinator.acknowledge(id)
                ReminderIntents.ACTION_DONE -> coordinator.complete(id)
                ReminderIntents.ACTION_SNOOZE -> coordinator.snooze(id)
            }
        }
    }
}

/** Plant Erinnerungen neu: nach Neustart, App-Update sowie Zeit-/Zeitzonen-/Berechtigungsänderung. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: ReminderCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in HANDLED_ACTIONS) return
        runAsync { coordinator.rescheduleAll() }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
