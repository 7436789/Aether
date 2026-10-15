package studio.cluvex.aether.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import hev.htproxy.TProxyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.cluvex.aether.MainActivity
import studio.cluvex.aether.R
import studio.cluvex.aether.core.AetherController
import studio.cluvex.aether.core.AetherRuntime
import studio.cluvex.aether.core.AetherState
import studio.cluvex.aether.data.SettingsStore
import java.time.Duration
import java.time.Instant

/**
 * Foreground service that owns the Aether core and, when the user asked
 * for it, raises the TUN front end on top of the local SOCKS5 proxy.
 */
class AetherService : LifecycleService() {

    companion object {
        private const val TAG = "AetherService"
        private const val CHANNEL_ID = "aether_status"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "studio.cluvex.aether.START"
        const val ACTION_STOP = "studio.cluvex.aether.STOP"

        fun start(context: Context) {
            context.startService(Intent(context, AetherService::class.java).apply { action = ACTION_START })
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AetherService::class.java).apply { action = ACTION_STOP })
        }
    }

    private var controller: AetherController? = null
    private var loops: MutableList<Job> = mutableListOf()

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
            }
            else -> {
                if (controller == null) {
                    startForegroundInternal()
                    startCore()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startCore() {
        val settings = SettingsStore.settings.value
        val engine = AetherController(this)
        controller = engine

        startForegroundInternal()

        lifecycleScope.launch(Dispatchers.Default) {
            try {
                engine.start(settings)
            } catch (error: Exception) {
                Log.e(TAG, "the core refused to start", error)
                AetherRuntime.setState(AetherState.Failed(error.message ?: "unknown start error"))
                engine.stop()
                shutdown()
                return@launch
            }

            // -- native stderr pump -----------------------------------------
            loops += launch(Dispatchers.IO) {
                while (isActive && controller != null) {
                    val n = engine.drainStderr()
                    if (n <= 0) {
                        delay(120)
                    }
                }
            }

            // -- core job watcher -------------------------------------------
            loops += launch(Dispatchers.Default) {
                while (isActive && controller != null) {
                    val alive = engine.pollJob()
                    if (!alive) {
                        stopTun()
                        break
                    }
                    delay(500)
                }
            }

            // -- proxy liveness + TUN bring-up --------------------------------
            loops += launch(Dispatchers.Default) {
                var connectedAt: Instant? = null
                var vpnStarted = false
                var vpnUp = false
                while (isActive && controller != null) {
                    val current = SettingsStore.settings.value
                    val alive = engine.probeSocks(current)
                    engine.socksAlive = alive

                    val connectedSince = connectedAt
                    if (alive) {
                        if (connectedSince == null) {
                            connectedAt = Instant.now()
                            AetherRuntime.setState(AetherState.Starting(AetherState.Starting.Phase.CONNECTING))
                        }
                        if (!vpnStarted && current.vpnMode) {
                            vpnStarted = true
                            vpnUp = startTun(current.socksPort, current.ipMode.key != "v4")
                            if (!vpnUp) {
                                AetherRuntime.appendLog(":: tun front end unavailable, SOCKS5 only ::")
                            }
                        }
                        AetherRuntime.setState(
                            AetherState.Connected(
                                since = connectedAt ?: Instant.now(),
                                socksAlive = true,
                                vpnActive = vpnUp,
                            ),
                        )
                    } else {
                        connectedAt?.let {
                            // Was connected, proxy went away: the tunnel is healing
                            // itself (the core reconnects on its own); show it.
                            AetherRuntime.setState(
                                AetherState.Connected(since = it, socksAlive = false, vpnActive = vpnUp),
                            )
                        }
                        vpnUp = vpnUp && vpnStarted
                    }

                    updateNotification(alive, vpnUp, connectedAt)
                    delay(1500)
                }
            }

            // -- tun throughput reporter ---------------------------------------
            loops += launch(Dispatchers.Default) {
                while (isActive && controller != null) {
                    if (TunHolder.isRunning()) {
                        val stats = runCatching { TunHolder.stats() }.getOrNull()
                        if (stats != null && stats.size == 4) {
                            // [tx_packets, tx_bytes, rx_packets, rx_bytes]
                            AetherRuntime.reportThroughput(rx = stats[3], tx = stats[1])
                        }
                    }
                    delay(2000)
                }
            }
        }
    }

    private fun shutdown() {
        val engine = controller
        controller = null
        loops.forEach { it.cancel() }
        loops.clear()
        Thread {
            engine?.stop()
            stopTun()
        }.start()
        lifecycleScope.launch {
            withContext(NonCancellable) {
                delay(300)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // -- TUN front end -----------------------------------------------------

    private fun startTun(socksPort: Int, ipv6: Boolean): Boolean = try {
        TunnelVpnService.start(this, socksPort, ipv6)
        true
    } catch (error: Exception) {
        Log.e(TAG, "could not start the tun front end", error)
        false
    }

    private fun stopTun() {
        TunnelVpnService.stop(this)
    }

    // -- notification ------------------------------------------------------

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startForegroundInternal() {
        val notification = buildNotification(getString(R.string.notif_starting))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(alive: Boolean, vpn: Boolean, since: Instant?) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val text = when {
            alive && vpn -> getString(R.string.notif_connected_tun, formatDuration(since))
            alive -> getString(R.string.notif_connected_socks)
            else -> getString(R.string.notif_starting)
        }
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AetherService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_aether)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun formatDuration(since: Instant?): String {
        since ?: return ""
        val seconds = Duration.between(since, Instant.now()).seconds
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}

/** Single tun worker; the hev library is process-global anyway. */
internal object TunHolder {
    private var service: TProxyService? = null

    @Synchronized
    fun ensure(): TProxyService = service ?: TProxyService().also { service = it }

    @Synchronized
    fun isRunning(): Boolean = runCatching { ensure().TProxyIsRunning() }.getOrDefault(false)

    @Synchronized
    fun stats(): LongArray = ensure().TProxyGetStats()
}
