package com.mantrasiddhi.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.domain.model.TrendPoint
import com.mantrasiddhi.presentation.navigation.*
import com.mantrasiddhi.presentation.screens.sandhya.WeeklyTrend
import com.mantrasiddhi.presentation.screens.sandhya.MetricsSection
import org.junit.Assert.assertTrue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.components.ProgressRing
import com.mantrasiddhi.presentation.screens.auth.WelcomeScreen
import com.mantrasiddhi.presentation.screens.home.HomeContent
import com.mantrasiddhi.presentation.screens.settings.SettingsContent
import com.mantrasiddhi.presentation.screens.practice.components.MicButton
import com.mantrasiddhi.presentation.screens.practice.components.MantraDisplay
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.review.ReviewFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Regression coverage for UI callbacks and accessibility after the presentation rewrite. */
class LotusUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun welcomeKeepsBothRoleActions() {
        var parent = 0; var child = 0
        compose.setContent { AnushtanamTheme(false) { WelcomeScreen({ parent++ }, { child++ }) } }
        compose.onNodeWithText(text(R.string.auth_role_parent)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.auth_role_child)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, parent); assertEquals(1, child) }
    }

    @Test fun homeKeepsEveryDestinationReachable() {
        val clicks = mutableListOf<String>()
        compose.setContent { AnushtanamTheme(false) {
            HomeContent(ReviewFixtures.home, { clicks += "japa" }, { clicks += "practice" },
                { clicks += "targets" }, { clicks += "children" }, { clicks += "sandhya" },
                { clicks += "family" }, { clicks += "purashcharana" })
        } }
        listOf(R.string.title_practice, R.string.title_recitation, R.string.home_active_target,
            R.string.home_purashcharana_title, R.string.home_sandhya_title, R.string.home_family_title).forEach {
            compose.onNodeWithText(text(it)).performScrollTo().performClick()
        }
        compose.onNodeWithText("Aarav").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("practice", "japa", "targets", "purashcharana", "sandhya", "family", "children"), clicks) }
    }

    @Test fun preferencesKeepTheirStoredValuesAndSelectionSemantics() {
        val theme = mutableStateOf("LIGHT")
        val language = mutableStateOf("BOTH")
        compose.setContent { AnushtanamTheme(theme.value == "DARK") {
            SettingsContent(ReviewFixtures.parent, theme.value, language.value,
                { theme.value = it }, { language.value = it }, {})
        } }
        compose.onNodeWithText(text(R.string.settings_theme_dark)).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(text(R.string.settings_script_devanagari)).performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("DARK", theme.value); assertEquals("DEVANAGARI", language.value) }
        compose.onNodeWithText(text(R.string.settings_advanced_section)).assertDoesNotExist()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
    }

    @Test fun processingDisablesTheRecordingAction() {
        var clicks = 0
        compose.setContent { AnushtanamTheme(false) { MicButton(false, true, { clicks++ }) } }
        compose.onNodeWithContentDescription(text(R.string.mic_start_japa)).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun mantraTransliterationStillToggles() {
        compose.setContent { AnushtanamTheme(false) { MantraDisplay(null) } }
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText(GayatriMantra.transliterations[1]).assertExists()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithText(GayatriMantra.transliterations[1]).assertDoesNotExist()
    }

    @Test fun progressAnnouncesTheRealValueDuringAnimation() {
        compose.setContent { AnushtanamTheme(false) { ProgressRing(0.3f) } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.3f, 0f..1f))).assertExists()
    }

    @Test fun floatingNavigationKeepsEveryParentTabUsableAt320dp() {
        val route = mutableStateOf(Screen.Home.route)
        val destinations = navigationDestinations(true)
        val visited = mutableListOf<String>()
        compose.setContent { AnushtanamTheme(false) {
            FloatingNavigationBar(destinations, route.value, {
                route.value = it.route; visited += it.route
            }, Modifier.width(320.dp))
        } }
        destinations.forEach { destination ->
            compose.onNodeWithContentDescription(text(destination.screen.labelResId))
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                .performClick().assertIsSelected()
        }
        compose.runOnIdle { assertEquals(destinations.map { it.screen.route }, visited) }
    }

    @Test fun childNavigationHasFiveTabsWithoutTheParentDestination() {
        compose.setContent { AnushtanamTheme(true) {
            FloatingNavigationBar(navigationDestinations(false), Screen.Home.route, {})
        } }
        compose.onNodeWithContentDescription(text(R.string.title_child_progress)).assertDoesNotExist()
        compose.onAllNodes(isSelectable()).assertCountEquals(5)
    }

    @Test fun completingPrayersKeepsEveryWeeklyBarWithinItsPlotLane() {
        val points = mutableStateOf(listOf(
            TrendPoint("20260901", 0, 3, 0), TrendPoint("20260902", 0, 0, 0),
            TrendPoint("20260903", 0, 1, 0)))
        compose.setContent { AnushtanamTheme(false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Box(Modifier.width(272.dp)) { WeeklyTrend(points.value) }
            }
        } }
        // Normal completion, observations without a schedule, and an imported extra count.
        compose.runOnIdle { points.value = points.value.mapIndexed { index, point ->
            point.copy(observed = if (index == 2) 20 else 3)
        } }
        points.value.forEach { point ->
            val lane = compose.onNodeWithTag("trend-lane-${point.dateKey}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val bar = compose.onNodeWithTag("trend-bar-${point.dateKey}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Bar must stay inside its own plot", bar.top >= lane.top && bar.bottom <= lane.bottom && bar.left >= lane.left && bar.right <= lane.right)
            compose.onNodeWithContentDescription("${point.dateKey}: ${point.observed} prayers completed, ${point.scheduled} scheduled").assertExists()
        }
    }

    @Test fun completedPeriodBarsKeepTheirWidthWithLargerText() {
        compose.setContent { AnushtanamTheme(false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                Column(Modifier.width(272.dp).verticalScroll(rememberScrollState())) {
                    MetricsSection(ReviewFixtures.completedSandhya)
                }
            }
        } }
        val completed = SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(1f, 0f..1f))
        // The first node is the circular summary; the other three are the period bars.
        compose.onAllNodes(completed).assertCountEquals(4)
        for (index in 1..3) compose.onAllNodes(completed)[index].performScrollTo().assertWidthIsAtLeast(220.dp)
        compose.onAllNodesWithText("7 / 7 (100%)").assertCountEquals(3)
    }
}
