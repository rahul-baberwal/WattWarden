package com.rahulbaberwal.wattwarden.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object WW {
    val Navy = Color(0xFF0B1220)
    val Surface = Color(0xFF12233F)
    val SurfaceHi = Color(0xFF1C3358)
    val Mint = Color(0xFF19E3B1)
    val Amber = Color(0xFFFFB020)
    val Red = Color(0xFFFF5A5F)
    val Text = Color(0xFFE8EEF7)
    val Muted = Color(0xFF8FA3BF)
}

private val scheme = darkColorScheme(
    primary = WW.Mint,
    onPrimary = WW.Navy,
    secondary = WW.Amber,
    onSecondary = WW.Navy,
    background = WW.Navy,
    onBackground = WW.Text,
    surface = WW.Surface,
    onSurface = WW.Text,
    surfaceVariant = WW.SurfaceHi,
    onSurfaceVariant = WW.Muted,
    error = WW.Red,
    outline = WW.Muted,
    secondaryContainer = WW.SurfaceHi,
    onSecondaryContainer = WW.Mint,
)

@Composable
fun WattWardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
