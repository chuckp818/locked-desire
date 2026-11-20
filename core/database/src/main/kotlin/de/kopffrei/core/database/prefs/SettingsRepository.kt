package de.kopffrei.core.database.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.kopffrei.core.common.model.AccentPreset
import de.kopffrei.core.common.model.DashboardCard
import de.kopffrei.core.common.model.QuickAction
import de.kopffrei.core.common.model.ThemeMode
import de.kopffrei.core.common.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "kopffrei_settings")

/** Einstellungen, lokal in DataStore gespeichert. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<UserSettings> = context.settingsStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }

    /** Liest die Einstellungen, ändert sie mit [transform] und speichert das Ergebnis. */
    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.settingsStore.edit { prefs ->
            transform(prefs.toSettings()).writeTo(prefs)
        }
    }

    suspend fun reset() {
        context.settingsStore.edit { it.clear() }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val LARGE_BUTTONS = booleanPreferencesKey("large_buttons")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val DASHBOARD_ORDER = stringPreferencesKey("dashboard_order")
        val DASHBOARD_HIDDEN = stringSetPreferencesKey("dashboard_hidden")
        val DASHBOARD_COMPACT = booleanPreferencesKey("dashboard_compact")
        val SHOW_MOTIVATION = booleanPreferencesKey("show_motivation")
        val QUICK_ACTION = stringPreferencesKey("quick_action")
        val PINNED_SHORTCUTS = stringSetPreferencesKey("pinned_shortcuts")
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: default

    private fun Preferences.toSettings(): UserSettings {
        val defaults = UserSettings()
        val order = this[Keys.DASHBOARD_ORDER]
            ?.split(',')
            ?.mapNotNull { name -> DashboardCard.entries.firstOrNull { it.name == name } }
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.dashboardOrder
        val hidden = this[Keys.DASHBOARD_HIDDEN].orEmpty()
            .mapNotNull { name -> DashboardCard.entries.firstOrNull { it.name == name } }
            .toSet()
        return UserSettings(
            themeMode = this[Keys.THEME_MODE].toEnum(defaults.themeMode),
            accent = this[Keys.ACCENT].toEnum(defaults.accent),
            fontScale = (this[Keys.FONT_SCALE] ?: defaults.fontScale).coerceIn(0.8f, 1.8f),
            largeButtons = this[Keys.LARGE_BUTTONS] ?: defaults.largeButtons,
            reduceAnimations = this[Keys.REDUCE_ANIMATIONS] ?: defaults.reduceAnimations,
            biometricLock = this[Keys.BIOMETRIC_LOCK] ?: defaults.biometricLock,
            snoozeMinutes = this[Keys.SNOOZE_MINUTES] ?: defaults.snoozeMinutes,
            dashboardOrder = order,
            dashboardHidden = hidden,
            dashboardCompact = this[Keys.DASHBOARD_COMPACT] ?: defaults.dashboardCompact,
            showMotivation = this[Keys.SHOW_MOTIVATION] ?: defaults.showMotivation,
            quickAction = this[Keys.QUICK_ACTION].toEnum(defaults.quickAction),
            pinnedShortcuts = this[Keys.PINNED_SHORTCUTS].orEmpty(),
        )
    }

    private fun UserSettings.writeTo(prefs: MutablePreferences) {
        prefs[Keys.THEME_MODE] = themeMode.name
        prefs[Keys.ACCENT] = accent.name
        prefs[Keys.FONT_SCALE] = fontScale
        prefs[Keys.LARGE_BUTTONS] = largeButtons
        prefs[Keys.REDUCE_ANIMATIONS] = reduceAnimations
        prefs[Keys.BIOMETRIC_LOCK] = biometricLock
        prefs[Keys.SNOOZE_MINUTES] = snoozeMinutes
        prefs[Keys.DASHBOARD_ORDER] = dashboardOrder.joinToString(",") { it.name }
        prefs[Keys.DASHBOARD_HIDDEN] = dashboardHidden.map { it.name }.toSet()
        prefs[Keys.DASHBOARD_COMPACT] = dashboardCompact
        prefs[Keys.SHOW_MOTIVATION] = showMotivation
        prefs[Keys.QUICK_ACTION] = quickAction.name
        prefs[Keys.PINNED_SHORTCUTS] = pinnedShortcuts
    }
}
