package com.mantrasiddhi.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.resources.R
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BackupStatusContent(state: BackupUiState, saving: Boolean = false, failed: Boolean = false,
    onResolve: (BackupReview, Boolean) -> Unit = { _, _ -> }, onSyncNow: () -> Unit = {}, onReview: (Boolean) -> Unit = {}, onRetryReview: () -> Unit = {}) {
    var reviewing by remember { mutableStateOf(false) }
    DisposableEffect(reviewing) { onReview(reviewing); onDispose { onReview(false) } }
    LotusCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(when {
                        state.localOnly -> R.string.backup_local
                        state.syncing -> R.string.backup_syncing_description
                        state.attention -> R.string.backup_attention
                        state.waiting -> R.string.backup_waiting
                        else -> R.string.backup_current
                    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!state.localOnly) {
                    val syncLabel = stringResource(if (state.syncing) R.string.backup_syncing else R.string.backup_sync_now)
                    OutlinedIconButton(onClick = onSyncNow,
                        enabled = state.manualReady && !state.syncing,
                        modifier = Modifier.padding(top = 4.dp).size(48.dp).semantics { contentDescription = syncLabel }) {
                        if (state.syncing) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(painterResource(R.drawable.ic_sync), contentDescription = null,
                                modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            if (state.quarantined > 0) {
                Text(stringResource(R.string.backup_quarantined, state.quarantined),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            if (state.lastSuccess > 0) {
                Text(stringResource(R.string.backup_last_saved,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lastSuccess))),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
            if (state.conflictCount > 0 || state.reviews.isNotEmpty()) {
                TextButton(onClick = { reviewing = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.backup_review))
                }
            }
        }
    }
    val review = state.reviews.firstOrNull()
    if (reviewing && review == null) {
        AlertDialog(onDismissRequest = { reviewing = false }, title = { Text(stringResource(R.string.backup_review_title)) },
            text = {
                if (state.reviewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else Text(stringResource(if (state.reviewFailed) R.string.backup_review_error else R.string.backup_current))
            }, confirmButton = {
                if (state.reviewFailed) TextButton(onClick = onRetryReview) { Text(stringResource(R.string.section_retry)) }
                else TextButton(onClick = { reviewing = false }) { Text(stringResource(R.string.messages_back)) }
            })
    }
    if (reviewing && review != null) {
        AlertDialog(onDismissRequest = { if (!saving) reviewing = false },
            title = { Text(stringResource(R.string.backup_review_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_review_description, review.date))
                    Text(stringResource(R.string.backup_review_current, review.current))
                    Text(stringResource(R.string.backup_review_other, review.alternative))
                    if (failed) Text(stringResource(R.string.backup_review_error), color = MaterialTheme.colorScheme.error)
                }
            }, confirmButton = {
                TextButton(onClick = { onResolve(review, false) }, enabled = !saving) {
                    Text(stringResource(R.string.backup_keep_current))
                }
            }, dismissButton = {
                TextButton(onClick = { onResolve(review, true) }, enabled = !saving) {
                    Text(stringResource(R.string.backup_use_other))
                }
            })
    }
}
