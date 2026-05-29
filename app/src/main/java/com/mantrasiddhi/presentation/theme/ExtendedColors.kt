/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended color roles not present in MaterialTheme.colorScheme.
 * MD3 supports custom colors via harmonization with the seed.
 */
data class ExtendedColorScheme(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

val LightExtendedColors = ExtendedColorScheme(
    success = Color(0xFF386A20),          // Generated from green seed harmonized with #FF6F00
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFB7F397),
    onSuccessContainer = Color(0xFF042100),
    warning = Color(0xFF7C5800),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFFDEA6),
    onWarningContainer = Color(0xFF271900),
)

val DarkExtendedColors = ExtendedColorScheme(
    success = Color(0xFF9CD67E),
    onSuccess = Color(0xFF0D3900),
    successContainer = Color(0xFF205107),
    onSuccessContainer = Color(0xFFB7F397),
    warning = Color(0xFFF5BE48),
    onWarning = Color(0xFF412D00),
    warningContainer = Color(0xFF5E4200),
    onWarningContainer = Color(0xFFFFDEA6),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
