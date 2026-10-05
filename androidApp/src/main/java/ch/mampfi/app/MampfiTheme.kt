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
    primary = Color(0xFF2F6B45), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6ECDD), onPrimaryContainer = Color(0xFF0D2B19),
    secondary = Color(0xFF536A56), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8DC), onSecondaryContainer = Color(0xFF15291A),
    tertiary = Color(0xFF9A5545), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD2), onTertiaryContainer = Color(0xFF3B0903),
    background = Color(0xFFEFF5ED), onBackground = Color(0xFF172019),
    surface = Color(0xFFFBFDF9), onSurface = Color(0xFF172019),
    surfaceVariant = Color(0xFFDEE8DE), onSurfaceVariant = Color(0xFF3F4941),
    surfaceDim = Color(0xFFD8DED6), surfaceBright = Color(0xFFFBFDF9),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F9F3),
    surfaceContainer = Color(0xFFEDF3EB), surfaceContainerHigh = Color(0xFFE7EDE5),
    surfaceContainerHighest = Color(0xFFE1E8DF),
    outline = Color(0xFF6F7A71), outlineVariant = Color(0xFFBECABD),
    inverseSurface = Color(0xFF2C322D), inverseOnSurface = Color(0xFFF0F2ED), inversePrimary = Color(0xFFA8D5B4),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

@Composable
fun MampfiTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
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
