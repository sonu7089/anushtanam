package com.mantrasiddhi.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.presentation.screens.home.*
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.resources.R
import org.junit.*
import org.junit.Assert.assertEquals

class HomeDemandUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun slowSectionsKeepPracticeAndMessagesNavigationUsableAndBelowFoldItemsLazy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var practice = 0
        var messages = 0
        compose.setContent { AnushtanamTheme(false) {
            HomeContent(HomeUiState.Success(UserProfile(1, "Fixture", ProfileType.INDIVIDUAL, avatarIndex = 0),
                todayCount = 0, activeTarget = null, streakDays = 0,
                statsLoading = true, japaLoading = true, vowLoading = true),
                onRecitation = {}, onPractice = { practice++ }, onTargets = {}, onSandhya = {},
                onPurashcharana = {}, onMessages = { messages++ })
        } }
        compose.onNodeWithText(context.getString(R.string.messages_open)).assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("home_section_4")
        compose.onNodeWithText(context.getString(R.string.title_practice)).performClick()
        compose.runOnIdle { assertEquals(1, practice) }
        compose.onNode(hasScrollToIndexAction()).performScrollToKey("home_section_10")
        compose.onNodeWithText(context.getString(R.string.messages_open)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, messages) }
    }
}
