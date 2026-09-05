/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.presentation.AuthGateState
import com.mantrasiddhi.presentation.MainViewModel
import com.mantrasiddhi.presentation.components.VoiceModelGate
import com.mantrasiddhi.presentation.screens.auth.AuthViewModel
import com.mantrasiddhi.presentation.screens.auth.ChildAuthScreen
import com.mantrasiddhi.presentation.screens.auth.ParentAuthScreen
import com.mantrasiddhi.presentation.screens.auth.WelcomeScreen
import com.mantrasiddhi.presentation.screens.childprogress.ChildProgressScreen
import com.mantrasiddhi.presentation.screens.family.FamilyScreen
import com.mantrasiddhi.presentation.screens.family.FamilyViewModel
import com.mantrasiddhi.presentation.screens.home.HomeScreen
import com.mantrasiddhi.presentation.screens.home.HomeViewModel
import com.mantrasiddhi.presentation.screens.login.LoginScreen
import com.mantrasiddhi.presentation.screens.login.LoginViewModel
import com.mantrasiddhi.presentation.screens.practice.PracticeScreen
import com.mantrasiddhi.presentation.screens.progress.ProgressScreen
import com.mantrasiddhi.presentation.screens.purashcharana.PurashcharanaScreen
import com.mantrasiddhi.presentation.screens.recitation.RecitationScreen
import com.mantrasiddhi.presentation.screens.sandhya.SandhyaScreen
import com.mantrasiddhi.presentation.screens.settings.SettingsScreen
import com.mantrasiddhi.presentation.screens.settings.SettingsViewModel
import com.mantrasiddhi.presentation.screens.targets.TargetsScreen
import com.mantrasiddhi.presentation.theme.EmphasizedEasing

private val NavigationRouteOrder = mapOf(
    Screen.Login.route to 0,
    Screen.Home.route to 1,
    Screen.Practice.route to 2,
    Screen.Recitation.route to 3,
    Screen.Progress.route to 4,
    Screen.ChildProgress.route to 5,
    Screen.Sandhya.route to 6,
    Screen.Settings.route to 7,
    Screen.Targets.route to 8,
    // Purashcharana is a detail push from Home, like Targets, so it slides in from the
    // right of everything the bottom bar can reach.
    Screen.Purashcharana.route to 9
)

/**
 * Bottom-nav tab destinations switch instantly (no animation): any transition between
 * two always-full-screen tabs reads as a flash/blink, especially on mid-range devices.
 * Only pushes to detail screens (Family, Targets, Sandhya, auth) keep a short slide.
 */
private val BottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.Practice.route,
    Screen.Recitation.route,
    Screen.Progress.route,
    Screen.ChildProgress.route,
    Screen.Settings.route
)

private fun isTabSwitch(initialRoute: String?, targetRoute: String?): Boolean =
    initialRoute in BottomNavRoutes && targetRoute in BottomNavRoutes

