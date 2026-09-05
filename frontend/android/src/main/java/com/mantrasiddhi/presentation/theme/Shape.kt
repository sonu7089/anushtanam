/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AnushtanamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Chips, snackbars
    small = RoundedCornerShape(12.dp),        // Text fields, menus
    medium = RoundedCornerShape(20.dp),      // Cards
    large = RoundedCornerShape(24.dp),       // FABs, nav drawer
    extraLarge = RoundedCornerShape(32.dp),  // Dialogs, bottom sheets
)

val ChartBarShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
