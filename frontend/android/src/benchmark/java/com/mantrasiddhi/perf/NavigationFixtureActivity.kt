package com.mantrasiddhi.perf

import android.os.Bundle
import android.os.Trace
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.usecase.RecitationOverviewStats
import com.mantrasiddhi.presentation.components.LotusTopAppBar
import com.mantrasiddhi.presentation.navigation.*
import com.mantrasiddhi.presentation.screens.home.HomeContent
import com.mantrasiddhi.presentation.screens.home.HomeUiState
import com.mantrasiddhi.presentation.screens.practice.PracticeScreen
import com.mantrasiddhi.presentation.screens.progress.ProgressContent
import com.mantrasiddhi.presentation.screens.progress.ProgressUiState
import com.mantrasiddhi.presentation.screens.settings.SettingsContent
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import com.mantrasiddhi.resources.R
import dagger.hilt.android.AndroidEntryPoint

/** Optimized layout fixture, available only in benchmark APKs. Never opens or seeds an account. */
@AndroidEntryPoint
class NavigationFixtureActivity : ComponentActivity() {
    @OptIn(InternalComposeTracingApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Attribution runs only: normal before/after frame measurements leave this disabled.
        FixtureCompositionTracer.enabled = intent.getBooleanExtra("traceCompositions", false)
        Composer.setTracer(FixtureCompositionTracer)
        setContent { AnushtanamTheme(darkTheme = intent.getBooleanExtra("dark", false)) {
            NavigationFixture()
        } }
    }
}

@OptIn(InternalComposeTracingApi::class)
private object FixtureCompositionTracer : CompositionTracer {
    var enabled = false
    override fun isTraceInProgress() = enabled && Trace.isEnabled()
    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
        Trace.beginSection(info.take(127))
    }
    override fun traceEventEnd() = Trace.endSection()
}

private val fixtureUser = UserProfile(1, "Navigation fixture", ProfileType.INDIVIDUAL, avatarIndex = 0)
private val fixtureHome = HomeUiState.Success(fixtureUser, 36, null, 7, todayJapa = "108")
private val fixtureProgress = ProgressUiState.Success(RecitationOverviewStats(128, 108, .92, 7, .98))

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NavigationFixture() {
    val controller = rememberNavController()
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { MantraBottomBar(controller, showFamily = false) }
    ) { padding ->
        NavHost(controller, startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { enterScreenTransition() }, exitTransition = { exitScreenTransition() },
            popEnterTransition = { enterScreenTransition(pop = true) },
            popExitTransition = { exitScreenTransition(pop = true) }) {
            composable(Screen.Home.route) {
                HomeContent(fixtureHome, {}, { controller.navigate(Screen.Practice.route) }, {}, {}, {})
            }
            composable(Screen.Practice.route) {
                // Real idle Practice UI and its ViewModel; no voice gate/account or microphone use.
                PracticeScreen(Modifier.testTag("perf_practice_ready"))
            }
            composable(Screen.Progress.route) {
                Scaffold(topBar = { LotusTopAppBar(title = { Text(stringResource(R.string.title_progress)) }) }) { inner ->
                    Surface(Modifier.fillMaxSize().padding(inner), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize()) { ProgressContent(fixtureProgress) }
                    }
                }
            }
            composable(Screen.Settings.route) {
                SettingsContent(fixtureUser, "LIGHT", "BOTH", {}, {}, {}, Modifier.testTag("perf_settings_ready"))
            }
            // The fixture does not assert voice readiness or exercise recording.
            composable(Screen.Recitation.route) { Text("Use the signed-in journey to verify Japa") }
        }
    }
}
