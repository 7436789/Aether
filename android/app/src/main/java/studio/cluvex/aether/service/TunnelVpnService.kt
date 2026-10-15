package studio.cluvex.aether.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File

/**
 * Raises a TUN interface through [VpnService] and hands its descriptor to
 * hev-socks5-tunnel, which turns device traffic into SOCKS5 sessions on
 * the local Aether proxy.
 *
 * The app itself is excluded from the tunnel (`addDisallowedApplication`)
 * so the core's own sockets — and the Go transport processes it spawns —
 * always dial the internet directly and never loop back through the TUN.
 */
class TunnelVpnService : VpnService() {

    companion object {
        private const val TAG = "TunnelVpnService"
        private const val EXTRA_PORT = "socks_port"
        private const val EXTRA_IPV6 = "ipv6"

        private const val ADDRESS_V4 = "198.18.0.1"
        private const val ADDRESS_V6 = "fc00::1"
        private const val DNS_V4 = "1.1.1.1"
        private const val DNS_V6 = "2606:4700:4700::1111"
        private const val MTU = 8500

        fun start(context: Context, socksPort: Int, ipv6: Boolean) {
            context.startService(
                Intent(context, TunnelVpnService::class.java)
                    .putExtra(EXTRA_PORT, socksPort)
                    .putExtra(EXTRA_IPV6, ipv6),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TunnelVpnService::class.java))
        }
    }

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val port = intent?.getIntExtra(EXTRA_PORT, 0) ?: 0
        if (port == 0) {
            // stop path
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }
        val ipv6 = intent?.getBooleanExtra(EXTRA_IPV6, false) ?: false

        teardown()

        val builder = Builder()
            .setSession("Aether")
            .setMtu(MTU)
            .setBlocking(true)
            .addAddress(ADDRESS_V4, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(DNS_V4)
        if (ipv6) {
            builder.addAddress(ADDRESS_V6, 128)
            builder.addRoute("::", 0)
            builder.addDnsServer(DNS_V6)
        }
        try {
            builder.addDisallowedApplication(packageName)
        } catch (error: Exception) {
            Log.w(TAG, "could not exclude this app from the tunnel", error)
        }

        val descriptor = try {
            builder.establish()
        } catch (error: Exception) {
            Log.e(TAG, "the system refused the VPN", error)
            stopSelf()
            return START_NOT_STICKY
        }
        if (descriptor == null) {
            Log.e(TAG, "the system revoked the VPN permission")
            stopSelf()
            return START_NOT_STICKY
        }
        tun = descriptor

        val config = File(filesDir, "hev-tunnel.yml")
        config.writeText(
            """
            tunnel:
              name: tun0
              mtu: $MTU
              ipv4: $ADDRESS_V4
            ${if (ipv6) "  ipv6: '$ADDRESS_V6'" else ""}
            socks5:
              address: 127.0.0.1
              port: $port
              udp: 'udp'
            misc:
              log-level: warn
            """.trimIndent().replace("\n\n", "\n"),
        )

        val ok = runCatching {
            TunHolder.ensure().TProxyStartService(config.absolutePath, descriptor.fd)
        }.getOrDefault(false)

        if (!ok) {
            Log.e(TAG, "hev-socks5-tunnel refused to start")
            studio.cluvex.aether.core.AetherRuntime.appendLog(":: tun2socks failed to start ::")
            teardown()
            stopSelf()
            return START_NOT_STICKY
        }
        Log.i(TAG, "tun front end up on fd ${descriptor.fd}")
        return START_STICKY
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        runCatching { TunHolder.ensure().TProxyStopService() }
        runCatching { tun?.close() }
        tun = null
    }
}
