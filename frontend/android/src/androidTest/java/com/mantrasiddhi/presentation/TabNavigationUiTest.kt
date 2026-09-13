package com.mantrasiddhi.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mantrasiddhi.presentation.navigation.*
import com.mantrasiddhi.presentation.screens.settings.SettingsContent
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TabNavigationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun peerTabsArePlacedDirectlyWhileDetailPushesStillSlide() {
        compose.setContent { AnushtanamTheme(false) {
            val controller = rememberNavController()
            Column {
                NavHost(controller, startDestination = Screen.Home.route, modifier = Modifier.weight(1f),
                    enterTransition = { enterScreenTransition() }, exitTransition = { exitScreenTransition() },
                    popEnterTransition = { enterScreenTransition(pop = true) },
                    popExitTransition = { exitScreenTransition(pop = true) }) {
                    composable(Screen.Home.route) { Box(Modifier.fillMaxSize().testTag("home_content")) }
                    composable(Screen.Practice.route) {
                        Box(Modifier.fillMaxSize().testTag("practice_content")) {
                            androidx.compose.material3.Button(onClick = { controller.navigate(Screen.Targets.route) }) {
                                Text("Open detail")
                            }
                        }
                    }
                    composable(Screen.Targets.route) { Box(Modifier.fillMaxSize().testTag("detail_content")) }
                }
                MantraBottomBar(controller, showFamily = false)
            }
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("perf_tab_practice").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("practice_content").assertLeftPositionInRootIsEqualTo(0.dp)
        compose.onNodeWithTag("home_content").assertDoesNotExist()
        compose.onNodeWithText("Open detail").performClick()
        compose.mainClock.advanceTimeBy(64)
        assertTrue(compose.onNodeWithTag("detail_content").fetchSemanticsNode().positionInRoot.x > 0f)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("detail_content").assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test fun changingTabsUpdatesRouteObserversWithoutRecomposingTheGraphOwner() {
        var ownerCompositions = 0
        compose.setContent { AnushtanamTheme(false) {
            val controller = rememberNavController()
            SideEffect { ownerCompositions++ }
            Column {
                ObserveNavigationRoute(controller) { Text("route: $it") }
                NavHost(controller, startDestination = Screen.Home.route, modifier = Modifier.weight(1f)) {
                    composable(Screen.Home.route) { Text("Home content") }
                    composable(Screen.Practice.route) { Text("Practice content") }
                }
                MantraBottomBar(controller, showFamily = false)
            }
        } }
        compose.waitForIdle()
        var before = 0
        compose.runOnIdle { before = ownerCompositions }
        compose.onNodeWithTag("perf_tab_practice").performClick().assertIsSelected()
        compose.onNodeWithText("route: practice").assertIsDisplayed()
        compose.onNodeWithTag("perf_tab_home").performClick().assertIsSelected()
        compose.onNodeWithText("route: home").assertIsDisplayed()
        compose.runOnIdle { assertEquals(before, ownerCompositions) }
    }

    @Test fun navigationAdaptsToWidthAndFontChangesWithRtlAndRapidSelection() {
        var width by mutableStateOf(400.dp)
        var fontScale by mutableFloatStateOf(1f)
        var route by mutableStateOf(Screen.Home.route)
        val destinations = navigationDestinations(true)
        compose.setContent { AnushtanamTheme(false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                FloatingNavigationBar(destinations, route, { route = it.route }, Modifier.width(width))
            }
        } }
        compose.onNodeWithTag("perf_tab_home").assertHeightIsEqualTo(64.dp)
        compose.runOnIdle { width = 320.dp }
        compose.onNodeWithTag("perf_tab_home").assertHeightIsEqualTo(56.dp).assertWidthIsAtLeast(48.dp)
        compose.runOnIdle { width = 400.dp; fontScale = 1.6f }
        compose.onNodeWithTag("perf_tab_home").assertHeightIsEqualTo(56.dp)
        compose.mainClock.autoAdvance = false
        for (destination in destinations.reversed()) {
            compose.onNodeWithTag("perf_tab_${destination.screen.route}").performClick()
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("perf_tab_${destination.screen.route}").assertIsSelected()
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(Screen.Home.route, route) }
        compose.onAllNodes(isSelected()).assertCountEquals(1)
    }

    @Test fun settingsDefersOffscreenContentAndRestoresItsScrollPosition() {
        var backupCompositions = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AnushtanamTheme(false) {
            Box(Modifier.height(360.dp)) {
                SettingsContent(null, "LIGHT", "BOTH", {}, {}, {}, backupContent = {
                    SideEffect { backupCompositions++ }
                    Text("Navigation backup fixture")
                })
            }
        } }
        compose.runOnIdle { assertEquals(0, backupCompositions) }
        compose.onNodeWithTag("settings_sections").performScrollToNode(hasText("Navigation backup fixture"))
        compose.onNodeWithText("Navigation backup fixture").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Navigation backup fixture").assertIsDisplayed()
    }
}
