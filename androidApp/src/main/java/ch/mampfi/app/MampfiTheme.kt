package ch.mampfi.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MampfiDarkColors = darkColorScheme(
    primary = Color(0xFFB7D8B4), onPrimary = Color(0xFF102117),
    primaryContainer = Color(0xFF274C35), onPrimaryContainer = Color(0xFFD7F5D2),
    secondary = Color(0xFFE3C68A), onSecondary = Color(0xFF2A2009),
    secondaryContainer = Color(0xFF4A3A17), onSecondaryContainer = Color(0xFFFFEBC0),
    tertiary = Color(0xFFE5B8D1), onTertiary = Color(0xFF43263A),
    background = Color(0xFF0B1510), onBackground = Color(0xFFE2E9DF),
    surface = Color(0xFF132219), onSurface = Color(0xFFE2E9DF),
    surfaceVariant = Color(0xFF26382C), onSurfaceVariant = Color(0xFFC4D0C3),
    outline = Color(0xFF8D9D8F), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val MampfiLightColors = lightColorScheme(
    primary = Color(0xFF087F5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3E4), onPrimaryContainer = Color(0xFF073B2D),
    secondary = Color(0xFF8A6A00), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF0B8), onSecondaryContainer = Color(0xFF2B2000),
    tertiary = Color(0xFF7A5265), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E8), onTertiaryContainer = Color(0xFF30111F),
    background = Color(0xFFF4F6F5), onBackground = Color(0xFF18201D),
    surface = Color.White, onSurface = Color(0xFF18201D),
    surfaceVariant = Color(0xFFE8ECEA), onSurfaceVariant = Color(0xFF414946),
    surfaceDim = Color(0xFFD7DBD9), surfaceBright = Color.White,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FAF9),
    surfaceContainer = Color(0xFFF1F4F2), surfaceContainerHigh = Color(0xFFEBEFED),
    surfaceContainerHighest = Color(0xFFE5EAE7),
    outline = Color(0xFF6F7A75), outlineVariant = Color(0xFFCBD3CF),
    inverseSurface = Color(0xFF2D3330), inverseOnSurface = Color(0xFFF0F2F1), inversePrimary = Color(0xFF72DDB8),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

@Composable
fun MampfiTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) MampfiDarkColors else MampfiLightColors,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
