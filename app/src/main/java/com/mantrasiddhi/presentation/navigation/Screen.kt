/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Practice : Screen("practice")
    data object Recitation : Screen("recitation")
    data object Progress : Screen("progress")
    data object Targets : Screen("targets")
    data object ChildProgress : Screen("child_progress")
    data object Settings : Screen("settings")
}
