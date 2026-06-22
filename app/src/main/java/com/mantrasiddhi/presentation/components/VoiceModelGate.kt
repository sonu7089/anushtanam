/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mantrasiddhi.presentation.screens.voicemodel.VoiceModelScreen
import com.mantrasiddhi.presentation.screens.voicemodel.VoiceModelUiState
import com.mantrasiddhi.presentation.screens.voicemodel.VoiceModelViewModel

/**
 * Gates [content] behind the on-device Hindi voice model. While availability is
 * being checked a loader is shown; if the model is missing the [VoiceModelScreen]
 * blocker replaces the content until the model is installed. Wrap the recitation
 * features (Practice, Recitation) with this so they never run without the model.
 *
 * Availability is re-checked on every resume, so returning from the system
 * settings/download flow automatically unblocks the content once the model lands.
 */
@Composable
fun VoiceModelGate(
    content: @Composable () -> Unit
) {
    val viewModel: VoiceModelViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val state = uiState) {
        is VoiceModelUiState.Checking -> VoiceModelLoading()
        is VoiceModelUiState.Ready -> content()
        is VoiceModelUiState.NeedsModel -> VoiceModelScreen(
            state = state.state,
            download = state.download,
            onDownloadClick = viewModel::startDownload,
            onRetryClick = viewModel::refresh
        )
    }
}

/** Centered spinner shown while the voice-model status is being resolved. */
@Composable
private fun VoiceModelLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
