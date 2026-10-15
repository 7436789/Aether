package studio.cluvex.aether.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Pixel-style Material You palette, seeded with Google Blue and a
 * "connected" green for the tunnel state. Light surfaces are the pale
 * blue-tinted neutrals stock Pixel apps use; dark surfaces follow the
 * #12141B shade of the system quick-settings panel.
 */
val PixelBlue = Color(0xFF0B57D0)
val PixelBlueContainer = Color(0xFFD3E3FD)
val PixelBlueDeep = Color(0xFF041E49)
val PixelBlueDark = Color(0xFFA8C7FA)
val ConnectedGreen = Color(0xFF146C2E)
val ConnectedGreenContainer = Color(0xFFC4EED0)
val ConnectedGreenDark = Color(0xFF6DD58C)
val PixelAmber = Color(0xFFB25E00)

private val LightScheme = lightColorScheme(
    primary = PixelBlue,
    onPrimary = Color.White,
    primaryContainer = PixelBlueContainer,
    onPrimaryContainer = PixelBlueDeep,
    secondary = Color(0xFF565E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF131C2B),
    tertiary = ConnectedGreen,
    onTertiary = Color.White,
    tertiaryContainer = ConnectedGreenContainer,
    onTertiaryContainer = Color(0xFF072711),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF0F3FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF0F3FA),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE9EBF3),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFF),
    surfaceContainer = Color(0xFFF2F4FA),
    surfaceContainerHigh = Color(0xFFECEEF5),
    surfaceContainerHighest = Color(0xFFE6E9F1),
    outline = Color(0xFF747780),
    outlineVariant = Color(0xFFC4C7D2),
)

private val DarkScheme = darkColorScheme(
    primary = PixelBlueDark,
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = PixelBlueContainer,
    secondary = Color(0xFFBFC8DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = ConnectedGreenDark,
    onTertiary = Color(0xFF0F3D1D),
    tertiaryContainer = Color(0xFF0F5223),
    onTertiaryContainer = ConnectedGreenContainer,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF12141B),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF12141B),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF2A2D34),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFF0C0E14),
    surfaceContainerLow = Color(0xFF191B21),
    surfaceContainer = Color(0xFF1D2025),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF3E4049),
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
