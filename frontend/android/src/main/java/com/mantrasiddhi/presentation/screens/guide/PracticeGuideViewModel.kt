package com.mantrasiddhi.presentation.screens.guide

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.preferences.PracticeGuidePreferences
import com.mantrasiddhi.data.preferences.PracticeGuideRecord
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PracticeGuideState(
    val profileId: Long? = null,
    val record: PracticeGuideRecord? = null,
    val visible: Boolean = false,
    val saving: Boolean = false,
    val saveError: Boolean = false
) {
    val needsReview get() = record?.shownAt != null && record.acknowledgedAt == null
    val firstUse get() = record != null && record.shownAt == null && record.acknowledgedAt == null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PracticeGuideViewModel @Inject constructor(
    getCurrentUser: GetCurrentUserUseCase,
    private val preferences: PracticeGuidePreferences,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(PracticeGuideState())
    val state = _state.asStateFlow()
    private val offeredProfiles = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            getCurrentUser().map { it?.id }.distinctUntilChanged().flatMapLatest { profile ->
                val restored = profile != null && savedState.get<Long>("guide_profile") == profile
                if (profile != null && !restored) savedState.remove<Long>("guide_profile")
                _state.value = PracticeGuideState(profileId = profile, visible = restored)
                if (profile == null) flowOf(null) else preferences.observe(profile, VERSION)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        // Keep the offline guide usable even when local preferences cannot be read.
                        emit(PracticeGuideRecord(VERSION))
                    }
            }.collect { record -> _state.update { it.copy(record = record) } }
        }
    }

    fun offerFirstUse() {
        val current = state.value
        val profile = current.profileId ?: return
        if (current.firstUse && !current.visible && offeredProfiles.add(profile)) open(false)
    }

    fun open(openedByUser: Boolean = true) {
        val profile = state.value.profileId ?: return
        offeredProfiles.add(profile)
        savedState["guide_profile"] = profile
        _state.update { it.copy(visible = true, saveError = false) }
        viewModelScope.launch {
            try { preferences.markShown(profile, VERSION, openedByUser) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                // Reading and continuing remain available. An acknowledgement write is retriable.
            }
        }
    }

    fun dismiss() {
        savedState.remove<Long>("guide_profile")
        _state.update { it.copy(visible = false, saveError = false) }
    }

    fun acknowledge() {
        val current = state.value
        val profile = current.profileId ?: return
        if (current.saving) return
        _state.update { it.copy(saving = true, saveError = false) }
        viewModelScope.launch {
            try {
                preferences.acknowledge(profile, VERSION)
                if (state.value.profileId == profile) dismiss()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (state.value.profileId == profile) _state.update { it.copy(saveError = true) }
            } finally {
                if (state.value.profileId == profile) _state.update { it.copy(saving = false) }
            }
        }
    }

    companion object { const val VERSION = 1 }
}
