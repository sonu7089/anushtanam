/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        // Color Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            LegendItem(label = "Mastered (≥85%)", color = Color(0xFF4CAF50))
            LegendItem(label = "Learning (70-85%)", color = Color(0xFFFF9800))
            LegendItem(label = "Focus (<70%)", color = Color(0xFFE53935))
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

                Card(
                    modifier = Modifier
                        .padding(6.dp)
                        .width(105.dp)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    shape = RoundedCornerShape(8.dp)
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
                            fontWeight = FontWeight.Bold,
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
                .clip(RoundedCornerShape(3.dp))
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

private fun getMasteryColors(score: Double, attempts: Int): Triple<Color, Color, Color> {
    if (attempts == 0) {
        // Default grey state
        return Triple(
            Color(0xFFF5F5F5),
            Color(0xFF757575),
            Color(0xFFE0E0E0)
        )
    }

    return when {
        score >= 0.85 -> Triple(
            Color(0xFFE8F5E9), // Light green background
            Color(0xFF2E7D32), // Dark green text
            Color(0xFFA5D6A7)  // Medium green border
        )
        score >= 0.70 -> Triple(
            Color(0xFFFFF3E0), // Light orange background
            Color(0xFFE65100), // Dark orange text
            Color(0xFFFFCC80)  // Medium orange border
        )
        else -> Triple(
            Color(0xFFFFEBEE), // Light red background
            Color(0xFFC62828), // Dark red text
            Color(0xFFEF9A9A)  // Medium red border
        )
    }
}
