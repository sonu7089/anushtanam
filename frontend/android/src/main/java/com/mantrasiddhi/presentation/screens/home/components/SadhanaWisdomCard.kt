package com.mantrasiddhi.presentation.screens.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.time.LocalDate

/** The note and its next-note cue form one accessible, tappable reading area. */
@Composable
fun SadhanaWisdomCard(modifier: Modifier = Modifier) {
    val startIndex = remember { SadhanaWisdom.indexForDay(LocalDate.now().dayOfYear) }
    var teachingIndex by remember { mutableIntStateOf(startIndex) }
    val teaching = SadhanaWisdom.teachingAt(teachingIndex)
    val nextNoteLabel = stringResource(R.string.home_wisdom_next)
    val nextNote = { teachingIndex = (teachingIndex + 1).mod(SadhanaWisdom.count) }

    Column(modifier.fillMaxWidth().padding(vertical = 12.dp).animateContentSize(tween(180))) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_wisdom_title), Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Text("${teachingIndex.mod(SadhanaWisdom.count) + 1} / ${SadhanaWisdom.count}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = nextNoteLabel, role = Role.Button, onClick = nextNote)
            .padding(vertical = 6.dp)) {
            Text(stringResource(teaching.titleRes), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.secondaryContainer))
                Spacer(Modifier.width(18.dp))
                Text(stringResource(teaching.bodyRes), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.align(Alignment.End).heightIn(min = 48.dp).padding(horizontal = 12.dp),
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
