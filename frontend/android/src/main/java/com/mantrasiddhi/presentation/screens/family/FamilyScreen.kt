/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.family

import androidx.compose.foundation.layout.imePadding
import com.mantrasiddhi.presentation.components.PageIntro
import com.mantrasiddhi.presentation.components.LotusTopAppBar
import com.mantrasiddhi.presentation.components.LotusCard
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.CloudGoal
import com.mantrasiddhi.domain.model.ParentChildLink
import com.mantrasiddhi.presentation.components.ProgressRing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    viewModel: FamilyViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { key ->
            val text = when (key) {
                "link_request_sent" -> context.getString(R.string.family_link_request_sent)
                "link_approved" -> context.getString(R.string.family_link_approved)
                "goal_created" -> context.getString(R.string.family_goal_created)
                "generic_error" -> context.getString(R.string.family_generic_error)
                else -> key // raw error text from the backend
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    var goalForChild by remember { mutableStateOf<ParentChildLink?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            LotusTopAppBar(
                title = { Text(stringResource(R.string.family_title), fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.auth_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp).imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            PageIntro(stringResource(R.string.lotus_family_intro), stringResource(R.string.lotus_family_description))
            when (uiState.account?.role) {
                AccountRole.PARENT -> ParentSection(
                    uiState = uiState,
                    onRequestLink = viewModel::requestLink,
                    onSelectChild = viewModel::selectChild,
                    onSetGoal = { goalForChild = it }
                )
                AccountRole.CHILD -> ChildSection(
                    uiState = uiState,
                    onApprove = viewModel::approve,
                    onReject = viewModel::reject
                )
                null -> Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    goalForChild?.let { child ->
        CloudGoalDialog(
            childName = child.childName,
            onDismiss = { goalForChild = null },
            onConfirm = { rewardType, desc, count ->
                viewModel.createGoal(child.childUid, rewardType, desc, count)
                goalForChild = null
            }
        )
    }
}

@Composable
internal fun ParentSection(
    uiState: FamilyUiState,
    onRequestLink: (String) -> Unit,
    onSelectChild: (ParentChildLink?) -> Unit,
    onSetGoal: (ParentChildLink) -> Unit
) {
    var code by remember { mutableStateOf("") }

    LotusCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.family_link_child_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.family_link_child_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 12 && it.all(Char::isDigit)) code = it },
                label = { Text(stringResource(R.string.family_child_id_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onRequestLink(code); code = "" },
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.family_send_request))
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = stringResource(R.string.family_your_children),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(8.dp))

    if (uiState.linkedChildren.isEmpty()) {
        Text(
            text = stringResource(R.string.family_no_children),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        uiState.linkedChildren.forEach { child ->
            val isSelected = uiState.selectedChild?.childUid == child.childUid
            LotusCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = child.childName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onSelectChild(if (isSelected) null else child) }) {
                                Text(
                                    stringResource(
                                        if (isSelected) R.string.family_hide_goals else R.string.family_view_goals
                                    )
                                )
                            }
                            FilledTonalButton(onClick = { onSetGoal(child) }) {
                                Text(stringResource(R.string.family_set_goal))
                            }
                        }
                    }
                    if (isSelected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (uiState.selectedChildGoals.isEmpty()) {
                            Text(
                                text = stringResource(R.string.family_no_goals_for_child),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.selectedChildGoals.forEach { goal ->
                                GoalRow(goal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChildSection(
    uiState: FamilyUiState,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val myId = uiState.account?.linkCode

    LotusCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.family_your_id_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.family_your_id_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = myId ?: "—",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (myId != null) {
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(myId))
                        Toast.makeText(context, context.getString(R.string.auth_child_id_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.auth_copy))
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = stringResource(R.string.family_link_requests),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (uiState.pendingRequests.isEmpty()) {
        Text(
            text = stringResource(R.string.family_no_requests),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        uiState.pendingRequests.forEach { req ->
            LotusCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = stringResource(R.string.family_request_from, req.parentName),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApprove(req.id) }) {
                            Text(stringResource(R.string.family_approve))
                        }
                        OutlinedButton(onClick = { onReject(req.id) }) {
                            Text(stringResource(R.string.family_reject))
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    Text(
        text = stringResource(R.string.family_goals_from_parent),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (uiState.myGoals.isEmpty()) {
        Text(
            text = stringResource(R.string.family_no_goals),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        uiState.myGoals.forEach { goal ->
            LotusCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(24.dp)) { GoalRow(goal) }
            }
        }
    }
}

@Composable
private fun GoalRow(goal: CloudGoal) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            ProgressRing(progress = goal.progress, strokeWidth = 6.dp) {
                Text(
                    text = "${(goal.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(0.dp))
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = stringResource(
                    R.string.family_goal_progress,
                    goal.currentRecitationCount,
                    goal.targetRecitationCount
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (goal.hasReward) {
                Text(
                    text = "${goal.rewardType.emoji} ${goal.rewardDescription}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (goal.isCompleted) {
                Text(
                    text = stringResource(R.string.family_goal_completed),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
