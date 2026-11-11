package com.salmonllama.filematrix.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val MatrixEmerald = Color(0xFF00E676)
val MatrixCyan = Color(0xFF00E5FF)
val MatrixAccent = Color(0xFF3D5AFE)
val MatrixWarning = Color(0xFFFFB74D)
val MatrixError = Color(0xFFFF5252)

val DarkSurfaceLowest = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkSurfaceHigh = Color(0xFF21262D)
val DarkSurfaceHighest = Color(0xFF30363D)
val DarkOutline = Color(0xFF30363D)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF090D16),
    primaryContainer = Color(0xFF1F6FEB),
    onPrimaryContainer = Color(0xFFF0F6FC),
    secondary = MatrixEmerald,
    onSecondary = Color(0xFF003816),
    secondaryContainer = Color(0xFF005323),
    onSecondaryContainer = Color(0xFF7AFFAB),
    tertiary = MatrixCyan,
    onTertiary = Color(0xFF00363D),
    background = DarkSurfaceLowest,
    onBackground = Color(0xFFF0F6FC),
    surface = DarkSurface,
    onSurface = Color(0xFFC9D1D9),
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLowest = DarkSurfaceLowest,
    surfaceContainerLow = Color(0xFF11161D),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    outline = DarkOutline,
    outlineVariant = Color(0xFF21262D)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF0969DA),
    secondary = Color(0xFF1A7F37),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAFBE1),
    onSecondaryContainer = Color(0xFF1A7F37),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEFF2),
    onSurfaceVariant = Color(0xFF656D76),
    outline = Color(0xFFD0D7DE)
)

@Composable
fun FileMatrixTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MatrixTypography,
        content = content
    )
}