@Composable
fun MantraApp(
    mainViewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val currentUser by mainViewModel.currentUser.collectAsState(initial = null)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Detail screens reached from Home (not bottom-nav tabs) hide the bar so it never
    // shows with nothing selected. Auth screens hide it too.
    val detailRoutes = setOf(
        Screen.Login.route,
        Screen.AuthGate.route,
        Screen.Welcome.route,
        Screen.ParentAuth.route,
        Screen.ChildAuth.route,
        Screen.Family.route,
        Screen.Sandhya.route,
        Screen.Targets.route,
        Screen.Purashcharana.route
    )
    val showBottomBar = currentRoute != null && currentRoute !in detailRoutes
    val isParent = currentUser?.profileType == ProfileType.PARENT

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
            startDestination = Screen.AuthGate.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            // Bottom-nav tab switches are INSTANT (EnterTransition.None): any animation
            // between two full-screen tabs reads as a blink. Detail pushes (Family,
            // Targets, Sandhya, auth) keep a short opaque slide — no fade, which is what
            // previously flashed the background through the crossfade.
            enterTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    EnterTransition.None
                } else {
                    val initialOrder = NavigationRouteOrder[initialState.destination.route] ?: 0
                    val targetOrder = NavigationRouteOrder[targetState.destination.route] ?: 0
                    val direction = if (targetOrder > initialOrder) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    slideIntoContainer(direction, animationSpec = tween(220, easing = EmphasizedEasing))
                }
            },
            exitTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    ExitTransition.None
                } else {
                    val initialOrder = NavigationRouteOrder[initialState.destination.route] ?: 0
                    val targetOrder = NavigationRouteOrder[targetState.destination.route] ?: 0
                    val direction = if (targetOrder > initialOrder) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    slideOutOfContainer(direction, animationSpec = tween(220, easing = EmphasizedEasing))
                }
            },
            popEnterTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    EnterTransition.None
                } else {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(220, easing = EmphasizedEasing)
                    )
                }
            },
            popExitTransition = {
                if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                    ExitTransition.None
                } else {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(220, easing = EmphasizedEasing)
                    )
                }
            }
        ) {
            // Entry gate: decides Auth vs Home from the persisted Firebase session.
            composable(Screen.AuthGate.route) {
                AuthGate(
                    mainViewModel = mainViewModel,
                    onAuthenticated = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.AuthGate.route) { inclusive = true }
                        }
                    },
                    onUnauthenticated = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(Screen.AuthGate.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    onSelectParent = { navController.navigate(Screen.ParentAuth.route) },
                    onSelectChild = { navController.navigate(Screen.ChildAuth.route) }
                )
            }

            composable(Screen.ParentAuth.route) {
                val authViewModel: AuthViewModel = hiltViewModel()
                ParentAuthScreen(
                    viewModel = authViewModel,
                    onBack = { navController.popBackStack() },
                    onAuthenticated = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.ChildAuth.route) {
                val authViewModel: AuthViewModel = hiltViewModel()
                ChildAuthScreen(
                    viewModel = authViewModel,
                    onBack = { navController.popBackStack() },
                    onAuthenticated = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Family.route) {
                val familyViewModel: FamilyViewModel = hiltViewModel()
                FamilyScreen(
                    viewModel = familyViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

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
                    onNavigateToChildProgress = { navController.navigate(Screen.ChildProgress.route) },
                    onNavigateToSandhya = { navController.navigate(Screen.Sandhya.route) },
                    onNavigateToFamily = { navController.navigate(Screen.Family.route) },
                    onNavigateToPurashcharana = { navController.navigate(Screen.Purashcharana.route) }
                )
            }

            composable(Screen.Practice.route) {
                VoiceModelGate {
                    PracticeScreen()
                }
            }

            composable(Screen.Recitation.route) {
                VoiceModelGate {
                    RecitationScreen()
                }
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

            composable(Screen.Sandhya.route) {
                SandhyaScreen(viewModel = hiltViewModel())
            }

            composable(Screen.Purashcharana.route) {
                PurashcharanaScreen(
                    viewModel = hiltViewModel(),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onLogout = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Splash-like gate shown as the app's start destination. It observes the persisted
 * Firebase auth state (resolved by [MainViewModel]) and routes to Home when a session
 * exists, or to the Welcome/auth flow otherwise.
 */
@Composable
private fun AuthGate(
    mainViewModel: MainViewModel,
    onAuthenticated: () -> Unit,
    onUnauthenticated: () -> Unit
) {
    val state by mainViewModel.authGateState.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            is AuthGateState.Authenticated -> onAuthenticated()
            is AuthGateState.Unauthenticated -> onUnauthenticated()
            is AuthGateState.Loading -> Unit
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

private data class NavItem(
    val screen: Screen,
    val activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun MantraBottomBar(
    navController: NavHostController,
    isParent: Boolean
) {
    val items = androidx.compose.runtime.remember(isParent) {
        buildList {
            add(NavItem(Screen.Home, Icons.Default.Home, Icons.Outlined.Home))
            add(NavItem(Screen.Practice, Icons.Default.Edit, Icons.Outlined.Edit))
            add(NavItem(Screen.Recitation, Icons.Default.PlayArrow, Icons.Outlined.PlayArrow))
            add(NavItem(Screen.Progress, Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Outlined.List))
            if (isParent) {
                add(NavItem(Screen.ChildProgress, Icons.Default.Face, Icons.Outlined.Face))
            }
            add(NavItem(Screen.Settings, Icons.Default.Settings, Icons.Outlined.Settings))
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val compact = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.2f
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        items.forEach { navItem ->
            val isSelected = currentRoute == navItem.screen.route
            val label = stringResource(id = navItem.screen.labelResId)
            val navigate = {
                if (currentRoute != navItem.screen.route) {
                    navController.navigate(navItem.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            if (compact) {
                // Six parent destinations retain full touch targets when labels would be squeezed.
                Box(Modifier.weight(1f).height(80.dp), contentAlignment = Alignment.Center) {
                    IconButton(onClick = navigate,
                        modifier = Modifier.size(48.dp).semantics { selected = isSelected },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else androidx.compose.ui.graphics.Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Icon(if (isSelected) navItem.activeIcon else navItem.inactiveIcon, label)
                    }
                }
            } else {
            NavigationBarItem(
                icon = { 
                    Icon(
                        imageVector = if (isSelected) navItem.activeIcon else navItem.inactiveIcon, 
                        contentDescription = label
                    ) 
                },
                label = { Text(text = label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                selected = isSelected,
                onClick = navigate
            )
            }
        }
    }
}
