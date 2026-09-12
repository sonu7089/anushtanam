package com.mantrasiddhi.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.presentation.navigation.*
import com.mantrasiddhi.presentation.screens.home.*
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.review.ReviewFixtures
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProgressiveEntryUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun unresolvedEntryDoesNotComposePersonalScreensOrEnableNavigation() {
        var composedPersonalScreen = false
        compose.setContent { AnushtanamTheme(false) {
            AppEntryBoundary(AuthGateState.Loading, {}, {}) {
                composedPersonalScreen = true
                Text("Previously opened profile")
            }
        } }
        compose.onNodeWithTag("perf_entry_shell").assertIsDisplayed()
        compose.onNodeWithTag("perf_home_ready").assertDoesNotExist()
        compose.onNodeWithText("Previously opened profile").assertDoesNotExist()
        compose.onNodeWithText("0").assertDoesNotExist()
        navigationDestinations(false).forEach {
            compose.onNodeWithTag("entry_tab_${it.screen.route}").assertIsNotEnabled()
        }
        listOf(R.string.title_practice, R.string.title_recitation).forEach {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(it)))
            compose.onNode(hasText(text(it)) and hasClickAction()).assertIsNotEnabled()
        }
        compose.runOnIdle { assertFalse(composedPersonalScreen) }
    }

    @Test fun recoveryKeepsRetryAndSwitchProfileAndBlocksPersonalContent() {
        val state = mutableStateOf<AuthGateState>(AuthGateState.NeedsRecovery)
        var retries = 0; var signOuts = 0
        compose.setContent { AnushtanamTheme(false) {
            AppEntryBoundary(state.value, { retries++; state.value = AuthGateState.Loading }, { signOuts++ }) {
                Text("Private data")
            }
        } }
        compose.onNodeWithText(text(R.string.settings_switch_profile)).performClick()
        compose.onNodeWithText(text(R.string.auth_retry_profile)).performClick()
        compose.onNodeWithTag("perf_entry_shell").assertIsDisplayed()
        compose.onNodeWithText("Private data").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries); assertEquals(1, signOuts) }
    }

    @Test fun entryRoutesOnlyAfterTheAccountDecision() {
        val state = mutableStateOf<AuthGateState>(AuthGateState.Loading)
        val routes = mutableListOf<String>()
        compose.setContent { AnushtanamTheme(false) {
            AppEntryRoute(state.value, { routes += "home" }, { routes += "welcome" },
                { routes += "child-setup" }, {}, {})
        } }
        compose.runOnIdle { assertTrue(routes.isEmpty()); state.value = AuthGateState.NeedsRecovery }
        compose.runOnIdle { assertTrue(routes.isEmpty()); state.value = AuthGateState.NeedsChildSetup }
        compose.runOnIdle { assertEquals(listOf("child-setup"), routes); state.value = AuthGateState.Unauthenticated }
        compose.runOnIdle { assertEquals(listOf("child-setup", "welcome"), routes); state.value = AuthGateState.Authenticated }
        compose.runOnIdle { assertEquals(listOf("child-setup", "welcome", "home"), routes) }
    }

    @Test fun verifiedHomeEnablesPracticeBeforeCountsAndGoalsFinish() {
        val entry = mutableStateOf<AuthGateState>(AuthGateState.Loading)
        val home = mutableStateOf(ReviewFixtures.home.copy(todayCount = 0, todayJapa = null,
            streakDays = 0, activeTarget = null, japaLoading = true, statsLoading = true, vowLoading = true))
        var practices = 0
        compose.setContent { AnushtanamTheme(false) {
            AppEntryBoundary(entry.value, {}, {}) {
                HomeContent(home.value, {}, { practices++ }, {}, {}, {})
            }
        } }
        compose.onNodeWithText("Namaste, Ananya").assertDoesNotExist()
        compose.runOnIdle { entry.value = AuthGateState.Authenticated }
        compose.onNodeWithText("Namaste, Ananya").assertIsDisplayed()
        compose.onNodeWithTag("home_today_loading").assertIsDisplayed()
        compose.onNodeWithTag("home_today_value").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.title_practice)))
        compose.onNodeWithText(text(R.string.title_practice)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, practices); home.value = home.value.copy(todayJapa = "37", japaLoading = false) }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithTag("home_today_value").assertTextEquals("37")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(text(R.string.home_active_target)))
        compose.onNodeWithText(text(R.string.no_active_targets)).assertDoesNotExist()
        // Account verification can restart without leaving the previous personal screen visible.
        compose.runOnIdle { entry.value = AuthGateState.Loading }
        compose.onNodeWithTag("perf_entry_shell").assertIsDisplayed()
        compose.onNodeWithText("Namaste, Ananya").assertDoesNotExist()
    }

    @Test fun compactLargeTextShellScrollsAndKeepsDisabledTouchTargets() {
        compose.setContent { AnushtanamTheme(true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                HomeEntryPlaceholder(Modifier.width(320.dp))
            }
        } }
        navigationDestinations(false).forEach {
            compose.onNodeWithTag("entry_tab_${it.screen.route}").assertIsNotEnabled()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
            text(R.string.home_opening_profile))).assertIsDisplayed()
    }

    @Test fun restoredAccountScreenRequiresVerifiedStartupOrAnInteractiveSession() {
        val state = mutableStateOf<AuthGateState>(AuthGateState.NeedsChildSetup)
        val hasSession = mutableStateOf(true)
        compose.setContent { AnushtanamTheme(false) {
            AccountScreenBoundary(accountScreensReady(state.value, hasSession.value)) { Text("Private screen") }
        } }
        compose.onNodeWithText("Private screen").assertDoesNotExist()
        compose.runOnIdle { state.value = AuthGateState.Unauthenticated; hasSession.value = false }
        compose.onNodeWithText("Private screen").assertDoesNotExist()
        compose.runOnIdle { hasSession.value = true }
        compose.onNodeWithText("Private screen").assertIsDisplayed()
        compose.runOnIdle { state.value = AuthGateState.NeedsRecovery }
        compose.onNodeWithText("Private screen").assertDoesNotExist()
        compose.runOnIdle { state.value = AuthGateState.Authenticated }
        compose.onNodeWithText("Private screen").assertIsDisplayed()
    }

    @Test fun homeRemainsMountedAndStationaryThroughEveryHandoffFrame() {
        val state = mutableStateOf<HomeUiState.Success?>(null)
        var mounts = 0
        var disposals = 0
        compose.setContent { AnushtanamTheme(false) {
            val nav = rememberNavController()
            Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                FloatingNavigationBar(navigationDestinations(state.value != null), Screen.Home.route, {}, enabled = state.value != null)
            }) { padding ->
                NavHost(nav, startDestination = Screen.Home.route,
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                    enterTransition = { enterScreenTransition() }, exitTransition = { exitScreenTransition() }) {
                    composable(Screen.Home.route) {
                        DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                        HomeContent(state.value, {}, {}, {}, {}, {})
                    }
                }
            }
        } }
        val tags = listOf("home_header", "home_hero", "home_practice_entries", "home_goal")
        val before = tags.associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        compose.mainClock.autoAdvance = false
        val pending = ReviewFixtures.home.copy(japaLoading = true, statsLoading = true, vowLoading = true, streakDays = 0)
        for (next in listOf(pending, ReviewFixtures.home.copy(todayJapa = "37"))) {
            compose.runOnIdle { state.value = next }
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                tags.forEach { tag ->
                    assertEquals("$tag moved during readiness", before.getValue(tag),
                        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot)
                }
            }
        }
        compose.onNodeWithTag("home_today_value").assertTextEquals("37")
        compose.runOnIdle { assertEquals(1, mounts); assertEquals(0, disposals) }
    }

    @Test fun scrolledLargeTextHomeKeepsItsPositionWhenValuesArrive() {
        val state = mutableStateOf<HomeUiState.Success?>(null)
        compose.setContent { AnushtanamTheme(true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                HomeContent(state.value, {}, {}, {}, {}, {}, Modifier.width(320.dp))
            }
        } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag("home_practice_entries"))
        val before = compose.onNodeWithTag("home_practice_entries").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state.value = ReviewFixtures.home }
        assertEquals(before, compose.onNodeWithTag("home_practice_entries").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText(text(R.string.title_practice)).assertIsEnabled()
    }
}
