package studio.cluvex.aether.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import studio.cluvex.aether.R
import studio.cluvex.aether.data.AetherSettings
import studio.cluvex.aether.data.IpMode
import studio.cluvex.aether.data.Noize
import studio.cluvex.aether.data.Protocol
import studio.cluvex.aether.data.ScanMode
import studio.cluvex.aether.data.SettingsStore

/**
 * Everything tunable before a connection. Locked while the tunnel is up
 * because the core reads its command line exactly once per run.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier,
    settings: AetherSettings,
    locked: Boolean,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (locked) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                ),
            ) {
                Text(
                    stringResource(R.string.settings_locked),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        SettingCard(title = stringResource(R.string.settings_protocol), hint = stringResource(R.string.settings_protocol_hint)) {
            ChipRow(enabled = !locked) {
                Protocol.entries.forEach { proto ->
                    FilterChip(
                        selected = settings.protocol == proto,
                        enabled = !locked,
                        onClick = {
                            SettingsStore.update(context) { it.copy(protocol = proto) }
                        },
                        label = { Text(stringResource(protocolLabel(proto))) },
                    )
                }
            }
        }

        SettingCard(title = stringResource(R.string.settings_scan), hint = stringResource(R.string.settings_scan_hint)) {
            ChipRow(enabled = !locked) {
                ScanMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.scanMode == mode,
                        enabled = !locked,
                        onClick = {
                            SettingsStore.update(context) { it.copy(scanMode = mode) }
                        },
                        label = { Text(stringResource(scanLabel(mode))) },
                    )
                }
            }
        }

        SettingCard(title = stringResource(R.string.settings_noize), hint = stringResource(R.string.settings_noize_hint)) {
            ChipRow(enabled = !locked) {
                Noize.entries.forEach { noize ->
                    FilterChip(
                        selected = settings.noize == noize,
                        enabled = !locked,
                        onClick = {
                            SettingsStore.update(context) { it.copy(noize = noize) }
                        },
                        label = { Text(noize.value) },
                    )
                }
            }
        }

        SettingCard(title = stringResource(R.string.settings_ip), hint = stringResource(R.string.settings_ip_hint)) {
            ChipRow(enabled = !locked) {
                IpMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.ipMode == mode,
                        enabled = !locked,
                        onClick = {
                            SettingsStore.update(context) { it.copy(ipMode = mode) }
                        },
                        label = { Text(mode.key) },
                    )
                }
            }
        }

        SettingCard(title = stringResource(R.string.settings_vpn), hint = stringResource(R.string.settings_vpn_hint)) {
            ToggleRow(
                checked = settings.vpnMode,
                enabled = !locked,
                onChecked = { value ->
                    SettingsStore.update(context) { it.copy(vpnMode = value) }
                },
            )
        }

        SettingCard(title = stringResource(R.string.settings_advanced)) {
            var peer by rememberSaveable(settings.peer) { mutableStateOf(settings.peer) }
            var exitLoc by rememberSaveable(settings.exitLoc) { mutableStateOf(settings.exitLoc) }

            OutlinedTextField(
                value = peer,
                onValueChange = { peer = it },
                label = { Text(stringResource(R.string.settings_peer)) },
                placeholder = { Text(stringResource(R.string.settings_peer_hint)) },
                enabled = !locked,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = exitLoc,
                onValueChange = { exitLoc = it },
                label = { Text(stringResource(R.string.settings_exitloc)) },
                placeholder = { Text(stringResource(R.string.settings_exitloc_hint)) },
                enabled = !locked,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LaunchedEffect(peer, exitLoc, locked) {
                if (!locked) {
                    kotlinx.coroutines.delay(600)
                    SettingsStore.update(context) { current ->
                        if (current.peer != peer || current.exitLoc != exitLoc) {
                            current.copy(peer = peer.trim(), exitLoc = exitLoc.trim())
                        } else {
                            current
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                checked = settings.tor,
                enabled = !locked,
                title = stringResource(R.string.settings_tor),
                hint = stringResource(R.string.settings_tor_hint),
                onChecked = { value ->
                    SettingsStore.update(context) { it.copy(tor = value) }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_loglevel) + ": " + settings.logLevel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(enabled = !locked) {
                listOf("error", "warn", "info", "debug").forEach { level ->
                    FilterChip(
                        selected = settings.logLevel == level,
                        enabled = !locked,
                        onClick = {
                            SettingsStore.update(context) { it.copy(logLevel = level) }
                        },
                        label = { Text(level) },
                    )
                }
            }
        }

        SettingCard(title = stringResource(R.string.settings_language)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("fa" to "فارسی", "en" to "English").forEach { (tag, label) ->
                    FilterChip(
                        selected = AppCompatDelegate.getApplicationLocales()
                            .toLanguageTags() == tag,
                        onClick = {
                            context.getSharedPreferences("aether_ui", android.content.Context.MODE_PRIVATE)
                                .edit().putString("language", tag).apply()
                            AppCompatDelegate.setApplicationLocales(
                                LocaleListCompat.forLanguageTags(tag),
                            )
                        },
                        label = { Text(label) },
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = AppCompatDelegate.getApplicationLocales().isEmpty,
                    onClick = {
                        context.getSharedPreferences("aether_ui", android.content.Context.MODE_PRIVATE)
                            .edit().remove("language").apply()
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    },
                    label = { Text(stringResource(R.string.settings_language_system)) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettingCard(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun ChipRow(enabled: Boolean = true, content: @Composable () -> Unit) {
    Column {
        content()
    }
}

@Composable
private fun ToggleRow(
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
    title: String? = null,
    hint: String? = null,
) {
    if (title != null) {
        Column {
            ToggleRowContent(checked, enabled, title, hint, onChecked)
        }
    } else {
        ToggleRowContent(checked, enabled, null, null, onChecked)
    }
}

@Composable
private fun ToggleRowContent(
    checked: Boolean,
    enabled: Boolean,
    title: String?,
    hint: String?,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
            }
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChecked)
    }
}

private fun protocolLabel(protocol: Protocol): Int = when (protocol) {
    Protocol.MASQUE -> R.string.proto_masque
    Protocol.WIREGUARD -> R.string.proto_wireguard
    Protocol.GOOL -> R.string.proto_gool
    Protocol.GOOL_CLASSIC -> R.string.proto_gool_classic
    Protocol.MIM -> R.string.proto_mim
}

private fun scanLabel(mode: ScanMode): Int = when (mode) {
    ScanMode.TURBO -> R.string.scan_turbo
    ScanMode.BALANCED -> R.string.scan_balanced
    ScanMode.THOROUGH -> R.string.scan_thorough
    ScanMode.VERIFIED -> R.string.scan_verified
    ScanMode.IRONCLAD -> R.string.scan_ironclad
}
