package de.kopffrei.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Setzt und löscht Systemalarme. Bevorzugt werden exakte Alarme (auch im Ruhezustand/Doze). Fehlt die
 * Berechtigung dafür, wird ein ungenauer Alarm gesetzt – die Erinnerung kommt dann etwas später,
 * geht aber nicht verloren.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean = canScheduleExact(context)

    fun schedule(reminderId: String, triggerAtMillis: Long) {
        val pending = pendingIntent(reminderId, create = true) ?: return
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (_: SecurityException) {
            // Berechtigung wurde gerade entzogen: ungenau planen statt abzustürzen.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    fun cancel(reminderId: String) {
        val pending = pendingIntent(reminderId, create = false) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntent(reminderId: String, create: Boolean): PendingIntent? {
        val intent = ReminderIntents.forReceiver(
            context, ReminderReceiver::class.java, ReminderIntents.ACTION_FIRE, reminderId,
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        fun canScheduleExact(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }
}
