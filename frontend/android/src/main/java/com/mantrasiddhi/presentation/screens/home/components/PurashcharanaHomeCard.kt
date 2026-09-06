/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home.components

import com.mantrasiddhi.presentation.components.LotusCard
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.presentation.screens.purashcharana.components.PurashcharanaMala
import com.mantrasiddhi.presentation.screens.purashcharana.formatIndianCount
import com.mantrasiddhi.presentation.screens.purashcharana.formatPurashcharanaPercent
import com.mantrasiddhi.presentation.screens.purashcharana.milestoneTitleRes

/**
 * Home's doorway to the Purashcharana — the great sankalpa of one lakh japa per akshara.
 *
 * Before enrolling it is an invitation and nothing more: the vow is years of work, so the
 * card leads to the screen that explains it rather than offering a one-tap commitment.
 * Once enrolled it becomes a status line — where the count stands, how far in, which
 * stage is next — because that is what a sadhak opens the app wanting to see.
 *
 * @param purashcharana the active sankalpa, or `null` when none has been taken.
 * @param onClick opens the full Purashcharana screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurashcharanaHomeCard(
    purashcharana: Purashcharana?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LotusCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PurashcharanaMala(modifier = Modifier.size(76.dp))

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(id = R.string.home_purashcharana_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (purashcharana == null) {
                    Text(
                        text = stringResource(id = R.string.home_purashcharana_subtitle_open),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.home_purashcharana_cta),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = stringResource(
                            id = R.string.home_purashcharana_subtitle_enrolled,
                            formatIndianCount(purashcharana.currentJapaCount),
                            formatIndianCount(purashcharana.targetJapaCount)
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(
                            id = R.string.purashcharana_percent_format,
                            formatPurashcharanaPercent(purashcharana.progress)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(
                            id = R.string.purashcharana_day_number,
                            purashcharana.dayNumber(System.currentTimeMillis())
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Once every stage is behind them there is no "next" to point at, so the
                    // line becomes the arrival itself.
                    val nextMilestone = purashcharana.nextMilestone()
                    if (nextMilestone != null) {
                        Text(
                            text = stringResource(
                                id = R.string.purashcharana_next_milestone_format,
                                stringResource(id = milestoneTitleRes(nextMilestone.stage))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.purashcharana_complete_title),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}
