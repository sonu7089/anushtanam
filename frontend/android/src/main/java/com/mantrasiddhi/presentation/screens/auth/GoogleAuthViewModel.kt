package com.mantrasiddhi.presentation.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.session.SyncSignedInUserUseCase
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.AuthRepository
import com.mantrasiddhi.resources.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoogleAuthUiState(
    val busy: Boolean = false,
    val errorRes: Int? = null,
    val account: AuthAccount? = null,
    val importChoices: List<UserProfile> = emptyList(),
    val needsProfileRetry: Boolean = false,
    val completed: Boolean = false,
    val linked: Boolean = false
)

@HiltViewModel
class GoogleAuthViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val picker: GoogleCredentialPicker,
    private val profiles: SyncSignedInUserUseCase
) : ViewModel() {
    private val _state = MutableStateFlow(GoogleAuthUiState())
    val state = _state.asStateFlow()
    val account = auth.currentAccount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var linking = false
    private var importId: Long? = null

    fun chooseAnotherAccount() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                auth.signOut()
                importId = null
                _state.value = GoogleAuthUiState()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(busy = false, errorRes = R.string.google_try_again) } }
        }
    }

    fun refreshProfile() {
        if (_state.value.busy) return
        linking = true
        _state.update { it.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            try {
                val updated = auth.refreshAccount().getOrThrow()
                _state.update { it.copy(account = updated) }
                finishProfile(updated)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { _state.update { it.copy(busy = false, errorRes = messageFor(failure)) } }
        }
    }

    fun start(context: Context, role: AccountRole = AccountRole.INDIVIDUAL, linkTo: AuthAccount? = null) {
        if (_state.value.busy || _state.value.needsProfileRetry || _state.value.importChoices.isNotEmpty()) return
        linking = linkTo != null
        importId = null
        _state.value = GoogleAuthUiState(busy = true)
        viewModelScope.launch {
            try {
                val token = picker.pick(context)
                if (token == null) {
                    _state.value = GoogleAuthUiState()
                    return@launch
                }
                val selected = (if (linkTo == null) auth.signInWithGoogle(token, role)
                    else auth.linkGoogle(token, linkTo.uid)).getOrThrow()
                _state.update { it.copy(account = selected) }
                prepareProfile(selected)
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(busy = false) }
                throw cancelled
            } catch (failure: Exception) {
                _state.update { it.copy(busy = false, errorRes = messageFor(failure)) }
            }
        }
    }

    private suspend fun prepareProfile(selected: AuthAccount) {
        try {
            val choices = if (linking || selected.role == AccountRole.CHILD) emptyList() else profiles.profilesForImport(selected)
            if (choices.isNotEmpty()) {
                _state.update { it.copy(busy = false, importChoices = choices, needsProfileRetry = false) }
            } else finishProfile(selected)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { profileFailed() }
    }

    fun continueWithProfile(profileId: Long?) {
        val selected = _state.value.account ?: return
        if (_state.value.busy) return
        if (profileId != null && _state.value.importChoices.none { it.id == profileId }) return
        importId = profileId
        _state.update { it.copy(busy = true, errorRes = null) }
        viewModelScope.launch { finishProfile(selected) }
    }

    fun retryProfile() {
        val selected = _state.value.account ?: return
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, errorRes = null) }
        viewModelScope.launch {
            if (importId != null) finishProfile(selected) else prepareProfile(selected)
        }
    }

    private suspend fun finishProfile(selected: AuthAccount) {
        try {
            if (importId == null) profiles(selected) else profiles.importProfile(selected, importId!!)
            _state.update { it.copy(busy = false, errorRes = null, importChoices = emptyList(),
                needsProfileRetry = false, completed = true, linked = linking) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { profileFailed() }
    }

    private fun profileFailed() {
        _state.update { it.copy(busy = false, errorRes = R.string.auth_profile_retry,
            needsProfileRetry = true) }
    }

    private fun messageFor(failure: Exception): Int = when (failure) {
        is GooglePickerFailure -> failure.messageRes
        is GoogleAuthFailure -> when (failure.problem) {
            GoogleAuthProblem.OFFLINE -> R.string.google_offline
            GoogleAuthProblem.COLLISION -> if (linking) R.string.google_link_collision else R.string.google_signin_collision
            GoogleAuthProblem.EXPIRED -> R.string.google_expired
            GoogleAuthProblem.UNAVAILABLE -> R.string.google_unavailable
            GoogleAuthProblem.WRONG_ACCOUNT -> R.string.google_wrong_account
            GoogleAuthProblem.ROLE_MISMATCH -> R.string.google_role_mismatch
            GoogleAuthProblem.UNKNOWN -> R.string.google_try_again
        }
        else -> R.string.google_try_again
    }
}
