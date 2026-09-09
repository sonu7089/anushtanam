package com.mantrasiddhi.presentation.screens.guide

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PracticeGuideState(val profileId: Long? = null, val visible: Boolean = false)

/** The guide opens on request. Only the open sheet is saved for activity recreation. */
@HiltViewModel
class PracticeGuideViewModel @Inject constructor(
    getCurrentUser: GetCurrentUserUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(PracticeGuideState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().map { it?.id }.distinctUntilChanged().collect { profile ->
                val restored = profile != null && savedState.get<Long>("guide_profile") == profile
                // An initial null can mean the saved profile is still loading.
                // A logout after a known profile must clear the saved sheet.
                if (!restored && (profile != null || _state.value.profileId != null)) {
                    savedState.remove<Long>("guide_profile")
                }
                _state.value = PracticeGuideState(profile, restored)
            }
        }
    }

    fun open() {
        val profile = state.value.profileId ?: return
        savedState["guide_profile"] = profile
        _state.update { it.copy(visible = true) }
    }

    fun dismiss() {
        savedState.remove<Long>("guide_profile")
        _state.update { it.copy(visible = false) }
    }
}
