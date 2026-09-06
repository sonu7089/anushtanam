package com.mantrasiddhi.review

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mantrasiddhi.presentation.navigation.Screen
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.usecase.RecitationOverviewStats
import com.mantrasiddhi.presentation.components.*
import com.mantrasiddhi.presentation.navigation.MantraBottomBar
import com.mantrasiddhi.presentation.screens.auth.*
import com.mantrasiddhi.presentation.screens.home.*
import com.mantrasiddhi.presentation.screens.settings.SettingsContent
import com.mantrasiddhi.presentation.screens.progress.*
import com.mantrasiddhi.presentation.screens.purashcharana.PurashcharanaContent
import com.mantrasiddhi.presentation.screens.practice.components.*
import com.mantrasiddhi.presentation.screens.recitation.components.TargetProgressHeader
import com.mantrasiddhi.presentation.screens.family.*
import com.mantrasiddhi.presentation.screens.sandhya.SandhyaScreen
import com.mantrasiddhi.presentation.screens.sandhya.MetricsSection
import com.mantrasiddhi.presentation.screens.targets.components.*
import com.mantrasiddhi.presentation.screens.childprogress.components.ChildCard
import com.mantrasiddhi.presentation.screens.voicemodel.VoiceModelScreen
import com.mantrasiddhi.presentation.screens.voicemodel.DownloadUiState
import com.mantrasiddhi.presentation.theme.AnushtanamTheme
import dagger.hilt.android.AndroidEntryPoint

/** Real UI components with local fixtures. Never changes a profile or writes to Firebase. */
@AndroidEntryPoint
class DesignReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val page = intent.getStringExtra("page") ?: "welcome"
        val initialDark = intent.getBooleanExtra("dark", false)
        setContent {
            var dark by remember { mutableStateOf(initialDark) }
            AnushtanamTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ReviewNavigation(page, dark) { dark = it }
                }
            }
        }
    }
}

