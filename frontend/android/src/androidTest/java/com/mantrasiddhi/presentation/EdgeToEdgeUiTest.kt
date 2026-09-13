package com.mantrasiddhi.presentation

import android.content.Intent
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.review.DesignReviewActivity
import com.mantrasiddhi.review.ReviewFixtures
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercise actual window dispatch, nested scaffolds, and navigation with side cutouts. */
class EdgeToEdgeUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Suppress("DEPRECATION") // Reading the legacy color verifies three-button theme behavior.
    @Test fun darkThemeKeepsSystemNavigationTransparentWithLightIcons() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, DesignReviewActivity::class.java)
            .putExtra("page", "settings").putExtra("dark", true)
        ActivityScenario.launch<DesignReviewActivity>(intent).use { scenario ->
            compose.waitForIdle()
            scenario.onActivity { activity ->
                assertEquals(android.graphics.Color.TRANSPARENT, activity.window.navigationBarColor)
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertFalse("Dark app needs light status icons", controller.isAppearanceLightStatusBars)
                assertFalse("Dark app needs light navigation icons", controller.isAppearanceLightNavigationBars)
                assertTrue("Three-button contrast protection remains enabled", activity.window.isNavigationBarContrastEnforced)
            }
        }
    }

    @Test fun cutoutsAndSystemBarsKeepHomeAndSettingsActionsInsideSafeBounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, DesignReviewActivity::class.java).putExtra("page", "home")
        ActivityScenario.launch<DesignReviewActivity>(intent).use { scenario ->
            compose.waitForIdle()
            var width = 0
            var height = 0
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                width = decor.width
                height = decor.height
                assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS,
                    activity.window.attributes.layoutInDisplayCutoutMode)
                ViewCompat.dispatchApplyWindowInsets(decor, WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 60, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 90))
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(110, 0, 80, 0))
                    .build())
            }
            compose.waitForIdle()
            val greeting = context.getString(R.string.home_greeting, ReviewFixtures.parent.name)
            val title = compose.onNodeWithText(greeting).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Home title clears status bar", title.top >= 60)
            assertTrue("Home title clears left cutout", title.left >= 110)
            val home = compose.onNodeWithTag("perf_tab_home").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val settings = compose.onNodeWithTag("perf_tab_settings").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Navigation clears left cutout", home.left >= 110)
            assertTrue("Navigation clears right cutout", settings.right <= width - 80)
            assertTrue("Navigation clears bottom bar", settings.bottom <= height - 90)
            compose.onNodeWithTag("perf_tab_settings").performClick()
            val settingsTitle = compose.onNodeWithText(context.getString(R.string.settings_title))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Settings title clears status bar", settingsTitle.top >= 60)
            assertTrue("Settings title clears cutout", settingsTitle.left >= 110)
        }
    }
}
