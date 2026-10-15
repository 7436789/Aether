package dev.zeptun

/**
 * Bridge to zeptun (https://github.com/Noisemux/zeptun), the tun2socks
 * engine written in Zig. `libzeptun-jni.so` registers these natives on
 * the class `dev.zeptun.Zeptun` through `JNI_OnLoad`, so the package,
 * the class name and the method signatures must match exactly.
 *
 * The engine is statically linked inside `libzeptun-jni.so`, so no extra
 * library has to be shipped, and all load segments are 16 KB aligned as
 * Android 15 and later require.
 */
object Zeptun {

    /**
     * Indices into the live statistics snapshot, in the order the fields
     * appear in `ZeptunStats` after `version` and `workers`. Counted from
     * the engine's point of view of the TUN device: rx is what the engine
     * reads out of the TUN (traffic leaving the apps), tx is what it
     * writes back into the TUN (traffic arriving for the apps).
     */
    const val STAT_RX_PACKETS = 0
    const val STAT_RX_BYTES = 1 // uploads — device to internet
    const val STAT_TX_PACKETS = 2
    const val STAT_TX_BYTES = 3 // downloads — internet to device
    const val STAT_TCP_ACTIVE = 12
    const val STAT_UDP_ACTIVE = 19

    init {
        System.loadLibrary("zeptun-jni")
    }

    /**
     * Starts the tunnel worker on its own thread; returns immediately.
     *
     * @param service the VpnService instance; its `protect(int)` is called
     *   for every upstream socket the engine opens, which keeps proxy
     *   traffic out of the tunnel
     * @param fd the TUN descriptor from `VpnService.Builder.establish()`
     * @param config a zeptun TOML document; `null` selects the mobile
     *   preset as it stands
     * @return 0 (`ZEPTUN_OK`) or a zeptun error code
     */
    external fun nativeStart(service: Any?, fd: Int, config: String?): Int

    /** Stops the worker, joins its thread and releases the engine. */
    external fun nativeStop()

    /** Engine version string, for diagnostics. */
    external fun nativeVersion(): String

    /** One counter of the live stats snapshot; -1 while the engine is down. */
    external fun nativeCounter(index: Int): Long
}
