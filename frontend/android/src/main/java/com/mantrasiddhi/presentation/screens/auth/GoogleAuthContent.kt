package com.mantrasiddhi.presentation.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R

@Composable
fun GoogleSignInButton(onClick: () -> Unit, enabled: Boolean, linking: Boolean = false) {
    OutlinedButton(onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
        border = BorderStroke(1.dp, Color(0xFF747775))) {
        Image(painterResource(com.mantrasiddhi.R.drawable.google_g), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(if (linking) R.string.google_link_action else R.string.google_continue))
    }
}

@Composable
fun GoogleAuthFeedback(state: GoogleAuthUiState, onRetry: () -> Unit, onImport: (Long?) -> Unit,
    onDifferentAccount: (() -> Unit)? = null) {
    if (state.busy) {
        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.google_opening), style = MaterialTheme.typography.bodyMedium)
        }
    }
    state.errorRes?.let {
        Text(stringResource(it), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium)
    }
    if (state.needsProfileRetry) {
        TextButton(onRetry, enabled = !state.busy) { Text(stringResource(R.string.auth_retry_profile)) }
    }
    if (state.importChoices.isNotEmpty() && !state.needsProfileRetry) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.google_import_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.google_import_body, state.account?.email ?: state.account?.displayName.orEmpty()),
                style = MaterialTheme.typography.bodyMedium)
            state.importChoices.forEach { profile ->
                OutlinedButton({ onImport(profile.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.google_import_profile, profile.name))
                }
            }
            TextButton({ onImport(null) }, enabled = !state.busy) { Text(stringResource(R.string.google_import_skip)) }
        }
    }
    if (onDifferentAccount != null && !state.completed &&
        (state.account != null || state.errorRes == R.string.google_wrong_account)) {
        TextButton(onDifferentAccount, enabled = !state.busy) { Text(stringResource(R.string.google_choose_another)) }
    }
}
