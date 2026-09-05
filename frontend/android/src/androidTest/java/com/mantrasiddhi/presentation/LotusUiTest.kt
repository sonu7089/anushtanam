package com.mantrasiddhi.presentation

import androidx.compose.runtime.mutableStateOf
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
            SettingsContent(ReviewFixtures.parent, theme.value, language.value, false,
                { theme.value = it }, { language.value = it }, {}, {})
        } }
        compose.onNodeWithText(text(R.string.settings_theme_dark)).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText(text(R.string.settings_script_devanagari)).performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals("DARK", theme.value); assertEquals("DEVANAGARI", language.value) }
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
}
