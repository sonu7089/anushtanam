package com.mantrasiddhi.data.remote

import com.mantrasiddhi.domain.model.AccountResolution
import com.mantrasiddhi.domain.model.AuthAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-owned profile producer. The lock only protects state; I/O runs in [scope]. */
internal class AccountStateProducer(
    private val scope: CoroutineScope,
    private val currentUid: () -> String?,
    private val load: suspend (String) -> AuthAccount,
    private val reload: suspend (String) -> AuthAccount
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow<AccountResolution>(AccountResolution.Unresolved())
    val state = mutableState.asStateFlow()
    private var initialized = false
    private var uid: String? = null
    private var generation = 0L
    private var loading: Deferred<Result<AuthAccount>>? = null
    private var refreshing: Deferred<Result<AuthAccount>>? = null
    private var interactive = false

    fun identityChanged(newUid: String?) = synchronized(lock) {
        if (!initialized || uid != newUid) {
            initialized = true
            uid = newUid
            invalidate()
            mutableState.value = newUid?.let { AccountResolution.Unresolved(it) }
                ?: AccountResolution.SignedOut
        }
        if (newUid != null && !interactive && mutableState.value is AccountResolution.Unresolved && loading == null) {
            loading = start(newUid) { load(newUid) }
        }
    }

    suspend fun resolve(expectedUid: String): AuthAccount {
        val work = synchronized(lock) {
            check(currentUid() == expectedUid) { "Account changed" }
            identityChanged(expectedUid)
            val cached = (mutableState.value as? AccountResolution.Resolved)?.account
            if (cached != null) return cached
            loading ?: start(expectedUid) { load(expectedUid) }.also { loading = it }
        }
        return work.await().getOrThrow().also { check(currentUid() == expectedUid) { "Account changed" } }
    }

    suspend fun refresh(expectedUid: String): AuthAccount {
        val work = synchronized(lock) {
            check(currentUid() == expectedUid) { "Account changed" }
            identityChanged(expectedUid)
            refreshing?.takeIf { !it.isCompleted } ?: run {
                // A cache-capable startup read cannot satisfy a deliberate server recovery.
                // All concurrent recovery requests share this one follow-up, including writes.
                val initial = loading?.takeIf { !it.isCompleted }
                start(expectedUid) {
                    initial?.await()?.getOrThrow()
                    reload(expectedUid)
                }.also { refreshing = it }
            }
        }
        return work.await().getOrThrow().also { check(currentUid() == expectedUid) { "Account changed" } }
    }

    /** Interactive auth owns profile creation/linking; its auth callback must not race that work. */
    fun beginInteractive() = synchronized(lock) {
        interactive = true
        invalidate()
    }

    fun finishInteractive(account: AuthAccount?) = synchronized(lock) {
        identityChanged(currentUid())
        if (account != null && account.uid == uid && account.uid == currentUid()) {
            invalidate()
            mutableState.value = AccountResolution.Resolved(account)
        }
        interactive = false
        identityChanged(currentUid())
    }

    private fun invalidate() {
        generation++
        loading?.cancel()
        refreshing?.cancel()
        loading = null
        refreshing = null
    }

    private fun start(owner: String, block: suspend () -> AuthAccount): Deferred<Result<AuthAccount>> {
        val request = generation
        return scope.async(start = CoroutineStart.LAZY) {
            try {
                val account = block()
                synchronized(lock) {
                    if (request != generation || owner != uid || owner != currentUid()) {
                        throw CancellationException("Account changed")
                    }
                    check(account.uid == owner) { "Account changed" }
                    mutableState.value = AccountResolution.Resolved(account)
                }
                Result.success(account)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Retain failure in the shared result; callers can retry without killing
                // the process-owned producer or launching an unobserved failing job.
                Result.failure(failure)
            }
        }.also { it.start() }
    }
}
