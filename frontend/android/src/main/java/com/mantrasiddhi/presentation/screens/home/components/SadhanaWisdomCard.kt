package com.mantrasiddhi.presentation.screens.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.presentation.content.SadhanaWisdom
import com.mantrasiddhi.domain.model.TeachingMessage
import java.time.LocalDate

/** The note and its next-note cue form one accessible, tappable reading area. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SadhanaWisdomCard(modifier: Modifier = Modifier, reflection: TeachingMessage? = null,
                     onReadMore: (String) -> Unit = {}) {
    val startIndex = rememberSaveable { SadhanaWisdom.indexForDay(LocalDate.now().dayOfYear) }
    // Keep the reader's place when a fetch completes or the phone rotates.
    var position by rememberSaveable { mutableIntStateOf(-1) }
    val remote = reflection?.takeIf { position == -1 }
    val teachingIndex = (startIndex + position.coerceAtLeast(0)).mod(SadhanaWisdom.count)
    val teaching = SadhanaWisdom.teachingAt(teachingIndex)
    val nextNoteLabel = stringResource(R.string.home_wisdom_next)
    val nextNote = {
        position = when {
            remote != null -> 0
            position.coerceAtLeast(0) < SadhanaWisdom.count - 1 -> position.coerceAtLeast(0) + 1
            reflection != null -> -1
            else -> 0
        }
    }

    Column(modifier.fillMaxWidth().padding(vertical = 12.dp).animateContentSize(tween(180))) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_wisdom_title), Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            val total = SadhanaWisdom.count + if (reflection != null) 1 else 0
            val number = if (remote != null) 1 else teachingIndex + 1 + if (reflection != null) 1 else 0
            Text("$number / $total",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .then(if (remote == null) Modifier.clickable(onClickLabel = nextNoteLabel,
                role = Role.Button, onClick = nextNote) else Modifier)
            .padding(vertical = 6.dp)) {
            Text(remote?.title ?: stringResource(teaching.titleRes), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondaryContainer))
                Spacer(Modifier.width(18.dp))
                val body = remote?.let {
                    if (it.body.length <= 600) it.body else it.summary.ifBlank {
                        it.body.take(280).substringBeforeLast(' ', it.body.take(280)) + "…"
                    }
                } ?: stringResource(teaching.bodyRes)
                Text(body, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (remote != null && remote.attribution.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(remote.attribution, Modifier.padding(start = 20.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            if (remote != null) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onReadMore(remote.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.home_wisdom_read_more))
                    }
                    TextButton(onClick = nextNote, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(nextNoteLabel)
                        Spacer(Modifier.width(8.dp))
                        Icon(painterResource(com.mantrasiddhi.R.drawable.arrow_forward), null, Modifier.size(18.dp))
                    }
                }
            } else Row(Modifier.align(Alignment.End).heightIn(min = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(nextNoteLabel, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(com.mantrasiddhi.R.drawable.arrow_forward), null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
