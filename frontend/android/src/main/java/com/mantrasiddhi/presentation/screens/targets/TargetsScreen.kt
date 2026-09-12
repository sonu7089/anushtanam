/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.targets

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.screens.targets.components.SetTargetDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(
    onBack: () -> Unit,
    onContinueJapa: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TargetsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var initialGoalUserId by remember { mutableStateOf<Long?>(null) }
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

    TargetsContent(
        uiState = uiState,
        onBack = onBack,
        onContinueJapa = onContinueJapa,
        onSetGoal = { userId ->
            initialGoalUserId = userId
            showSetDialog = true
        },
        onAbandon = {
            targetToAbandonUserId = (uiState as? TargetsUiState.Success)?.currentUser?.id
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
    val state = uiState as? TargetsUiState.Success ?: return
    // Show Set Target Dialog
    if (showSetDialog) {
        SetTargetDialog(
            currentUser = state.currentUser,
            children = state.children,
            initialUserId = initialGoalUserId,
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
