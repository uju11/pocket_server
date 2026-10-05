package com.remotemedia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TacticalColorScheme = darkColorScheme(
    primary = ElectricTeal,
    secondary = TacticalGreen,
    tertiary = TacticalOrange,
    background = TacticalBg,
    surface = TacticalSurface,
    onPrimary = TacticalBg,
    onBackground = TacticalText,
    onSurface = TacticalText
)

@Composable
fun RemoteMediaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TacticalColorScheme,
        content = content
    )
}
