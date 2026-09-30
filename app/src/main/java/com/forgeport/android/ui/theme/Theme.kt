package com.forgeport.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ForgePortDark = darkColorScheme(
    primary = Color(0xFF8CB4FF),
    onPrimary = Color(0xFF002F65),
    secondary = Color(0xFFAFC6FF),
    background = Color(0xFF080A0F),
    surface = Color(0xFF10131A),
    surfaceVariant = Color(0xFF181C25),
    onBackground = Color(0xFFE7EAF1),
    onSurface = Color(0xFFE7EAF1),
)

@Composable
fun ForgePortTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ForgePortDark,
        content = content,
    )
}
