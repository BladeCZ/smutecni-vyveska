package cz.pshornicek.vyveska.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgPrimary = Color(0xFF0F1117)
val BgSurface = Color(0xFF1A1D26)
val BgSurfaceElevated = Color(0xFF242936)
val AccentGold = Color(0xFFD97706)
val AccentGoldLight = Color(0xFFF59E0B)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)
val BorderColor = Color(0xFF2E3545)

private val DarkColorScheme = darkColorScheme(
    primary = AccentGold,
    secondary = AccentGoldLight,
    background = BgPrimary,
    surface = BgSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceElevated,
    outline = BorderColor
)

private val LightColorScheme = lightColorScheme(
    primary = AccentGold,
    secondary = AccentGoldLight,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    outline = Color(0xFFE2E8F0)
)

@Composable
fun SmutecniVyveskaTheme(
    darkTheme: Boolean = true, // Výchozí důstojný tmavý režim
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
