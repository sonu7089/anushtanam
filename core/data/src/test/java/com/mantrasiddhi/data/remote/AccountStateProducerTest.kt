package com.mantrasiddhi.data.remote

import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountStateProducerTest {
    private fun account(uid: String) = AuthAccount(uid, AccountRole.CHILD, uid)

    @Test fun `seven collectors and twenty role reads share one resolution`() = runTest {
        var loads = 0
        val pending = CompletableDeferred<AuthAccount>()
        val producer = AccountStateProducer(backgroundScope, { "A" }, { loads++; pending.await() }, { error("refresh") })
        producer.identityChanged("A")
        repeat(7) { backgroundScope.launch { producer.state.collect() } }
        runCurrent()
        assertEquals(AccountResolution.Unresolved("A"), producer.state.value)
        assertEquals(1, loads)
        pending.complete(account("A"))
        runCurrent()
        repeat(20) { assertEquals(account("A"), producer.resolve("A")) }
        producer.identityChanged("A")
        runCurrent()
        assertEquals(1, loads)
    }

    @Test fun `unresolved signed out and recovery remain distinct`() = runTest {
        var uid: String? = null
        val recovery = account("A").copy(googleLinked = true, profileNeedsSync = true)
        val producer = AccountStateProducer(backgroundScope, { uid }, { recovery }, { recovery.copy(profileNeedsSync = false) })
        assertEquals(AccountResolution.Unresolved(), producer.state.value)
        producer.identityChanged(null)
        assertEquals(AccountResolution.SignedOut, producer.state.value)
        uid = "A"
        producer.identityChanged(uid)
        assertEquals(AccountResolution.Unresolved("A"), producer.state.value)
        runCurrent()
        assertEquals(AccountResolution.Resolved(recovery), producer.state.value)
        assertEquals(false, producer.refresh("A").profileNeedsSync)
        uid = null
        producer.identityChanged(null)
        assertEquals(AccountResolution.SignedOut, producer.state.value)
    }

    @Test fun `concurrent explicit refreshes coalesce and a later refresh remains deliberate`() = runTest {
        var refreshes = 0
        val pending = CompletableDeferred<Unit>()
        val producer = AccountStateProducer(backgroundScope, { "A" }, { account(it) }, {
            refreshes++
            pending.await()
            account(it).copy(displayName = "Updated")
        })
        producer.identityChanged("A")
        runCurrent()
        val callers = List(7) { async { producer.refresh("A") } }
        runCurrent()
        assertEquals(1, refreshes)
        pending.complete(Unit)
        callers.awaitAll().forEach { assertEquals("Updated", it.displayName) }
        producer.refresh("A")
        assertEquals(2, refreshes)
    }

    @Test fun `refresh during startup queues one server recovery after the cache capable read`() = runTest {
        var loads = 0
        var refreshes = 0
        val initial = CompletableDeferred<AuthAccount>()
        val producer = AccountStateProducer(backgroundScope, { "A" }, { loads++; initial.await() }, {
            refreshes++
            account(it)
        })
        producer.identityChanged("A")
        val callers = List(7) { async { producer.refresh("A") } }
        runCurrent()
        assertEquals(1, loads)
        assertEquals(0, refreshes)
        initial.complete(account("A").copy(profileNeedsSync = true))
        callers.awaitAll().forEach { assertFalse(it.profileNeedsSync) }
        assertEquals(1, refreshes)
    }

    @Test fun `switch and sign out synchronously clear replay and cancel old work`() = runTest {
        var uid: String? = "A"
        var cancelled = false
        val producer = AccountStateProducer(backgroundScope, { uid }, {
            if (it == "A") try { awaitCancellation() } finally { cancelled = true }
            account(it)
        }, { account(it) })
        producer.identityChanged(uid)
        runCurrent()
        uid = "B"
        producer.identityChanged(uid)
        assertEquals(AccountResolution.Unresolved("B"), producer.state.value)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(AccountResolution.Resolved(account("B")), producer.state.value)
        uid = null
        producer.identityChanged(null)
        assertEquals(AccountResolution.SignedOut, producer.state.value)
    }

    @Test fun `A B A discards a late non cooperative result from the first A`() = runTest {
        var uid: String? = "A"
        var loads = 0
        val oldResult = CompletableDeferred<Unit>()
        val producer = AccountStateProducer(backgroundScope, { uid }, {
            loads++
            if (loads == 1) withContext(NonCancellable) { oldResult.await(); account(it).copy(displayName = "Stale") }
            else account(it)
        }, { account(it) })
        producer.identityChanged(uid)
        runCurrent()
        uid = "B"; producer.identityChanged(uid)
        uid = "A"; producer.identityChanged(uid)
        runCurrent()
        oldResult.complete(Unit)
        runCurrent()
        assertEquals(AccountResolution.Resolved(account("A")), producer.state.value)
    }

    @Test fun `refresh cannot publish or return for a replaced identity`() = runTest {
        var uid: String? = "A"
        val pending = CompletableDeferred<Unit>()
        val producer = AccountStateProducer(backgroundScope, { uid }, { account(it) }, {
            withContext(NonCancellable) { pending.await() }
            account(it).copy(displayName = "Late")
        })
        producer.identityChanged(uid)
        runCurrent()
        val refresh = async { runCatching { producer.refresh("A") } }
        runCurrent()
        uid = "B"; producer.identityChanged(uid)
        pending.complete(Unit)
        runCurrent()
        assertTrue(refresh.await().isFailure)
        assertEquals(AccountResolution.Resolved(account("B")), producer.state.value)
    }

    @Test fun `interactive creation defers listener load and publishes the completed profile`() = runTest {
        var uid: String? = null
        var loads = 0
        val producer = AccountStateProducer(backgroundScope, { uid }, { loads++; account(it) }, { account(it) })
        producer.identityChanged(null)
        producer.beginInteractive()
        uid = "A"; producer.identityChanged(uid)
        runCurrent()
        assertEquals(0, loads)
        val created = account("A").copy(profileNeedsSync = true)
        producer.finishInteractive(created)
        runCurrent()
        assertEquals(AccountResolution.Resolved(created), producer.state.value)
        assertEquals(0, loads)
    }

    @Test fun `failed interactive creation resumes recovery and failed refresh can retry`() = runTest {
        var uid: String? = null
        var refreshes = 0
        val producer = AccountStateProducer(backgroundScope, { uid }, { account(it).copy(profileNeedsSync = true) }, {
            if (++refreshes == 1) error("offline")
            account(it)
        })
        producer.beginInteractive()
        uid = "A"; producer.identityChanged(uid)
        producer.finishInteractive(null)
        runCurrent()
        assertTrue((producer.state.value as AccountResolution.Resolved).account.profileNeedsSync)
        assertTrue(runCatching { producer.refresh("A") }.isFailure)
        assertFalse(producer.refresh("A").profileNeedsSync)
        assertEquals(2, refreshes)
    }

    @Test fun `cancelling a refresh caller does not cancel the shared refresh`() = runTest {
        val pending = CompletableDeferred<Unit>()
        var refreshes = 0
        val producer = AccountStateProducer(backgroundScope, { "A" }, { account(it) }, {
            refreshes++; pending.await(); account(it)
        })
        producer.identityChanged("A")
        val first = launch { producer.refresh("A") }
        val second = async { producer.refresh("A") }
        runCurrent()
        first.cancel()
        pending.complete(Unit)
        assertEquals(account("A"), second.await())
        assertEquals(1, refreshes)
    }
}
