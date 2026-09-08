package com.mantrasiddhi.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.presentation.screens.auth.*
import com.mantrasiddhi.resources.R

@Composable
internal fun GoogleAccountContent(account: AuthAccount?, state: GoogleAuthUiState,
    onLink: () -> Unit, onRetry: () -> Unit, onImport: (Long?) -> Unit, onRefresh: () -> Unit) {
    account?.let { profile ->
        profile.email?.takeUnless { it.endsWith("@child.anushtanam.app") }?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        if (!profile.googleLinked) {
            Spacer(Modifier.height(12.dp))
            GoogleSignInButton(onLink, enabled = !state.busy && !state.needsProfileRetry, linking = true)
        }
        GoogleAuthFeedback(state, onRetry, onImport)
        if (profile.profileNeedsSync) {
            Text(stringResource(R.string.google_profile_pending), style = MaterialTheme.typography.bodySmall)
            TextButton(onRefresh, enabled = !state.busy) { Text(stringResource(R.string.google_profile_refresh)) }
        }
    }
}
