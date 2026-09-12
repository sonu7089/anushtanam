package com.mantrasiddhi.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.mantrasiddhi.data.sync.CloudSyncOutbox
import com.mantrasiddhi.data.sync.SyncKind
import com.mantrasiddhi.domain.repository.AuthRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FirestoreGoalRepositoryTest {
    @Test fun `rechecks the saved owner before enqueuing`() = runTest {
        val auth = mockk<FirebaseAuth>()
        every { auth.currentUser } returns mockk<FirebaseUser> { every { uid } returns "B" }
        val outbox = mockk<CloudSyncOutbox>(relaxed = true)
        val accounts = mockk<AuthRepository> { every { currentAccount } returns flowOf(null) }
        val repository = FirestoreGoalRepositoryImpl(auth, mockk(), outbox, accounts, backgroundScope)
        assertTrue(repository.incrementActiveGoalProgress("A").isFailure)
        coVerify(exactly = 0) { outbox.enqueue(any(), any(), any()) }
    }

    @Test fun `anonymous reward API rejects even the current owner`() = runTest {
        var uid = "A"
        val auth = mockk<FirebaseAuth>()
        every { auth.currentUser } returns mockk<FirebaseUser> { every { this@mockk.uid } answers { uid } }
        val outbox = mockk<CloudSyncOutbox>()
        coEvery { outbox.enqueue("A", SyncKind.CHILD_GOAL, emptyMap()) } coAnswers {
            uid = "B"
        }
        val accounts = mockk<AuthRepository> { every { currentAccount } returns flowOf(null) }
        val repository = FirestoreGoalRepositoryImpl(auth, mockk(), outbox, accounts, backgroundScope)
        assertTrue(repository.incrementActiveGoalProgress("A").isFailure)
        coVerify(exactly = 0) { outbox.enqueue("A", SyncKind.CHILD_GOAL, emptyMap()) }
        coVerify(exactly = 0) { outbox.enqueue("B", any(), any()) }
    }
}
