package com.mantrasiddhi.presentation.screens.targets

import com.mantrasiddhi.presentation.components.AppArtwork
import com.mantrasiddhi.presentation.components.AppArt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.presentation.components.LotusTopAppBar
import com.mantrasiddhi.resources.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsContent(
    uiState: TargetsUiState,
    onBack: () -> Unit,
    onContinueJapa: () -> Unit,
    onSetGoal: (Long?) -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LotusTopAppBar(title = { Text(stringResource(R.string.targets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.goals_back))
                    }
                })
        }
    ) { padding ->
        when (uiState) {
            TargetsUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }
            is TargetsUiState.Error -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                Text(stringResource(uiState.messageResId), color = MaterialTheme.colorScheme.error)
            }
            is TargetsUiState.Success -> {
                val current = remember(uiState.activeTarget, uiState.completedTargets) {
                    uiState.activeTarget ?: uiState.completedTargets
                        .filter { it.isActive }.maxByOrNull { it.startedAtMillis }
                }
                val history = remember(uiState.completedTargets, current?.stableId) {
                    uiState.completedTargets.filter { it.stableId != current?.stableId }
                        .sortedByDescending { it.startedAtMillis }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        if (current == null) {
                            EmptyGoal { onSetGoal(uiState.currentUser.id) }
                        } else {
                            GoalProgress(current, onContinueJapa, { onSetGoal(uiState.currentUser.id) }, onAbandon)
                        }
                    }
                    if (uiState.currentUser.profileType == ProfileType.PARENT && uiState.children.isNotEmpty()) {
                        item {
                            OutlinedButton(onClick = { onSetGoal(uiState.children.first().id) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = MaterialTheme.shapes.medium,
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
                                Text(stringResource(R.string.goals_child))
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.target_history), style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { heading() })
                                LotusCard(Modifier.fillMaxWidth()) {
                                    history.forEachIndexed { index, target ->
                                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant)
                                        GoalHistoryRow(target)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalProgress(target: Target, onContinue: () -> Unit, onNew: () -> Unit, onAbandon: () -> Unit) {
    val completed = target.isCompleted
    val numbers = NumberFormat.getIntegerInstance()
    val count = numbers.format(target.currentRecitationCount)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LotusCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(if (completed) R.string.targets_completed_goal_title else R.string.goals_japa),
                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.goals_started, goalDate(target)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    GoalArtwork(Modifier.size(64.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(count, style = if (count.length > 7) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.goals_counted, numbers.format(target.targetRecitationCount)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(progress = { target.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    Text(if (completed) stringResource(R.string.targets_percent_format, 100)
                        else stringResource(R.string.goals_remaining, numbers.format((target.targetRecitationCount - target.currentRecitationCount).coerceAtLeast(0))),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (target.hasReward) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.goals_reward), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(target.rewardDescription, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Text(stringResource(if (completed) R.string.goals_complete_body else R.string.goals_count_note),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp))
        Button(onClick = if (completed) onNew else onContinue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(if (completed) R.string.goals_new else R.string.goals_continue), Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
        }
        if (!completed) TextButton(onClick = onAbandon, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.target_abandon), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyGoal(onSetGoal: () -> Unit) {
    LotusCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            GoalArtwork(Modifier.size(80.dp))
            Text(stringResource(R.string.goals_empty_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.goals_empty_body), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onSetGoal, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(16.dp)) {
                Text(stringResource(R.string.target_set_new))
            }
        }
    }
}

@Composable
private fun GoalArtwork(modifier: Modifier) {
    AppArtwork(AppArt.MALA, modifier)
}

@Composable
private fun GoalHistoryRow(target: Target) {
    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Default.Check, null, Modifier.padding(8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.goals_history_count, NumberFormat.getIntegerInstance().format(target.targetRecitationCount)),
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.goals_started, goalDate(target)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (target.hasReward) Text(target.rewardDescription, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun goalDate(target: Target): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(target.startedAtMillis))
