package com.mantrasiddhi.presentation.screens.home.components

import com.mantrasiddhi.presentation.components.AppArtwork
import com.mantrasiddhi.presentation.components.AppArt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.presentation.components.IllustratedCardContent
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.resources.R

@Composable
fun SandhyaHomeCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LotusCard(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("home-sandhya"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        IllustratedCardContent(artwork = {
            AppArtwork(AppArt.VESSEL, it)
        }) {
            Text(stringResource(R.string.home_sandhya_title), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.home_sandhya_subtitle), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_sandhya_open), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
