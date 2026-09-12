package com.mantrasiddhi.data.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CloudSyncRequestsTest {
    @Test fun `leaving Settings does not cancel sync and repeated taps share one request`() = runBlocking {
        val runner = mockk<CloudSyncRunner>()
        val auth = mockk<FirebaseAuth>()
        val user = mockk<FirebaseUser>()
        every { auth.currentUser } returns user
        every { user.uid } returns "owner"
        every { runner.activeUid } returns MutableStateFlow<String?>(null)
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        coEvery { runner.syncNow("owner") } coAnswers {
            if (!entered.isCompleted) entered.complete(Unit)
            finish.await(); false
        }
        val requests = CloudSyncRequests(runner, mockk(relaxed = true), mockk(relaxed = true), auth, mockk(relaxed = true))
        withTimeout(5_000) {
            val sync = requests.request("owner", manual = true)
            val settingsWait = launch { sync.join() }
            entered.await()
            repeat(10) { assertSame(sync, requests.request("owner", manual = true)) }
            settingsWait.cancelAndJoin()
            assertTrue(sync.isActive)
            finish.complete(Unit)
            sync.join()
            coVerify(exactly = 2) { runner.syncNow("owner") }
        }
    }
    @Test fun `new account cancels old request and starts without another local event`() = runBlocking {
        val runner = mockk<CloudSyncRunner>()
        val auth = mockk<FirebaseAuth>()
        val user = mockk<FirebaseUser>()
        val owner = java.util.concurrent.atomic.AtomicReference("first")
        every { auth.currentUser } returns user
        every { user.uid } answers { owner.get() }
        every { runner.activeUid } returns MutableStateFlow<String?>(null)
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        coEvery { runner.syncNow("first") } coAnswers {
            entered.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        coEvery { runner.syncNow("second") } coAnswers { assertTrue(cancelled.isCompleted); false }
        val requests = CloudSyncRequests(runner, mockk(relaxed = true), mockk(relaxed = true), auth, mockk(relaxed = true))
        withTimeout(5_000) {
            val first = requests.request("first", manual = true)
            entered.await()
            owner.set("second")
            val second = requests.request("second", manual = true)
            assertNotSame(first, second)
            second.join()
            assertTrue(first.isCancelled)
            coVerify(exactly = 1) { runner.syncNow("second") }
        }
    }

}
