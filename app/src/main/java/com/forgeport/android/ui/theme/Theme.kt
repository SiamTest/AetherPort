package com.forgeport.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Auralis's charcoal surfaces and violet accent, shared by every native screen.
private val AetherPortColors = darkColorScheme(
    primary = Color(0xFFC7B5FF), onPrimary = Color(0xFF302347),
    primaryContainer = Color(0xFF332B45), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFC9BEDA), onSecondary = Color(0xFF302A3C),
    secondaryContainer = Color(0xFF332B45), onSecondaryContainer = Color(0xFFE9DDFF),
    tertiary = Color(0xFFD7B8D3), onTertiary = Color(0xFF3B2638),
    tertiaryContainer = Color(0xFF50394D), onTertiaryContainer = Color(0xFFF5D4EE),
    background = Color(0xFF111015), onBackground = Color(0xFFE9E5EF),
    surface = Color(0xFF19171E), onSurface = Color(0xFFE9E5EF),
    surfaceVariant = Color(0xFF211E28), onSurfaceVariant = Color(0xFFAAA4B5),
    surfaceContainerLowest = Color(0xFF111015), surfaceContainerLow = Color(0xFF17151B),
    surfaceContainer = Color(0xFF211E28), surfaceContainerHigh = Color(0xFF2B2438),
    surfaceContainerHighest = Color(0xFF332B45),
    outline = Color(0xFF766D85), outlineVariant = Color(0xFF302B39),
    error = Color(0xFFFFB4C8), onError = Color(0xFF5D112B),
    errorContainer = Color(0xFF3C2730), onErrorContainer = Color(0xFFFFCDD8),
)

private val AetherPortShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Native sans-serif on Android; scalable type with Auralis's compact hierarchy.
private val AetherPortTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp),
)

@Composable
fun AetherPortTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AetherPortColors, shapes = AetherPortShapes,
        typography = AetherPortTypography, content = content)
}
