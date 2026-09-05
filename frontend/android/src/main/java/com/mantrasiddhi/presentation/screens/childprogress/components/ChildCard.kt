package com.mantrasiddhi.presentation.screens.childprogress.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.domain.model.ChildProgress
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.presentation.components.ProfileAvatar

@Composable
fun ChildCard(childProgress: ChildProgress, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LotusCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(childProgress.child.avatarIndex, childProgress.child.name, 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(childProgress.child.name, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.child_total_recitations, childProgress.totalRecitations),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val activeTarget = childProgress.activeTarget
            val targetText = if (activeTarget != null) {
                stringResource(R.string.home_target_progress, activeTarget.currentRecitationCount,
                    activeTarget.targetRecitationCount)
            } else {
                stringResource(R.string.no_active_targets)
            }
            Text(stringResource(R.string.child_card_sankalpa_format, targetText),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.child_goals_completed_label), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(childProgress.completedTargetCount.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
