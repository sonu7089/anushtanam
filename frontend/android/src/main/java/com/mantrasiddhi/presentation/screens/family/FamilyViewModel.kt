/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.presentation.screens.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.ParentChildLink
import com.mantrasiddhi.domain.model.RewardType
import com.mantrasiddhi.domain.usecase.ApproveLinkUseCase
import com.mantrasiddhi.domain.usecase.CreateChildGoalUseCase
import com.mantrasiddhi.domain.usecase.ObserveAuthAccountUseCase
import com.mantrasiddhi.domain.usecase.ObserveChildGoalsUseCase
import com.mantrasiddhi.domain.usecase.ObserveGoalsForChildUseCase
import com.mantrasiddhi.domain.usecase.ObserveLinkedChildrenUseCase
import com.mantrasiddhi.domain.usecase.ObservePendingLinkRequestsUseCase
import com.mantrasiddhi.domain.usecase.RejectLinkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import com.mantrasiddhi.domain.repository.LinkRepository
import com.mantrasiddhi.domain.model.FamilyLinkFailure
import com.mantrasiddhi.domain.model.FamilyLinkProblem
import com.mantrasiddhi.resources.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Backs the Family screen: parent linking + goal-setting, child link-approval + goals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FamilyViewModel @Inject constructor(
    observeAuthAccountUseCase: ObserveAuthAccountUseCase,
    private val approveLinkUseCase: ApproveLinkUseCase,
    private val rejectLinkUseCase: RejectLinkUseCase,
    private val createChildGoalUseCase: CreateChildGoalUseCase,
    observeLinkedChildrenUseCase: ObserveLinkedChildrenUseCase,
    observePendingLinkRequestsUseCase: ObservePendingLinkRequestsUseCase,
    observeChildGoalsUseCase: ObserveChildGoalsUseCase,
    private val observeGoalsForChildUseCase: ObserveGoalsForChildUseCase,
    private val links: LinkRepository,
    private val scanner: FamilyQrScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilyUiState())
    val uiState: StateFlow<FamilyUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val selectedChildUid = MutableStateFlow<String?>(null)
    private var inviteWatch: Job? = null

    init {
        viewModelScope.launch {
            observeAuthAccountUseCase()
                .onEach { account ->
                    val previous = _uiState.value.account
                    if (account?.uid != previous?.uid || account?.role != previous?.role) {
                        inviteWatch?.cancel()
                        _uiState.value = FamilyUiState(account = account)
                        selectedChildUid.value = null
                    } else _uiState.update { it.copy(account = account) }
                }
                .distinctUntilChangedBy { it?.let { account -> account.uid to account.role } }
                .collectLatest { account ->
                    // Children belong to this account job; a replacement cancels all of them.
                    coroutineScope {
                        if (account?.role == AccountRole.PARENT) {
                            launch {
                                accountData { observeLinkedChildrenUseCase() }.collect { children ->
                                    _uiState.update { if (it.account?.uid == account.uid && it.account.role == account.role)
                                        it.copy(linkedChildren = children) else it }
                                }
                            }
                        } else if (account?.role == AccountRole.CHILD) {
                            launch {
                                accountData { observePendingLinkRequestsUseCase() }.collect { requests ->
                                    _uiState.update { if (it.account?.uid == account.uid && it.account.role == account.role)
                                        it.copy(pendingRequests = requests) else it }
                                }
                            }
                            launch {
                                accountData { observeChildGoalsUseCase(account.uid) }.collect { goals ->
                                    _uiState.update { if (it.account?.uid == account.uid && it.account.role == account.role)
                                        it.copy(myGoals = goals) else it }
                                }
                            }
                            launch {
                                accountData { links.approvedParentsForChild() }.collect { parents ->
                                    _uiState.update { if (it.account?.uid == account.uid && it.account.role == account.role)
                                        it.copy(linkedParents = parents) else it }
                                }
                            }
                        }
                    }
                }
        }
        viewModelScope.launch {
            selectedChildUid.flatMapLatest { uid ->
                val owner = _uiState.value.account?.uid
                if (uid != null && owner != null) accountData { observeGoalsForChildUseCase(uid) }.map { Triple(owner, uid, it) }
                else flowOf(null)
            }.buffer(0).collect { result ->
                if (result != null) _uiState.update {
                    if (it.account?.uid == result.first && selectedChildUid.value == result.second)
                        it.copy(selectedChildGoals = result.third) else it
                }
            }
        }
    }

    // A failed section must not terminate identity observation and retain an old account.
    private fun <T> accountData(source: () -> Flow<List<T>>): Flow<List<T>> =
        flow { emitAll(source()) }.catch { emit(emptyList()) }

    fun requestLink(childCode: String, pin: String) = runLink {
        links.linkWithPin(childCode, pin).getOrThrow()
        _messages.emit(MSG_LINK_APPROVED)
    }

    fun scanQr(context: Context) = runLink {
        val owner = _uiState.value.account?.uid
        val payload = try { scanner.scan(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            _uiState.update { it.copy(errorRes = R.string.family_scan_unavailable) }
            return@runLink
        }
        if (payload != null && _uiState.value.account?.uid == owner) {
            links.linkWithQr(payload).getOrThrow()
            _messages.emit(MSG_LINK_APPROVED)
        }
    }

    fun createQr() = runLink {
        val uid = _uiState.value.account?.uid
        inviteWatch?.cancel()
        _uiState.update { it.copy(qrInvite = null) }
        val invite = links.createQrInvite().getOrThrow()
        if (_uiState.value.account?.uid != uid) return@runLink
        _uiState.update { it.copy(qrInvite = invite) }
        inviteWatch?.cancel()
        inviteWatch = viewModelScope.launch {
            links.observeQrInvite(invite.token).collect { updated ->
                if (_uiState.value.account?.uid == uid) _uiState.update {
                    it.copy(qrInvite = updated, errorRes = if (updated == null) R.string.family_qr_unavailable else null)
                }
            }
        }
    }

    private fun runLink(action: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        val uid = _uiState.value.account?.uid ?: return
        _uiState.update { it.copy(isBusy = true, errorRes = null) }
        viewModelScope.launch {
            try { if (_uiState.value.account?.uid == uid) action() }
            catch (_: TimeoutCancellationException) {
                if (_uiState.value.account?.uid == uid) _uiState.update { it.copy(errorRes = R.string.family_link_offline) }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (_uiState.value.account?.uid == uid) _uiState.update { it.copy(errorRes = when ((failure as? FamilyLinkFailure)?.problem) {
                    FamilyLinkProblem.INVALID_CREDENTIALS -> R.string.family_pin_invalid
                    FamilyLinkProblem.INVALID_QR -> R.string.family_qr_invalid
                    FamilyLinkProblem.QR_UNAVAILABLE -> R.string.family_qr_unavailable
                    FamilyLinkProblem.OFFLINE -> R.string.family_link_offline
                    FamilyLinkProblem.TOO_MANY_ATTEMPTS -> R.string.family_link_wait
                    FamilyLinkProblem.WRONG_ACCOUNT -> R.string.family_link_wrong_account
                    else -> R.string.family_link_unavailable
                }) }
            } finally {
                if (_uiState.value.account?.uid == uid) _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    fun approve(linkId: String) {
        viewModelScope.launch {
            approveLinkUseCase(linkId)
                .onSuccess { _messages.emit(MSG_LINK_APPROVED) }
                .onFailure { e -> _messages.emit(e.message ?: MSG_GENERIC_ERROR) }
        }
    }

    fun reject(linkId: String) {
        viewModelScope.launch {
            rejectLinkUseCase(linkId)
                .onFailure { e -> _messages.emit(e.message ?: MSG_GENERIC_ERROR) }
        }
    }

    fun selectChild(link: ParentChildLink?) {
        _uiState.update { it.copy(selectedChild = link, selectedChildGoals = emptyList()) }
        selectedChildUid.value = link?.childUid
    }

    fun createGoal(childUid: String, rewardType: RewardType, rewardDescription: String, targetCount: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            createChildGoalUseCase(childUid, rewardType, rewardDescription, targetCount)
                .onSuccess { _messages.emit(MSG_GOAL_CREATED) }
                .onFailure { e -> _messages.emit(e.message ?: MSG_GENERIC_ERROR) }
            _uiState.update { it.copy(isBusy = false) }
        }
    }

    private companion object {
        // Marker keys resolved to localized strings by the screen.
        const val MSG_LINK_REQUEST_SENT = "link_request_sent"
        const val MSG_LINK_APPROVED = "link_approved"
        const val MSG_GOAL_CREATED = "goal_created"
        const val MSG_GENERIC_ERROR = "generic_error"
    }
}
