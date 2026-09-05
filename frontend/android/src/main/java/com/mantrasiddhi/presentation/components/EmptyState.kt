package com.mantrasiddhi.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun EmptyState(title: String, description: String, modifier: Modifier = Modifier,
               actionButton: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        LotusArtwork(LotusArt.EMBLEM, Modifier.size(140.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        actionButton?.let { Spacer(Modifier.height(24.dp)); it() }
    }
}

/** Bitmap replacement for the former procedural lotus. */
@Composable
fun LotusIllustration(modifier: Modifier = Modifier) = LotusArtwork(LotusArt.EMBLEM, modifier)
