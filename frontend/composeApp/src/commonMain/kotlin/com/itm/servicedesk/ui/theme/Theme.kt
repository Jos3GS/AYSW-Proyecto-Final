package com.itm.servicedesk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ItmBluePrimary = Color(0xFF0F4C81)
private val ItmBlueOnPrimary = Color(0xFFFFFFFF)
private val ItmBlueContainer = Color(0xFFD6E4F5)
private val ItmBlueOnContainer = Color(0xFF0A2E50)
private val ItmTealSecondary = Color(0xFF006D77)
private val ItmTealOnSecondary = Color(0xFFFFFFFF)
private val ItmBackground = Color(0xFFF7F9FC)
private val ItmSurface = Color(0xFFFFFFFF)
private val ItmOnSurface = Color(0xFF1A1C1E)
private val ItmError = Color(0xFFB3261E)
private val ItmOnError = Color(0xFFFFFFFF)
private val ItmErrorContainer = Color(0xFFF9DEDC)
private val ItmOnErrorContainer = Color(0xFF410E0B)

private val ServiceDeskLightColorScheme =
    lightColorScheme(
        primary = ItmBluePrimary,
        onPrimary = ItmBlueOnPrimary,
        primaryContainer = ItmBlueContainer,
        onPrimaryContainer = ItmBlueOnContainer,
        secondary = ItmTealSecondary,
        onSecondary = ItmTealOnSecondary,
        background = ItmBackground,
        surface = ItmSurface,
        onSurface = ItmOnSurface,
        error = ItmError,
        onError = ItmOnError,
        errorContainer = ItmErrorContainer,
        onErrorContainer = ItmOnErrorContainer,
    )

@Composable
fun ServiceDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ServiceDeskLightColorScheme,
        content = content,
    )
}
