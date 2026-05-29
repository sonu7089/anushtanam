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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordFeedbackGrid(
    wordResults: List<WordResult>,
    onWordClick: (WordResult, Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
                    Color.Gray.copy(alpha = 0.1f),
                    Color.Gray,
                    Color.Gray.copy(alpha = 0.3f)
                )
                score >= 0.85f -> Triple(
                    Color(0xFFE8F5E9), // Light Green
                    Color(0xFF2E7D32), // Dark Green
                    Color(0xFF81C784)
                )
                score >= 0.50f -> Triple(
                    Color(0xFFFFFDE7), // Light Yellow/Gold
                    Color(0xFFE65100), // Orange-Gold
                    Color(0xFFFFD54F)
                )
                else -> Triple(
                    Color(0xFFFFEBEE), // Light Red
                    Color(0xFFC62828), // Dark Red
                    Color(0xFFE57373)
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = textColor
                )
            }
        }
    }
}
