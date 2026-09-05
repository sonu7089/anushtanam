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
    success = Color(0xFF406850),          // Botanical green for success
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFE0EFE3),
    onSuccessContainer = Color(0xFF183D25),
    warning = Color(0xFF7D632B),
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFF5EDD8),
    onWarningContainer = Color(0xFF41351C),
)

val DarkExtendedColors = ExtendedColorScheme(
    success = Color(0xFFA9CCB1),
    onSuccess = Color(0xFF1E3927),
    successContainer = Color(0xFF324D39),
    onSuccessContainer = Color(0xFFE0EFE3),
    warning = Color(0xFFDDCA98),
    onWarning = Color(0xFF40351D),
    warningContainer = Color(0xFF53462A),
    onWarningContainer = Color(0xFFF5EDD8),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
