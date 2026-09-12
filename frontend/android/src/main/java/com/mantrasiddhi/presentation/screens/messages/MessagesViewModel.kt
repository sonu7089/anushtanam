package com.mantrasiddhi.presentation.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.mantrasiddhi.data.messages.TeachingRepository
import com.mantrasiddhi.data.messages.TeachingNotifications
import com.mantrasiddhi.domain.model.TeachingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class MessagesState(val items: List<TeachingMessage> = emptyList(), val loading: Boolean = true,
    val offline: Boolean = false, val hasMore: Boolean = false, val detail: TeachingMessage? = null,
    val refreshReady: Boolean = true, val refreshing: Boolean = false, val appending: Boolean = false,
    val refreshFailed: Boolean = false, val appendFailed: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MessagesViewModel @Inject constructor(private val repository: TeachingRepository,
    val notifications: TeachingNotifications) : ViewModel() {
    private val _state = MutableStateFlow(MessagesState())
    private var cursor: DocumentSnapshot? = null
    private var request: Job? = null
    private var requestId = 0L
    private var contentId: String? = null
    private val ready = repository.feedReadyAt.flatMapLatest {
        flow {
            val remaining = repository.refreshDelayMillis()
            emit(remaining == 0L)
            if (remaining > 0) { delay(remaining); emit(true) }
        }
    }
    val state = combine(_state, ready) { state, ready -> state.copy(refreshReady = ready) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), MessagesState())

    fun stop() {
        requestId++
        request?.cancel(); request = null
        _state.value = _state.value.copy(loading = false, refreshing = false, appending = false)
    }

    fun refresh(id: String? = null, more: Boolean = false) {
        val changed = contentId != id
        if (!changed && request?.isActive == true) return
        if (more && (cursor == null || !_state.value.hasMore)) return
        request?.cancel()
        val token = ++requestId
        contentId = id
        if (changed) { _state.value = MessagesState(); cursor = null }
        val cached = _state.value.items.isNotEmpty() || _state.value.detail != null
        _state.value = _state.value.copy(loading = !cached && !more, refreshing = !more,
            appending = more, refreshFailed = false, appendFailed = false)
        request = viewModelScope.launch {
            try {
                if (id != null) repository.observeDetail(id).collect { result ->
                    if (token == requestId) _state.value = _state.value.copy(detail = result.message
                        ?: _state.value.detail?.takeIf { result.offline && it.isVisible(System.currentTimeMillis()) },
                        loading = false, refreshing = result.refreshing, offline = result.offline,
                        refreshFailed = result.offline && !result.refreshing)
                } else if (more) {
                    val result = repository.page(cursor)
                    if (token == requestId) {
                        // A failed append must retain its cursor so Retry requests the same page.
                        if (result.offline) _state.value = _state.value.copy(appending = false, appendFailed = true)
                        else {
                            cursor = result.cursor
                            _state.value = _state.value.copy(items = (_state.value.items + result.messages).distinctBy { it.id }
                                .filter { it.isVisible(System.currentTimeMillis()) }, hasMore = result.hasMore, appending = false)
                        }
                    }
                } else repository.observePage().collect { result ->
                    if (token == requestId) {
                        val previous = _state.value
                        val retain = (result.refreshing || result.offline) && previous.items.isNotEmpty()
                        val retainedPages = retain && previous.items.size > result.messages.size
                        if (!retainedPages) cursor = result.cursor
                        val items = if (retain) (result.messages + previous.items).distinctBy { it.id } else result.messages
                        _state.value = previous.copy(items = items.filter { it.isVisible(System.currentTimeMillis()) }, loading = false,
                            refreshing = result.refreshing, offline = result.offline,
                            hasMore = if (retainedPages) previous.hasMore else result.hasMore,
                            refreshFailed = result.offline && !result.refreshing)
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                if (token == requestId) _state.value = _state.value.copy(loading = false, refreshing = false,
                    appending = false, offline = true, refreshFailed = !more, appendFailed = more)
            }
        }
    }
}
