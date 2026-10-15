package hev.htproxy

/**
 * Bridge to hev-socks5-tunnel (tun2socks), whose JNI surface is compiled
 * with the default package `hev.htproxy` and class name `TProxyService`.
 *
 * The shared library registers these natives through `JNI_OnLoad`, so the
 * package, the class name and the method signatures must match exactly.
 */
class TProxyService {

    companion object {
        init {
            System.loadLibrary("hev-socks5-tunnel")
        }
    }

    /**
     * Starts the tunnel worker.
     *
     * @param configPath absolute path of the YAML configuration file
     * @param fd the TUN file descriptor handed over by VpnService.Builder
     */
    external fun TProxyStartService(configPath: String, fd: Int): Boolean

    /** Stops the tunnel worker and releases the TUN interface. */
    external fun TProxyStopService(): Boolean

    /** Whether the tunnel worker is currently running. */
    external fun TProxyIsRunning(): Boolean

    /** Live counters: rx bytes, tx bytes, active session count. */
    external fun TProxyGetStats(): LongArray
}
