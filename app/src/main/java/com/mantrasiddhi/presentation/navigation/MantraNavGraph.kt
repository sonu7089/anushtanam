/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.presentation.MainViewModel
import com.mantrasiddhi.presentation.screens.childprogress.ChildProgressScreen
import com.mantrasiddhi.presentation.screens.home.HomeScreen
import com.mantrasiddhi.presentation.screens.home.HomeViewModel
import com.mantrasiddhi.presentation.screens.login.LoginScreen
import com.mantrasiddhi.presentation.screens.login.LoginViewModel
import com.mantrasiddhi.presentation.screens.practice.PracticeScreen
import com.mantrasiddhi.presentation.screens.progress.ProgressScreen
import com.mantrasiddhi.presentation.screens.recitation.RecitationScreen
import com.mantrasiddhi.presentation.screens.settings.SettingsScreen
import com.mantrasiddhi.presentation.screens.settings.SettingsViewModel
import com.mantrasiddhi.presentation.screens.targets.TargetsScreen

@Composable
fun MantraApp(
    mainViewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val currentUser by mainViewModel.currentUser.collectAsState(initial = null)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute != Screen.Login.route && currentRoute != null
    val isParent = currentUser?.profileType == ProfileType.PARENT

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                MantraBottomBar(
                    navController = navController,
                    isParent = isParent
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                val loginViewModel: LoginViewModel = hiltViewModel()
                LoginScreen(
                    viewModel = loginViewModel,
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToRecitation = { navController.navigate(Screen.Recitation.route) },
                    onNavigateToPractice = { navController.navigate(Screen.Practice.route) },
                    onNavigateToTargets = { navController.navigate(Screen.Targets.route) },
                    onNavigateToChildProgress = { navController.navigate(Screen.ChildProgress.route) }
                )
            }

            composable(Screen.Practice.route) {
                PracticeScreen()
            }

            composable(Screen.Recitation.route) {
                RecitationScreen()
            }

            composable(Screen.Progress.route) {
                ProgressScreen()
            }

            composable(Screen.Targets.route) {
                TargetsScreen()
            }

            composable(Screen.ChildProgress.route) {
                ChildProgressScreen()
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MantraBottomBar(
    navController: NavHostController,
    isParent: Boolean
) {
    val items = mutableListOf(
        Screen.Home to Pair("Home", Icons.Default.Home),
        Screen.Practice to Pair("Practice", Icons.Default.Edit),
        Screen.Recitation to Pair("Recite", Icons.Default.PlayArrow),
        Screen.Progress to Pair("Progress", Icons.Default.List),
        Screen.Settings to Pair("Settings", Icons.Default.Settings)
    )

    if (isParent) {
        // Insert Children tab before Settings
        items.add(items.size - 1, Screen.ChildProgress to Pair("Children", Icons.Default.Face))
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        items.forEach { (screen, info) ->
            val (label, icon) = info
            NavigationBarItem(
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(text = label) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
