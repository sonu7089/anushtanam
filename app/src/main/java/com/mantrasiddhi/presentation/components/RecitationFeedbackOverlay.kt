/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
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
import com.mantrasiddhi.R
import com.mantrasiddhi.presentation.screens.recitation.RecitationFeedbackState

@Composable
fun RecitationFeedbackOverlay(
    feedback: RecitationFeedbackState?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        if (feedback != null) {
            val styleData = if (feedback.isCorrect) {
                FeedbackStyle(
                    backgroundColor = Color(0xFFE8F5E9),
                    tintColor = Color(0xFF2E7D32),
                    icon = Icons.Default.CheckCircle,
                    statusText = stringResource(R.string.recitation_correct)
                )
            } else {
                FeedbackStyle(
                    backgroundColor = Color(0xFFFFEBEE),
                    tintColor = Color(0xFFC62828),
                    icon = Icons.Default.Warning,
                    statusText = stringResource(R.string.recitation_try_again)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(styleData.backgroundColor)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = styleData.icon,
                        contentDescription = null,
                        tint = styleData.tintColor,
                        modifier = Modifier.size(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = styleData.statusText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = styleData.tintColor
                            )
                            Text(
                                text = "${(feedback.overallScore * 100).toInt()}% Accuracy",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = styleData.tintColor
                            )
                        }

                        if (!feedback.isCorrect && feedback.worstWord != null && feedback.suggestion != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Focus on \"${feedback.worstWord}\": ${feedback.suggestion}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class FeedbackStyle(
    val backgroundColor: Color,
    val tintColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val statusText: String
)
