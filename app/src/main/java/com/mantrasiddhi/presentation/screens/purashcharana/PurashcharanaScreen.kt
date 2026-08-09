/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.purashcharana

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.evaluation.reference.GayatriMantra
import com.mantrasiddhi.domain.model.Purashcharana
import com.mantrasiddhi.domain.model.PurashcharanaPath
import com.mantrasiddhi.presentation.screens.purashcharana.components.AksharaMandala
import com.mantrasiddhi.presentation.screens.purashcharana.components.MilestonePathVisual
import com.mantrasiddhi.presentation.theme.LocalExtendedColors
import kotlin.math.roundToInt

/**
 * The Purashcharana screen: what the great sankalpa asks, where the sadhak stands on it,
 * and the ladder of stages still ahead.
 *
 * It is a detail screen pushed from Home (like Sandhya and Family) rather than a bottom-nav
 * tab: a sadhak visits it to take stock, not several times a session. The same screen serves
 * before and after enrolling, because the explanation of the vow is exactly what someone
 * already walking it wants to re-read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurashcharanaScreen(
    viewModel: PurashcharanaViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is PurashcharanaEvent.Enrolled ->
                    context.getString(R.string.purashcharana_enrolled_snack)
                is PurashcharanaEvent.Withdrawn ->
                    context.getString(R.string.purashcharana_withdrawn_snack)
                is PurashcharanaEvent.ShowError ->
                    context.getString(
                        R.string.purashcharana_error_format,
                        event.detail ?: context.getString(R.string.error_unknown)
                    )
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_purashcharana),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.purashcharana_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = uiState) {
                is PurashcharanaUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is PurashcharanaUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(id = state.messageResId),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                is PurashcharanaUiState.Success -> {
                    PurashcharanaContent(
                        purashcharana = state.purashcharana,
                        onTakeSankalpa = viewModel::takeSankalpa,
                        onWithdrawSankalpa = viewModel::withdrawSankalpa
                    )
                }
            }
        }
    }
}

@Composable
private fun PurashcharanaContent(
    purashcharana: Purashcharana?,
    onTakeSankalpa: () -> Unit,
    onWithdrawSankalpa: () -> Unit
) {
    var showSankalpaDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    // Before enrolling, the screen still has to show the real arithmetic and the real
    // ladder — that is what the sadhak is deciding about — so both fall back to the
    // Gayatri's own akshara count rather than to placeholders.
    val aksharaCount = purashcharana?.mantraAksharaCount ?: GayatriMantra.AKSHARA_COUNT
    val targetCount = purashcharana?.targetJapaCount ?: PurashcharanaPath.targetFor(aksharaCount)
    val currentCount = purashcharana?.currentJapaCount ?: 0L
    val milestones = remember(aksharaCount) { PurashcharanaPath.milestonesFor(aksharaCount) }
    val reachedCount = milestones.count { it.japaCount <= currentCount }

    // Sampled once per entry to the screen. Day number and pace move once a day; a ticking
    // clock here would buy nothing and recompose the whole page.
    val nowMillis = remember { System.currentTimeMillis() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        HeroSection(
            aksharaCount = aksharaCount,
            targetCount = targetCount,
            progress = purashcharana?.progress ?: 0f
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionCard(
            titleRes = R.string.purashcharana_what_title,
            bodyRes = R.string.purashcharana_what_body
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            purashcharana == null -> TakeSankalpaSection(onTakeSankalpa = { showSankalpaDialog = true })
            purashcharana.isCompleted -> PurnahutiCard()
            else -> {
                ProgressCard(
                    purashcharana = purashcharana,
                    nowMillis = nowMillis
                )
                Spacer(modifier = Modifier.height(16.dp))
                PaceCard(purashcharana = purashcharana, nowMillis = nowMillis)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(id = R.string.purashcharana_milestones_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        MilestonePathVisual(
            milestones = milestones,
            reachedCount = reachedCount
        )

        Spacer(modifier = Modifier.height(28.dp))

        SectionCard(
            titleRes = R.string.purashcharana_counting_title,
            bodyRes = R.string.purashcharana_counting_body
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard(
            titleRes = R.string.purashcharana_niyama_title,
            bodyRes = R.string.purashcharana_niyama_body
        )

        // Setting the vow aside is destructive, so it sits last and stays quiet: a text
        // button in a muted colour, never a filled one competing with "Take the Sankalpa".
        if (purashcharana != null && !purashcharana.isCompleted) {
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(
                onClick = { showWithdrawDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(id = R.string.purashcharana_withdraw),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showSankalpaDialog) {
        AlertDialog(
            onDismissRequest = { showSankalpaDialog = false },
            title = { Text(text = stringResource(id = R.string.purashcharana_sankalpa_dialog_title)) },
            text = { Text(text = stringResource(id = R.string.purashcharana_sankalpa_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSankalpaDialog = false
                        onTakeSankalpa()
                    }
                ) {
                    Text(text = stringResource(id = R.string.purashcharana_sankalpa_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSankalpaDialog = false }) {
                    Text(text = stringResource(id = R.string.purashcharana_sankalpa_dialog_cancel))
                }
            }
        )
    }

    if (showWithdrawDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawDialog = false },
            title = { Text(text = stringResource(id = R.string.purashcharana_withdraw_dialog_title)) },
            text = { Text(text = stringResource(id = R.string.purashcharana_withdraw_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWithdrawDialog = false
                        onWithdrawSankalpa()
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.purashcharana_withdraw_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }) {
                    Text(text = stringResource(id = R.string.purashcharana_withdraw_cancel))
                }
            }
        )
    }
}

/**
 * The mandala and the arithmetic the whole vow rests on: aksharas x one lakh.
 */
