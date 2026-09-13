package com.mantrasiddhi.presentation.state

import com.mantrasiddhi.domain.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Retain the last frame while a tab is hidden, but stop its data subscriptions.
 * Only the session observer stays attached, so sign-out/account replacement clears
 * personal state even while there are no UI collectors. A timeout alone cannot do that.
 */
internal fun <T> Flow<UserProfile?>.profileStateWhileSubscribed(
    scope: CoroutineScope,
    initialValue: T,
    reset: (UserProfile?) -> T,
    observe: (UserProfile) -> Flow<T>
): StateFlow<T> {
    val state = MutableStateFlow(initialValue)
    scope.launch {
        var owner: Pair<Long, String?>? = null
        this@profileStateWhileSubscribed.collectLatest { user ->
            val identity = user?.let { it.id to it.firebaseUid }
            if (identity != owner || user == null) {
                owner = identity
                state.value = reset(user)
            }
            if (user != null) {
                state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { visible ->
                    if (visible) observe(user).collect { state.value = it }
                }
            }
        }
    }
    return state.asStateFlow()
}
