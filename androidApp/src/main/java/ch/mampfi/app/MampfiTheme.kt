package ch.mampfi.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MampfiColors = darkColorScheme(
    primary = Color(0xFFB7D8B4), onPrimary = Color(0xFF102117),
    primaryContainer = Color(0xFF274C35), onPrimaryContainer = Color(0xFFD7F5D2),
    secondary = Color(0xFFE3C68A), onSecondary = Color(0xFF2A2009),
    secondaryContainer = Color(0xFF4A3A17), onSecondaryContainer = Color(0xFFFFEBC0),
    background = Color(0xFF0B1510), onBackground = Color(0xFFE2E9DF),
    surface = Color(0xFF132219), onSurface = Color(0xFFE2E9DF),
    surfaceVariant = Color(0xFF26382C), onSurfaceVariant = Color(0xFFC4D0C3),
    outline = Color(0xFF8D9D8F), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

@Composable
fun MampfiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MampfiColors,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
