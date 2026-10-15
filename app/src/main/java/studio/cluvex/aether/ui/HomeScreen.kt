package studio.cluvex.aether.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.cluvex.aether.R
import studio.cluvex.aether.core.AetherRuntime
import studio.cluvex.aether.core.AetherState
import studio.cluvex.aether.data.AetherSettings
import studio.cluvex.aether.data.Protocol
import studio.cluvex.aether.data.SettingsStore
import java.time.Duration
import java.time.Instant

/**
 * The hero screen, Pixel style: a large top bar with the ether-ripple mark,
 * one unmissable circular power control over a soft glow, a Material You
 * status card and one-tap protocol chips. Everything else lives behind the
 * other tabs.
 */
@Composable
fun HomeScreen(
    modifier: Modifier,
    state: AetherState,
    settings: AetherSettings,
    busy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val throughput by AetherRuntime.throughput.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        BrandHeader()
        Spacer(Modifier.height(14.dp))
        StatusHeader(state)
        Spacer(Modifier.height(18.dp))
        PowerButton(
            state = state,
            busy = busy,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
        )
        Spacer(Modifier.height(24.dp))

        when (state) {
            is AetherState.Failed -> ErrorCard(state.error)
            else -> Unit
        }

        StatusCard(state, settings, throughput)
        Spacer(Modifier.height(20.dp))
        ProtocolPicker(settings)
        Spacer(Modifier.height(24.dp))
    }
}

/** Large top app bar look: ripple mark + app name, the Pixel way. */
@Composable
private fun BrandHeader() {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(26.dp)) {
                val c = Offset(this.size.width / 2, this.size.height / 2)
                drawCircle(color = primary, radius = 3.2f)
                drawCircle(color = primary, radius = 7.4f, style = Stroke(width = 2.0f))
                drawCircle(color = primary, radius = 11.4f, style = Stroke(width = 1.4f))
            }
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatusHeader(state: AetherState) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when (state) {
        is AetherState.Idle -> stringResource(R.string.state_idle) to scheme.onSurfaceVariant
        is AetherState.Starting -> when (state.phase) {
            AetherState.Starting.Phase.PREPARING -> stringResource(R.string.state_preparing)
            AetherState.Starting.Phase.SCANNING -> stringResource(R.string.state_scanning)
            AetherState.Starting.Phase.CONNECTING -> stringResource(R.string.state_connecting)
        } to scheme.primary
        is AetherState.Connected -> stringResource(R.string.state_connected) to scheme.tertiary
        AetherState.Stopping -> stringResource(R.string.state_stopping) to scheme.onSurfaceVariant
        is AetherState.Failed -> stringResource(R.string.state_failed) to scheme.error
    }
    val breathing = state is AetherState.Starting || state is AetherState.Connected
    val pulse = rememberInfiniteTransition(label = "statusDot")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(
                    color = color.copy(alpha = if (breathing) dotAlpha else 1f),
                    RoundedCornerShape(50),
                ),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The Pixel-style power control: a wide ring plus a filled inner disc that
 * lights up in Google blue while the tunnel is up, with a soft glow halo.
 */
