package com.mantrasiddhi.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.presentation.screens.guide.*
import com.mantrasiddhi.presentation.screens.home.HomeContent
import com.mantrasiddhi.presentation.screens.settings.SettingsContent
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.review.ReviewFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PracticeGuideUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private fun openSection(id: Int) = compose.onNodeWithText(text(id)).performScrollTo().performClick()

    @Test fun homeGuideOpensOnRequestAndCanBeReopenedWithoutAcknowledgement() {
        compose.setContent {
            var visible by remember { mutableStateOf(false) }
            AnushtanamTheme(false) {
                HomeContent(ReviewFixtures.home, {}, {}, {}, {}, {},
                    onOpenGuide = { visible = true })
                if (visible) PracticeGuideSheet(onDismiss = { visible = false })
            }
        }
        compose.onNodeWithText(text(R.string.guide_intro)).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("home_section_7")
        compose.onNodeWithText(text(R.string.guide_learn)).performScrollTo().performClick()
        openSection(R.string.guide_methods_title)
        listOf(R.string.guide_vaikhari, R.string.guide_upamsu, R.string.guide_manasika).forEach {
            compose.onNodeWithText(text(it)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("I have read this").assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.guide_close)).assertIsDisplayed().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("home_section_7")
        compose.onNodeWithText(text(R.string.guide_learn)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.guide_intro)).assertIsDisplayed()
    }

    @Test fun verseAndAllSixCautionsExpandAndCollapse() {
        compose.setContent { AnushtanamTheme(true) { PracticeGuideSheet(onDismiss = {}) } }
        compose.onNodeWithText(text(R.string.guide_verse_sanskrit)).assertDoesNotExist()
        openSection(R.string.guide_verse_title)
        listOf(R.string.guide_verse_sanskrit, R.string.guide_verse_transliteration, R.string.guide_verse_source).forEach {
            compose.onNodeWithText(text(it)).performScrollTo().assertIsDisplayed()
        }
        openSection(R.string.guide_verse_title)
        compose.onNodeWithText(text(R.string.guide_verse_sanskrit)).assertDoesNotExist()
        openSection(R.string.guide_cautions_title)
        listOf(R.string.guide_caution_song_body, R.string.guide_caution_pace_body,
            R.string.guide_caution_head_body, R.string.guide_caution_text_body,
            R.string.guide_caution_meaning_body, R.string.guide_caution_voice_body,
            R.string.guide_cautions_context, R.string.guide_quality_note).forEach {
            compose.onNodeWithText(text(it)).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithContentDescription(text(R.string.guide_close)).assertIsDisplayed()
        openSection(R.string.guide_cautions_title)
        compose.onNodeWithText(text(R.string.guide_caution_voice_body)).assertDoesNotExist()
    }

    @Test fun expandedSectionSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AnushtanamTheme(false) { PracticeGuideSheet(onDismiss = {}) } }
        openSection(R.string.guide_verse_title)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.guide_verse_sanskrit)).performScrollTo().assertIsDisplayed()
    }

    @Test fun homeGuidePronunciationActionWorks() {
        var practice = 0
        compose.setContent {
            var visible by remember { mutableStateOf(false) }
            AnushtanamTheme(false) {
                HomeContent(ReviewFixtures.home, {}, {}, {}, {}, {},
                    onOpenGuide = { visible = true })
                if (visible) PracticeGuideSheet(onDismiss = { visible = false }, onPractice = { practice++ })
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("home_section_7")
        compose.onNodeWithText(text(R.string.guide_learn)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.guide_pronunciation)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, practice) }
        compose.onNodeWithContentDescription(text(R.string.guide_close)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.guide_intro)).assertDoesNotExist()
    }
}
