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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mantrasiddhi.R
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import com.mantrasiddhi.presentation.components.PermissionHandler
import com.mantrasiddhi.presentation.screens.practice.components.AudioPlayerBar
import com.mantrasiddhi.presentation.screens.practice.components.MantraDisplay
import com.mantrasiddhi.presentation.screens.practice.components.MicButton
import com.mantrasiddhi.presentation.screens.practice.components.WordFeedbackGrid
import com.mantrasiddhi.presentation.theme.LocalExtendedColors
import com.mantrasiddhi.presentation.theme.ScoreBands
import com.mantrasiddhi.presentation.theme.ScoreLevel

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
    val hasSelfAudio by viewModel.hasSelfAudio.collectAsState()

    val context = LocalContext.current
    var requestMicPermission by remember { mutableStateOf(false) }

    val extendedColors = LocalExtendedColors.current

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

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.home_practice_mode),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Text(
                            text = stringResource(R.string.practice_time_seconds, state.secondsElapsed),
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
                    // Note: the "we couldn't hear you clearly" unclear-audio banner was
                    // intentionally removed. The pronunciation-not-assessed notice remains.
                    if (hasSelfAudio && !state.evaluation.acousticAssessed) {
                        DegradationNotice(
                            text = stringResource(R.string.practice_pronunciation_not_assessed),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    val score = state.evaluation.overallScore
                    val animatedScore by animateFloatAsState(
                        targetValue = score * 100f,
                        animationSpec = tween(durationMillis = 1000),
                        label = "scoreAnimation"
                    )

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.practice_overall_score),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val scoreLevel = ScoreBands.levelFor(score)
                            val scoreColor = when (scoreLevel) {
                                ScoreLevel.STRONG -> extendedColors.success
                                ScoreLevel.MODERATE -> extendedColors.warning
                                ScoreLevel.WEAK -> MaterialTheme.colorScheme.error
                            }

                            Text(
                                text = "${animatedScore.toInt()}%",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = scoreColor
                            )

                            val encouragement = when (scoreLevel) {
                                ScoreLevel.STRONG -> stringResource(R.string.practice_encouragement_excellent)
                                ScoreLevel.MODERATE -> stringResource(R.string.practice_encouragement_good)
                                ScoreLevel.WEAK -> stringResource(R.string.practice_encouragement_practice)
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

                    // Phase 5.5: opt-in Vedic swara/pitch score, shown only when the
                    // user enabled Advanced mode and pitch could be tracked. Separate
                    // from the headline score — it never changes it.
                    state.evaluation.swara?.takeIf { it.assessed }?.let { swara ->
                        SwaraCard(swara)
                    }

                    Text(
                        text = stringResource(R.string.practice_tap_word_chip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Start)
                    )

                    WordFeedbackGrid(
                        wordResults = state.evaluation.wordResults,
                        onWordClick = { _, index ->
                            // Tap a word to hear its correct pronunciation (no popup).
                            viewModel.playReferenceWord(index)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.resetPractice() },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.practice_try_again),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                
                is PracticeUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(vertical = 32.dp)
                    ) {
                        Text(
                            text = stringResource(id = state.messageResId),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.resetPractice() },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.practice_try_again),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A small, full-width banner used to clearly communicate a degraded or partial
 * assessment (Phase 5.4): unclear audio prompting a retry, or pronunciation that
 * could not be assessed acoustically.
 */
@Composable
private fun DegradationNotice(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

/**
 * Opt-in Vedic swara/pitch score card (Phase 5.5). Shows the pitch-match percentage
 * and a directional coaching hint. Purely additive — it does not affect the headline
 * recitation score.
 */
@Composable
private fun SwaraCard(swara: com.mantrasiddhi.domain.evaluation.acoustic.SwaraAssessment) {
    val hintRes = when (swara.hint) {
        com.mantrasiddhi.domain.evaluation.acoustic.SwaraAssessment.Hint.GOOD -> R.string.practice_swara_hint_good
        com.mantrasiddhi.domain.evaluation.acoustic.SwaraAssessment.Hint.FLAT -> R.string.practice_swara_hint_flat
        com.mantrasiddhi.domain.evaluation.acoustic.SwaraAssessment.Hint.WRONG_DIRECTION -> R.string.practice_swara_hint_direction
        com.mantrasiddhi.domain.evaluation.acoustic.SwaraAssessment.Hint.UNKNOWN -> R.string.practice_swara_not_assessed
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.practice_swara_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.practice_swara_accuracy, (swara.overallScore * 100).toInt()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}
