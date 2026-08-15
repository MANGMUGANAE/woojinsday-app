package com.daejin.woojintoday.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val WoojinColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    error = ErrorRed,
    onError = OnPrimary
)

private val WoojinShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun WoojinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WoojinColorScheme,
        typography = Typography,
        shapes = WoojinShapes,
        content = content
    )
}