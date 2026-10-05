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
    primary = Color(0xFF355E3B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E8D2), onPrimaryContainer = Color(0xFF102117),
    secondary = Color(0xFF765B1B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF9E3AA), onSecondaryContainer = Color(0xFF271A00),
    tertiary = Color(0xFF79536A), onTertiary = Color.White,
    background = Color(0xFFF7F4EA), onBackground = Color(0xFF192018),
    surface = Color(0xFFFFFCF4), onSurface = Color(0xFF192018),
    surfaceVariant = Color(0xFFE1E8DC), onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF737A70), error = Color(0xFFBA1A1A), onError = Color.White,
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
