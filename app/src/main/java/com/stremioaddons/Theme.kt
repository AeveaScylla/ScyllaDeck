package com.stremioaddons

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Dark = darkColorScheme(
    primary = Color(0xFFA78BFA), onPrimary = Color(0xFF1B0B45),
    primaryContainer = Color(0xFF3B2A7A), onPrimaryContainer = Color(0xFFE9E0FF),
    secondary = Color(0xFFB8A8F5), onSecondary = Color(0xFF241552),
    secondaryContainer = Color(0xFF2E2655), onSecondaryContainer = Color(0xFFE3DCFF),
    background = Color(0xFF0F0D17), onBackground = Color(0xFFEAE6F5),
    surface = Color(0xFF0F0D17), onSurface = Color(0xFFEAE6F5),
    surfaceVariant = Color(0xFF231F37), onSurfaceVariant = Color(0xFFB4AFCB),
    surfaceContainer = Color(0xFF1A1729), surfaceContainerHigh = Color(0xFF221E36), surfaceContainerHighest = Color(0xFF2B2744),
    outline = Color(0xFF5A5578), outlineVariant = Color(0xFF3A3656),
)

private val Light = lightColorScheme(
    primary = Color(0xFF6D4AE0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEFF), onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF5E5790), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4DFFF), onSecondaryContainer = Color(0xFF1A1249),
    background = Color(0xFFFBF9FF), onBackground = Color(0xFF1B1A22),
    surface = Color(0xFFFBF9FF), onSurface = Color(0xFF1B1A22),
    surfaceVariant = Color(0xFFEDE9F7), onSurfaceVariant = Color(0xFF4B4860),
    surfaceContainer = Color(0xFFF3F0FB), surfaceContainerHigh = Color(0xFFECE8F7), surfaceContainerHighest = Color(0xFFE5E1F2),
    outline = Color(0xFF7C7893), outlineVariant = Color(0xFFCBC6DD),
)

@Composable
fun AddonTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content
    )
}
