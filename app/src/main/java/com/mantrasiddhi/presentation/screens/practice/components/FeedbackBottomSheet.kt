/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.practice.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.evaluation.ImprovementSuggestionEngine
import com.mantrasiddhi.domain.evaluation.WordResult

import com.mantrasiddhi.presentation.feedback.contrastiveTipText
import com.mantrasiddhi.presentation.theme.LocalExtendedColors
import com.mantrasiddhi.presentation.theme.ScoreBands
import com.mantrasiddhi.presentation.theme.ScoreLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackBottomSheet(
    wordResult: WordResult?,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    onReplayClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaySelfClick: (() -> Unit)? = null
) {
    if (wordResult == null) return

    val extendedColors = LocalExtendedColors.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${stringResource(R.string.practice_feedback_title)}: ${wordResult.referenceWord.uppercase()}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(
                    R.string.practice_feedback_spoken,
                    wordResult.spokenWord ?: stringResource(R.string.practice_feedback_skipped)
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            val scorePercentage = (wordResult.score * 100).toInt()
            val scoreColor = when (ScoreBands.levelFor(wordResult.score)) {
                ScoreLevel.STRONG -> extendedColors.success
                ScoreLevel.MODERATE -> extendedColors.warning
                ScoreLevel.WEAK -> MaterialTheme.colorScheme.error
            }

            Text(
                text = stringResource(R.string.practice_feedback_accuracy, scorePercentage),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Syllable-level, contrastive coaching (Phase 4.1): names the specific
            // syllable/feature that needs work, sourced from strings.xml.
            val tip = ImprovementSuggestionEngine.tipForWordResult(wordResult)
            val feedbackText = tip?.let { contrastiveTipText(it) }
                ?: stringResource(R.string.feedback_word_accurate, wordResult.referenceWord)

            Text(
                text = feedbackText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onReplayClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.practice_listen_reference_word))
            }

            if (onPlaySelfClick != null) {
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onPlaySelfClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.practice_listen_self))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.recitation_dismiss))
            }
        }
    }
}
