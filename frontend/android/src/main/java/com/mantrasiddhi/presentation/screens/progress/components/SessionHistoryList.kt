/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.progress.components

import com.mantrasiddhi.presentation.components.LotusCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.theme.LocalExtendedColors
import com.mantrasiddhi.domain.model.RecitationMode
import com.mantrasiddhi.domain.model.RecitationSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Scrollable list of past recitation sessions.
 */
@Composable
fun SessionHistoryList(
    sessions: List<RecitationSession>,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true,
    footer: @Composable () -> Unit = {}
) {
    val locale = LocalConfiguration.current.locales[0]
    // One resume/clock listener for the list, not a formatter/listener for each row.
    val timeRevision = com.mantrasiddhi.presentation.components.rememberDeadlineTime(emptyList())
    val zone = remember(timeRevision) { ZoneId.systemDefault() }
    val dateFormat = remember(locale, zone) {
        DateTimeFormatter.ofPattern("MMM dd, yyyy · hh:mm a", locale).withZone(zone)
    }
    if (sessions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.progress_no_sessions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (isScrollable) {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                SessionHistoryItem(session = session, dateFormat = dateFormat)
            }
            item(key = "history_footer") { footer() }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth()
        ) {
            sessions.forEach { session ->
                SessionHistoryItem(session = session, dateFormat = dateFormat)
            }
        }
    }
}

@Composable
private fun SessionHistoryItem(session: RecitationSession, dateFormat: DateTimeFormatter) {
    val extendedColors = LocalExtendedColors.current
    val scorePercent = (session.overallScore * 100).toInt()
    val badgeColor = when {
        session.isCorrect -> extendedColors.success
        session.overallScore >= 0.70 -> extendedColors.warning
        else -> MaterialTheme.colorScheme.error
    }

    val formattedDate = remember(session.createdAt, dateFormat) {
        dateFormat.format(Instant.ofEpochMilli(session.createdAt))
    }

    val modeLabel = if (session.mode == RecitationMode.PRACTICE) {
        stringResource(R.string.progress_mode_practice)
    } else {
        stringResource(R.string.progress_mode_recitation)
    }
    val modeColor = if (session.mode == RecitationMode.PRACTICE) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.primary
    }

    LotusCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left score badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$scorePercent%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = badgeColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Center details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = modeLabel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = modeColor
                    )
                    if (session.isCorrect) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(extendedColors.successContainer.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.progress_correct_badge),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = extendedColors.success
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Right duration
            val durationSec = stringResource(
                id = R.string.progress_session_duration_format,
                session.durationMs / 1000f
            )
            Text(
                text = durationSec,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
