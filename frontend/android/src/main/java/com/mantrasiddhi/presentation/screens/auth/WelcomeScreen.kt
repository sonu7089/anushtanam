package com.mantrasiddhi.presentation.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.components.*

@Composable
fun WelcomeScreen(onSelectParent: () -> Unit, onSelectChild: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LotusArtwork(LotusArt.EMBLEM, Modifier.size(48.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.app_name_devanagari), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            Spacer(Modifier.height(28.dp))
            AmbientArtwork(LotusArt.LOTUS, Modifier.fillMaxWidth().height(260.dp))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.lotus_welcome_eyebrow), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.lotus_welcome_title), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.auth_welcome_subtitle), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            MantraButton(stringResource(R.string.auth_role_parent), onSelectParent, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onSelectChild, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.large) {
                Text(stringResource(R.string.auth_role_child), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
