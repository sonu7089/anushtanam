/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.R
import com.mantrasiddhi.presentation.components.LotusIllustration
import com.mantrasiddhi.presentation.content.SadhanaWisdom
import java.time.LocalDate

/**
 * The Sadhana Marg card: one short teaching from the tradition, shown on Home.
 *
 * The app owner asked for storytelling on the home page — the things a sadhak learns on
 * the path (nadi shuddhi, swara, ajapa) told honestly, including how long they take. One
 * teaching at a time is the whole point: a list would be scrolled past, a single paragraph
 * gets read.
 *
 * ## Why the day-of-year is computed here
 * It is a calendar fact, not app data. Routing it through HomeUiState would add a field
 * no other part of the dashboard reads, and it would still be stale at midnight unless the
 * ViewModel also ran a timer — so the composable reads the date directly and
 * [SadhanaWisdom] keeps the choice stable for the whole day (a random pick would change on
 * every recomposition and flicker under the reader's eyes).
 */
@Composable
fun SadhanaWisdomCard(modifier: Modifier = Modifier) {
    val startIndex = remember { SadhanaWisdom.indexForDay(LocalDate.now().dayOfYear) }

    // Local-only cycling: "next teaching" is browsing, not state worth persisting or
    // reporting anywhere. It wraps forever, so the sadhak can never reach a dead end.
    var teachingIndex by remember { mutableIntStateOf(startIndex) }
    val teaching = SadhanaWisdom.teachingAt(teachingIndex)

    // Keyed on the index so moving to the next teaching always starts it collapsed.
    var expanded by remember(teachingIndex) { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The brand lotus, reused rather than a second emblem invented for Home.
                LotusIllustration(modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.home_wisdom_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.home_wisdom_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(id = teaching.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Four lines is enough for the shorter teachings to show whole; the longer ones
            // invite a tap. Never one line — these are meant to be read, not glanced at.
            Text(
                text = stringResource(id = teaching.bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .animateContentSize()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { teachingIndex++ }) {
                    Text(
                        text = stringResource(id = R.string.home_wisdom_next),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
