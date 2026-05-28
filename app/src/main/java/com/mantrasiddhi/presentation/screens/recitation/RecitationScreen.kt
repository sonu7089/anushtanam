/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.recitation

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.R
import com.mantrasiddhi.presentation.components.MantraButton
import com.mantrasiddhi.presentation.components.RecitationFeedbackOverlay
import com.mantrasiddhi.presentation.screens.practice.components.MicButton
import com.mantrasiddhi.presentation.screens.recitation.components.CelebrationOverlay
import com.mantrasiddhi.presentation.screens.recitation.components.LiveCounter
import com.mantrasiddhi.presentation.screens.recitation.components.RecitationFeed
import com.mantrasiddhi.presentation.screens.recitation.components.TargetProgressHeader

@Composable
fun RecitationScreen(
    modifier: Modifier = Modifier,
    viewModel: RecitationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
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
                                    viewModel.startRecitation()
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
                            MantraButton(
                                text = stringResource(R.string.recitation_pause),
                                onClick = { viewModel.pauseRecitation() },
                                modifier = Modifier.weight(1f)
                            )
                            MantraButton(
                                text = stringResource(R.string.recitation_stop),
                                onClick = { viewModel.stopRecitation() },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            MantraButton(
                                text = stringResource(R.string.recitation_begin),
                                onClick = { viewModel.startRecitation() },
                                enabled = state.activeTarget != null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    RecitationFeed(sessions = state.recentSessions)
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
