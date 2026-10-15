package studio.cluvex.aether.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Aether palette: deep space blues with a luminous cyan accent, the same
// mood the core project uses (an "ether" glow over a dark sky).
val EtherCyan = Color(0xFF7FE7F4)
val EtherTeal = Color(0xFF38BDF8)
val DeepSpace = Color(0xFF0B1026)
val DeepSurface = Color(0xFF111936)
val DeepSurfaceHigh = Color(0xFF1A244B)
val WarmAmber = Color(0xFFFBBF24)
val SoftRed = Color(0xFFF87171)

private val DarkScheme = darkColorScheme(
    primary = EtherCyan,
    onPrimary = Color(0xFF06222B),
    primaryContainer = Color(0xFF134E5E),
    onPrimaryContainer = Color(0xFFCFF6FF),
    secondary = EtherTeal,
    onSecondary = Color(0xFF062735),
    secondaryContainer = Color(0xFF0E3A4E),
    onSecondaryContainer = Color(0xFFC9EDFF),
    tertiary = WarmAmber,
    onTertiary = Color(0xFF3A2A00),
    background = DeepSpace,
    onBackground = Color(0xFFE7EAF6),
    surface = DeepSurface,
    onSurface = Color(0xFFE7EAF6),
    surfaceVariant = DeepSurfaceHigh,
    onSurfaceVariant = Color(0xFFB6BEDC),
    error = SoftRed,
    onError = Color(0xFF4C0B0B),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF6FF),
    onPrimaryContainer = Color(0xFF06222B),
    secondary = Color(0xFF0369A1),
    onSecondary = Color.White,
    tertiary = Color(0xFFB45309),
    background = Color(0xFFF5F7FD),
    onBackground = Color(0xFF151A2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151A2E),
    surfaceVariant = Color(0xFFE2E8F5),
    onSurfaceVariant = Color(0xFF49526E),
)

@Composable
fun AetherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
