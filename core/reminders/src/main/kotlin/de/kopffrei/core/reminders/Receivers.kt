package de.kopffrei.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "KopfFreiReminders"

/**
 * Zugang zum [ReminderCoordinator] für die Empfänger. Bewusst ohne @AndroidEntryPoint: Bei
 * BroadcastReceivern scheitert dort der Aufruf von super.onReceive in Kotlin ("Abstract member cannot
 * be accessed directly"), weil Hilt die Oberklasse erst nachträglich im Bytecode austauscht.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun coordinator(): ReminderCoordinator
}

private fun Context.reminderCoordinator(): ReminderCoordinator =
    EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java).coordinator()

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
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val coordinator = context.reminderCoordinator()
        val id = intent.getStringExtra(ReminderIntents.EXTRA_REMINDER_ID) ?: return
        runAsync { coordinator.onAlarm(id) }
    }
}

/** Empfängt die Knöpfe der Benachrichtigung: Gesehen, Erledigt, Später. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val coordinator = context.reminderCoordinator()
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
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val coordinator = context.reminderCoordinator()
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
