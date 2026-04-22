package com.pixelplay.dotsboxes.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary          = Purple80,
    onPrimary        = Purple10,
    primaryContainer = Purple40,
    onPrimaryContainer = Purple90,
    secondary        = Indigo80,
    onSecondary      = Color(0xFF001870),
    background       = DarkBackground,
    onBackground     = DarkOnSurface,
    surface          = DarkSurface,
    onSurface        = DarkOnSurface,
    surfaceVariant   = DarkSurface2,
    outline          = Color(0xFF665C80)
)

private val LightColors = lightColorScheme(
    primary          = Purple40,
    onPrimary        = Color.White,
    primaryContainer = Purple90,
    onPrimaryContainer = Purple10,
    secondary        = Indigo40,
    onSecondary      = Color.White,
    background       = LightBackground,
    onBackground     = LightOnSurface,
    surface          = LightSurface,
    onSurface        = LightOnSurface,
    surfaceVariant   = LightSurface2,
    outline          = Color(0xFF8A7DAA)
)

@Composable
fun DotsBoxesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = DotsBoxesTypography,
        content     = content
    )
}

/** Colour helpers used in composables. */
val playerOneColor  @Composable get() = Player1Blue
val playerTwoColor  @Composable get() = Player2Orange
val playerOneBoxFill @Composable get() = Player1BlueLight.copy(alpha = 0.35f)
val playerTwoBoxFill @Composable get() = Player2OrangeLight.copy(alpha = 0.35f)
