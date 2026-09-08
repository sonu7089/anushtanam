package com.mantrasiddhi.presentation

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.review.DesignReviewActivity
import com.mantrasiddhi.resources.R
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class GoogleAuthUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test fun missingGoogleConfigurationExplainsNextStepAndKeepsEmailSignInAvailable() {
        assumeTrue(context.getString(com.mantrasiddhi.R.string.google_sign_in_web_client_id).isBlank())
        ActivityScenario.launch<DesignReviewActivity>(
            Intent(context, DesignReviewActivity::class.java).putExtra("page", "parent-auth")
        ).use {
            compose.onNodeWithText(text(R.string.google_continue)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.google_not_ready)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(text(R.string.auth_login)).performScrollTo().assertIsEnabled()
            compose.onNodeWithText(text(R.string.auth_forgot_password)).performScrollTo().assertIsEnabled()
        }
    }

    @Test fun googleProfileShowsNameAndInitialWhenPhotoIsMissing() {
        ActivityScenario.launch<DesignReviewActivity>(
            Intent(context, DesignReviewActivity::class.java).putExtra("page", "settings-google")
        ).use {
            compose.onNodeWithText("Ananya").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("A").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun childGoogleSignInKeepsIdAndPinAvailableWhenGoogleIsNotConfigured() {
        assumeTrue(context.getString(com.mantrasiddhi.R.string.google_sign_in_web_client_id).isBlank())
        ActivityScenario.launch<DesignReviewActivity>(Intent(context, DesignReviewActivity::class.java)
            .putExtra("page", "child-auth")).use {
            compose.onNodeWithText(text(R.string.google_continue)).performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.google_not_ready)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(text(R.string.auth_child_id_label)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(text(R.string.auth_pin_label)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(text(R.string.auth_login)).performScrollTo().assertIsEnabled()
        }
    }

    @Test fun childCanLinkGoogleFromSettings() {
        ActivityScenario.launch<DesignReviewActivity>(Intent(context, DesignReviewActivity::class.java)
            .putExtra("page", "settings-child-google")).use {
            compose.onNodeWithText(text(R.string.google_link_action)).performScrollTo().assertIsEnabled()
            compose.onNodeWithText("c123456789012@child.anushtanam.app").assertDoesNotExist()
        }
    }

    @Test fun familyOffersQrScanAndRequiresBothIdAndPinForManualLinking() {
        ActivityScenario.launch<DesignReviewActivity>(Intent(context, DesignReviewActivity::class.java)
            .putExtra("page", "family")).use {
            compose.onNodeWithText(text(R.string.family_scan_qr)).performScrollTo().assertIsEnabled()
            compose.onNodeWithText(text(R.string.family_link_now)).performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText(text(R.string.family_child_id_label)).performScrollTo().performTextInput("123456789012")
            compose.onNodeWithText(text(R.string.family_link_now)).performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText(text(R.string.auth_pin_label)).performScrollTo().performTextInput("123456")
            compose.onNodeWithText(text(R.string.family_link_now)).performScrollTo().assertIsEnabled()
        }
    }

    @Test fun childQrIsVisibleWithItsOneTimeUseExplanation() {
        ActivityScenario.launch<DesignReviewActivity>(Intent(context, DesignReviewActivity::class.java)
            .putExtra("page", "family-child-qr")).use {
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(text(R.string.family_qr_description)).fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(text(R.string.family_qr_description)).assertIsDisplayed()
            compose.onNodeWithText(text(R.string.family_qr_lifetime)).assertIsDisplayed()
        }
    }
}
