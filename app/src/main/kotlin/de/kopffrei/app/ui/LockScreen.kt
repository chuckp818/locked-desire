package de.kopffrei.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import de.kopffrei.core.designsystem.components.KfButton
import de.kopffrei.core.designsystem.components.KfButtonStyle
import de.kopffrei.core.security.BiometricAuth
import de.kopffrei.core.security.LockAvailability

/**
 * Sperrbildschirm der optionalen App-Sperre. Er zeigt keinerlei Inhalte der App.
 * Die Prüfung übernimmt das Betriebssystem (Fingerabdruck/Gesicht/Geräte-PIN); die App speichert nichts.
 */
@Composable
fun LockScreen(
    activity: FragmentActivity,
    onUnlocked: () -> Unit,
    onDisableLock: () -> Unit,
) {
    var recheck by rememberSaveable { mutableIntStateOf(0) }
    val availability = remember(recheck) { BiometricAuth.availability(activity) }
    var error by remember { mutableStateOf<String?>(null) }
    // Die automatische Abfrage nur einmal starten (nicht bei jeder Drehung erneut).
    var autoPrompted by rememberSaveable { mutableStateOf(false) }
    val currentOnUnlocked by rememberUpdatedState(onUnlocked)

    val prompt: () -> Unit = {
        error = null
        BiometricAuth.authenticate(
            activity = activity,
            title = "KopfFrei entsperren",
            subtitle = "Deine Einträge bleiben privat.",
            onSuccess = { currentOnUnlocked() },
            onFailure = { message -> error = message },
        )
    }

    LaunchedEffect(availability) {
        if (availability == LockAvailability.AVAILABLE && !autoPrompted) {
            autoPrompted = true
            prompt()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("KopfFrei ist gesperrt", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))

            if (availability == LockAvailability.AVAILABLE) {
                Text(
                    "Entsperre mit Fingerabdruck, Gesicht oder deiner Geräte-PIN.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                val message = error
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
                KfButton("Entsperren", onClick = prompt)
            } else if (availability == LockAvailability.UNAVAILABLE) {
                // Vermutlich nur vorübergehend: nicht entsperren, sondern erneut prüfen lassen.
                Text(
                    BiometricAuth.availabilityText(availability),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                KfButton("Erneut versuchen", onClick = { recheck++ })
            } else {
                // Notausgang: Ohne eingerichtete Geräte-Sperre (keine PIN/Biometrie) könnte die Abfrage nie
                // gelingen – und das Gerät selbst ist dann ohnehin ungeschützt.
                Text(
                    BiometricAuth.availabilityText(availability),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ohne Gerätesperre kann die App-Sperre nicht prüfen. Du kannst sie ausschalten und später in den Einstellungen neu einrichten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                KfButton("App-Sperre ausschalten und öffnen", onClick = onDisableLock, style = KfButtonStyle.Tonal)
            }
        }
    }
}
