package studio.cluvex.aether.core

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import studio.cluvex.aether.MainActivity
import studio.cluvex.aether.data.SettingsStore
import studio.cluvex.aether.service.AetherService

/**
 * The one place that turns the tunnel on or off. The app button, the
 * home-screen widget and the quick-settings tile all funnel through here so
 * they can never disagree.
 *
 * Connecting from a widget or tile works without opening the app as long as
 * the system VPN consent has already been granted (the normal case). The
 * first time, or when the platform blocks a background service start, we
 * hop into [MainActivity] just long enough to ask.
 */
object AetherToggle {

    /** True while a tunnel exists or is being brought up/torn down. */
    val isRunning: Boolean
        get() = AetherRuntime.state.value.let {
            it is AetherState.Connected || it is AetherState.Starting || it is AetherState.Stopping
        }

    fun toggle(context: Context) {
        if (isRunning) {
            disconnect(context)
        } else {
            connect(context)
        }
    }

    fun disconnect(context: Context) {
        // The service runs as a foreground service while the tunnel is up,
        // so a plain start() is allowed; guard anyway for edge races.
        runCatching { AetherService.stop(context) }
    }

    fun connect(context: Context) {
        if (SettingsStore.settings.value.vpnMode) {
            val consent = runCatching { VpnService.prepare(context) }.getOrNull()
            if (consent != null) {
                // System needs a user-visible answer — open the app to ask.
                openMain(context, MainActivity.ACTION_CONNECT)
                return
            }
        }
        try {
            AetherService.start(context)
        } catch (error: Exception) {
            // Background start restrictions or process quirks: let the
            // activity do it from the foreground.
            openMain(context, MainActivity.ACTION_CONNECT)
        }
    }

    private fun openMain(context: Context, action: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            setAction(action)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ContextCompat.startActivity(context, intent, null) }
    }
}
