/*
 * Anushtanam - Gayatri Mantra Recitation App
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
import com.mantrasiddhi.presentation.theme.LocalExtendedColors

@Composable
fun RecitationFeedbackOverlay(
    feedback: RecitationFeedbackState?,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current

    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        if (feedback != null) {
            val styleData = if (feedback.isCorrect) {
                FeedbackStyle(
                    backgroundColor = extendedColors.successContainer,
                    tintColor = extendedColors.onSuccessContainer,
                    icon = Icons.Default.CheckCircle,
                    statusText = stringResource(R.string.recitation_correct)
                )
            } else {
                FeedbackStyle(
                    backgroundColor = MaterialTheme.colorScheme.errorContainer,
                    tintColor = MaterialTheme.colorScheme.onErrorContainer,
                    icon = Icons.Default.Warning,
                    statusText = stringResource(R.string.recitation_try_again)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(MaterialTheme.shapes.large)
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
                        // Accuracy percentage intentionally not shown in Recitation mode —
                        // the reciter just needs the correct/try-again status while counting.
                        Text(
                            text = styleData.statusText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = styleData.tintColor
                        )

                        if (!feedback.isCorrect && feedback.worstWord != null && feedback.suggestion != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.recitation_feedback_focus_format, feedback.worstWord, feedback.suggestion),
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
