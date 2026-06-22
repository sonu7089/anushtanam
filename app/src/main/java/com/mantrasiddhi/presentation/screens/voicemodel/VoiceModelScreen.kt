/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.voicemodel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mantrasiddhi.R
import com.mantrasiddhi.domain.model.VoiceModelState
import com.mantrasiddhi.presentation.components.LotusIllustration

/**
 * Full-screen blocker shown when the on-device Hindi voice model is missing. It
 * explains why the model is required and offers a one-tap download/update plus
 * exact manual steps, so the recitation features never fall back to a flaky
 * network recognizer.
 *
 * @param state the resolved availability of the voice model.
 * @param download the current in-app download sub-state.
 * @param onDownloadClick starts (or retries) a system model download.
 * @param onRetryClick re-checks availability after the user installs the model.
 */
@Composable
fun VoiceModelScreen(
    state: VoiceModelState,
    download: DownloadUiState,
    onDownloadClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        LotusIllustration(modifier = Modifier.size(96.dp))

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(id = titleRes(state)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(id = bodyRes(state)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        DownloadActionSection(
            state = state,
            download = download,
            onDownloadClick = onDownloadClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { openVoiceInputSettings(context) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.voice_model_open_settings))
        }

        TextButton(
            onClick = onRetryClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.voice_model_check_again))
        }

        Spacer(modifier = Modifier.height(8.dp))

        ManualStepsSection()
    }
}

/**
 * Renders the primary download affordance: a progress bar while downloading, a
 * retry on failure, or a download button when idle. Hidden entirely when the
 * device has no on-device recognizer to download into.
 */
@Composable
private fun DownloadActionSection(
    state: VoiceModelState,
    download: DownloadUiState,
    onDownloadClick: () -> Unit
) {
    val systemDownloading = state == VoiceModelState.DOWNLOADING
    when {
        download is DownloadUiState.InProgress || systemDownloading -> {
            val percent = (download as? DownloadUiState.InProgress)?.percent
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    percent != null -> stringResource(R.string.voice_model_downloading_percent, percent)
                    systemDownloading -> stringResource(R.string.voice_model_system_downloading)
                    else -> stringResource(R.string.voice_model_downloading_preparing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        state == VoiceModelState.RECOGNIZER_UNAVAILABLE -> {
            // No on-device recognizer exists; downloading cannot help. The manual
            // steps below guide installing the speech service itself.
        }

        else -> {
            if (download is DownloadUiState.Failed) {
                Text(
                    text = stringResource(R.string.voice_model_download_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = onDownloadClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        id = if (download is DownloadUiState.Failed) {
                            R.string.voice_model_try_again
                        } else {
                            R.string.voice_model_download_button
                        }
                    )
                )
            }
        }
    }
}

/** Collapsible card listing the exact manual install steps (OEM-tolerant). */
@Composable
private fun ManualStepsSection() {
    var expanded by remember { mutableStateOf(false) }

    TextButton(onClick = { expanded = !expanded }) {
        Text(
            text = stringResource(
                id = if (expanded) R.string.voice_model_hide_steps else R.string.voice_model_show_steps
            )
        )
    }

    if (expanded) {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.voice_model_steps_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val steps = listOf(
                    R.string.voice_model_step_1,
                    R.string.voice_model_step_2,
                    R.string.voice_model_step_3,
                    R.string.voice_model_step_4,
                    R.string.voice_model_step_5
                )
                steps.forEach { stepRes ->
                    Text(
                        text = stringResource(id = stepRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.voice_model_steps_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Resolves the headline string for the given [state]. */
@StringRes
private fun titleRes(state: VoiceModelState): Int = when (state) {
    VoiceModelState.UNSUPPORTED -> R.string.voice_model_title_unsupported
    VoiceModelState.RECOGNIZER_UNAVAILABLE -> R.string.voice_model_title_unavailable
    else -> R.string.voice_model_title_required
}

/** Resolves the explanatory body string for the given [state]. */
@StringRes
private fun bodyRes(state: VoiceModelState): Int = when (state) {
    VoiceModelState.DOWNLOADING -> R.string.voice_model_body_downloading
    VoiceModelState.UNSUPPORTED -> R.string.voice_model_body_unsupported
    VoiceModelState.RECOGNIZER_UNAVAILABLE -> R.string.voice_model_body_unavailable
    else -> R.string.voice_model_body_required
}

/**
 * Opens the system voice-input settings, falling back to the top-level Settings
 * app when the device exposes no dedicated voice-input screen.
 */
private fun openVoiceInputSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (e2: Exception) {
            // No settings activity resolvable; nothing more we can do.
        }
    }
}
