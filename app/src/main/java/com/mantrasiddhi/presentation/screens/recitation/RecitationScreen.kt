/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.recitation

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.mantrasiddhi.R
import com.mantrasiddhi.presentation.components.PermissionHandler
import com.mantrasiddhi.presentation.components.RecitationFeedbackOverlay
import com.mantrasiddhi.presentation.screens.practice.components.MicButton
import com.mantrasiddhi.presentation.screens.recitation.components.CelebrationOverlay
import com.mantrasiddhi.presentation.screens.recitation.components.LiveCounter
import com.mantrasiddhi.presentation.screens.recitation.components.RecitationFeed
import com.mantrasiddhi.presentation.screens.recitation.components.TargetProgressHeader
import com.mantrasiddhi.presentation.screens.targets.components.SetTargetDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.height

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
                    snackbarHostState.showSnackbar(context.getString(R.string.targets_snack_error, event.message))
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
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_recitation),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
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
                val currentSessions = state.recentSessions
                val correctCount = currentSessions.filter { it.isCorrect }.size

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TargetProgressHeader(target = state.activeTarget)

                    RecitationFeedbackOverlay(
                        feedback = state.lastFeedback,
                        modifier = Modifier.fillMaxWidth()
                    )

                    LiveCounter(
                        sessionCount = correctCount,
                        isListening = state.isListening
                    )

                    MicButton(
                        isRecording = state.isListening,
                        isProcessing = false,
                        onClick = {
                            if (state.activeTarget != null) {
                                if (state.isListening) {
                                    viewModel.pauseRecitation()
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.isListening) {
                            FilledTonalButton(
                                onClick = { viewModel.pauseRecitation() },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.recitation_pause),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.stopRecitation() },
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.recitation_stop),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.startRecitation()
                                    } else {
                                        requestMicPermission = true
                                    }
                                },
                                enabled = state.activeTarget != null,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.recitation_begin),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    // Self-target setup: lets the reciter set their own goal without
                    // leaving the recitation tab. Shown when no target is active yet.
                    if (state.activeTarget == null) {
                        OutlinedButton(
                            onClick = { showSetTargetDialog = true },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.target_set_new),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    RecitationFeed(sessions = state.recentSessions)
                }

                if (showSetTargetDialog) {
                    SetTargetDialog(
                        currentUser = state.user,
                        children = emptyList(),
                        onDismiss = { showSetTargetDialog = false },
                        onConfirm = { count, money, _ ->
                            viewModel.createTarget(count, money)
                        }
                    )
                }

                if (state.showCelebration && state.activeTarget != null) {
                    CelebrationOverlay(
                        moneyAmount = state.activeTarget.moneyAmount,
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
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        }
    }
}
}

