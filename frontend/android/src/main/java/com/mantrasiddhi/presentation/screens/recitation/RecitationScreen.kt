/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.recitation

import androidx.compose.foundation.layout.size
import com.mantrasiddhi.presentation.components.LotusArt
import com.mantrasiddhi.presentation.components.LotusArtwork
import androidx.compose.foundation.layout.heightIn
import com.mantrasiddhi.presentation.components.LotusTopAppBar
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.components.PermissionHandler
import com.mantrasiddhi.presentation.components.RecitationFeedbackOverlay
import com.mantrasiddhi.presentation.screens.practice.components.MicButton
import com.mantrasiddhi.presentation.screens.recitation.components.CelebrationOverlay
import com.mantrasiddhi.presentation.screens.recitation.components.JapaCountedPulse
import com.mantrasiddhi.presentation.screens.recitation.components.TargetProgressHeader
import com.mantrasiddhi.presentation.screens.targets.components.SetTargetDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecitationScreen(
    modifier: Modifier = Modifier,
    viewModel: RecitationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var requestMicPermission by remember { mutableStateOf(false) }
    var showSetTargetDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(key1 = true) {
        viewModel.events.collect { event ->
            when (event) {
                is RecitationEvent.TargetCreated -> {
                    showSetTargetDialog = false
                    snackbarHostState.showSnackbar(context.getString(R.string.targets_snack_set_success))
                }
                is RecitationEvent.ShowError -> {
                    // Prefer the failure's own detail when the layer below supplied one;
                    // otherwise fall back to the localized reason, so the sadhak never
                    // sees a raw English default string.
                    val reason = event.detail?.takeIf { it.isNotBlank() }
                        ?: context.getString(event.messageResId)
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.targets_snack_error, reason)
                    )
                }
            }
        }
    }

    if (requestMicPermission) {
        PermissionHandler(
            onPermissionGranted = {
                requestMicPermission = false
                viewModel.startRecitation()
            },
            onPermissionDenied = {
                requestMicPermission = false
            },
            rationaleContent = { onRequestPermission ->
                LaunchedEffect(Unit) {
                    onRequestPermission()
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            LotusTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_recitation),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is RecitationUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            
            is RecitationUiState.Active -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LotusArtwork(LotusArt.DIYA, Modifier.size(104.dp))
                    Text(stringResource(R.string.lotus_recitation_intro), style = MaterialTheme.typography.headlineSmall)
                    TargetProgressHeader(target = state.activeTarget)

                    RecitationFeedbackOverlay(
                        feedback = state.lastFeedback,
                        modifier = Modifier.fillMaxWidth()
                    )

                    MicButton(
                        isRecording = state.isListening,
                        isProcessing = false,
                        onClick = {
                            if (state.activeTarget != null) {
                                if (state.isListening) {
                                    // Stop flushes the pending (already recited) mantra to the
                                    // evaluator first, so tapping the mic right after finishing
                                    // still counts that recitation.
                                    viewModel.stopRecitation()
                                } else {
                                    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.startRecitation()
                                    } else {
                                        requestMicPermission = true
                                    }
                                }
                            }
                        }
                    )

                    if (state.isListening && state.currentSpeechText.isNotBlank()) {
                        Text(
                            text = "\"${state.currentSpeechText}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    // The mic button above is the single start/stop control — tap to begin
                    // reciting, tap again to stop. No separate pause/stop buttons.

                    // Self-target setup: lets the reciter set their own goal without
                    // leaving the recitation tab. Shown when no target is active yet.
                    if (state.activeTarget == null) {
                        OutlinedButton(
                            onClick = { showSetTargetDialog = true },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.target_set_new),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }

                if (showSetTargetDialog) {
                    SetTargetDialog(
                        currentUser = state.user,
                        children = emptyList(),
                        onDismiss = { showSetTargetDialog = false },
                        onConfirm = { count, rewardType, rewardDescription, _ ->
                            viewModel.createTarget(count, rewardType, rewardDescription)
                        }
                    )
                }

                if (state.showCelebration && state.celebrationTarget != null) {
                    CelebrationOverlay(
                        rewardType = state.celebrationTarget.rewardType,
                        rewardDescription = state.celebrationTarget.rewardDescription,
                        onDismiss = { viewModel.dismissCelebration() }
                    )
                }
            }
            
            is RecitationUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = state.messageResId),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }

            // Last child of the outermost Box on purpose: the confirmation belongs to the
            // whole screen and is centred on it, not on the progress card or the mic. It
            // declares no pointer input, so taps still reach the mic button beneath it.
            (uiState as? RecitationUiState.Active)?.let { activeState ->
                JapaCountedPulse(pulseId = activeState.japaCountedPulseId)
            }
    }
}
}

