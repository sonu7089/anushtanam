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
    modifier: Modifier = Modifier
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
                text = "Spoken: ${wordResult.spokenWord ?: "— (Skipped)"}",
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
                text = "Accuracy: $scorePercentage%",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = scoreColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            val suggestion = wordResult.feedback?.message 
                ?: "Perfect pronunciation! Continue chanting with the same devotion."
            
            val spiritualTip = ImprovementSuggestionEngine.getSuggestionForWord(wordResult.referenceWord)

            Text(
                text = suggestion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${stringResource(R.string.practice_tip_title)}: $spiritualTip",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onReplayClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.practice_listen_reference))
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