@Composable
private fun HeroSection(
    aksharaCount: Int,
    targetCount: Long,
    progress: Float
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AksharaMandala(
            modifier = Modifier.size(148.dp),
            aksharaCount = aksharaCount,
            progress = progress
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                id = R.string.purashcharana_math_format,
                aksharaCount,
                formatIndianCount(targetCount)
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.purashcharana_gayatri_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** A plain title + long-form body card, used for the storytelling sections. */
@Composable
private fun SectionCard(@StringRes titleRes: Int, @StringRes bodyRes: Int) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TakeSankalpaSection(onTakeSankalpa: () -> Unit) {
    Button(
        onClick = onTakeSankalpa,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Text(
            text = stringResource(id = R.string.purashcharana_take_sankalpa),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The live block for a sankalpa in progress: how far, how long, and what is next.
 */
@Composable
private fun ProgressCard(
    purashcharana: Purashcharana,
    nowMillis: Long
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        id = R.string.purashcharana_percent_format,
                        formatPurashcharanaPercent(purashcharana.progress)
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(
                        id = R.string.purashcharana_day_number,
                        purashcharana.dayNumber(nowMillis)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = purashcharana.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(
                    id = R.string.purashcharana_progress_format,
                    formatIndianCount(purashcharana.currentJapaCount),
                    formatIndianCount(purashcharana.targetJapaCount)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    id = R.string.purashcharana_remaining_format,
                    formatIndianCount(purashcharana.remainingJapaCount)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            purashcharana.nextMilestone()?.let { next ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        id = R.string.purashcharana_next_milestone_format,
                        stringResource(id = milestoneTitleRes(next.stage))
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * Pace and projection. Deliberately honest: when there is no verified japa yet there is
 * nothing to measure, and the card says so rather than showing a zero and an eternity.
 */
@Composable
private fun PaceCard(
    purashcharana: Purashcharana,
    nowMillis: Long
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.purashcharana_pace_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            val perDay = purashcharana.japaPerDay(nowMillis)
            if (perDay == null) {
                Text(
                    text = stringResource(id = R.string.purashcharana_pace_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            Text(
                text = stringResource(
                    id = R.string.purashcharana_pace_format,
                    perDay.roundToInt().coerceAtLeast(1)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            purashcharana.projectedDaysRemaining(nowMillis)?.let { days ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.purashcharana_eta_format, etaText(days)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Turns a day count into the coarsest unit that is still true. A Purashcharana is measured
 * in years for most of its life, and "8,213 days to go" is a number nobody can feel.
 */
@Composable
private fun etaText(days: Long): String = when {
    days >= 730L -> stringResource(
        id = R.string.purashcharana_eta_years,
        (days / 365L).toInt().coerceAtLeast(2)
    )
    days >= 60L -> stringResource(
        id = R.string.purashcharana_eta_months,
        (days / 30L).toInt().coerceAtLeast(2)
    )
    else -> stringResource(
        id = R.string.purashcharana_eta_days,
        days.toInt().coerceAtLeast(1)
    )
}

/**
 * Shown once the full count is reached. Success colours (not the sindoor primary) mark the
 * one moment on this screen that is an arrival rather than a stage.
 */
@Composable
private fun PurnahutiCard() {
    val extended = LocalExtendedColors.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = extended.successContainer,
            contentColor = extended.onSuccessContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(id = R.string.purashcharana_complete_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = extended.onSuccessContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.purashcharana_complete_body),
                style = MaterialTheme.typography.bodyMedium,
                color = extended.onSuccessContainer
            )
        }
    }
}
