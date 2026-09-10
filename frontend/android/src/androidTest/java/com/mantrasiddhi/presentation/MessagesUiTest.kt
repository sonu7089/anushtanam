package com.mantrasiddhi.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mantrasiddhi.domain.model.TeachingMessage
import com.mantrasiddhi.presentation.screens.messages.*
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier

class MessagesUiTest {
    @get:Rule val compose = createComposeRule()
    private val note = TeachingMessage("care", 1, "Begin with care", "A moment for your practice.",
        "Take your time. Let your next repetition begin with care.", "Anushtanam", 100, Long.MAX_VALUE)

    @Test fun selectingAMessageOpensItsOwnId() {
        var selected = ""
        compose.setContent { AnushtanamTheme(false) {
            MessagesContent(MessagesState(items = listOf(note), loading = false), false, {}, { selected = it }, {}, {})
        } }
        compose.onNodeWithText(note.title).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("care", selected) }
    }
    @Test fun detailShowsTheFullBodyWithoutExtraActions() {
        compose.setContent { AnushtanamTheme(false) {
            MessagesContent(MessagesState(detail = note, loading = false), true, {}, {}, {}, {})
        } }
        compose.onNodeWithText(note.body).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("See current messages").assertDoesNotExist()
        compose.onNodeWithText("Refresh").assertDoesNotExist()
        compose.onNodeWithContentDescription("Refresh").assertDoesNotExist()
    }
    @Test fun unavailableOfflineMessageExplainsHowToReturnWithoutExtraActions() {
        compose.setContent { AnushtanamTheme(true) {
            MessagesContent(MessagesState(loading = false, offline = true), true, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Let us try that again").assertExists()
        compose.onNodeWithContentDescription("Back").assertHasClickAction()
        compose.onNodeWithText("See current messages").assertDoesNotExist()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }
    @Test fun readingAndBackRemainAvailableWithLargeTextOnANarrowPhone() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                AnushtanamTheme(true) { Box(Modifier.width(320.dp)) {
                    MessagesContent(MessagesState(detail = note, loading = false), true, {}, {}, {}, {})
                } }
            }
        }
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText(note.body).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun refreshIconRespectsCooldownAndThereIsNoRefreshTextButton() {
        var ready by androidx.compose.runtime.mutableStateOf(false)
        var clicks = 0
        compose.setContent { AnushtanamTheme(false) {
            MessagesContent(MessagesState(loading = false, refreshReady = ready), false, {}, {}, { clicks++ }, {})
        } }
        compose.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
        compose.onNodeWithText("Refresh").assertDoesNotExist()
        compose.runOnIdle { ready = true }
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun blockedNotificationToggleStillOffersEnableWithoutASettingsButton() {
        var wanted = false
        compose.setContent { AnushtanamTheme(false) {
            MessageNotificationSettingContent(false, false, onToggle = { wanted = it })
        } }
        compose.onNodeWithText("Teachings and messages").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(true, wanted) }
        compose.onNodeWithText("Open notification settings").assertDoesNotExist()
    }
}
