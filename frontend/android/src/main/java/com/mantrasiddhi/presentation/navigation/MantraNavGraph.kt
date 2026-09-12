/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.navigation
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mantrasiddhi.domain.model.ProfileType
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
import com.mantrasiddhi.presentation.screens.guide.PracticeGuideViewModel
import com.mantrasiddhi.presentation.screens.guide.PracticeGuideSheet
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
    Screen.Family.route to 5,
    Screen.ChildProgress.route to 9,
    Screen.Sandhya.route to 6,
    Screen.Settings.route to 7,
    Screen.SandhyaSettings.route to 8,
    Screen.Targets.route to 8,
    // Purashcharana is a detail push from Home, like Targets, so it slides in from the
    // right of everything the bottom bar can reach.
    Screen.Purashcharana.route to 9
)

/** Tab transitions share the selection pill's duration; detail pushes stay shorter. */
private val BottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.Practice.route,
    Screen.Recitation.route,
    Screen.Progress.route,
    Screen.Family.route,
    Screen.Settings.route
)

private fun isTabSwitch(initialRoute: String?, targetRoute: String?): Boolean =
    initialRoute in BottomNavRoutes && targetRoute in BottomNavRoutes

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.enterScreenTransition(pop: Boolean = false): EnterTransition {
    val tab = isTabSwitch(initialState.destination.route, targetState.destination.route)
    val forward = (NavigationRouteOrder[targetState.destination.route] ?: 0) >
        (NavigationRouteOrder[initialState.destination.route] ?: 0)
    val left = if (tab) forward else !pop && forward
    return slideIntoContainer(
        if (left) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(if (tab) 320 else 220, easing = EmphasizedEasing)
    )
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.exitScreenTransition(pop: Boolean = false): ExitTransition {
    val tab = isTabSwitch(initialState.destination.route, targetState.destination.route)
    val forward = (NavigationRouteOrder[targetState.destination.route] ?: 0) >
        (NavigationRouteOrder[initialState.destination.route] ?: 0)
    val left = if (tab) forward else !pop && forward
    return slideOutOfContainer(
        if (left) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(if (tab) 320 else 220, easing = EmphasizedEasing)
    )
}

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun MantraApp(
    mainViewModel: MainViewModel,
    navController: NavHostController = rememberNavController(),
    reminderTarget: SandhyaReminderTarget? = null,
    onReminderHandled: () -> Unit = {},
    messageId: String? = null,
    onMessageHandled: () -> Unit = {},
    homeNotificationId: String? = null,
    onHomeNotificationHandled: () -> Unit = {},
    onStartupReady: () -> Unit = {}
) {
    val currentUser by mainViewModel.currentUser.collectAsState(initial = null)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // Activity scope shares the open guide across every entry point.
    val guideViewModel: PracticeGuideViewModel = hiltViewModel()
    val guide by guideViewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val authRoutes = setOf(Screen.AuthGate.route, Screen.Welcome.route, Screen.ParentAuth.route, Screen.ChildAuth.route, Screen.Login.route)
    LaunchedEffect(homeNotificationId, currentRoute) {
        if (homeNotificationId != null && currentRoute != null && currentRoute !in authRoutes) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
            onHomeNotificationHandled()
        }
    }
    LaunchedEffect(messageId, currentRoute) {
        if (messageId != null && currentRoute != null && currentRoute !in authRoutes) {
            navController.navigate("messages/$messageId") { launchSingleTop = true }
            onMessageHandled()
        }
    }
    LaunchedEffect(reminderTarget, currentUser?.id, currentRoute, guide.profileId) {
        val target = reminderTarget ?: return@LaunchedEffect
        val user = currentUser ?: return@LaunchedEffect
        if (currentRoute == null || currentRoute in authRoutes) return@LaunchedEffect
        if (target.userId != user.id) {
            android.widget.Toast.makeText(context, com.mantrasiddhi.resources.R.string.reminder_wrong_profile, android.widget.Toast.LENGTH_LONG).show()
            onReminderHandled()
        } else if (target.stage == com.mantrasiddhi.domain.model.SandhyaReminderStage.PREPARATION) {
            if (guide.profileId != user.id) return@LaunchedEffect
            guideViewModel.open()
            onReminderHandled()
        } else if (currentRoute != Screen.Sandhya.route) {
            navController.navigate(Screen.Sandhya.route) { launchSingleTop = true }
        }
    }


    // Detail screens reached from Home (not bottom-nav tabs) hide the bar so it never
    // shows with nothing selected. Auth screens hide it too.
    val detailRoutes = setOf(
        "messages", "messages/{contentId}",
        Screen.Login.route,
        Screen.AuthGate.route,
        Screen.Welcome.route,
        Screen.ParentAuth.route,
        Screen.ChildAuth.route,
        Screen.ChildProgress.route,
        Screen.Sandhya.route,
        Screen.SandhyaSettings.route,
        Screen.Targets.route,
        Screen.Purashcharana.route
    )
    val showBottomBar = currentRoute != null && currentRoute !in detailRoutes
    val showFamily = currentUser?.profileType?.let { it != ProfileType.INDIVIDUAL } == true

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                MantraBottomBar(
                    navController = navController,
                    showFamily = showFamily
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.AuthGate.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            // Opaque horizontal motion avoids exposing the scaffold during a crossfade.
            enterTransition = { enterScreenTransition() },
            exitTransition = { exitScreenTransition() },
            popEnterTransition = { enterScreenTransition(pop = true) },
            popExitTransition = { exitScreenTransition(pop = true) }
        ) {
            // Entry gate: decides Auth vs Home from the persisted Firebase session.
            composable(Screen.AuthGate.route) {
                AuthGate(
                    mainViewModel = mainViewModel,
                    onChildSetup = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(Screen.AuthGate.route) { inclusive = true }
                        }
                        navController.navigate(Screen.ChildAuth.route)
                    },
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
                    modifier = Modifier.semantics { testTagsAsResourceId = true }
                        .testTag("perf_welcome_ready").onGloballyPositioned { onStartupReady() },
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
                    onOpenChildProgress = if (currentUser?.profileType == ProfileType.PARENT) {
                        { navController.navigate(Screen.ChildProgress.route) }
                    } else null
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
                    onUsable = onStartupReady,
                    viewModel = homeViewModel,
                    onNavigateToRecitation = { navController.navigate(Screen.Recitation.route) },
                    onNavigateToPractice = { navController.navigate(Screen.Practice.route) },
                    onNavigateToTargets = { navController.navigate(Screen.Targets.route) },
                    onNavigateToSandhya = { navController.navigate(Screen.Sandhya.route) },
                    onNavigateToPurashcharana = { navController.navigate(Screen.Purashcharana.route) },
                    onOpenGuide = { guideViewModel.open() },
                    onMessages = { navController.navigate("messages") },
                    onOpenReflection = { navController.navigate("messages/$it") }
                )
            }

            composable("messages") {
                com.mantrasiddhi.presentation.screens.messages.MessagesScreen(
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate("messages/$it") })
            }
            composable("messages/{contentId}") { entry ->
                com.mantrasiddhi.presentation.screens.messages.MessagesScreen(
                    contentId = entry.arguments?.getString("contentId"),
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate("messages/$it") })
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
                TargetsScreen(
                    onBack = { navController.popBackStack() },
                    onContinueJapa = {
                        navController.navigate(Screen.Recitation.route) {
                            popUpTo(Screen.Targets.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.ChildProgress.route) {
                ChildProgressScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Sandhya.route) {
                SandhyaScreen(viewModel = hiltViewModel(), onOpenGuide = { guideViewModel.open() },
                    onOpenSettings = { navController.navigate(Screen.SandhyaSettings.route) { launchSingleTop = true } },
                    reminderTarget = reminderTarget?.takeIf { it.userId == currentUser?.id && it.stage == com.mantrasiddhi.domain.model.SandhyaReminderStage.EVENT },
                    onReminderHandled = onReminderHandled)
            }

            composable(Screen.Purashcharana.route) {
                PurashcharanaScreen(
                    viewModel = hiltViewModel(),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.SandhyaSettings.route) {
                com.mantrasiddhi.presentation.screens.settings.SandhyaSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenSandhyaSettings = { navController.navigate(Screen.SandhyaSettings.route) { launchSingleTop = true } },
                    onLogout = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
    if (guide.visible) {
        key(guide.profileId) {
            PracticeGuideSheet(onDismiss = guideViewModel::dismiss, onPractice = {
                guideViewModel.dismiss()
                navController.navigate(Screen.Practice.route) { launchSingleTop = true }
            })
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
    onUnauthenticated: () -> Unit,
    onChildSetup: () -> Unit
) {
    val state by mainViewModel.authGateState.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            is AuthGateState.Authenticated -> onAuthenticated()
            is AuthGateState.Unauthenticated -> onUnauthenticated()
            is AuthGateState.Loading -> Unit
            is AuthGateState.NeedsRecovery -> Unit
            is AuthGateState.NeedsChildSetup -> onChildSetup()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (state == AuthGateState.NeedsRecovery) {
            androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.mantrasiddhi.resources.R.string.auth_profile_retry))
                androidx.compose.material3.Button(mainViewModel::retryOpeningProfile) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.mantrasiddhi.resources.R.string.auth_retry_profile))
                }
                androidx.compose.material3.TextButton(mainViewModel::signOutFromRecovery) {
                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(com.mantrasiddhi.resources.R.string.settings_switch_profile))
                }
            }
        } else CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
