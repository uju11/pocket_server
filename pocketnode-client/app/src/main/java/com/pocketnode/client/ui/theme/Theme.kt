package com.pocketnode.client.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = TacticalRed,
    secondary = ElectricTeal,
    tertiary = TacticalYellow,
    background = TacticalBg,
    surface = TacticalCanvas,
    onPrimary = TacticalTextWhite,
    onSecondary = TacticalBg,
    onTertiary = TacticalBg,
    onBackground = TacticalText,
    onSurface = TacticalText
)

@Composable
fun PocketNodeClientTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = TacticalBg.toArgb()
            window.navigationBarColor = TacticalBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