object ReviewFixtures {
    val parent = UserProfile(1, "Ananya", ProfileType.PARENT, avatarIndex = 1)
    val child = UserProfile(2, "Aarav", ProfileType.CHILD, parentId = 1, avatarIndex = 0)
    val target = Target(1, 1, 1, 108, 36)
    val childProgress = ChildProgress(child, target.copy(userId = 2), 216, 2)
    val home = HomeUiState.Success(parent, 36, target, 7, listOf(childProgress))
    val completedSandhya = SandhyaAdherence.EMPTY.copy(
        totalScheduled = 21, totalObserved = 21, overallAdherenceRate = 1.0,
        currentStreakAllThree = 7, longestStreakAllThree = 12,
        currentStreakAtLeastOne = 7, longestStreakAtLeastOne = 18,
        perPeriod = SandhyaPeriod.entries.map { PeriodAdherence(it, 7, 7) },
        onTimeCount = 21, onTimeRate = 1.0, mostConsistentPeriod = SandhyaPeriod.PRATAH,
        last7Days = (1..7).map { TrendPoint("2026090$it", it.toLong(), 3, 3) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewPage(title: Int, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { LotusTopAppBar(title = { Text(stringResource(title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).then(
            if (scroll) Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}


private val reviewTabs = mapOf(
    "home" to Screen.Home, "practice" to Screen.Practice, "recitation" to Screen.Recitation,
    "progress" to Screen.Progress, "children" to Screen.ChildProgress, "settings" to Screen.Settings
)

@Composable
private fun ReviewNavigation(initialPage: String, dark: Boolean, onDarkChange: (Boolean) -> Unit) {
    val start = reviewTabs[initialPage]
    if (start == null) {
        ReviewContent(initialPage, dark, onDarkChange)
        return
    }
    val controller = rememberNavController()
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { MantraBottomBar(controller, isParent = true) }) { padding ->
        NavHost(controller, startDestination = start.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            reviewTabs.forEach { (page, screen) ->
                composable(screen.route) { ReviewContent(page, dark, onDarkChange) }
            }
        }
    }
}

@Composable
private fun ReviewContent(page: String, dark: Boolean, onDarkChange: (Boolean) -> Unit) {
                    when (page) {
                        "reflection" -> ReviewPage(R.string.title_home) {
                            com.mantrasiddhi.presentation.screens.home.components.SadhanaWisdomCard()
                        }
                        "voice" -> VoiceModelScreen(VoiceModelState.NOT_INSTALLED, DownloadUiState.Idle, {}, {}, Modifier.safeDrawingPadding())
                        "empty" -> ReviewPage(R.string.title_progress) {
                            EmptyState(stringResource(R.string.progress_empty_title), stringResource(R.string.progress_empty_description))
                        }
                        "welcome" -> WelcomeScreen({}, {})
                        "parent-auth" -> ParentAuthScreen(hiltViewModel(), {}, {})
                        "child-auth" -> ChildAuthScreen(hiltViewModel(), {}, {})
                        "child-id" -> ChildIdReveal("123456789012", false, {})
                        "home" -> HomeContent(ReviewFixtures.home, {}, {}, {}, {}, {}, {}, {})
                        "settings" -> SettingsContent(ReviewFixtures.parent, if (dark) "DARK" else "LIGHT", "BOTH",
                            { onDarkChange(it == "DARK") }, {}, {})
                        "progress" -> ReviewPage(R.string.title_progress, scroll = false) {
                            ProgressContent(ProgressUiState.Success(RecitationOverviewStats(128, 108, .92, 7, .98),
                                emptyList(), emptyList(), emptyList(), emptyList()))
                        }
                        "purashcharana" -> ReviewPage(R.string.title_purashcharana, scroll = false) {
                            PurashcharanaContent(null, {}, {})
                        }
                        "family" -> ReviewPage(R.string.family_title) {
                            PageIntro(stringResource(R.string.lotus_family_intro), stringResource(R.string.lotus_family_description), LotusArt.FAMILY)
                            ParentSection(FamilyUiState(account = AuthAccount("preview", AccountRole.PARENT, "Ananya")), {}, {}, {})
                        }
                        "children" -> ReviewPage(R.string.children_progress_title) {
                            PageIntro(stringResource(R.string.lotus_children_intro), stringResource(R.string.lotus_children_description), LotusArt.FAMILY)
                            ChildCard(ReviewFixtures.childProgress, {})
                        }
                        "targets" -> ReviewPage(R.string.targets_title) {
                            PageIntro(stringResource(R.string.lotus_targets_intro), stringResource(R.string.lotus_targets_description), LotusArt.RITUAL)
                            TargetCard(ReviewFixtures.target, {})
                        }
                        "set-target" -> ReviewPage(R.string.targets_title) {
                            SetTargetDialog(ReviewFixtures.parent, emptyList(), {}, { _, _, _, _ -> })
                        }
                        "sandhya" -> SandhyaScreen(hiltViewModel())
                        "sandhya-complete" -> ReviewPage(R.string.title_sandhya) {
                            MetricsSection(ReviewFixtures.completedSandhya)
                        }
                        "practice" -> ReviewPage(R.string.home_practice_mode) {
                            Text(stringResource(R.string.lotus_practice_intro), style = MaterialTheme.typography.headlineSmall)
                            AudioPlayerBar(false, 0, {})
                            MantraDisplay(null)
                            Text(stringResource(R.string.practice_intro), style = MaterialTheme.typography.bodyMedium)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MicButton(false, false, {}) }
                        }
                        "recitation" -> ReviewPage(R.string.title_recitation) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LotusArtwork(LotusArt.DIYA, Modifier.size(104.dp)) }
                            Text(stringResource(R.string.lotus_recitation_intro), style = MaterialTheme.typography.headlineSmall)
                            TargetProgressHeader(ReviewFixtures.target)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MicButton(true, false, {}) }
                        }
                    }
}
