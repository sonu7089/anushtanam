package com.mantrasiddhi.data.remote

import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Production producers on Android dispatchers, with disposable in-memory account/goal fixtures. */
class AccountSharingDeviceTest {
    private fun child(uid: String) = AuthAccount(uid, AccountRole.CHILD, uid)
    private fun goal(id: String, uid: String) = CloudGoal(id, "P", uid, RewardType.OTHER, id, 10, 0, false)

    @Test fun subscribersAndTwentyReadsShareResolutionAndRefresh() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val loads = AtomicInteger()
            val refreshes = AtomicInteger()
            val pending = CompletableDeferred<Unit>()
            val refreshing = CompletableDeferred<Unit>()
            val producer = AccountStateProducer(scope, { "A" }, {
                loads.incrementAndGet(); pending.await(); child(it)
            }, {
                refreshes.incrementAndGet(); refreshing.await(); child(it).copy(displayName = "Updated")
            })
            val collectors = List(7) { scope.launch(Dispatchers.Main) { producer.state.collect() } }
            producer.identityChanged("A")
            assertEquals(AccountResolution.Unresolved("A"), producer.state.value)
            pending.complete(Unit)
            withTimeout(5_000) { producer.state.first { it is AccountResolution.Resolved } }
            repeat(20) { assertEquals("A", producer.resolve("A").uid) }
            assertEquals(1, loads.get())
            val calls = List(7) { scope.async(start = CoroutineStart.UNDISPATCHED) { producer.refresh("A") } }
            refreshing.complete(Unit)
            withTimeout(5_000) { calls.awaitAll() }
            assertEquals(1, refreshes.get())
            assertEquals("Updated", (producer.state.value as AccountResolution.Resolved).account.displayName)
            collectors.forEach { it.cancel() }
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
    }

    @Test fun switchDuringResolutionClearsReplayAndRejectsOldResult() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val uid = AtomicReference<String?>("A")
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val producer = AccountStateProducer(scope, { uid.get() }, {
                if (it == "A") try { started.complete(Unit); awaitCancellation() }
                finally { cancelled.complete(Unit) }
                child(it).copy(needsChildPinSetup = true)
            }, { child(it) })
            producer.identityChanged("A")
            withTimeout(5_000) { started.await() }
            uid.set("B"); producer.identityChanged("B")
            withTimeout(5_000) { cancelled.await() }
            val resolved = withTimeout(5_000) { producer.state.first { it is AccountResolution.Resolved } } as AccountResolution.Resolved
            assertEquals("B", resolved.account.uid)
            assertTrue(resolved.account.needsChildPinSetup)
            uid.set(null); producer.identityChanged(null)
            assertEquals(AccountResolution.SignedOut, producer.state.value)
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
    }

    @Test fun mainAndFamilyShareGoalWorkAndCancelOnAccountReplacement() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val active = AtomicInteger()
        try {
            val accounts = MutableStateFlow<AuthAccount?>(child("A"))
            val starts = AtomicInteger()
            val source = MutableStateFlow(listOf(goal("old", "A")))
            val shared = SharedChildGoals(accounts, scope, { accounts.value?.uid }) { uid -> flow {
                starts.incrementAndGet(); active.incrementAndGet()
                try {
                    if (uid == "A") emitAll(source) else emitAll(MutableStateFlow(listOf(goal("b", uid))))
                } finally { active.decrementAndGet() }
            } }
            val main = MutableStateFlow<List<CloudGoal>?>(null)
            val family = MutableStateFlow<List<CloudGoal>?>(null)
            val a = scope.launch(Dispatchers.Main) { shared.observe("A").collect { main.value = it } }
            val b = scope.launch(Dispatchers.Main) { shared.observe("A").collect { family.value = it } }
            withTimeout(5_000) { main.first { it != null }; family.first { it != null } }
            assertEquals(1, starts.get())
            b.cancelAndJoin()
            accounts.value = child("A").copy(displayName = "Renamed")
            source.value += goal("new", "A")
            withTimeout(5_000) { main.first { it?.size == 2 } }
            assertEquals(1, starts.get())
            accounts.value = child("B")
            val fresh = withTimeout(5_000) { shared.observe("B").first() }
            assertEquals("B", fresh.single().childUid)
            assertEquals(1, active.get())
            a.cancelAndJoin()
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
        assertEquals(0, active.get())
    }
}
