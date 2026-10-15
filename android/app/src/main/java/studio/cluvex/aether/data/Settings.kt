package studio.cluvex.aether.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tunnel transport handed to the core as its protocol flag. */
enum class Protocol(val flag: String, val key: String) {
    MASQUE("--masque", "masque"),
    WIREGUARD("--wg", "wg"),
    GOOL("--gool", "gool"),
    GOOL_CLASSIC("--gool-classic", "gool_classic"),
    MIM("--mim", "mim"),
    ;

    companion object {
        fun fromKey(key: String?): Protocol =
            entries.firstOrNull { it.key == key } ?: MASQUE
    }
}

/** Endpoint discovery strategy (`--scan`). */
enum class ScanMode(val value: String, val key: String) {
    TURBO("turbo", "turbo"),
    BALANCED("balanced", "balanced"),
    THOROUGH("thorough", "thorough"),
    VERIFIED("verified", "verified"),
    IRONCLAD("ironclad", "ironclad"),
    ;

    companion object {
        fun fromKey(key: String?): ScanMode =
            entries.firstOrNull { it.key == key } ?: BALANCED
    }
}

/** Traffic obfuscation profile (`--noize`). */
enum class Noize(val value: String, val key: String) {
    OFF("off", "off"),
    LIGHT("light", "light"),
    FIREWALL("firewall", "firewall"),
    BALANCED("balanced", "balanced"),
    GFW("gfw", "gfw"),
    AGGRESSIVE("aggressive", "aggressive"),
    ;

    companion object {
        fun fromKey(key: String?): Noize =
            entries.firstOrNull { it.key == key } ?: FIREWALL
    }
}

/** IP family preference. */
enum class IpMode(val flag: String, val key: String) {
    V4("-4", "v4"),
    V6("-6", "v6"),
    DUAL("--dual", "dual"),
    ;

    companion object {
        fun fromKey(key: String?): IpMode =
            entries.firstOrNull { it.key == key } ?: V4
    }
}

/**
 * Everything the UI can tune before a connect. Persisted as plain
 * key/value pairs in [android.content.SharedPreferences]; deliberately
 * boring on purpose — the core itself lives in `aether.toml`.
 */
data class AetherSettings(
    val protocol: Protocol = Protocol.MASQUE,
    val scanMode: ScanMode = ScanMode.BALANCED,
    val noize: Noize = Noize.FIREWALL,
    val ipMode: IpMode = IpMode.V4,
    /** Route the whole device through a TUN front end (hev-socks5-tunnel). */
    val vpnMode: Boolean = true,
    /** Local SOCKS5 listen port of the core. */
    val socksPort: Int = 1819,
    /** Optional fixed gateway, handed to the core as `--peer ip:port`. */
    val peer: String = "",
    /** Optional exit country filter, handed to the core as `--exit-loc`. */
    val exitLoc: String = "",
    /** Carry a Tor exit inside the tunnel (`--tor`). */
    val tor: Boolean = false,
    /** Core log verbosity (AETHER_LOG_LEVEL). */
    val logLevel: String = "info",
) {
    val socksAddress: String get() = "127.0.0.1:$socksPort"

    /**
     * The exact command line the core receives. Every prompt the CLI would
     * otherwise show is answered here, so the core never blocks on stdin.
     */
    fun toCoreArguments(configPath: String): List<String> {
        val args = mutableListOf(
            protocol.flag,
            "--scan", scanMode.value,
            "--noize", noize.value,
            ipMode.flag,
            "--bind", socksAddress,
            "--config", configPath,
            "--stats", "--stats-secs", "30",
        )
        if (peer.isNotBlank()) {
            args += listOf("--peer", peer.trim())
        }
        if (exitLoc.isNotBlank()) {
            args += listOf("--exit-loc", exitLoc.trim())
        }
        if (tor) {
            args += "--tor"
        }
        return args
    }
}

/**
 * Small app-level settings store with a Compose-friendly state flow.
 */
object SettingsStore {

    private const val PREFS = "aether_settings"

    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_SCAN = "scan_mode"
    private const val KEY_NOIZE = "noize"
    private const val KEY_IP = "ip_mode"
    private const val KEY_VPN = "vpn_mode"
    private const val KEY_SOCKS_PORT = "socks_port"
    private const val KEY_PEER = "peer"
    private const val KEY_EXIT_LOC = "exit_loc"
    private const val KEY_TOR = "tor"
    private const val KEY_LOG_LEVEL = "log_level"

    private val _settings = MutableStateFlow(AetherSettings())
    val settings: StateFlow<AetherSettings> = _settings.asStateFlow()

    fun load(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        _settings.value = AetherSettings(
            protocol = Protocol.fromKey(prefs.getString(KEY_PROTOCOL, null)),
            scanMode = ScanMode.fromKey(prefs.getString(KEY_SCAN, null)),
            noize = Noize.fromKey(prefs.getString(KEY_NOIZE, null)),
            ipMode = IpMode.fromKey(prefs.getString(KEY_IP, null)),
            vpnMode = prefs.getBoolean(KEY_VPN, true),
            socksPort = prefs.getInt(KEY_SOCKS_PORT, 1819),
            peer = prefs.getString(KEY_PEER, "") ?: "",
            exitLoc = prefs.getString(KEY_EXIT_LOC, "") ?: "",
            tor = prefs.getBoolean(KEY_TOR, false),
            logLevel = prefs.getString(KEY_LOG_LEVEL, "info") ?: "info",
        )
    }

    fun update(context: android.content.Context, transform: (AetherSettings) -> AetherSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROTOCOL, next.protocol.key)
            .putString(KEY_SCAN, next.scanMode.key)
            .putString(KEY_NOIZE, next.noize.key)
            .putString(KEY_IP, next.ipMode.key)
            .putBoolean(KEY_VPN, next.vpnMode)
            .putInt(KEY_SOCKS_PORT, next.socksPort)
            .putString(KEY_PEER, next.peer)
            .putString(KEY_EXIT_LOC, next.exitLoc)
            .putBoolean(KEY_TOR, next.tor)
            .putString(KEY_LOG_LEVEL, next.logLevel)
            .apply()
    }
}
