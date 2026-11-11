package com.lockeddesire.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lockeddesire.app.ui.AppRoot
import com.lockeddesire.app.ui.AppViewModel
import com.lockeddesire.app.ui.components.LocalVm
import com.lockeddesire.app.ui.theme.LockedDesireTheme

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    val pendingRoute = mutableStateOf<String?>(null)
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inhalte nicht in Screenshots und in der App-Übersicht zeigen
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        pendingRoute.value = intent?.getStringExtra("route")
        setContent {
            val prefs by vm.prefs.collectAsState()
            LockedDesireTheme(accent = prefs.accent, effects = prefs.effectsEnabled) {
                CompositionLocalProvider(LocalVm provides vm) {
                    AppRoot(activity = this)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("route")?.let { pendingRoute.value = it }
    }

    override fun onStop() {
        super.onStop()
        if (vm.externalLaunch) vm.externalLaunch = false
        else if (!isChangingConfigurations && vm.lock.value.pinSet) vm.unlocked.value = false
        vm.stopSpeaking()
    }

    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun biometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun showBiometric(onSuccess: () -> Unit) {
        if (!biometricAvailable()) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Entsperren")
            .setSubtitle("Mit Fingerabdruck oder Gesicht entsperren")
            .setNegativeButtonText("PIN verwenden")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        try { prompt.authenticate(info) } catch (_: Exception) { }
    }
}
