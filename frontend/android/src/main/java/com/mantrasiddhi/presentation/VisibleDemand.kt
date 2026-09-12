package com.mantrasiddhi.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** UI subscriptions own reads; mutation/save jobs keep their independent ViewModel scope. */
internal fun <T> ViewModel.observeWhileSubscribed(state: MutableStateFlow<T>,
    onStop: () -> Unit = {}, block: suspend CoroutineScope.() -> Unit) = viewModelScope.launch {
    state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { visible ->
        if (visible) try { coroutineScope(block) } finally { onStop() }
    }
}
