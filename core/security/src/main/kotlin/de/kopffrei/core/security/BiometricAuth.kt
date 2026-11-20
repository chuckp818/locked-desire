package de.kopffrei.core.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Ergebnis der Prüfung, ob eine App-Sperre auf diesem Gerät möglich ist. */
enum class LockAvailability {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE,
}

/**
 * Optionale App-Sperre per Fingerabdruck/Gesicht bzw. Geräte-PIN/-Muster (nur über das Betriebssystem;
 * die App speichert keine Geheimnisse). Die Sperre schützt die Oberfläche der App – die Datenbank
 * selbst ist nicht verschlüsselt (siehe README).
 */
object BiometricAuth {

    /**
     * Ab Android 11 (API 30) darf BIOMETRIC_WEAK mit der Geräte-PIN kombiniert werden; darunter
     * geht das nicht, dort ist nur Biometrie möglich.
     */
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        } else {
            BIOMETRIC_WEAK
        }

    fun availability(context: Context): LockAvailability =
        when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> LockAvailability.NO_HARDWARE
            else -> LockAvailability.UNAVAILABLE
        }

    fun availabilityText(availability: LockAvailability): String = when (availability) {
        LockAvailability.AVAILABLE -> "Verfügbar"
        LockAvailability.NOT_ENROLLED -> "Auf diesem Gerät ist keine Sperre (Fingerabdruck, Gesicht oder PIN) eingerichtet."
        LockAvailability.NO_HARDWARE -> "Dieses Gerät unterstützt keine biometrische Sperre."
        LockAvailability.UNAVAILABLE -> "Die Sperre ist gerade nicht verfügbar."
    }

    /**
     * Zeigt die Systemabfrage. [onSuccess] wird nur bei erfolgreicher Prüfung aufgerufen,
     * [onFailure] bei Abbruch oder Fehler (mit lesbarem Text).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure(errString.toString())
            }
            // onAuthenticationFailed (falscher Finger) braucht keine Behandlung: die Abfrage bleibt offen.
        }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(authenticators)
        if (subtitle != null) builder.setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Ohne DEVICE_CREDENTIAL verlangt BiometricPrompt einen Abbrechen-Knopf.
            builder.setNegativeButtonText("Abbrechen")
        }
        BiometricPrompt(activity, executor, callback).authenticate(builder.build())
    }
}
