package io.github.mikachu1302.homehub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The one place for colours and sizes. The first slice is plain Material 3; the real design comes later
 * (docs/PLAN.md, "App layout") and only has to change this file.
 */
object Dimens {
    val screenPadding = 16.dp
    val topPadding = 24.dp
    val gap = 12.dp
    val rowPadding = 16.dp
    val corner = 16.dp
    val toggleWidth = 84.dp
    val buttonHeight = 48.dp
    val bigKey = 72.dp
    val menuEntryHeight = 112.dp
}

private val Light = lightColorScheme(
    primary = Color(0xFF2F5FB3),
    onPrimary = Color.White,
    background = Color(0xFFF4F4F2),
    surface = Color.White,
    surfaceVariant = Color(0xFFECECEA),
    onSurfaceVariant = Color(0xFF4A4A48),
    outline = Color(0xFFB8B8B4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DB0F0),
    onPrimary = Color(0xFF0E2245),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFC4C4C0),
    outline = Color(0xFF5A5A58),
)

@Composable
fun HomeHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
