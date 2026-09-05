/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.practice.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.domain.evaluation.WordResult

import com.mantrasiddhi.presentation.theme.LocalExtendedColors
import com.mantrasiddhi.presentation.theme.ScoreBands
import com.mantrasiddhi.presentation.theme.ScoreLevel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordFeedbackGrid(
    wordResults: List<WordResult>,
    onWordClick: (WordResult, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current
    val outlineColor = MaterialTheme.colorScheme.outline
    val errorColor = MaterialTheme.colorScheme.error
    val errorContainerColor = MaterialTheme.colorScheme.errorContainer

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        wordResults.forEachIndexed { index, result ->
            val score = result.score
            val isMissing = result.spokenWord == null
            
            val (backgroundColor, textColor, borderColor) = when {
                isMissing -> Triple(
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f),
                    outlineColor,
                    outlineColor.copy(alpha = 0.3f)
                )
                ScoreBands.levelFor(score) == ScoreLevel.STRONG -> Triple(
                    extendedColors.successContainer.copy(alpha = 0.15f),
                    extendedColors.success,
                    extendedColors.success.copy(alpha = 0.3f)
                )
                ScoreBands.levelFor(score) == ScoreLevel.MODERATE -> Triple(
                    extendedColors.warningContainer.copy(alpha = 0.15f),
                    extendedColors.warning,
                    extendedColors.warning.copy(alpha = 0.3f)
                )
                else -> Triple(
                    errorContainerColor.copy(alpha = 0.15f),
                    errorColor,
                    errorColor.copy(alpha = 0.3f)
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .border(1.dp, borderColor, CircleShape)
                    .clickable { onWordClick(result, index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = result.referenceWord,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp
                    ),
                    color = textColor
                )
            }
        }
    }
}
