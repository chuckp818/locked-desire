package de.kopffrei.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.kopffrei.app.navigation.KopfFreiNavHost
import de.kopffrei.app.ui.LockScreen
import de.kopffrei.core.common.security.AppLockPolicy
import de.kopffrei.core.designsystem.theme.KopfFreiTheme

/**
 * Einzige Activity. FragmentActivity, weil BiometricPrompt sie braucht. launchMode=singleTop:
 * Widgets und Benachrichtigungen kommen über onNewIntent als Deep Link an.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Zuletzt per onNewIntent empfangener Deep Link, bis die Navigation ihn abgeholt hat. */
    private var pendingDeepLink by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
            val current = settings

            // Mit aktiver Sperre darf die Aufgabenübersicht (zuletzt benutzte Apps) keinen Inhalt zeigen.
            LaunchedEffect(current?.biometricLock) {
                if (current?.biometricLock == true) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Solange die Einstellungen nicht gelesen sind, bleibt nur der Fensterhintergrund sichtbar.
            if (current != null) {
                KopfFreiTheme(current) {
                    if (AppLockPolicy.needsUnlock(current.biometricLock, unlocked)) {
                        LockScreen(
                            activity = this@MainActivity,
                            onUnlocked = viewModel::onUnlocked,
                            onDisableLock = viewModel::disableLockAndOpen,
                        )
                    } else {
                        KopfFreiNavHost(
                            settings = current,
                            pendingDeepLink = pendingDeepLink,
                            onDeepLinkHandled = { pendingDeepLink = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent: Ist die Navigation (z. B. wegen der Sperre) noch nicht aufgebaut, liest sie den Link
        // beim Start aus dem Intent der Activity.
        setIntent(intent)
        pendingDeepLink = intent
    }

    override fun onStart() {
        super.onStart()
        viewModel.onActivityStarted()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onActivityStopped(isChangingConfigurations)
    }
}
