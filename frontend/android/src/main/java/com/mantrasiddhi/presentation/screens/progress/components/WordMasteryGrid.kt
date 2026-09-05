/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress.components

import com.mantrasiddhi.presentation.components.LotusCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.theme.LocalExtendedColors
import com.mantrasiddhi.presentation.theme.ScoreBands
import com.mantrasiddhi.presentation.theme.ScoreLevel
import com.mantrasiddhi.domain.usecase.WordMastery

/**
 * Visual grid showing per-word accuracy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordMasteryGrid(
    wordMasteryList: List<WordMastery>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val extendedColors = LocalExtendedColors.current
        // Color Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            LegendItem(label = stringResource(R.string.progress_legend_mastered), color = extendedColors.success)
            LegendItem(label = stringResource(R.string.progress_legend_learning), color = extendedColors.warning)
            LegendItem(label = stringResource(R.string.progress_legend_focus), color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Heatmap tags
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.Center,
            maxItemsInEachRow = 3
        ) {
            wordMasteryList.forEach { item ->
                val (bgColor, textColor, borderColor) = getMasteryColors(item.averageScore, item.totalAttempts)

                LotusCard(
                    modifier = Modifier
                        .padding(6.dp)
                        .width(105.dp)
                        .border(1.dp, borderColor, MaterialTheme.shapes.small),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.word,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (item.totalAttempts == 0) "—" else "${(item.averageScore * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun getMasteryColors(score: Double, attempts: Int): Triple<Color, Color, Color> {
    val extendedColors = LocalExtendedColors.current
    if (attempts == 0) {
        // Default surfaceVariant state
        return Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
    }

    return when (ScoreBands.levelFor(score, ScoreBands.MASTERY_MODERATE_THRESHOLD)) {
        ScoreLevel.STRONG -> Triple(
            extendedColors.successContainer.copy(alpha = 0.2f),
            extendedColors.success,
            extendedColors.success.copy(alpha = 0.4f)
        )
        ScoreLevel.MODERATE -> Triple(
            extendedColors.warningContainer.copy(alpha = 0.2f),
            extendedColors.warning,
            extendedColors.warning.copy(alpha = 0.4f)
        )
        ScoreLevel.WEAK -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        )
    }
}
