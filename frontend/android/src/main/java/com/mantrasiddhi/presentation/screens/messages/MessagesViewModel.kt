package com.mantrasiddhi.presentation.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.mantrasiddhi.data.messages.TeachingRepository
import com.mantrasiddhi.data.messages.TeachingNotifications
import com.mantrasiddhi.domain.model.TeachingMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

data class MessagesState(val items: List<TeachingMessage> = emptyList(), val loading: Boolean = true,
                         val offline: Boolean = false, val hasMore: Boolean = false,
                         val detail: TeachingMessage? = null, val refreshReady: Boolean = true)

@HiltViewModel
class MessagesViewModel @Inject constructor(private val repository: TeachingRepository,
    val notifications: TeachingNotifications) : ViewModel() {
    private val _state = MutableStateFlow(MessagesState())
    val state = _state.asStateFlow()
    private var cursor: DocumentSnapshot? = null
    private var busy = false

    init {
        viewModelScope.launch {
            repository.feedReadyAt.collectLatest {
                val remaining = repository.refreshDelayMillis()
                _state.value = _state.value.copy(refreshReady = remaining == 0L)
                if (remaining > 0) {
                    delay(remaining)
                    _state.value = _state.value.copy(refreshReady = true)
                }
            }
        }
    }

    fun refresh(id: String? = null, more: Boolean = false) {
        if (busy) return
        busy = true
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try {
                if (id != null) {
                    val result = repository.detail(id)
                    _state.value = MessagesState(loading = false, offline = result.offline, detail = result.message)
                } else {
                    val result = repository.page(if (more) cursor else null)
                    cursor = result.cursor
                    _state.value = MessagesState(items = ((if (more) _state.value.items else emptyList()) +
                        result.messages).distinctBy { it.id }.filter { it.isVisible(System.currentTimeMillis()) },
                        loading = false, offline = result.offline, hasMore = result.hasMore,
                        refreshReady = repository.refreshDelayMillis() == 0L)
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (_: Exception) { _state.value = _state.value.copy(loading = false, offline = true)
            } finally { busy = false }
        }
    }
}
