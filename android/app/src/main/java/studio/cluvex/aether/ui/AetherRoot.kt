package studio.cluvex.aether.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import studio.cluvex.aether.R
import studio.cluvex.aether.core.AetherRuntime
import studio.cluvex.aether.core.AetherState
import studio.cluvex.aether.data.SettingsStore

private enum class Tab(val icon: Int, val label: Int) {
    HOME(R.drawable.ic_tab_home, R.string.tab_home),
    LOGS(R.drawable.ic_tab_logs, R.string.tab_logs),
    SETTINGS(R.drawable.ic_tab_settings, R.string.tab_settings),
    ABOUT(R.drawable.ic_tab_about, R.string.tab_about),
}

/**
 * App shell: bottom navigation plus the four screens. All state flows in
 * from [AetherRuntime] and [SettingsStore]; no view models needed for an
 * app this size.
 */
@Composable
fun AetherRoot(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val state by AetherRuntime.state.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                painter = painterResource(item.icon),
                                contentDescription = stringResource(item.label),
                            )
                        },
                        label = { Text(stringResource(item.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val busy = state is AetherState.Starting || state is AetherState.Stopping
        when (tab) {
            Tab.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                state = state,
                settings = settings,
                busy = busy,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
            )
            Tab.LOGS -> LogsScreen(
                modifier = Modifier.padding(padding),
            )
            Tab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(padding),
                settings = settings,
                locked = busy || state is AetherState.Connected,
            )
            Tab.ABOUT -> AboutScreen(
                modifier = Modifier.padding(padding),
            )
        }
    }
}
