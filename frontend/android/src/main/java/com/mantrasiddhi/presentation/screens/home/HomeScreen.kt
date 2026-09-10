package com.mantrasiddhi.presentation.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToRecitation: () -> Unit,
    onNavigateToPractice: () -> Unit,
    onNavigateToTargets: () -> Unit,
    onNavigateToSandhya: () -> Unit = {},
    onNavigateToPurashcharana: () -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = {},
    onMessages: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    when (val state = uiState) {
        HomeUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is HomeUiState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(state.messageResId), color = MaterialTheme.colorScheme.error)
        }
        is HomeUiState.Success -> HomeContent(state, onNavigateToRecitation, onNavigateToPractice,
            onNavigateToTargets, onNavigateToSandhya,
            onNavigateToPurashcharana, modifier, onOpenGuide, onMessages)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HomeContent(
    state: HomeUiState.Success,
    onRecitation: () -> Unit, onPractice: () -> Unit, onTargets: () -> Unit,
    onSandhya: () -> Unit,
    onPurashcharana: () -> Unit, modifier: Modifier = Modifier,
    onOpenGuide: () -> Unit = {},
    onMessages: () -> Unit = {}
) {
    Scaffold(modifier = modifier, topBar = {
        LotusTopAppBar(title = { Text(stringResource(R.string.home_greeting, state.user.name)) },
            actions = { ProfileAvatar(state.user.avatarIndex, state.user.name, 40.dp, Modifier.padding(end = 20.dp),
                photoUrl = state.user.photoUrl.takeIf { state.user.useGooglePhoto }) })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.fillMaxWidth()) {
                AmbientArtwork(LotusArt.LOTUS, Modifier.fillMaxWidth().height(224.dp))
                Text(stringResource(R.string.home_blessed_day), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.BottomStart).padding(top = 192.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.widthIn(min = 160.dp)) {
                    Text(stringResource(R.string.home_today_summary), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.todayJapa ?: state.todayCount.toString(), style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.lotus_japa_completed), modifier = Modifier.widthIn(max = 132.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.streakDays > 0) {
                    Surface(modifier = Modifier.align(Alignment.CenterVertically),
                        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(stringResource(R.string.home_streak, state.streakDays),
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Column {
                SectionLabel(stringResource(R.string.lotus_daily_practice))
                AdaptivePair(first = { cardModifier ->
                    PracticeEntry(stringResource(R.string.title_practice), stringResource(R.string.lotus_listen_learn),
                        onPractice, cardModifier, false)
                }, second = { cardModifier ->
                    PracticeEntry(stringResource(R.string.title_recitation), stringResource(R.string.lotus_begin_recitation),
                        onRecitation, cardModifier, true)
                })
            }
            LotusCard(onClick = onTargets, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (state.activeTarget?.isCompleted == true)
                                R.string.targets_completed_goal_title else R.string.home_active_target),
                                style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.home_active_target_gloss), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(16.dp))
                    val target = state.activeTarget
                    if (target == null) {
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
            SandhyaHomeCard(onSandhya)
            com.mantrasiddhi.presentation.screens.guide.PracticeGuideCard(onOpenGuide)
            PurashcharanaHomeCard(state.purashcharana, onPurashcharana)
            SadhanaWisdomCard()
            com.mantrasiddhi.presentation.screens.messages.MessagesHomeEntry(onMessages)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PracticeEntry(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier, primary: Boolean) {
    LotusCard(onClick = onClick, modifier = modifier, border = null,
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight()) {
            Image(painterResource(if (primary) com.mantrasiddhi.R.drawable.purashcharana_mala
                else com.mantrasiddhi.R.drawable.gayatri_practice_book), contentDescription = null,
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
