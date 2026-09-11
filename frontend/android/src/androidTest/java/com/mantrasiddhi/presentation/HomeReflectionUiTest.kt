package com.mantrasiddhi.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.presentation.screens.home.components.SadhanaWisdomCard
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.review.ReviewFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeReflectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mentorNoteOpensAndCanBePassedOverWithoutChangingTheMessagesList() {
        var opened = ""
        compose.setContent { AnushtanamTheme(false) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SadhanaWisdomCard(reflection = ReviewFixtures.reflection, onReadMore = { opened = it })
            }
        } }
        compose.onNodeWithText("A moment to reflect").assertExists()
        compose.onNodeWithText(ReviewFixtures.reflection.body).assertExists()
        compose.onNodeWithText("Read more").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("home-reflection", opened) }
        compose.onNodeWithText("Next note").performScrollTo().performClick()
        compose.onNodeWithText(ReviewFixtures.reflection.title).assertDoesNotExist()
        repeat(8) { compose.onNodeWithText("Next note").performScrollTo().performClick() }
        compose.onNodeWithText(ReviewFixtures.reflection.title).assertExists()
    }

    @Test fun delayedContentDoesNotTakeTheReaderAwayFromAnotherNote() {
        val reflection = mutableStateOf<com.mantrasiddhi.domain.model.TeachingMessage?>(null)
        compose.setContent { AnushtanamTheme(false) {
            Column(Modifier.verticalScroll(rememberScrollState())) { SadhanaWisdomCard(reflection = reflection.value) }
        } }
        compose.onNodeWithText("Next note").performScrollTo().performClick()
        compose.runOnIdle { reflection.value = ReviewFixtures.reflection }
        compose.onNodeWithText(ReviewFixtures.reflection.title).assertDoesNotExist()
    }

    @Test fun longReflectionUsesSummaryAndBothActionsFitWithLargeText() {
        var opened = false
        val note = ReviewFixtures.reflection.copy(body = "Take a little time. ".repeat(100),
            attribution = "A note from your teacher")
        compose.setContent { AnushtanamTheme(true) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    SadhanaWisdomCard(reflection = note, onReadMore = { opened = true })
                }
            }
        } }
        compose.onNodeWithText(note.summary).assertExists()
        compose.onNodeWithText(note.body).assertDoesNotExist()
        compose.onNodeWithText(note.attribution).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Read more").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, opened) }
        compose.onNodeWithText("Next note").performScrollTo().assertIsDisplayed()
    }

    @Test fun removedReflectionReturnsToABuiltInNote() {
        val reflection = mutableStateOf<com.mantrasiddhi.domain.model.TeachingMessage?>(ReviewFixtures.reflection)
        compose.setContent { AnushtanamTheme(false) {
            Column(Modifier.verticalScroll(rememberScrollState())) { SadhanaWisdomCard(reflection = reflection.value) }
        } }
        compose.runOnIdle { reflection.value = null }
        compose.onNodeWithText(ReviewFixtures.reflection.title).assertDoesNotExist()
        compose.onNodeWithText("Read more").assertDoesNotExist()
        compose.onNodeWithText("Next note").performScrollTo().assertIsDisplayed()
    }
}
