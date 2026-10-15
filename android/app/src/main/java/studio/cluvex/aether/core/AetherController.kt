package studio.cluvex.aether.core

import android.content.Context
import android.util.Log
import studio.cluvex.aether.bridge.AetherCore
import studio.cluvex.aether.data.AetherSettings
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong
import java.time.Instant

/**
 * Owns the core lifecycle inside the foreground service:
 *
 *  1. prepares the pluggable transports the Go binaries provide,
 *  2. sets the AETHER_* environment the core reads,
 *  3. redirects native stderr into the in-app log,
 *  4. starts the core through `aether_core_start` with a complete,
 *     prompt-free command line,
 *  5. polls the job and the SOCKS5 port until the tunnel is serving.
 */
class AetherController(private val context: Context) {

    companion object {
        private const val TAG = "AetherController"
        private val SOCKS_GREETING = byteArrayOf(0x05, 0x01, 0x00)
    }

    private val currentJob = AtomicLong(0)

    @Volatile
    private var lastSocksAlive: Boolean = false

    /**
     * Brings the core up. Returns when the core has accepted the command
     * line; the caller keeps watching [AetherRuntime.state] afterwards.
     */
    fun start(settings: AetherSettings) {
        check(currentJob.get() == 0L) { "the core is already running" }

        AetherRuntime.setState(AetherState.Starting(AetherState.Starting.Phase.PREPARING))
        AetherRuntime.appendLog(":: aether core starting ::")

        val libDir = context.applicationInfo.nativeLibraryDir
        AetherCore.initialize(libDir)

        prepareTransports(libDir)

        val home = context.filesDir
        AetherCore.setEnv("AETHER_LOG_LEVEL", settings.logLevel)
        AetherCore.setEnv("AETHER_TOR_PT_DIR", File(home, "pt").absolutePath)
        AetherCore.setEnv("AETHER_PSIPHON_BIN", File(home, "pt/psiphon-tunnel-core").absolutePath)
        AetherCore.setEnv("HOME", home.absolutePath)

        val configDir = File(home, "aether")
        configDir.mkdirs()
        val configPath = File(configDir, "aether.toml").absolutePath

        AetherCore.nativeStartStderrCapture()

        val args = settings.toCoreArguments(configPath)
        AetherRuntime.appendLog("\$ aether ${args.joinToString(" ")}")

        val handle = AetherCore.coreStart(args)
        currentJob.set(handle.jobId)

        AetherRuntime.setState(AetherState.Starting(AetherState.Starting.Phase.SCANNING))
    }

    /**
     * Stops the core. Safe to call from any thread; blocks briefly while
     * the job is cancelled and freed.
     */
    fun stop() {
        val job = currentJob.getAndSet(0L)
        if (job != 0L) {
            AetherCore.jobCancel(job)
            AetherCore.jobFree(job)
        }
        AetherCore.nativeStopStderrCapture()
        AetherRuntime.appendLog(":: aether core stopped ::")
        AetherRuntime.setState(AetherState.Idle)
    }

    /** Reads whatever the core wrote to stderr. Call from a pump loop. */
    fun drainStderr(): Int {
        val buffer = ByteArray(8192)
        val n = AetherCore.nativeReadStderr(buffer)
        if (n > 0) {
            AetherRuntime.appendLog(String(buffer, 0, n, Charsets.UTF_8))
        }
        return n
    }

    /**
     * Checks the job state; returns true while the core job is alive.
     * When the job finishes, [AetherRuntime] is updated accordingly.
     */
    fun pollJob(): Boolean {
        val job = currentJob.get()
        if (job == 0L) {
            return false
        }
        when (val snapshot = AetherCore.jobPoll(job)) {
            is AetherCore.JobState.Running -> return true
            is AetherCore.JobState.Done -> {
                currentJob.set(0L)
                AetherCore.jobFree(job)
                val result = snapshot.result
                if (result.ok) {
                    // `closed` or `stopped` — the tunnel ended without an error.
                    AetherRuntime.appendLog(":: tunnel ${result.state ?: "closed"} ::")
                    AetherRuntime.setState(AetherState.Idle)
                } else {
                    AetherRuntime.appendLog(":: core error: ${result.error} ::")
                    AetherRuntime.setState(
                        AetherState.Failed(result.error ?: "the core stopped with an error"),
                    )
                }
                return false
            }
        }
    }

    /**
     * Cheap liveness probe for the local SOCKS5 proxy: a TCP connect plus
     * the "no auth" greeting. If the server replies with 05 00 the data
     * plane is taking connections.
     *
     * @return true when the proxy answered
     */
    fun probeSocks(settings: AetherSettings): Boolean {
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress("127.0.0.1", settings.socksPort), 900)
                socket.soTimeout = 900
                socket.getOutputStream().write(SOCKS_GREETING)
                socket.getOutputStream().flush()
                val method = socket.getInputStream().read()
                method == 0x05
            }
        } catch (_: Exception) {
            false
        }
    }

    /** True when [probeSocks] last succeeded; used by the UI pulse loop. */
    var socksAlive: Boolean
        get() = lastSocksAlive
        set(value) {
            lastSocksAlive = value
        }

    /**
     * Copies the bundled Go transport binaries out of the APK into
     * `files/pt` with the exact names the core searches for. Packaging as
     * `lib*.so` under jniLibs makes Android extract them per ABI with the
     * executable bit already set; only the rename is left to do.
     */
    private fun prepareTransports(libDir: String) {
        val ptDir = File(context.filesDir, "pt")
        ptDir.mkdirs()

        val pairs = listOf(
            "liblyrebird.so" to "lyrebird",
            "libpsiphon-tunnel-core.so" to "psiphon-tunnel-core",
        )
        for ((bundled, name) in pairs) {
            val source = File(libDir, bundled)
            if (source.exists()) {
                val target = File(ptDir, name)
                if (!target.exists() || target.length() != source.length()) {
                    source.copyTo(target, overwrite = true)
                    target.setExecutable(true, false)
                    Log.i(TAG, "prepared transport $name (${source.length()} bytes)")
                }
            } else {
                Log.i(TAG, "transport $bundled not bundled, skipping")
            }
        }
    }
}