@Composable
private fun PowerButton(
    state: AetherState,
    busy: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val active = state is AetherState.Connected || busy
    val failed = state is AetherState.Failed
    val idleRingColor = scheme.onSurfaceVariant.copy(alpha = 0.38f)
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ringAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringAlpha",
    )
    val accent = when {
        state is AetherState.Connected -> scheme.tertiary
        failed -> scheme.error
        state is AetherState.Starting -> scheme.primary
        else -> scheme.primary
    }

    Box(
        modifier = Modifier
            .size(196.dp)
            .background(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        scheme.primary.copy(alpha = if (active) 0.16f else 0.07f),
                        Color.Transparent,
                    ),
                ),
                shape = CircleShape,
            )
            .clickable(enabled = !busy) {
                if (state is AetherState.Connected) onDisconnect() else onConnect()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(196.dp)) {
            val stroke = Stroke(width = 7f, cap = StrokeCap.Round)
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val radius = this.size.minDimension / 2 - 9f
            val ringColor = when {
                state is AetherState.Connected -> accent
                failed -> accent
                busy -> accent.copy(alpha = ringAlpha)
                else -> idleRingColor
            }
            drawCircle(color = ringColor, radius = radius, style = stroke)

            val inner = radius * 0.76f
            if (state is AetherState.Connected) {
                // Filled Material You disc with the glyph knocked out of it.
                drawCircle(color = accent, radius = inner, style = Stroke(width = inner * 2))
            }
            // The power glyph: a stem plus an open arc.
            val glyph = ringColor
            val stem = Offset(center.x, center.y - inner * 0.62f)
            drawLine(
                color = glyph,
                start = stem,
                end = Offset(center.x, center.y),
                strokeWidth = 9f,
                cap = StrokeCap.Round,
            )
            drawArc(
                color = glyph,
                startAngle = -60f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(center.x - inner * 0.52f, center.y - inner * 0.52f),
                size = androidx.compose.ui.geometry.Size(inner * 1.04f, inner * 1.04f),
                style = Stroke(width = 9f, cap = StrokeCap.Round),
            )
        }
        Text(
            text = stringResource(
                if (state is AetherState.Connected) R.string.home_disconnect else R.string.home_connect,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun ErrorCard(error: String) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = scheme.errorContainer.copy(alpha = 0.55f),
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.state_failed),
                color = scheme.error,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = scheme.onErrorContainer,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun StatusCard(
    state: AetherState,
    settings: AetherSettings,
    throughput: AetherRuntime.Throughput,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceContainerLow,
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state is AetherState.Connected) {
                SpeedBanner(throughput)
            }
            InfoRow(
                label = stringResource(R.string.home_socks_endpoint),
                value = settings.socksAddress,
            )
            when (state) {
                is AetherState.Connected -> {
                    InfoRow(
                        label = stringResource(
                            if (state.vpnActive) R.string.home_vpn_active else R.string.home_vpn_inactive,
                            settings.socksAddress,
                        ),
                        value = formatUptime(state.since),
                        highlight = state.vpnActive,
                    )
                    Text(
                        text = stringResource(
                            if (state.socksAlive) R.string.home_proxy_alive else R.string.home_tun_healing,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.socksAlive) scheme.tertiary else scheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                is AetherState.Starting -> InfoRow(
                    label = stringResource(R.string.home_proxy_wait),
                    value = "…",
                )
                else -> Unit
            }
        }
    }
}

/**
 * Live speeds straight off the zeptun counters, with the session count
 * and cumulative volume in a quieter line underneath.
 */
@Composable
private fun SpeedBanner(throughput: AetherRuntime.Throughput) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            SpeedColumn(
                label = stringResource(R.string.home_down),
                value = formatBytes(throughput.rateDown),
                color = scheme.primary,
            )
            SpeedColumn(
                label = stringResource(R.string.home_up),
                value = formatBytes(throughput.rateUp),
                color = scheme.secondary,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.home_throughput,
                    formatBytes(throughput.rxBytes),
                    formatBytes(throughput.txBytes),
                ),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.home_sessions, throughput.tcpActive, throughput.udpActive),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun SpeedColumn(label: String, value: String, color: Color) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$value/s",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, highlight: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Material You filter chips with the selected check, like Pixel settings. */
@Composable
private fun ProtocolPicker(settings: AetherSettings) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val connected = AetherRuntime.state.collectAsState().value is AetherState.Connected
    val scheme = MaterialTheme.colorScheme

    Column {
        Text(
            stringResource(R.string.home_protocol),
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Protocol.entries.forEach { protocol ->
                FilterChip(
                    selected = settings.protocol == protocol,
                    enabled = !connected,
                    onClick = {
                        if (!connected) {
                            SettingsStore.update(context) { it.copy(protocol = protocol) }
                        }
                    },
                    leadingIcon = if (settings.protocol == protocol) {
                        {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = scheme.onPrimaryContainer,
                            )
                        }
                    } else {
                        null
                    },
                    label = {
                        Text(
                            stringResource(protocolLabel(protocol)),
                            fontSize = 12.sp,
                        )
                    },
                )
            }
        }
        if (connected) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_apply_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun protocolLabel(protocol: Protocol): Int = when (protocol) {
    Protocol.MASQUE -> R.string.proto_masque
    Protocol.WIREGUARD -> R.string.proto_wireguard
    Protocol.GOOL -> R.string.proto_gool
    Protocol.GOOL_CLASSIC -> R.string.proto_gool_classic
    Protocol.MIM -> R.string.proto_mim
}

private fun formatUptime(since: Instant): String {
    val seconds = Duration.between(since, Instant.now()).seconds
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 30 -> "%.1f GB".format(bytes / 1e9)
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1 shl 10 -> "%.1f KB".format(bytes / 1e3)
    else -> "$bytes B"
}
