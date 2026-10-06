package com.forgeport.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ForgePortLight = lightColorScheme(
    primary = Color(0xFF3155C6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00164F),
    secondary = Color(0xFF585E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF735471),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F8),
    onTertiaryContainer = Color(0xFF2B122B),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E2EC),
    onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680),
)

private val ForgePortDark = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF173EAD),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFC0C6DD),
    onSecondary = Color(0xFF2A3042),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFE1BBDD),
    onTertiary = Color(0xFF412741),
    tertiaryContainer = Color(0xFF5A3D59),
    onTertiaryContainer = Color(0xFFFFD7F8),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF91909A),
)

private val ForgePortShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

internal val GalleryColorScheme = darkColorScheme(
    primary = Color(0xFFF9811A), onPrimary = Color(0xFF18120D),
    primaryContainer = Color(0xFF432710), onPrimaryContainer = Color(0xFFFFCCA3),
    secondary = Color(0xFFF9811A), onSecondary = Color.Black,
    secondaryContainer = Color(0xFF222222), onSecondaryContainer = Color(0xFFF5F5F5),
    background = Color.Black, onBackground = Color(0xFFF5F5F5),
    surface = Color.Black, onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF191919), onSurfaceVariant = Color(0xFFB8B8B8),
    surfaceContainer = Color(0xFF151515), surfaceContainerLow = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF222222), surfaceContainerHighest = Color(0xFF292929),
    outline = Color(0xFF48484D), outlineVariant = Color(0xFF333333),
)

@Composable
fun ForgePortTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) ForgePortDark else ForgePortLight,
        shapes = ForgePortShapes,
        content = content,
    )
}
