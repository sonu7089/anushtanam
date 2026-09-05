/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.recitation.components

import com.mantrasiddhi.presentation.components.LotusCard
import android.text.format.DateUtils
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.RecitationSession
import com.mantrasiddhi.presentation.theme.LocalExtendedColors

@Composable
fun RecitationFeed(
    sessions: List<RecitationSession>,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalExtendedColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.recitation_recent_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (sessions.isEmpty()) {
            LotusCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.recitation_feed_no_sessions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            sessions.forEach { session ->
                LotusCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val iconBg = if (session.isCorrect) {
                            extendedColors.successContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                        }
                        val iconTint = if (session.isCorrect) {
                            extendedColors.success
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        val icon = if (session.isCorrect) Icons.Default.Check else Icons.Default.Close

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = stringResource(
                                    id = if (session.isCorrect) {
                                        R.string.recitation_feed_correct_desc
                                    } else {
                                        R.string.recitation_feed_incorrect_desc
                                    }
                                ),
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            val displayText = if (session.transcription.isBlank()) {
                                stringResource(R.string.recitation_feed_silence)
                            } else {
                                "\"${session.transcription}\""
                            }
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            val timeAgo = DateUtils.getRelativeTimeSpanString(
                                session.createdAt,
                                System.currentTimeMillis(),
                                DateUtils.SECOND_IN_MILLIS
                            ).toString()

                            Text(
                                text = timeAgo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val scoreText = "${(session.overallScore * 100).toInt()}%"
                        Text(
                            text = scoreText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = iconTint
                        )
                    }
                }
            }
        }
    }
}
