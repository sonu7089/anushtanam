package com.mantrasiddhi.data.messages

import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TeachingRepositoryTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val collection = mockk<CollectionReference>()
    private val query = mockk<Query>()
    private val clock = mockk<TeachingRefreshClock>()
    private var now = 100_000L
    private val snapshot = mockk<QuerySnapshot> {
        every { documents } returns emptyList()
        every { size() } returns 0
    }

    private fun resultTask(failed: Boolean = false): Task<QuerySnapshot> {
        val task = mockk<Task<QuerySnapshot>>()
        every { task.addOnSuccessListener(any<OnSuccessListener<QuerySnapshot>>()) } answers {
            if (!failed) firstArg<OnSuccessListener<QuerySnapshot>>().onSuccess(snapshot)
            task
        }
        every { task.addOnFailureListener(any<OnFailureListener>()) } answers {
            if (failed) firstArg<OnFailureListener>().onFailure(IllegalStateException("Offline"))
            task
        }
        return task
    }

    private fun repository(failed: Boolean = false): TeachingRepository {
        every { clock.now() } answers { now }
        every { firestore.collection("publishedContent") } returns collection
        every { collection.orderBy("pinned", Query.Direction.DESCENDING) } returns query
        every { query.orderBy("publishedAt", Query.Direction.DESCENDING) } returns query
        every { query.limit(30) } returns query
        every { query.get(Source.SERVER) } returns resultTask(failed)
        every { query.get(Source.CACHE) } returns resultTask(failed)
        return TeachingRepository(firestore, clock)
    }

    @Test fun `repeated concurrent refreshes and reopening reuse the first page for ten seconds`() = runTest {
        val repository = repository()
        (1..20).map { async { repository.page() } }.awaitAll()
        now += 9_999
        repository.page()
        verify(exactly = 1) { query.get(Source.SERVER) }
        assertEquals(1, repository.refreshDelayMillis())
        now++
        repository.page()
        verify(exactly = 2) { query.get(Source.SERVER) }
    }

    @Test fun `failed requests cannot be spammed either`() = runTest {
        val repository = repository(failed = true)
        repeat(10) { assertTrue(repository.page().offline) }
        verify(exactly = 1) { query.get(Source.SERVER) }
        verify(exactly = 1) { query.get(Source.CACHE) }
        now += 10_000
        repository.page()
        verify(exactly = 2) { query.get(Source.SERVER) }
    }

    @Test fun `cooldown does not block reading an earlier page`() = runTest {
        val repository = repository()
        val cursor = mockk<DocumentSnapshot>()
        every { query.startAfter(cursor) } returns query
        repository.page()
        repository.page(cursor)
        verify(exactly = 2) { query.get(Source.SERVER) }
        assertEquals(10_000, repository.refreshDelayMillis())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `leaving during a request does not bypass the cooldown`() = runTest {
        val repository = repository()
        val pending = mockk<Task<QuerySnapshot>>()
        every { pending.addOnSuccessListener(any<OnSuccessListener<QuerySnapshot>>()) } returns pending
        every { pending.addOnFailureListener(any<OnFailureListener>()) } returns pending
        every { query.get(Source.SERVER) } returns pending
        val job = launch { repository.page() }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(repository.page().offline)
        verify(exactly = 1) { query.get(Source.SERVER) }
    }
}
