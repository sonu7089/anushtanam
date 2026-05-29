/*
 * Mantra Siddhi - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.targets.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.Target
import com.mantrasiddhi.presentation.components.MantraCard
import com.mantrasiddhi.presentation.theme.LocalExtendedColors

@Composable
fun CompletedTargetItem(
    target: Target,
    modifier: Modifier = Modifier,
    ownerName: String? = null
) {
    val successColor = LocalExtendedColors.current.success
    MantraCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(id = R.string.targets_completed_description),
                    tint = successColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (ownerName != null) {
                            stringResource(id = R.string.child_details_title, ownerName)
                        } else {
                            stringResource(id = R.string.targets_completed_goal_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            id = R.string.targets_completed_recitations_count,
                            target.targetRecitationCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            if (target.moneyAmount > 0.0) {
                Text(
                    text = "+₹${String.format("%.2f", target.moneyAmount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = successColor
                )
            }
        }
    }
}
