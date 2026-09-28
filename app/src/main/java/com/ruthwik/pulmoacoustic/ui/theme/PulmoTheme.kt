package com.ruthwik.pulmoacoustic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color(0xFF5A5A5A),
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF101010),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF101010),
    surfaceVariant = Color(0xFFECECEC),
    onSurfaceVariant = Color(0xFF606060),
    outline = Color(0xFFC7C7C7),
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF7F7F7),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFF7F7F7),
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFB8B8B8),
    outline = Color(0xFF3E3E3E),
)

private val PulmoShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun PulmoTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = PulmoShapes,
        content = content
    )
}
