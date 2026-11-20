package de.kopffrei.core.reminders

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Aktionen und Extras der Alarm- und Benachrichtigungs-Intents. */
internal object ReminderIntents {
    const val ACTION_FIRE = "de.kopffrei.action.REMINDER_FIRE"
    const val ACTION_ACK = "de.kopffrei.action.REMINDER_ACK"
    const val ACTION_DONE = "de.kopffrei.action.REMINDER_DONE"
    const val ACTION_SNOOZE = "de.kopffrei.action.REMINDER_SNOOZE"
    const val EXTRA_REMINDER_ID = "reminder_id"

    /**
     * Die URI macht jeden Intent einzigartig (PendingIntents werden über Aktion, Daten und Ziel
     * unterschieden, nicht über Extras).
     */
    fun forReceiver(context: Context, receiver: Class<*>, action: String, reminderId: String): Intent =
        Intent(context, receiver)
            .setAction(action)
            .setData(Uri.parse("kopffrei-reminder://$reminderId/${action.substringAfterLast('.')}"))
            .putExtra(EXTRA_REMINDER_ID, reminderId)
}
