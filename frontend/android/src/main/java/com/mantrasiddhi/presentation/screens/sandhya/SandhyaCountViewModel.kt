package com.mantrasiddhi.presentation.screens.sandhya

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.SandhyaCountRepository
import com.mantrasiddhi.domain.usecase.GetCurrentUserUseCase
import com.mantrasiddhi.domain.util.SandhyaDateKey
import com.mantrasiddhi.resources.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class SandhyaCountEditor(
    val draft: SandhyaCountDraft,
    val paused: Boolean = false,
    val saving: Boolean = false,
    val errorRes: Int? = null,
    val history: List<SandhyaCountChange> = emptyList(),
    val undoInput: String? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SandhyaCountViewModel @Inject constructor(
    private val repository: SandhyaCountRepository,
    private val preferences: SandhyaPreferences,
    getCurrentUser: GetCurrentUserUseCase
) : ViewModel() {
    private val currentUser = getCurrentUser()
    val minimums = currentUser.flatMapLatest { user ->
        if (user == null) flowOf(SandhyaMinimums()) else preferences.minimums(user.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SandhyaMinimums())
    private val actions = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val _editor = MutableStateFlow<SandhyaCountEditor?>(null)
    val editor = _editor.asStateFlow()
    private val _saved = MutableSharedFlow<SandhyaCountChange>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _errors = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val errors = _errors.asSharedFlow()

    init {
        viewModelScope.launch {
            for (action in actions) {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _editor.value = _editor.value?.copy(saving = false, errorRes = R.string.sandhya_count_save_error)
                    if (_editor.value == null) _errors.tryEmit(R.string.sandhya_count_save_error)
                }
            }
        }
        viewModelScope.launch {
            currentUser.map { it?.id }.distinctUntilChanged().collect { userId ->
                if (_editor.value?.draft?.userId != userId) _editor.value = null
            }
        }
    }

    fun open(period: SandhyaPeriod, date: LocalDate, mode: SandhyaCountMode) {
        val userId = currentUser.value?.id ?: return
        actions.trySend {
            if (currentUser.value?.id != userId) return@trySend
            val pending = repository.getDraft(userId)
            val dateKey = SandhyaDateKey.of(date)
            val count = repository.getCount(userId, dateKey, period)
            val draft = pending ?: SandhyaCountDraft(
                id = UUID.randomUUID().toString(), userId = userId, dateKey = dateKey, period = period,
                zoneId = count?.zoneId ?: preferences.current().zone().id,
                mode = if (mode == SandhyaCountMode.RECORD && count != null) SandhyaCountMode.EDIT else mode,
                input = if (mode == SandhyaCountMode.EDIT || (mode == SandhyaCountMode.RECORD && count != null))
                    (count?.quantity ?: 0).toString() else "0",
                baseQuantity = count?.quantity, baseRevision = count?.revision ?: 0,
                updatedAtMillis = System.currentTimeMillis()
            )
            repository.saveDraft(draft)
            val history = repository.history(userId, draft.dateKey, draft.period)
            if (currentUser.value?.id == userId) {
                _editor.value = SandhyaCountEditor(draft, paused = pending != null, history = history)
            }
        }
    }

    fun changeInput(input: String) = edit { it.copy(input = input) }

    fun changeDate(date: LocalDate) {
        val editor = _editor.value ?: return
        if (editor.saving || editor.draft.mode == SandhyaCountMode.EDIT || currentUser.value?.id != editor.draft.userId) return
        val dateKey = SandhyaDateKey.of(date)
        if (dateKey == editor.draft.dateKey) return
        _editor.value = editor.copy(saving = true)
        actions.trySend {
            SandhyaCountMath.validateDate(dateKey, editor.draft.zoneId)
            val count = repository.getCount(editor.draft.userId, dateKey, editor.draft.period)
            val draft = editor.draft.copy(dateKey = dateKey, baseQuantity = count?.quantity,
                baseRevision = count?.revision ?: 0, zoneId = count?.zoneId ?: editor.draft.zoneId,
                mode = if (editor.draft.mode == SandhyaCountMode.COUNTER) SandhyaCountMode.COUNTER
                    else if (count == null) SandhyaCountMode.RECORD else SandhyaCountMode.ADD)
            repository.saveDraft(draft)
            if (_editor.value?.draft?.id == draft.id) {
                _editor.value = editor.copy(draft = draft, saving = false,
                    history = repository.history(draft.userId, dateKey, draft.period))
            }
        }
    }

    fun step(amount: Long) {
        val editor = _editor.value ?: return
        if (editor.draft.mode == SandhyaCountMode.COUNTER && editor.paused) return
        edit { draft ->
            val count = draft.quantity ?: return@edit draft
            val next = runCatching { Math.addExact(count, amount) }.getOrNull() ?: return@edit draft
            draft.copy(input = next.coerceAtLeast(0).toString())
        }
        if (_editor.value?.draft?.input != editor.draft.input) {
            _editor.value = _editor.value?.copy(undoInput = editor.draft.input)
        }
    }

    fun undo() {
        val input = _editor.value?.undoInput ?: return
        changeInput(input)
        _editor.value = _editor.value?.copy(undoInput = null)
    }

    private fun edit(change: (SandhyaCountDraft) -> SandhyaCountDraft) {
        val editor = _editor.value ?: return
        if (editor.saving || editor.draft.userId != currentUser.value?.id) return
        val draft = change(editor.draft).copy(updatedAtMillis = System.currentTimeMillis())
        _editor.value = editor.copy(draft = draft, errorRes = null)
        actions.trySend { repository.saveDraft(draft) }
    }

    fun togglePause() {
        _editor.value = _editor.value?.let { if (it.saving) it else it.copy(paused = !it.paused) }
    }

    fun dismiss() {
        if (_editor.value?.saving != true) _editor.value = null
    }

    fun discard() {
        val editor = _editor.value ?: return
        if (editor.saving) return
        _editor.value = editor.copy(saving = true)
        actions.trySend {
            repository.discardDraft(editor.draft.userId, editor.draft.id)
            if (_editor.value?.draft?.id == editor.draft.id) _editor.value = null
        }
    }

    fun save() {
        val editor = _editor.value ?: return
        if (editor.saving || editor.draft.newTotal == null || editor.draft.userId != currentUser.value?.id) return
        _editor.value = editor.copy(saving = true, errorRes = null)
        actions.trySend {
            if (currentUser.value?.id != editor.draft.userId) return@trySend
            try {
                val saved = repository.save(editor.draft)
                if (_editor.value?.draft?.id == editor.draft.id) _editor.value = null
                if (currentUser.value?.id == editor.draft.userId) _saved.emit(saved)
            } catch (_: SandhyaCountConflict) {
                val count = repository.getCount(editor.draft.userId, editor.draft.dateKey, editor.draft.period)
                val refreshed = editor.draft.copy(baseQuantity = count?.quantity, baseRevision = count?.revision ?: 0)
                repository.saveDraft(refreshed)
                if (_editor.value?.draft?.id == editor.draft.id) {
                    _editor.value = editor.copy(draft = refreshed, saving = false,
                        errorRes = R.string.sandhya_count_changed,
                        history = repository.history(refreshed.userId, refreshed.dateKey, refreshed.period))
                }
            }
        }
    }
}
