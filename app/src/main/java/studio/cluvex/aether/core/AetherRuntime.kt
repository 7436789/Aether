package studio.cluvex.aether.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Lifecycle of the tunnel as the UI sees it.
 */
sealed interface AetherState {

    /** Nothing running. */
    data object Idle : AetherState

    /** The core is scanning / handshaking / registering the device. */
    data class Starting(val phase: Phase, val detail: String = "") : AetherState {
        enum class Phase { PREPARING, SCANNING, CONNECTING }
    }

    /** The tunnel is up; the SOCKS5 proxy may or may not be accepting yet. */
    data class Connected(
        val since: Instant,
        val socksAlive: Boolean,
        val vpnActive: Boolean,
    ) : AetherState

    /** A stop was requested and is being carried out. */
    data object Stopping : AetherState

    /** The core gave up; its error message is carried verbatim. */
    data class Failed(val error: String) : AetherState
}

/**
 * Rolling log of native stderr lines plus traffic counters reported by the
 * tun front end, shared between the service, the VPN and the UI.
 */
object AetherRuntime {

    private const val LOG_LIMIT = 800

    private val _state = MutableStateFlow<AetherState>(AetherState.Idle)
    val state: StateFlow<AetherState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    private val _throughput = MutableStateFlow(Throughput())
    val throughput: StateFlow<Throughput> = _throughput.asStateFlow()

    data class LogLine(val timestamp: Long, val text: String)

    data class Throughput(
        val rxBytes: Long = 0,
        val txBytes: Long = 0,
        val rateDown: Long = 0,
        val rateUp: Long = 0,
        val tcpActive: Long = 0,
        val udpActive: Long = 0,
    )

    fun setState(state: AetherState) {
        _state.value = state
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    fun appendLog(text: String) {
        if (text.isBlank()) {
            return
        }
        val stamp = System.currentTimeMillis()
        val lines = text.lineSequence()
            .filter { it.isNotBlank() }
            .map { LogLine(stamp, it.trim()) }
            .toList()
        if (lines.isEmpty()) {
            return
        }
        _log.value = (_log.value + lines).takeLast(LOG_LIMIT)
    }

    fun reportStats(
        rx: Long,
        tx: Long,
        rateDown: Long,
        rateUp: Long,
        tcpActive: Long,
        udpActive: Long,
    ) {
        _throughput.value = Throughput(rx, tx, rateDown, rateUp, tcpActive, udpActive)
    }

    fun reset() {
        _state.value = AetherState.Idle
        _throughput.value = Throughput()
    }
}
