package com.mantrasiddhi.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.sync.BackupObservation
import com.mantrasiddhi.data.sync.CloudSyncPolicy
import com.mantrasiddhi.data.sync.JapaCloudSync
import com.mantrasiddhi.data.sync.JapaSyncPolicy
import com.mantrasiddhi.data.sync.CloudSyncRequests
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class BackupReview(val id: String, val date: String, val current: Long,
    val alternative: Long, val operation: String)

internal data class BackupUiState(val localOnly: Boolean = true, val waiting: Boolean = false,
    val attention: Boolean = false, val lastSuccess: Long = 0, val reviews: List<BackupReview> = emptyList(),
    val syncing: Boolean = false, val manualReady: Boolean = true, val quarantined: Int = 0)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModel @Inject constructor(
    currentUser: GetCurrentUserUseCase,
    private val observations: BackupObservation,
    private val sync: JapaCloudSync,
    private val requests: CloudSyncRequests
) : ViewModel() {
    private val user = currentUser()
    internal val state = user.flatMapLatest { profile ->
        val uid = profile?.firebaseUid
        if (uid == null) flowOf(BackupUiState()) else {
            val content = combine(
                observations.state(uid), observations.pending(uid),
                observations.conflicts(uid), observations.entries(profile.id), observations.quarantined(uid)
            ) { backup, pending, conflicts, entries, quarantined ->
                BackupUiState(localOnly = false, waiting = pending > 0 || backup?.restorePending != false,
                    attention = backup?.needsAttention == true, lastSuccess = backup?.lastSuccessMs ?: 0, quarantined = quarantined,
                    reviews = conflicts.mapNotNull { conflict ->
                        val entry = entries.firstOrNull { it.id == conflict.entryId } ?: return@mapNotNull null
                        val first = CloudSyncPolicy.decode(conflict.firstPayload)
                        val second = CloudSyncPolicy.decode(conflict.secondPayload)
                        val alternative = if (JapaSyncPolicy.newer(first, second)) second else first
                        BackupReview(conflict.id, com.mantrasiddhi.domain.util.SandhyaDateKey.parse(entry.dateKey)
                            .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)), entry.quantity,
                            (alternative["quantity"] as Number).toLong(), entry.operationId)
                    })
            }
            val ready = requests.manualReady(uid)
            combine(content, requests.activeUid, ready) { state, active, manualReady ->
                state.copy(syncing = active == uid, manualReady = manualReady)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    internal val saving = MutableStateFlow(false)
    internal val failed = MutableStateFlow(false)
    internal fun syncNow() {
        val uid = user.value?.firebaseUid ?: return
        val current = state.value
        if (current.localOnly || current.syncing || !current.manualReady) return
        requests.request(uid, manual = true)
    }
    internal fun resolve(review: BackupReview, alternative: Boolean) {
        val uid = user.value?.firebaseUid ?: return
        if (saving.value) return
        saving.value = true
        failed.value = false
        viewModelScope.launch {
            try { sync.resolveConflict(uid, review.id, alternative, review.operation) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed.value = true }
            finally { saving.value = false }
        }
    }
}
