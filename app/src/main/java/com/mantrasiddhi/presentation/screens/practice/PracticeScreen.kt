/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.practice

import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.evaluation.WordResult
import com.mantrasiddhi.presentation.components.MantraButton
import com.mantrasiddhi.presentation.components.MantraCard
import com.mantrasiddhi.presentation.components.PermissionHandler
import com.mantrasiddhi.presentation.screens.practice.components.AudioPlayerBar
import com.mantrasiddhi.presentation.screens.practice.components.FeedbackBottomSheet
import com.mantrasiddhi.presentation.screens.practice.components.MantraDisplay
import com.mantrasiddhi.presentation.screens.practice.components.MicButton
import com.mantrasiddhi.presentation.screens.practice.components.WordFeedbackGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    modifier: Modifier = Modifier,
    viewModel: PracticeModeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPlayingBenchmark by viewModel.isPlayingBenchmark.collectAsState()
    val benchmarkPositionMs by viewModel.benchmarkPositionMs.collectAsState()
    val activeBenchmarkWordIndex by viewModel.activeBenchmarkWordIndex.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    var selectedWordResult by remember { mutableStateOf<WordResult?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var requestMicPermission by remember { mutableStateOf(false) }

    if (requestMicPermission) {
        PermissionHandler(
            onPermissionGranted = {
                requestMicPermission = false
                viewModel.startRecording()
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

    if (showBottomSheet && selectedWordResult != null) {
        FeedbackBottomSheet(
            wordResult = selectedWordResult,
            onDismissRequest = {
                showBottomSheet = false
                selectedWordResult = null
            },
            sheetState = sheetState
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Benchmark Player Card
        AudioPlayerBar(
            isPlaying = isPlayingBenchmark,
            positionMs = benchmarkPositionMs,
            onPlayPauseClick = {
                if (isPlayingBenchmark) {
                    viewModel.pauseBenchmark()
                } else {
                    viewModel.playBenchmark()
                }
            }
        )

        // Mantra Display (Highlighting active word during benchmark playback)
        MantraDisplay(
            activeWordIndex = activeBenchmarkWordIndex
        )

        // Practice State Renderers
        when (val state = uiState) {
            is PracticeUiState.Idle -> {
                Text(
                    text = stringResource(R.string.practice_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                MicButton(
                    isRecording = false,
                    isProcessing = false,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startRecording()
                        } else {
                            requestMicPermission = true
                        }
                    }
                )
            }
            
            is PracticeUiState.Recording -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.practice_stop),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828)
                    )
                    
                    Text(
                        text = "Time: ${state.secondsElapsed}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )

                    if (state.partialText.isNotBlank()) {
                        Text(
                            text = "\"${state.partialText}\"",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }

                MicButton(
                    isRecording = true,
                    isProcessing = false,
                    onClick = { viewModel.stopRecording() }
                )
            }
            
            is PracticeUiState.Processing -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 32.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = stringResource(R.string.practice_processing),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            is PracticeUiState.ShowingResults -> {
                val score = state.evaluation.overallScore
                val animatedScore by animateFloatAsState(
                    targetValue = score * 100f,
                    animationSpec = tween(durationMillis = 1000),
                    label = "scoreAnimation"
                )

                MantraCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.practice_overall_score),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val scoreColor = when {
                            score >= 0.85f -> Color(0xFF2E7D32)
                            score >= 0.50f -> Color(0xFFE65100)
                            else -> Color(0xFFC62828)
                        }

                        Text(
                            text = "${animatedScore.toInt()}%",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = scoreColor
                        )

                        val encouragement = when {
                            score >= 0.85f -> "Excellent recitation! Perfectly aligned with the benchmark."
                            score >= 0.50f -> "Good effort! Tap on the yellow or red words below to see improvements."
                            else -> "Keep practicing! Take your time and pronounce each syllable clearly."
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = encouragement,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                Text(
                    text = "Tap on any word chip to review details:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )

                WordFeedbackGrid(
                    wordResults = state.evaluation.wordResults,
                    onWordClick = { result ->
                        selectedWordResult = result
                        showBottomSheet = true
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                MantraButton(
                    text = stringResource(R.string.practice_try_again),
                    onClick = { viewModel.resetPractice() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            is PracticeUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(vertical = 32.dp)
                ) {
                    Text(
                        text = state.message,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    MantraButton(
                        text = stringResource(R.string.practice_try_again),
                        onClick = { viewModel.resetPractice() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
