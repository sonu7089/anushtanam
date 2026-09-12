package com.mantrasiddhi.data.remote

import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.model.CloudGoal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Shares mapping/query work for the current child only; never retains a map of old accounts. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SharedChildGoals(
    accounts: Flow<AuthAccount?>,
    scope: CoroutineScope,
    private val currentUid: () -> String?,
    source: (String) -> Flow<List<CloudGoal>>
) {
    private data class Snapshot(val uid: String?, val goals: List<CloudGoal>)

    private val snapshots = accounts
        .map { it?.takeIf { account -> account.role == AccountRole.CHILD }?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(Snapshot(null, emptyList()))
            else source(uid).map { Snapshot(uid, it) }
        }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0), replay = 1)

    fun observe(childUid: String): Flow<List<CloudGoal>> = snapshots
        // No synthetic empty emission before the first real snapshot: Main uses it as
        // the notification baseline. Replayed data must belong to the current identity.
        .filter { it.uid == childUid && currentUid() == childUid }
        .map { it.goals }
}
