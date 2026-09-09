package com.mantrasiddhi.presentation.screens.sandhya

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.domain.model.SandhyaCountDraft
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.resources.R

@Composable
internal fun SandhyaDraftCard(draft: SandhyaCountDraft, onResume: () -> Unit) {
    LotusCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SandhyaHeaderRow(
                title = { Text(stringResource(R.string.sandhya_unfinished_count), style = MaterialTheme.typography.titleMedium) },
                action = {
                    TextButton(onClick = onResume, contentPadding = PaddingValues(vertical = 12.dp)) {
                        Text(stringResource(R.string.sandhya_count_resume))
                    }
                }
            )
            Text(mantraCountText(draft.quantity ?: 0), style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.sandhya_count_context, stringResource(periodNameRes(draft.period)),
                practiceDateText(draft.dateKey)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
