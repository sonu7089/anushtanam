package com.mantrasiddhi.presentation

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.presentation.screens.guide.*
import com.mantrasiddhi.presentation.screens.home.HomeContent
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.review.ReviewFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PracticeGuideUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun homeGuideCanBeSkippedAcknowledgedAndReopened() {
        var reads = 0
        compose.setContent {
            var visible by remember { mutableStateOf(false) }
            var acknowledged by remember { mutableStateOf(false) }
            AnushtanamTheme(false) {
                HomeContent(ReviewFixtures.home, {}, {}, {}, {}, {}, {}, {},
                    onOpenGuide = { visible = true }, guideNeedsReview = !acknowledged)
                if (visible) PracticeGuideSheet(acknowledged, false, false,
                    { reads++; acknowledged = true; visible = false }, { visible = false })
            }
        }
        compose.onNodeWithText(text(R.string.guide_title)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.guide_vaikhari)).assertExists()
        compose.onNodeWithText(text(R.string.guide_upamsu)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_manasika)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_later)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, reads) }
        compose.onNodeWithText(text(R.string.guide_review)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.guide_acknowledge)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, reads) }
        compose.onNodeWithText(text(R.string.guide_review)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.guide_learn)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.guide_acknowledge)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.guide_close)).performScrollTo().performClick()
    }

    @Test fun failedSaveOffersRetryAndKeepsContinueAvailable() {
        var acknowledged = 0
        var dismissed = 0
        compose.setContent { AnushtanamTheme(true) {
            PracticeGuideSheet(false, false, true, { acknowledged++ }, { dismissed++ })
        } }
        compose.onNodeWithText(text(R.string.guide_save_error)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_acknowledge)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.guide_later)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, acknowledged); assertEquals(1, dismissed) }
    }

    @Test fun closeIsReachableWithoutScrollingAndDoesNotAcknowledge() {
        var acknowledged = 0
        var dismissed = 0
        compose.setContent { AnushtanamTheme(false) {
            PracticeGuideSheet(false, false, false, { acknowledged++ }, { dismissed++ })
        } }
        compose.onNodeWithContentDescription(text(R.string.guide_close)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, acknowledged); assertEquals(1, dismissed) }
    }
}
