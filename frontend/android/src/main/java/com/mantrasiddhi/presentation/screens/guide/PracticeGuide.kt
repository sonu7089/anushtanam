package com.mantrasiddhi.presentation.screens.guide

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.presentation.components.LotusCard
import com.mantrasiddhi.presentation.components.IllustratedCardContent
import com.mantrasiddhi.resources.R

@Composable
fun PracticeGuideCard(needsReview: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LotusCard(onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        IllustratedCardContent(artwork = { artModifier ->
            Box(artModifier, contentAlignment = Alignment.Center) {
                PracticeGuideArtwork(Modifier.size(56.dp))
            }
        }) {
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.guide_home_message), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (needsReview) R.string.guide_review else R.string.guide_learn),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
                }
        }
    }
}

@Composable
fun PracticeGuideReminder(needsReview: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
    messageRes: Int = R.string.guide_reminder) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(messageRes), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (needsReview) R.string.guide_review else R.string.guide_how),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeGuideSheet(acknowledged: Boolean, saving: Boolean, saveError: Boolean,
    onAcknowledge: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(Modifier.fillMaxWidth()) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.guide_close))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background) {
        PracticeGuideContent(acknowledged, saving, saveError, onAcknowledge, onDismiss)
    }
}

@Composable
fun PracticeGuideContent(acknowledged: Boolean, saving: Boolean, saveError: Boolean,
    onAcknowledge: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PracticeGuideArtwork(Modifier.size(72.dp))
            Text(stringResource(if (acknowledged) R.string.guide_title else R.string.guide_before),
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Method(R.string.guide_vaikhari, R.string.guide_vaikhari_label, R.string.guide_vaikhari_body)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Method(R.string.guide_upamsu, R.string.guide_upamsu_label, R.string.guide_upamsu_body)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Method(R.string.guide_manasika, R.string.guide_manasika_label, R.string.guide_manasika_body)
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.guide_daily), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.guide_daily_body), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.guide_app_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.guide_app_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (saveError) Text(stringResource(R.string.guide_save_error), color = MaterialTheme.colorScheme.error)
        Column(Modifier.fillMaxWidth()) {
            if (!acknowledged) Button(onClick = onAcknowledge, enabled = !saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(stringResource(R.string.guide_acknowledge))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (acknowledged) R.string.guide_close else R.string.guide_later))
            }
        }
    }
}

@Composable
private fun PracticeGuideArtwork(modifier: Modifier = Modifier) {
    Image(painterResource(com.mantrasiddhi.R.drawable.gayatri_practice_book),
        contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
}

@Composable
private fun Method(name: Int, label: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(name), style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
