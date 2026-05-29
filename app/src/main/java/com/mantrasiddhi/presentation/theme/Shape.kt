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
    extraSmall = RoundedCornerShape(4.dp),   // Chips, snackbars
    small = RoundedCornerShape(8.dp),        // Text fields, menus
    medium = RoundedCornerShape(12.dp),      // Cards
    large = RoundedCornerShape(16.dp),       // FABs, nav drawer
    extraLarge = RoundedCornerShape(28.dp),  // Dialogs, bottom sheets
)
