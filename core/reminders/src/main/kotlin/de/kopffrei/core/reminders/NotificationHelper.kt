package de.kopffrei.core.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.kopffrei.core.common.DeepLinks
import javax.inject.Inject
import javax.inject.Singleton

/** Erstellt Benachrichtigungskanal und Erinnerungs-Benachrichtigungen. */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Erinnerungen",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Erinnerungen an Termine, Aufgaben und Notizen"
                enableVibration(true)
                setShowBadge(true)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun show(
        reminderId: String,
        title: String,
        text: String,
        deepLink: String,
        showDoneAction: Boolean,
        snoozeMinutes: Int,
    ) {
        ensureChannels()
        val id = notificationId(reminderId)

        // Antippen öffnet nur die App. Die Erinnerung gilt dadurch NICHT als erledigt.
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Auf dem Sperrbildschirm erscheint nur ein neutraler Text (Privatsphäre).
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_kopffrei)
            .setContentTitle("KopfFrei")
            .setContentText("Du hast eine Erinnerung.")
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_kopffrei)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(false)
            .setContentIntent(open)

        if (showDoneAction) {
            builder.addAction(0, "Erledigt", actionIntent(reminderId, ReminderIntents.ACTION_DONE, id))
        }
        builder.addAction(0, "Gesehen", actionIntent(reminderId, ReminderIntents.ACTION_ACK, id))
        builder.addAction(0, "Später ($snoozeMinutes Min.)", actionIntent(reminderId, ReminderIntents.ACTION_SNOOZE, id))

        notify(id, builder.build())
    }

    /** Testbenachrichtigung für die Einstellungen (ohne Aktionen). */
    fun showTest() {
        ensureChannels()
        val open = PendingIntent.getActivity(
            context,
            TEST_ID,
            Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.TODAY))
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_kopffrei)
            .setContentTitle("Test-Erinnerung")
            .setContentText("So sieht eine Erinnerung von KopfFrei aus.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notify(TEST_ID, notification)
    }

    fun cancel(reminderId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    private fun actionIntent(reminderId: String, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            ReminderIntents.forReceiver(context, ReminderActionReceiver::class.java, action, reminderId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notify(id: Int, notification: android.app.Notification) {
        val manager = NotificationManagerCompat.from(context)
        // Ohne Erlaubnis (Android 13+) wird nichts angezeigt; die Erinnerung bleibt trotzdem in der App sichtbar.
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Erlaubnis wurde zwischen Prüfung und Senden entzogen.
        }
    }

    private fun notificationId(reminderId: String): Int = reminderId.hashCode()

    companion object {
        const val CHANNEL_REMINDERS = "kopffrei_reminders"
        private const val TEST_ID = 1
    }
}
