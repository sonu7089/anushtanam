package com.mantrasiddhi.presentation

import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

/**
 * The app supports API 33+, so it does not need AndroidX's pre-30 cutout/bar-color
 * compatibility paths. The theme supplies transparent bars (the status-bar color
 * only on API 33/34). Compose owns the safe drawing insets and icon contrast.
 */
internal fun ComponentActivity.configureAppWindow() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
}
