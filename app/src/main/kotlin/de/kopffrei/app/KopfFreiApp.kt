package de.kopffrei.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import de.kopffrei.core.common.WidgetUpdater
import de.kopffrei.core.reminders.NotificationHelper
import de.kopffrei.core.reminders.ReminderCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KopfFreiApp : Application() {
    @Inject lateinit var notifications: NotificationHelper

    @Inject lateinit var reminders: ReminderCoordinator

    @Inject lateinit var widgets: WidgetUpdater

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        // Bei jedem Prozessstart alle offenen Erinnerungen neu planen (nach App-Update, Neustart oder
        // wenn das System Alarme verworfen hat) und die Widgets auffrischen.
        appScope.launch {
            try {
                reminders.rescheduleAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Erinnerungen konnten nicht neu geplant werden", e)
            }
            widgets.requestUpdate()
        }
    }

    private companion object {
        const val TAG = "KopfFreiApp"
    }
}
