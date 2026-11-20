package de.kopffrei.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.model.AccentPreset
import de.kopffrei.core.common.model.QuickAction
import de.kopffrei.core.common.model.ThemeMode
import de.kopffrei.core.designsystem.components.ConfirmDialog
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfChip
import de.kopffrei.core.designsystem.components.KfDropdownField
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfSwitchRow
import de.kopffrei.core.designsystem.components.SectionTitle
import de.kopffrei.core.reminders.ReminderPermissions
import de.kopffrei.core.security.BiometricAuth
import de.kopffrei.core.security.LockAvailability
import kotlin.math.roundToInt

private val SNOOZE_OPTIONS = listOf(5, 10, 15, 30, 60)

/** Einstellungen: Darstellung, Erinnerungen und Berechtigungen, Datenschutz, Daten. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var notificationsAllowed by remember { mutableStateOf(ReminderPermissions.notificationsAllowed(context)) }
    var exactAllowed by remember { mutableStateOf(ReminderPermissions.exactAlarmsAllowed(context)) }
    var batteryIgnored by remember { mutableStateOf(ReminderPermissions.ignoresBatteryOptimizations(context)) }
    var confirmExport by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fontDraft by remember(settings.fontScale) { mutableFloatStateOf(settings.fontScale) }

    // Status nach der Rückkehr aus den Systemeinstellungen neu lesen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = ReminderPermissions.notificationsAllowed(context)
        exactAllowed = ReminderPermissions.exactAlarmsAllowed(context)
        batteryIgnored = ReminderPermissions.ignoresBatteryOptimizations(context)
    }
    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = ReminderPermissions.notificationsAllowed(context)
        if (!granted && !notificationsAllowed) {
            startSettingsIntent(context, ReminderPermissions.notificationSettingsIntent(context), viewModel::showMessage)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportTo(uri) }

    KfScreen(
        title = "Einstellungen",
        modifier = modifier,
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------------------------------------------------- Darstellung
            item(key = "look") {
                KfCard {
                    SectionTitle("Darstellung")
                    Text("Modus", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            KfChip(mode.label, selected = settings.themeMode == mode, onClick = {
                                viewModel.update { it.copy(themeMode = mode) }
                            })
                        }
                    }
                    Text("Akzentfarbe", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AccentPreset.entries.forEach { accent ->
                            KfChip(accent.label, selected = settings.accent == accent, onClick = {
                                viewModel.update { it.copy(accent = accent) }
                            })
                        }
                    }
                    Text(
                        "Schriftgröße: ${(fontDraft * 100).roundToInt()} %",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Slider(
                        value = fontDraft,
                        onValueChange = { fontDraft = it },
                        onValueChangeFinished = { viewModel.update { it.copy(fontScale = fontDraft) } },
                        valueRange = 0.85f..1.5f,
                        steps = 12,
                    )
                    KfSwitchRow(
                        label = "Größere Schaltflächen",
                        checked = settings.largeButtons,
                        onCheckedChange = { value -> viewModel.update { it.copy(largeButtons = value) } },
                    )
                    KfSwitchRow(
                        label = "Animationen reduzieren",
                        checked = settings.reduceAnimations,
                        onCheckedChange = { value -> viewModel.update { it.copy(reduceAnimations = value) } },
                    )
                }
            }

            // ---------------------------------------------------------- Startseite
            item(key = "home") {
                KfCard {
                    SectionTitle("Startseite und Schnellaktion")
                    KfDropdownField(
                        label = "Dauerhafte Schnellaktion",
                        selected = settings.quickAction,
                        options = QuickAction.entries.toList(),
                        optionLabel = { it.label },
                        onSelect = { value -> viewModel.update { it.copy(quickAction = value) } },
                    )
                    KfSwitchRow(
                        label = "Kompakte Karten",
                        checked = settings.dashboardCompact,
                        onCheckedChange = { value -> viewModel.update { it.copy(dashboardCompact = value) } },
                    )
                    KfSwitchRow(
                        label = "Ermutigende Sätze zeigen",
                        checked = settings.showMotivation,
                        onCheckedChange = { value -> viewModel.update { it.copy(showMotivation = value) } },
                        description = "Reihenfolge und Sichtbarkeit der Karten änderst du direkt auf der Startseite.",
                    )
                }
            }

            // ---------------------------------------------------------- Erinnerungen
            item(key = "reminders") {
                KfCard {
                    SectionTitle("Erinnerungen")
                    KfDropdownField(
                        label = "„Später“ erinnert nach",
                        selected = settings.snoozeMinutes,
                        options = (SNOOZE_OPTIONS + settings.snoozeMinutes).distinct().sorted(),
                        optionLabel = { "$it Minuten" },
                        onSelect = { value -> viewModel.update { it.copy(snoozeMinutes = value) } },
                    )
                    PermissionRow(
                        title = "Benachrichtigungen",
                        ok = notificationsAllowed,
                        okText = "Erlaubt",
                        missingText = "Nicht erlaubt – Erinnerungen können nicht angezeigt werden.",
                        actionText = "Erlauben",
                        onAction = {
                            if (ReminderPermissions.needsNotificationRuntimePermission) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                startSettingsIntent(
                                    context,
                                    ReminderPermissions.notificationSettingsIntent(context),
                                    viewModel::showMessage,
                                )
                            }
                        },
                    )
                    val exactIntent = ReminderPermissions.exactAlarmSettingsIntent(context)
                    PermissionRow(
                        title = "Genaue Alarme",
                        ok = exactAllowed,
                        okText = "Erlaubt",
                        missingText = "Nicht erlaubt – Erinnerungen können sich um einige Minuten verspäten.",
                        actionText = "Einstellungen öffnen",
                        onAction = exactIntent?.let { intent -> { startSettingsIntent(context, intent, viewModel::showMessage) } },
                    )
                    PermissionRow(
                        title = "Akku-Optimierung",
                        ok = batteryIgnored,
                        okText = "Für KopfFrei ausgenommen",
                        missingText = "Aktiv – auf manchen Geräten verzögert das Erinnerungen.",
                        actionText = "Einstellungen öffnen",
                        onAction = {
                            startSettingsIntent(context, ReminderPermissions.batterySettingsIntent(), viewModel::showMessage)
                        },
                    )
                    KfButton(
                        "Testbenachrichtigung senden",
                        onClick = {
                            if (notificationsAllowed) {
                                viewModel.sendTestNotification()
                            } else {
                                viewModel.showMessage("Benachrichtigungen sind nicht erlaubt.")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = KfButtonStyle.Tonal,
                    )
                    Text(
                        "Erinnerungen bleiben offen, bis du sie mit „Gesehen“ oder „Erledigt“ bestätigst – " +
                            "Öffnen oder Wegwischen reicht nicht.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------------------------------------------------- Datenschutz
            item(key = "privacy") {
                val availability = remember { BiometricAuth.availability(context) }
                KfCard {
                    SectionTitle("Datenschutz")
                    KfSwitchRow(
                        label = "App-Sperre",
                        checked = settings.biometricLock,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                viewModel.update { it.copy(biometricLock = false) }
                            } else if (availability != LockAvailability.AVAILABLE) {
                                viewModel.showMessage(BiometricAuth.availabilityText(availability))
                            } else {
                                val activity = context.findFragmentActivity()
                                if (activity == null) {
                                    viewModel.showMessage("Die Sperre kann hier nicht eingerichtet werden.")
                                } else {
                                    // Erst einmal bestätigen, damit man sich nicht selbst aussperrt.
                                    BiometricAuth.authenticate(
                                        activity = activity,
                                        title = "App-Sperre aktivieren",
                                        subtitle = "Bestätige einmal, damit die Sperre sicher funktioniert.",
                                        onSuccess = { viewModel.update { it.copy(biometricLock = true) } },
                                        onFailure = { text -> viewModel.showMessage(text) },
                                    )
                                }
                            }
                        },
                        description = if (availability == LockAvailability.AVAILABLE) {
                            "Fingerabdruck, Gesicht oder Geräte-PIN beim Öffnen der App."
                        } else {
                            BiometricAuth.availabilityText(availability)
                        },
                    )
                    Text(
                        "Alles bleibt auf diesem Gerät: keine Registrierung, kein Internet, keine Werbung, keine " +
                            "Auswertung. Die Sperre schützt die Oberfläche der App; die Datenbank selbst ist " +
                            "nicht verschlüsselt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------------------------------------------------- Daten
            item(key = "data") {
                KfCard {
                    SectionTitle("Meine Daten")
                    KfButton(
                        "Backup exportieren (JSON)",
                        onClick = { confirmExport = true },
                        modifier = Modifier.fillMaxWidth(),
                        style = KfButtonStyle.Tonal,
                    )
                    Text(
                        "Der Export enthält alle Texte, aber nicht die Anhangsdateien. Ein Import ist noch nicht enthalten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    KfButton(
                        "Alle Daten löschen",
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        style = KfButtonStyle.Outlined,
                    )
                }
            }

            item(key = "about") {
                Text(
                    "KopfFrei – Phase 1 (Grundlage). Routinen, KI-Hilfe, Community, Statistiken und " +
                        "Partner-Funktionen sind noch nicht enthalten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmExport) {
        ConfirmDialog(
            title = "Backup exportieren?",
            message = "Die Datei enthält alle deine Notizen, Gedanken, Tagebucheinträge, Aufgaben und Termine " +
                "im Klartext – sie ist nicht verschlüsselt. Speichere sie nur an einem Ort, dem du vertraust.",
            confirmText = "Exportieren",
            onConfirm = {
                confirmExport = false
                exportLauncher.launch("kopffrei-backup.json")
            },
            onDismiss = { confirmExport = false },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Alle Daten löschen?",
            message = "Alle Notizen, Gedanken, Tagebucheinträge, Aufgaben, Termine, Erinnerungen, Anhänge und " +
                "Einstellungen werden endgültig von diesem Gerät gelöscht. Das lässt sich nicht rückgängig machen.",
            confirmText = "Alles löschen",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteEverything()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun PermissionRow(
    title: String,
    ok: Boolean,
    okText: String,
    missingText: String,
    actionText: String,
    onAction: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            if (ok) okText else missingText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        )
        if (!ok && onAction != null) {
            KfButton(actionText, onAction, style = KfButtonStyle.Text)
        }
    }
}

private fun startSettingsIntent(context: Context, intent: Intent, onError: (String) -> Unit) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        onError("Diese Einstellungsseite gibt es auf diesem Gerät nicht.")
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
