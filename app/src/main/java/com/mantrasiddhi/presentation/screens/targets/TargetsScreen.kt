/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.targets

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.R
import com.mantrasiddhi.presentation.components.MantraButton
import com.mantrasiddhi.presentation.screens.targets.components.CompletedTargetItem
import com.mantrasiddhi.presentation.screens.targets.components.SetTargetDialog
import com.mantrasiddhi.presentation.screens.targets.components.TargetCard

@Composable
fun TargetsScreen(
    modifier: Modifier = Modifier,
    viewModel: TargetsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showSetDialog by remember { mutableStateOf(false) }
    var targetToAbandonUserId by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(key1 = true) {
        viewModel.events.collect { event ->
            when (event) {
                is TargetsEvent.TargetCreated -> {
                    showSetDialog = false
                    snackbarHostState.showSnackbar("Target set successfully!")
                }
                is TargetsEvent.TargetAbandoned -> {
                    targetToAbandonUserId = null
                    snackbarHostState.showSnackbar("Target abandoned. Locked money returned.")
                }
                is TargetsEvent.ShowError -> {
                    snackbarHostState.showSnackbar("Error: ${event.message}")
                }
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is TargetsUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = stringResource(id = R.string.targets_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Active Target display
                        item {
                            Text(
                                text = "Active Target",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                            
                            MantraButton(
                                text = stringResource(id = R.string.target_set_new),
                                onClick = { showSetDialog = true },
                                enabled = isSetEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // Target history
                        if (state.completedTargets.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(id = R.string.target_history),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            items(state.completedTargets) { completedTarget ->
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
                            onConfirm = { count, money, targetUserId ->
                                viewModel.createTarget(count, money, targetUserId)
                            }
                        )
                    }
                    
                    // Confirm Abandon Dialog
                    if (targetToAbandonUserId != null) {
                        val activeTarget = state.activeTarget
                        val lockedAmt = activeTarget?.moneyAmount ?: 0.0
                        
                        AlertDialog(
                            onDismissRequest = { targetToAbandonUserId = null },
                            title = { Text(text = "Abandon Target") },
                            text = { 
                                Text(
                                    text = stringResource(
                                        id = R.string.target_abandon_confirm, 
                                        lockedAmt
                                    )
                                ) 
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = { viewModel.abandonTarget(targetToAbandonUserId) }
                                ) {
                                    Text(text = "Abandon", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { targetToAbandonUserId = null }) {
                                    Text(text = "Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
