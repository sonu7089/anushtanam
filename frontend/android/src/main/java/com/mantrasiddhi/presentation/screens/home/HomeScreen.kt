package com.mantrasiddhi.presentation.screens.home

import com.mantrasiddhi.presentation.components.AppArtwork
import com.mantrasiddhi.presentation.components.AppArt
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.components.*
import com.mantrasiddhi.presentation.screens.home.components.SandhyaHomeCard
import com.mantrasiddhi.presentation.screens.home.components.PurashcharanaHomeCard
import com.mantrasiddhi.presentation.screens.home.components.SadhanaWisdomCard
import com.mantrasiddhi.domain.model.TeachingMessage
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel?,
    onNavigateToRecitation: () -> Unit,
    onNavigateToPractice: () -> Unit,
    onNavigateToTargets: () -> Unit,
    onNavigateToSandhya: () -> Unit = {},
    onNavigateToPurashcharana: () -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = {},
    onMessages: () -> Unit = {},
    onOpenReflection: (String) -> Unit = {},
    onUsable: () -> Unit = {}
) {
    // The same screen stays composed while the verified-profile producer becomes available.
    val uiState = viewModel?.uiState?.collectAsStateWithLifecycle()?.value ?: HomeUiState.Loading
    val reflection = viewModel?.reflection?.collectAsStateWithLifecycle()?.value
    var placed by remember { mutableStateOf(false) }
    val ready = uiState is HomeUiState.Success
    LaunchedEffect(placed, ready) {
        if (placed && ready) {
            withFrameNanos { }
            onUsable()
        }
    }
    val now = rememberDeadlineTime(listOfNotNull(reflection?.publishedAt, reflection?.expiresAt))
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, viewModel) {
        val model = viewModel ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.refreshReflection()
            try { kotlinx.coroutines.awaitCancellation() } finally { model.stopReflection() }
        }
    }
    if (uiState is HomeUiState.Error) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(uiState.messageResId), color = MaterialTheme.colorScheme.error)
        }
    } else {
        val state = uiState as? HomeUiState.Success
        HomeContent(state, onNavigateToRecitation, onNavigateToPractice,
            onNavigateToTargets, onNavigateToSandhya,
            onNavigateToPurashcharana, modifier.onGloballyPositioned { placed = true }, onOpenGuide, onMessages,
            reflection?.takeIf { it.isVisible(now) }, onOpenReflection, { viewModel?.retry() })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HomeContent(
    state: HomeUiState.Success?,
    onRecitation: () -> Unit, onPractice: () -> Unit, onTargets: () -> Unit,
    onSandhya: () -> Unit,
    onPurashcharana: () -> Unit, modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = {},
    onMessages: () -> Unit = {},
    reflection: TeachingMessage? = null,
    onOpenReflection: (String) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val opening = stringResource(R.string.home_opening_profile)
    val summaryHeight = with(LocalDensity.current) { MaterialTheme.typography.displaySmall.lineHeight.toDp() }.coerceAtLeast(44.dp)
    Scaffold(modifier = modifier.testTag(if (state == null) "perf_entry_shell" else "perf_home_ready"), topBar = {
        LotusTopAppBar(modifier = Modifier.testTag("home_header"),
            title = { Text(if (state == null) stringResource(R.string.title_home) else stringResource(R.string.home_greeting, state.user.name),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { if (state == null) stateDescription = opening }) },
            actions = {
                if (state == null) Box(Modifier.padding(end = 20.dp).size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest))
                else ProfileAvatar(state.user.avatarIndex, state.user.name, 40.dp, Modifier.padding(end = 20.dp),
                    photoUrl = state.user.photoUrl.takeIf { state.user.useGooglePhoto })
            })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item(key = "home_section_1") {
                HomeHero()
            }
            item(key = "home_section_2") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_today_summary), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.heightIn(min = summaryHeight), verticalAlignment = Alignment.CenterVertically) {
                            if (state == null || state.japaLoading || state.japaFailed) HomeSectionStatus(state == null || state.japaLoading, state?.japaFailed == true, onRetry,
                                Modifier.width(64.dp).testTag("home_today_loading"))
                            else Text(state.todayJapa ?: state.todayCount.toString(), Modifier.widthIn(min = 64.dp).testTag("home_today_value"),
                                style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.lotus_japa_completed), modifier = Modifier.widthIn(max = 132.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Reserve the optional streak slot so its arrival cannot wrap the summary row.
                    Box(Modifier.width(96.dp).heightIn(min = 56.dp), contentAlignment = Alignment.CenterEnd) {
                    if (state != null && state.streakDays > 0) {
                        Surface(
                            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(stringResource(R.string.home_streak, state.streakDays),
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    }
                }
            }
            item(key = "home_section_4") {
                HomePracticeEntries(onPractice, onRecitation, enabled = state != null)
            }
            item(key = "home_section_5") {
                LotusCard(onClick = onTargets, enabled = state != null, modifier = Modifier.fillMaxWidth().testTag("home_goal")) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(if (state?.activeTarget?.isCompleted == true)
                                    R.string.targets_completed_goal_title else R.string.home_active_target),
                                    style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.home_active_target_gloss), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(16.dp))
                        Column(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        val target = state?.activeTarget
                        if (state == null || state.statsLoading || state.statsFailed) {
                            HomeSectionStatus(state == null || state.statsLoading, state?.statsFailed == true, onRetry)
                        } else if (target == null) {
                            Text(stringResource(R.string.no_active_targets), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.home_target_progress, target.currentRecitationCount,
                                    target.targetRecitationCount), style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.targets_percent_format, (target.progress * 100).toInt()),
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(progress = { target.progress }, modifier = Modifier.fillMaxWidth()
                                .height(6.dp).clip(MaterialTheme.shapes.small), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                            if (target.hasReward) {
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.target_reward_label, target.rewardType.emoji, target.rewardDescription),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        }
                    }
                }
            }
            if (state != null) {
            item(key = "home_section_6") {
                SandhyaHomeCard(onSandhya)
            }
            item(key = "home_section_7") {
                com.mantrasiddhi.presentation.screens.guide.PracticeGuideCard(onOpenGuide)
            }
            item(key = "home_section_8") {
                if (state.vowLoading || state.vowFailed) HomeSectionStatus(state.vowLoading, state.vowFailed, onRetry)
                else PurashcharanaHomeCard(state.purashcharana, onPurashcharana)
            }
            item(key = "home_section_9") {
                SadhanaWisdomCard(reflection = reflection, onReadMore = onOpenReflection)
            }
            item(key = "home_section_10") {
                com.mantrasiddhi.presentation.screens.messages.MessagesHomeEntry(onMessages)
            }
            }
            item(key = "home_section_11") {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
internal fun HomeHero() {
    Box(Modifier.fillMaxWidth().testTag("home_hero")) {
        AmbientArtwork(LotusArt.LOTUS, Modifier.fillMaxWidth().height(224.dp))
        Text(stringResource(R.string.home_blessed_day), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.BottomStart).padding(top = 192.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HomePracticeEntries(onPractice: () -> Unit, onRecitation: () -> Unit, enabled: Boolean = true) {
    Column(Modifier.testTag("home_practice_entries")) {
        SectionLabel(stringResource(R.string.lotus_daily_practice))
        AdaptivePair(first = { cardModifier ->
            PracticeEntry(stringResource(R.string.title_practice), stringResource(R.string.lotus_listen_learn),
                onPractice, cardModifier, false, enabled)
        }, second = { cardModifier ->
            PracticeEntry(stringResource(R.string.title_recitation), stringResource(R.string.lotus_begin_recitation),
                onRecitation, cardModifier, true, enabled)
        })
    }
}

@Composable
private fun PracticeEntry(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier,
    primary: Boolean, enabled: Boolean) {
    LotusCard(onClick = onClick, modifier = modifier, enabled = enabled, border = null,
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))) {
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            AppArtwork(if (primary) AppArt.MALA else AppArt.BOOK,
                modifier = Modifier.matchParentSize().padding(start = 28.dp, top = 8.dp, end = 6.dp, bottom = 4.dp)
                    .alpha(0.08f), contentScale = ContentScale.Fit, alignment = Alignment.BottomEnd)
            Column(Modifier.fillMaxWidth().fillMaxHeight().padding(18.dp).heightIn(min = 100.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(com.mantrasiddhi.R.drawable.arrow_forward), null, Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HomeSectionStatus(loading: Boolean, failed: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    if (loading) HomeValuePlaceholder(modifier.fillMaxWidth().height(32.dp))
    if (failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.section_retry)) }
}
