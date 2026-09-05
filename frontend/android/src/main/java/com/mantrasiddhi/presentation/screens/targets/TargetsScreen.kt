/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.targets

import com.mantrasiddhi.presentation.components.LotusArt
import com.mantrasiddhi.presentation.components.PageIntro
import androidx.compose.foundation.layout.heightIn
import com.mantrasiddhi.presentation.components.LotusTopAppBar
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.screens.targets.components.CompletedTargetItem
import com.mantrasiddhi.presentation.screens.targets.components.SetTargetDialog
import com.mantrasiddhi.presentation.screens.targets.components.TargetCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(
    modifier: Modifier = Modifier,
    viewModel: TargetsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    var showSetDialog by remember { mutableStateOf(false) }
    var targetToAbandonUserId by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(key1 = true) {
        viewModel.events.collect { event ->
            when (event) {
                is TargetsEvent.TargetCreated -> {
                    showSetDialog = false
                    snackbarHostState.showSnackbar(context.getString(R.string.targets_snack_set_success))
                }
                is TargetsEvent.TargetAbandoned -> {
                    targetToAbandonUserId = null
                    snackbarHostState.showSnackbar(context.getString(R.string.targets_snack_abandoned))
                }
                is TargetsEvent.ShowError -> {
                    // Prefer the failure's own detail when the layer below supplied one;
                    // it names the actual problem better than the generic sentence can.
                    val reason = event.detail?.takeIf { it.isNotBlank() }
                        ?: context.getString(event.messageResId)
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.targets_snack_error, reason)
                    )
                }
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LotusTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.targets_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is TargetsUiState.Loading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                is TargetsUiState.Error -> {
                    Text(
                        text = stringResource(id = state.messageResId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                is TargetsUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            PageIntro(stringResource(R.string.lotus_targets_intro),
                                stringResource(R.string.lotus_targets_description), LotusArt.RITUAL)
                        }
                        // Active Target display
                        item {
                            Text(
                                text = stringResource(id = R.string.home_active_target),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        if (state.activeTarget != null) {
                            item {
                                TargetCard(
                                    target = state.activeTarget,
                                    onAbandonClick = { targetToAbandonUserId = state.currentUser.id }
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = stringResource(id = R.string.no_active_targets),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                        
                        // Action buttons
                        item {
                            // "Set New Target" button: enabled if user has no active target
                            // (If parent, they can set for child even if they have active target themselves,
                            //  so we enable it and handle active target validation in dialog/usecase)
                            val isSetEnabled = state.activeTarget == null || state.currentUser.profileType == com.mantrasiddhi.domain.model.ProfileType.PARENT
                            
                            Button(
                                onClick = { showSetDialog = true },
                                enabled = isSetEnabled,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.target_set_new),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                        
                        // Target history
                        if (state.completedTargets.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(id = R.string.target_history),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            items(state.completedTargets, key = { it.id }) { completedTarget ->
                                CompletedTargetItem(target = completedTarget)
                            }
                        }
                    }
                    
                    // Show Set Target Dialog
                    if (showSetDialog) {
                        SetTargetDialog(
                            currentUser = state.currentUser,
                            children = state.children,
                            onDismiss = { showSetDialog = false },
                            onConfirm = { count, rewardType, rewardDescription, targetUserId ->
                                viewModel.createTarget(count, rewardType, rewardDescription, targetUserId)
                            }
                        )
                    }
                    
                    // Confirm Abandon Dialog
                    if (targetToAbandonUserId != null) {
                        AlertDialog(
                            onDismissRequest = { targetToAbandonUserId = null },
                            title = { Text(text = stringResource(id = R.string.targets_abandon_dialog_title)) },
                            text = {
                                Text(
                                    text = stringResource(id = R.string.target_abandon_confirm)
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { viewModel.abandonTarget(targetToAbandonUserId) }
                                ) {
                                    Text(text = stringResource(id = R.string.targets_abandon_confirm_btn), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { targetToAbandonUserId = null }) {
                                    Text(text = stringResource(id = R.string.targets_abandon_cancel_btn))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
