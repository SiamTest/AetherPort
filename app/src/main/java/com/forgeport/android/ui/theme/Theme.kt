package com.forgeport.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AetherPortLight = lightColorScheme(
    primary = Color(0xFF006B57), onPrimary = Color.White,
    primaryContainer = Color(0xFFB0F1D8), onPrimaryContainer = Color(0xFF00291E),
    secondary = Color(0xFF42665B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E9E0), onSecondaryContainer = Color(0xFF112B23),
    tertiary = Color(0xFF006879), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFA9EDFA), onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFF7FBF8), onBackground = Color(0xFF17201C),
    surface = Color(0xFFF7FBF8), onSurface = Color(0xFF17201C),
    surfaceVariant = Color(0xFFDEE9E2), onSurfaceVariant = Color(0xFF414B45),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F6F2),
    surfaceContainer = Color(0xFFEAF1EC), surfaceContainerHigh = Color(0xFFE4ECE6),
    surfaceContainerHighest = Color(0xFFDEE7E0),
    outline = Color(0xFF717D75), outlineVariant = Color(0xFFC1CCC4),
)

private val AetherPortDark = darkColorScheme(
    primary = Color(0xFF6EE7B7), onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF164C3B), onPrimaryContainer = Color(0xFFB0F1D8),
    secondary = Color(0xFFB0CFC1), onSecondary = Color(0xFF1C362B),
    secondaryContainer = Color(0xFF2E463C), onSecondaryContainer = Color(0xFFD4E9E0),
    tertiary = Color(0xFF22D3EE), onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF004F5C), onTertiaryContainer = Color(0xFFA9EDFA),
    background = Color.Black, onBackground = Color(0xFFE3EEE7),
    surface = Color.Black, onSurface = Color(0xFFE3EEE7),
    surfaceVariant = Color(0xFF26332C), onSurfaceVariant = Color(0xFFBECEC3),
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF101715),
    surfaceContainer = Color(0xFF151D19), surfaceContainerHigh = Color(0xFF1D2621),
    surfaceContainerHighest = Color(0xFF26302A),
    outline = Color(0xFF899C8F), outlineVariant = Color(0xFF3D4B42),
)

private val AetherPortShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 12.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// A clear hierarchy with comfortable line heights; sp respects Android font scaling.
private val AetherPortTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun AetherPortTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) AetherPortDark else AetherPortLight,
        shapes = AetherPortShapes,
        typography = AetherPortTypography,
        content = content,
    )
}
