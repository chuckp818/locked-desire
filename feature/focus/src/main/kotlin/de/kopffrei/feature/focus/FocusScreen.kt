package de.kopffrei.feature.focus

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.kopffrei.core.common.focus.FocusSession
import de.kopffrei.core.designsystem.components.CircularCountdown
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.designsystem.components.KfCard
import de.kopffrei.core.designsystem.components.KfChip
import de.kopffrei.core.designsystem.components.KfScreen
import de.kopffrei.core.designsystem.components.KfTextField
import de.kopffrei.core.designsystem.components.SectionTitle
import de.kopffrei.core.reminders.ReminderPermissions

/** Fokus-Timer (leichte Variante). Fokus-Übungen und Atemhilfen sind für Phase 2 vorgesehen. */
@Composable
fun FocusScreen(
    modifier: Modifier = Modifier,
    viewModel: FocusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsAllowed by remember { mutableStateOf(ReminderPermissions.notificationsAllowed(context)) }
    var customText by remember { mutableStateOf("") }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = ReminderPermissions.notificationsAllowed(context)
        viewModel.reload()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = ReminderPermissions.notificationsAllowed(context)
        // Wurde die Anfrage abgelehnt (oder gar nicht mehr gezeigt), führt der Weg über die Systemeinstellungen.
        if (!granted && !notificationsAllowed) {
            context.startActivity(ReminderPermissions.notificationSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // Während der Timer läuft, bleibt der Bildschirm an.
    val view = LocalView.current
    val keepAwake = state.running
    DisposableEffect(keepAwake) {
        view.keepScreenOn = keepAwake
        onDispose { view.keepScreenOn = false }
    }

    KfScreen(title = "Fokus", modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "ring") {
                CircularCountdown(
                    progress = if (state.finished) 0f else state.progress,
                    centerText = if (state.finished) "Geschafft!" else FocusSession.formatClock(state.remainingMs),
                    subText = when {
                        state.finished -> "Die Zeit ist um."
                        state.running -> "Läuft"
                        state.paused -> "Pausiert"
                        else -> "Bereit · ${state.totalMinutes} Min."
                    },
                )
            }

            when {
                state.finished -> item(key = "finished") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KfButton(
                            "Nochmal ${state.totalMinutes} Min.",
                            { viewModel.start() },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Refresh,
                        )
                        KfButton(
                            "Fertig",
                            viewModel::reset,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Check,
                            style = KfButtonStyle.Tonal,
                        )
                    }
                }

                state.running -> item(key = "running") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KfButton("Pause", viewModel::pause, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Pause)
                        KfButton(
                            "Abbrechen",
                            viewModel::reset,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Stop,
                            style = KfButtonStyle.Outlined,
                        )
                    }
                }

                state.paused -> item(key = "paused") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KfButton("Weiter", viewModel::resume, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                        KfButton(
                            "Zurücksetzen",
                            viewModel::reset,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.Stop,
                            style = KfButtonStyle.Outlined,
                        )
                    }
                }

                else -> {
                    item(key = "presets") {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle("Wie lange?")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FocusSession.PRESET_MINUTES.forEach { minutes ->
                                    KfChip(
                                        label = "$minutes Min.",
                                        selected = state.totalMinutes == minutes,
                                        onClick = {
                                            customText = ""
                                            viewModel.selectMinutes(minutes)
                                        },
                                    )
                                }
                            }
                            KfTextField(
                                value = customText,
                                onValueChange = { text ->
                                    val digits = text.filter { it.isDigit() }.take(3)
                                    customText = digits
                                    digits.toIntOrNull()?.takeIf { it in 1..FocusViewModel.MAX_MINUTES }?.let(viewModel::selectMinutes)
                                },
                                label = "Eigene Dauer in Minuten (1–${FocusViewModel.MAX_MINUTES})",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                    item(key = "start") {
                        KfButton(
                            "Start · ${state.totalMinutes} Min.",
                            { viewModel.start() },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.PlayArrow,
                        )
                    }
                }
            }

            if (!notificationsAllowed && (state.running || state.idle)) {
                item(key = "notifications") {
                    KfCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Benachrichtigungen sind aus",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Ohne Benachrichtigungen meldet sich das Ende des Timers nicht, wenn die App geschlossen ist.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            KfButton(
                                "Aktivieren",
                                onClick = {
                                    if (ReminderPermissions.needsNotificationRuntimePermission) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        context.startActivity(
                                            ReminderPermissions.notificationSettingsIntent(context)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                },
                                style = KfButtonStyle.Tonal,
                            )
                        }
                    }
                }
            }

            item(key = "later") {
                Text(
                    "Noch nicht enthalten: Fokus-Übungen und Atemhilfen (Phase 2).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
