/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.navigation

import com.mantrasiddhi.resources.R

sealed class Screen(val route: String, val labelResId: Int = 0) {
    data object Login : Screen("login")
    data object AuthGate : Screen("auth_gate")
    data object Welcome : Screen("welcome")
    data object ParentAuth : Screen("parent_auth")
    data object ChildAuth : Screen("child_auth")
    data object Family : Screen("family", R.string.family_title)
    data object Home : Screen("home", R.string.title_home)
    data object Practice : Screen("practice", R.string.title_practice)
    data object Recitation : Screen("recitation", R.string.title_recitation)
    data object Progress : Screen("progress", R.string.title_progress)
    data object Targets : Screen("targets", R.string.title_targets)
    data object ChildProgress : Screen("child_progress", R.string.title_child_progress)
    data object Sandhya : Screen("sandhya", R.string.title_sandhya)
    data object Purashcharana : Screen("purashcharana", R.string.title_purashcharana)
    data object SandhyaSettings : Screen("settings/sandhya", R.string.sandhya_settings_title)
    data object Settings : Screen("settings", R.string.title_settings)
}
