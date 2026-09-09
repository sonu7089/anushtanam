package com.mantrasiddhi.presentation.screens.guide

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
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
fun PracticeGuideCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                    Text(stringResource(R.string.guide_learn),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
                }
        }
    }
}

@Composable
fun PracticeGuideReminder(onClick: () -> Unit, modifier: Modifier = Modifier,
    messageRes: Int = R.string.guide_reminder) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(messageRes), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.guide_how),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeGuideSheet(onDismiss: () -> Unit, onPractice: (() -> Unit)? = null) {
    // Keep reading position outside the dialog so activity recreation restores it too.
    var expandedSections by rememberSaveable { mutableStateOf(arrayListOf<Int>()) }
    val scrollState = rememberScrollState()
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
        PracticeGuideContent(onDismiss, onPractice, scrollState, expandedSections, { section ->
            expandedSections = ArrayList(expandedSections).apply {
                if (contains(section)) remove(section) else add(section)
            }
        })
    }
}

@Composable
private fun PracticeGuideContent(onDismiss: () -> Unit, onPractice: (() -> Unit)?,
    scrollState: ScrollState, expandedSections: List<Int>, onToggleSection: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(scrollState).padding(horizontal = 24.dp)
        .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PracticeGuideArtwork(Modifier.size(72.dp))
            Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() })
            GuideParagraph(R.string.guide_intro)
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.guide_daily), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.guide_daily_body), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GuideSection(R.string.guide_methods_title, R.string.guide_methods_summary,
                expanded = R.string.guide_methods_title in expandedSections,
                onToggle = { onToggleSection(R.string.guide_methods_title) }) {
                Method(R.string.guide_vaikhari, R.string.guide_vaikhari_label, R.string.guide_vaikhari_body)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Method(R.string.guide_upamsu, R.string.guide_upamsu_label, R.string.guide_upamsu_body)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Method(R.string.guide_manasika, R.string.guide_manasika_label, R.string.guide_manasika_body)
            }
            GuideSection(R.string.guide_verse_title, R.string.guide_verse_summary,
                expanded = R.string.guide_verse_title in expandedSections,
                onToggle = { onToggleSection(R.string.guide_verse_title) }) {
                GuideParagraph(R.string.guide_verse_intro)
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.guide_verse_sanskrit),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Default, fontSize = 20.sp, lineHeight = 34.sp))
                        Text(stringResource(R.string.guide_verse_transliteration),
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Default),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.guide_verse_source), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GuideSection(R.string.guide_cautions_title, R.string.guide_cautions_summary,
                expanded = R.string.guide_cautions_title in expandedSections,
                onToggle = { onToggleSection(R.string.guide_cautions_title) }) {
                GuideParagraph(R.string.guide_cautions_intro)
                Method(R.string.guide_caution_song_title, R.string.guide_caution_song_term, R.string.guide_caution_song_body)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Method(R.string.guide_caution_pace_title, R.string.guide_caution_pace_term, R.string.guide_caution_pace_body)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Method(R.string.guide_caution_head_title, R.string.guide_caution_head_term, R.string.guide_caution_head_body)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Method(R.string.guide_caution_text_title, R.string.guide_caution_text_term, R.string.guide_caution_text_body)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Method(R.string.guide_caution_meaning_title, R.string.guide_caution_meaning_term, R.string.guide_caution_meaning_body)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Method(R.string.guide_caution_voice_title, R.string.guide_caution_voice_term, R.string.guide_caution_voice_body)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                GuideParagraph(R.string.guide_cautions_context)
                GuideParagraph(R.string.guide_quality_note)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.guide_app_title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            GuideParagraph(R.string.guide_app_body)
            if (onPractice != null) TextButton(onClick = onPractice,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.guide_pronunciation))
            }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.guide_close))
        }
    }
}

@Composable
private fun GuideSection(title: Int, summary: Int, expanded: Boolean, onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "Guide section arrow")
    val sectionState = stringResource(if (expanded) R.string.guide_expanded else R.string.guide_collapsed)
    val clickLabel = stringResource(if (expanded) R.string.guide_collapse else R.string.guide_expand)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onToggle)
                .semantics { heading(); stateDescription = sectionState }
                .padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.KeyboardArrowDown, null,
                    modifier = Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.primary)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
            }
        }
    }
}

@Composable
private fun GuideParagraph(resource: Int) {
    Text(stringResource(resource), style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
