/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.voicemodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.speech.VoiceModelManager
import com.mantrasiddhi.domain.model.VoiceModelDownload
import com.mantrasiddhi.domain.model.VoiceModelState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

/** UI state for the voice-model gate / download screen. */
sealed interface VoiceModelUiState {
    /** Availability is being queried. */
    data object Checking : VoiceModelUiState

    /** A pack is confirmed, or the user has chosen to try an unverified service. */
    data object Ready : VoiceModelUiState

    /** Show the relevant download, settings, or trial action. */
    data class NeedsModel(
        val state: VoiceModelState,
        val download: DownloadUiState
    ) : VoiceModelUiState
}

/** Sub-state describing an in-app download attempt. */
sealed interface DownloadUiState {
    /** No download in progress; the user may start one. */
    data object Idle : DownloadUiState

    /** A download is running; [percent] is `null` when indeterminate. */
    data class InProgress(val percent: Int?) : DownloadUiState

    /** The last download attempt failed with [errorCode]. */
    data class Failed(val errorCode: Int) : DownloadUiState
}

/**
 * Checks Hindi speech support and offers a download or a deliberate listening
 * trial when the service cannot report its installed packs.
 */
@HiltViewModel
class VoiceModelViewModel @Inject constructor(
    private val voiceModelManager: VoiceModelManager
) : ViewModel() {

    // Once the model has been seen READY, later gate instances start directly in Ready
    // (re-verifying silently) instead of flashing a full-screen spinner. This removed a
    // visible "blink" on every switch to the Practice/Recitation tabs, whose ViewModels
    // are recreated per navigation.
    private val _uiState = MutableStateFlow<VoiceModelUiState>(
        if (lastKnownReady) VoiceModelUiState.Ready else VoiceModelUiState.Checking
    )
    val uiState: StateFlow<VoiceModelUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var refreshAgain = false
    private var resumed = false

    /** Initialization owns the first resume; subsequent resumes invalidate availability. */
    fun onResume() {
        if (resumed) refresh() else resumed = true
    }

    init {
        refresh()
    }

    /**
     * Re-queries model availability. Safe to call on every screen resume; an active
     * download indicator is preserved rather than reset to a loading spinner, and a
     * previously-Ready gate is never demoted to the Checking spinner — it only changes
     * if the model has actually gone missing.
     */
    fun refresh() {
        if (refreshJob?.isActive == true) {
            refreshAgain = true
            return
        }
        refreshJob = viewModelScope.launch {
            do {
                refreshAgain = false
                val activeDownload = currentDownload() as? DownloadUiState.InProgress
                if (activeDownload == null && _uiState.value !is VoiceModelUiState.Ready) {
                    _uiState.value = VoiceModelUiState.Checking
                }
                val status = voiceModelManager.checkStatus()
                // A settings/download event invalidated this result while it was in flight.
                if (refreshAgain) continue
                lastKnownReady = status.state == VoiceModelState.READY ||
                    (status.state == VoiceModelState.CHECK_UNAVAILABLE && voiceModelManager.trialAllowed)
                _uiState.value = when {
                    lastKnownReady -> VoiceModelUiState.Ready
                    else -> VoiceModelUiState.NeedsModel(
                        state = status.state,
                        download = currentDownload() ?: activeDownload ?: DownloadUiState.Idle
                    )
                }
            } while (refreshAgain)
        }
    }

    fun tryListening() {
        if (voiceModelManager.allowUnverifiedTrial()) {
            lastKnownReady = true
            _uiState.value = VoiceModelUiState.Ready
        }
    }

    /** Asks the system to download/update the model, reflecting progress in [uiState]. */
    fun startDownload() {
        viewModelScope.launch {
            updateDownload(DownloadUiState.InProgress(null))
            voiceModelManager.triggerDownload().collect { event ->
                when (event) {
                    is VoiceModelDownload.Scheduled -> updateDownload(DownloadUiState.InProgress(null))
                    is VoiceModelDownload.Progress -> updateDownload(DownloadUiState.InProgress(event.percent))
                    is VoiceModelDownload.Completed -> refresh()
                    is VoiceModelDownload.Failed -> updateDownload(DownloadUiState.Failed(event.errorCode))
                }
            }
        }
    }

    private fun currentDownload(): DownloadUiState? =
        (_uiState.value as? VoiceModelUiState.NeedsModel)?.download

    /** Updates only the download sub-state, keeping the resolved model state. */
    private fun updateDownload(download: DownloadUiState) {
        val state = (_uiState.value as? VoiceModelUiState.NeedsModel)?.state
            ?: VoiceModelState.NOT_INSTALLED
        _uiState.value = VoiceModelUiState.NeedsModel(state, download)
    }

    private companion object {
        /**
         * Process-wide memo of the last resolved availability. Purely a UX hint for the
         * gate's initial frame; every instance still re-verifies via [refresh].
         */
        @Volatile
        var lastKnownReady = false
    }
}
