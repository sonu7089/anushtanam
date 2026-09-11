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
import com.mantrasiddhi.presentation.navigation.enterScreenTransition
import com.mantrasiddhi.presentation.navigation.exitScreenTransition
import com.mantrasiddhi.presentation.screens.recitation.components.JapaCountedPulse
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
    val reflection = TeachingMessage("home-reflection", 1, "There is time to begin again",
        "If you have missed a few days, begin with a little time today.",
        "If you have missed a few days, you can begin again today. Sit for a little while and say the mantra with care. You do not have to make up for everything at once.",
        "", 1L, Long.MAX_VALUE)
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
    "progress" to Screen.Progress, "family" to Screen.Family, "settings" to Screen.Settings
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
        bottomBar = { MantraBottomBar(controller, showFamily = true) }) { padding ->
        NavHost(controller, startDestination = start.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { enterScreenTransition() }, exitTransition = { exitScreenTransition() },
            popEnterTransition = { enterScreenTransition(pop = true) },
            popExitTransition = { exitScreenTransition(pop = true) }) {
            reviewTabs.forEach { (page, screen) ->
                composable(screen.route) { ReviewContent(page, dark, onDarkChange) }
            }
        }
    }
}

@Composable
private fun ReviewContent(page: String, dark: Boolean, onDarkChange: (Boolean) -> Unit) {
                    when (page) {
                        "ripple" -> {
                            var pulse by remember { mutableLongStateOf(0L) }
                            Box(Modifier.fillMaxSize()) {
                                ReviewPage(R.string.title_recitation) {
                                    TargetProgressHeader(ReviewFixtures.target.copy(currentRecitationCount = 36 + pulse.toInt()))
                                    MantraDisplay(null)
                                    TextButton(onClick = { pulse++ }) { Text("Simulate counted japa") }
                                }
                                JapaCountedPulse(pulse)
                            }
                        }
                        "messages", "messages-phone", "message-detail", "messages-empty", "messages-offline" -> {
                            val note = TeachingMessage("begin-with-care", 1, "Begin with care",
                                "You do not have to hurry through your practice. Give this moment your attention.",
                                "Some days, the mind settles easily. On other days, it wanders.\n\nWhen you notice it wandering, return gently to the mantra. There is no need to be hard on yourself.\n\nTake your time. Let the next repetition begin with care.",
                                "Anushtanam", 1788998400000, Long.MAX_VALUE, "river", true)
                            val phoneNote = note.copy(id = "phone-note", title = "A small note for you",
                                summary = "Your test message is here. Take a quiet moment to read it.",
                                art = "jasmine", pinned = false)
                            com.mantrasiddhi.presentation.screens.messages.MessagesContent(
                                com.mantrasiddhi.presentation.screens.messages.MessagesState(
                                    items = when (page) {
                                        "messages" -> listOf(note, phoneNote)
                                        "messages-phone" -> listOf(phoneNote)
                                        else -> emptyList()
                                    }, loading = false,
                                    detail = if (page == "message-detail") note else null,
                                    offline = page == "messages-offline"), page == "message-detail", {}, {}, {}, {})
                        }
                        "home-messages" -> ReviewPage(R.string.title_home) {
                            com.mantrasiddhi.presentation.screens.home.components.SadhanaWisdomCard()
                            com.mantrasiddhi.presentation.screens.messages.MessagesHomeEntry({})
                        }
                        "settings-messages" -> SettingsContent(ReviewFixtures.parent, if (dark) "DARK" else "LIGHT", "BOTH",
                            {}, {}, {}, messagesContent = {
                                com.mantrasiddhi.presentation.screens.messages.MessageNotificationSetting()
                            })
                        "reflection" -> ReviewPage(R.string.title_home) {
                            com.mantrasiddhi.presentation.screens.home.components.SadhanaWisdomCard()
                        }
                        "reflection-mentor" -> ReviewPage(R.string.title_home) {
                            com.mantrasiddhi.presentation.screens.home.components.SadhanaWisdomCard(
                                reflection = ReviewFixtures.reflection)
                        }
                        "voice-check" -> VoiceModelScreen(VoiceModelState.CHECK_UNAVAILABLE, DownloadUiState.Idle, {}, {}, Modifier.safeDrawingPadding())
                        "voice-live" -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            VoiceModelGate { Text("Speech service ready", Modifier.padding(24.dp)) }
                        }
                        "voice" -> VoiceModelScreen(VoiceModelState.NOT_INSTALLED, DownloadUiState.Idle, {}, {}, Modifier.safeDrawingPadding())
                        "empty" -> ReviewPage(R.string.title_progress) {
                            EmptyState(stringResource(R.string.progress_empty_title), stringResource(R.string.progress_empty_description))
                        }
                        "welcome" -> WelcomeScreen({}, {})
                        "parent-auth" -> ParentAuthScreen(hiltViewModel(), {}, {})
                        "child-auth" -> ChildAuthScreen(hiltViewModel(), {}, {})
                        "child-google-pin" -> ReviewPage(R.string.auth_child_signup_title) {
                            ChildGooglePinSetup(GoogleAuthUiState(account = AuthAccount("child-preview", AccountRole.CHILD,
                                "Aarav", linkCode = "123456789012", googleLinked = true, needsChildPinSetup = true),
                                needsChildPin = true), { _, _ -> })
                        }
                        "child-id" -> ChildIdReveal("123456789012", false, {})
                        "home" -> {
                            var guideVisible by remember { mutableStateOf(false) }
                            HomeContent(ReviewFixtures.home, {}, {}, {}, {}, {},
                                onOpenGuide = { guideVisible = true })
                            if (guideVisible) com.mantrasiddhi.presentation.screens.guide.PracticeGuideSheet(
                                onDismiss = { guideVisible = false }, onPractice = {})
                        }
                        "home-cards" -> ReviewPage(R.string.title_home) {
                            com.mantrasiddhi.presentation.screens.home.components.SandhyaHomeCard({})
                            com.mantrasiddhi.presentation.screens.guide.PracticeGuideCard({})
                            com.mantrasiddhi.presentation.screens.home.components.PurashcharanaHomeCard(null, {})
                        }
                        "practice-guide" -> {
                            var visible by remember { mutableStateOf(true) }
                            ReviewPage(R.string.guide_title) {
                                com.mantrasiddhi.presentation.screens.guide.PracticeGuideCard({ visible = true })
                            }
                            if (visible) com.mantrasiddhi.presentation.screens.guide.PracticeGuideSheet(
                                onDismiss = { visible = false }, onPractice = {})
                        }
                        "settings-backup", "settings-backup-waiting", "settings-backup-attention", "settings-backup-conflict" -> {
                            SettingsContent(ReviewFixtures.parent, if (dark) "DARK" else "LIGHT", "BOTH", {}, {}, {},
                                backupContent = {
                                    com.mantrasiddhi.presentation.screens.settings.BackupStatusContent(
                                        com.mantrasiddhi.presentation.screens.settings.BackupUiState(localOnly = false,
                                            waiting = page == "settings-backup-waiting", attention = page == "settings-backup-attention",
                                            lastSuccess = 1789012800000,
                                            reviews = if (page == "settings-backup-conflict") listOf(
                                                com.mantrasiddhi.presentation.screens.settings.BackupReview("review", "10 Sep 2026", 500, 540, "op")
                                            ) else emptyList()))
                                })
                        }
                        "settings" -> SettingsContent(ReviewFixtures.parent, if (dark) "DARK" else "LIGHT", "BOTH",
                            { onDarkChange(it == "DARK") }, {}, {})
                        "settings-google", "settings-link-google", "settings-child-google" -> {
                            val linked = page == "settings-google"
                            val isChild = page == "settings-child-google"
                            val account = AuthAccount("preview", if (isChild) AccountRole.CHILD else AccountRole.PARENT,
                                if (isChild) "Aarav" else "Ananya",
                                email = if (isChild) "c123456789012@child.anushtanam.app" else "ananya@example.com", googleLinked = linked)
                            SettingsContent((if (isChild) ReviewFixtures.child else ReviewFixtures.parent).copy(photoSource = if (linked) "GOOGLE" else null,
                                useGooglePhoto = true), if (dark) "DARK" else "LIGHT", "BOTH",
                                { onDarkChange(it == "DARK") }, {}, {},
                                accountContent = {
                                    com.mantrasiddhi.presentation.screens.settings.GoogleAccountContent(
                                        account, GoogleAuthUiState(), {}, {}, {}, {})
                                })
                        }
                        "progress" -> ReviewPage(R.string.title_progress, scroll = false) {
                            ProgressContent(ProgressUiState.Success(RecitationOverviewStats(128, 108, .92, 7, .98),
                                emptyList(), emptyList(), emptyList(), emptyList()))
                        }
                        "purashcharana" -> ReviewPage(R.string.title_purashcharana, scroll = false) {
                            PurashcharanaContent(null, {}, {})
                        }
                        "family" -> ReviewPage(R.string.family_title) {
                            PageIntro(stringResource(R.string.lotus_family_intro), stringResource(R.string.lotus_family_description), LotusArt.FAMILY)
                            com.mantrasiddhi.presentation.screens.family.FamilyProgressEntry({})
                            Spacer(Modifier.height(20.dp))
                            ParentSection(FamilyUiState(account = AuthAccount("preview", AccountRole.PARENT, "Ananya")), { _, _ -> }, {}, {})
                        }
                        "family-child", "family-child-qr" -> ReviewPage(R.string.family_title) {
                            val invite = remember { com.mantrasiddhi.domain.model.FamilyQrInvite("a".repeat(64),
                                System.currentTimeMillis() + 300_000) }
                            PageIntro(stringResource(R.string.lotus_family_intro), stringResource(R.string.lotus_family_description), LotusArt.FAMILY)
                            com.mantrasiddhi.presentation.screens.family.ChildSection(FamilyUiState(
                                account = AuthAccount("preview", AccountRole.CHILD, "Aarav", linkCode = "123456789012"),
                                qrInvite = invite.takeIf { page == "family-child-qr" }), {}, {})
                        }
                        "children" -> ReviewPage(R.string.children_progress_title) {
                            PageIntro(stringResource(R.string.lotus_children_intro), stringResource(R.string.lotus_children_description), LotusArt.FAMILY)
                            ChildCard(ReviewFixtures.childProgress, {})
                        }
                        "targets", "targets-empty", "targets-complete" -> {
                            val complete = ReviewFixtures.target.copy(currentRecitationCount = 108, isCompleted = true)
                            val past = ReviewFixtures.target.copy(id = 20, stableId = "past-goal", isActive = false,
                                isCompleted = true, currentRecitationCount = 1008, targetRecitationCount = 1008,
                                startedAtMillis = ReviewFixtures.target.startedAtMillis - 14L * 86400000)
                            com.mantrasiddhi.presentation.screens.targets.TargetsContent(
                                com.mantrasiddhi.presentation.screens.targets.TargetsUiState.Success(
                                    currentUser = ReviewFixtures.parent,
                                    activeTarget = if (page == "targets") ReviewFixtures.target else null,
                                    completedTargets = if (page == "targets-complete") listOf(complete, past) else if (page == "targets") listOf(past) else emptyList(),
                                    children = emptyList()), {}, {}, {}, {})
                        }
                        "set-target" -> ReviewPage(R.string.targets_title) {
                            SetTargetDialog(ReviewFixtures.parent, emptyList(), {}, { _, _, _, _ -> })
                        }
                        "sandhya" -> SandhyaScreen(hiltViewModel())
                        "sandhya-counts", "sandhya-empty", "sandhya-entry", "sandhya-counter", "sandhya-draft" -> ReviewPage(R.string.title_sandhya) {
                            SandhyaCountReview(page)
                        }
                        "settings-sandhya" -> SandhyaSettingsReview()
                        "sandhya-next-reminders" -> ReviewPage(R.string.title_sandhya) {
                            ReminderReview(summary = true)
                        }
                        "settings-reminders", "settings-reminders-blocked" -> ReviewPage(R.string.settings_title) {
                            ReminderReview(blocked = page.endsWith("blocked"))
                        }
                        "settings-minimums" -> ReviewPage(R.string.settings_title) {
                            var minimums by remember { mutableStateOf(SandhyaMinimums()) }
                            com.mantrasiddhi.presentation.screens.settings.SandhyaMinimumSettings(
                                minimums, { minimums = it }, initiallyEditing = true)
                        }
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
