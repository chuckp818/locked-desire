package de.kopffrei.core.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Prüft und beschreibt die Systemvoraussetzungen für zuverlässige Erinnerungen. Die App fragt
 * Berechtigungen nie heimlich an: Der Nutzer sieht den Status in den Einstellungen und entscheidet.
 */
object ReminderPermissions {

    /** Benachrichtigungen sind erlaubt (ab Android 13 inkl. POST_NOTIFICATIONS). */
    fun notificationsAllowed(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Ab Android 13 muss die Berechtigung zur Laufzeit angefragt werden. */
    val needsNotificationRuntimePermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun exactAlarmsAllowed(context: Context): Boolean = ReminderScheduler.canScheduleExact(context)

    /** Akku-Optimierung ist für die App abgeschaltet (kann Erinnerungen auf manchen Geräten verbessern). */
    fun ignoresBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Einstellungsseite „Wecker & Erinnerungen“ (nur Android 12+, sonst null). */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Liste der Akku-Optimierungen (benötigt keine zusätzliche Berechtigung). */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
