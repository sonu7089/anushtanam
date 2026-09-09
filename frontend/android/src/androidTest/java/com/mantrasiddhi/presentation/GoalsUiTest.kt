package com.mantrasiddhi.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.presentation.screens.targets.TargetsContent
import com.mantrasiddhi.presentation.screens.targets.TargetsUiState
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.review.ReviewFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GoalsUiTest {
    @get:Rule val compose = createComposeRule()
    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun activeGoalKeepsNavigationAndFamilyActionsAvailable() {
        val actions = mutableListOf<String>()
        compose.setContent { AnushtanamTheme(false) {
            TargetsContent(TargetsUiState.Success(ReviewFixtures.parent, ReviewFixtures.target,
                emptyList(), listOf(ReviewFixtures.child)),
                { actions += "back" }, { actions += "japa" }, { actions += "set:$it" }, { actions += "aside" })
        } }
        compose.onNodeWithText("36").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.goals_continue)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.target_abandon)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.goals_child)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.goals_back)).performClick()
        compose.runOnIdle { assertEquals(listOf("japa", "aside", "set:2", "back"), actions) }
    }

    @Test fun completedGoalOffersANewGoalInsteadOfContinuingTheFinishedOne() {
        var selected: Long? = null
        val complete = ReviewFixtures.target.copy(isCompleted = true, currentRecitationCount = 108)
        compose.setContent { AnushtanamTheme(true) {
            TargetsContent(TargetsUiState.Success(ReviewFixtures.parent, null, listOf(complete)),
                {}, {}, { selected = it }, {})
        } }
        compose.onNodeWithText("108").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.goals_continue)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.target_abandon)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.target_history)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.goals_new)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1L, selected) }
    }

    @Test fun noGoalCanStartWithoutADisabledAction() {
        var selected: Long? = null
        compose.setContent { AnushtanamTheme(false) {
            TargetsContent(TargetsUiState.Success(ReviewFixtures.parent, null, emptyList()),
                {}, {}, { selected = it }, {})
        } }
        compose.onNodeWithText(text(R.string.target_set_new)).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1L, selected) }
    }

}
