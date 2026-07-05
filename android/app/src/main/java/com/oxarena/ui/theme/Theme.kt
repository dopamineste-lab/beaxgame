package com.oxarena.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val OxDarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = TextPrimary,
    primaryContainer = VioletDim,
    secondary = Mint,
    onSecondary = Background,
    secondaryContainer = MintDim,
    tertiary = Amber,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundElevated,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    outline = SurfaceStroke,
)

/**
 * The app is dark-first and premium; we intentionally do NOT use dynamic color
 * so the brand palette is consistent across devices. [darkTheme] is accepted for
 * completeness but the scheme is always the OX dark scheme.
 */
@Composable
fun OxArenaTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = OxDarkColors,
        typography = AppTypography,
        content = content,
    )
}
