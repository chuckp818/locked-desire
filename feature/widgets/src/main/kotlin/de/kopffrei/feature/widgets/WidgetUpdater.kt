package de.kopffrei.feature.widgets

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.kopffrei.core.common.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aktualisiert alle Widgets nach Schreibzugriffen. Mehrere Änderungen kurz hintereinander lösen
 * nur ein Update aus (Entprellung), damit der Akku nicht belastet wird.
 */
@Singleton
class GlanceWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) : WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var pending: Job? = null

    override fun requestUpdate() {
        synchronized(lock) {
            pending?.cancel()
            pending = scope.launch {
                delay(DEBOUNCE_MS)
                updateAllWidgets()
            }
        }
    }

    private suspend fun updateAllWidgets() {
        val widgets: List<GlanceAppWidget> = listOf(
            QuickNoteWidget(),
            VoiceInputWidget(),
            TodayEventsWidget(),
            TaskListWidget(),
            PinnedNoteWidget(),
            FocusTimerWidget(),
            DayOverviewWidget(),
            ThoughtParkingWidget(),
        )
        for (widget in widgets) {
            try {
                widget.updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Ein defektes Widget darf weder die App noch die anderen Widgets stören.
                Log.w(TAG, "Widget konnte nicht aktualisiert werden: ${widget::class.java.simpleName}", e)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val TAG = "KopfFreiWidgets"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetsModule {
    @Binds
    @Singleton
    abstract fun bindWidgetUpdater(impl: GlanceWidgetUpdater): WidgetUpdater
}
