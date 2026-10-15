package studio.cluvex.aether

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import studio.cluvex.aether.service.AetherService
import studio.cluvex.aether.ui.AetherRoot
import studio.cluvex.aether.ui.theme.AetherTheme

class MainActivity : AppCompatActivity() {

    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                AetherService.start(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore the in-app language picked in Settings (fa / en / system).
        val stored = getSharedPreferences("aether_ui", MODE_PRIVATE).getString("language", null)
        if (stored != null) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(stored),
            )
        }

        setContent {
            AetherTheme {
                AetherRoot(
                    onConnect = { connectWithVpnConsent() },
                    onDisconnect = { AetherService.stop(this) },
                )
            }
        }
    }

    /**
     * Connecting may need the system VPN dialog first; after consent the
     * foreground service brings up the core and the tun front end.
     */
    private fun connectWithVpnConsent() {
        val wantsVpn = studio.cluvex.aether.data.SettingsStore.settings.value.vpnMode
        if (wantsVpn) {
            val consent = VpnService.prepare(this)
            if (consent != null) {
                vpnConsent.launch(consent)
                return
            }
        }
        AetherService.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
