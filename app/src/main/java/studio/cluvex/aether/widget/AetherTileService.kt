package studio.cluvex.aether.widget

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import studio.cluvex.aether.R
import studio.cluvex.aether.core.AetherRuntime
import studio.cluvex.aether.core.AetherState
import studio.cluvex.aether.core.AetherToggle

/**
 * Quick-settings tile: one tap on the panel toggles the tunnel, no app
 * involved. The tile mirrors the live state (idle / scanning / connected)
 * while the panel is listening, exactly like the Pixel VPN tiles.
 */
class AetherTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: kotlinx.coroutines.Job? = null

    override fun onStartListening() {
        super.onStartListening()
        render(AetherRuntime.state.value)
        watcher = scope.launch {
            AetherRuntime.state.collect { render(it) }
        }
    }

    override fun onStopListening() {
        watcher?.cancel()
        watcher = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        AetherToggle.toggle(this)
        // Feedback right away; the watcher refines it as the state moves.
        render(AetherRuntime.state.value)
    }

    private fun render(state: AetherState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_power)
        val (tileState, subtitle) = when (state) {
            is AetherState.Idle -> Tile.STATE_INACTIVE to getString(R.string.state_idle)
            is AetherState.Starting -> when (state.phase) {
                AetherState.Starting.Phase.PREPARING -> Tile.STATE_ACTIVE to getString(R.string.state_preparing)
                AetherState.Starting.Phase.SCANNING -> Tile.STATE_ACTIVE to getString(R.string.state_scanning)
                AetherState.Starting.Phase.CONNECTING -> Tile.STATE_ACTIVE to getString(R.string.state_connecting)
            }
            is AetherState.Connected -> Tile.STATE_ACTIVE to getString(
                if (state.vpnActive) R.string.state_connected_tun else R.string.notif_connected_socks,
                formatUptime(state.since),
            )
            AetherState.Stopping -> Tile.STATE_INACTIVE to getString(R.string.state_stopping)
            is AetherState.Failed -> Tile.STATE_UNAVAILABLE to getString(R.string.state_failed)
        }
        tile.state = tileState
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }

    private fun formatUptime(since: java.time.Instant): String {
        val seconds = java.time.Duration.between(since, java.time.Instant.now()).seconds
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
