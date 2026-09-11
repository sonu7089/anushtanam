package com.mantrasiddhi.data.messages

import com.google.android.gms.tasks.*
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeReflectionRepositoryTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val collection = mockk<CollectionReference>()
    private val document = mockk<DocumentReference>()
    private val clock = mockk<TeachingRefreshClock>()
    private var elapsed = 100_000L
    private val wall = System.currentTimeMillis()

    private fun note(title: String = "Begin again", expires: Long = wall + 60_000,
                     locale: String = "en", schema: Long = 1, start: Long = wall - 60_000,
                     type: String = "teaching", published: Boolean = true): DocumentSnapshot = mockk {
        every { exists() } returns true
        every { id } returns TeachingRepository.HOME_REFLECTION_ID
        every { getLong(any()) } answers { mapOf("schemaVersion" to schema, "revision" to 1L,
            "publishedAt" to start, "expiresAt" to expires)[firstArg<String>()] }
        every { getString(any()) } answers { mapOf("title" to title, "summary" to "Take a little time.",
            "body" to "You can begin again when you are ready.", "attribution" to "", "locale" to locale,
            "type" to type, "art" to "river")[firstArg<String>()] }
        every { getBoolean(any()) } answers { if (firstArg<String>() == "published") published else false }
    }

    private fun task(value: DocumentSnapshot? = null, pending: Boolean = false): Task<DocumentSnapshot> {
        val task = mockk<Task<DocumentSnapshot>>()
        every { task.addOnSuccessListener(any<OnSuccessListener<DocumentSnapshot>>()) } answers {
            if (!pending && value != null) firstArg<OnSuccessListener<DocumentSnapshot>>().onSuccess(value)
            task
        }
        every { task.addOnFailureListener(any<OnFailureListener>()) } answers {
            if (!pending && value == null) firstArg<OnFailureListener>().onFailure(IllegalStateException("Offline"))
            task
        }
        return task
    }

    private fun repository(cache: DocumentSnapshot? = null, server: Task<DocumentSnapshot> = task()): TeachingRepository {
        every { clock.now() } answers { elapsed }
        every { firestore.collection("publishedContent") } returns collection
        every { collection.document("home-reflection") } returns document
        every { document.get(Source.CACHE) } returns task(cache)
        every { document.get(Source.SERVER) } returns server
        return TeachingRepository(firestore, clock)
    }

    @Test fun `saved reflection is available before the network completes and survives timeout`() = runTest {
        val repository = repository(note(), task(pending = true))
        val job = launch { repository.refreshReflection() }
        runCurrent()
        assertEquals("Begin again", repository.reflection.value?.title)
        advanceUntilIdle()
        job.join()
        assertEquals("Begin again", repository.reflection.value?.title)
    }

    @Test fun `an online removal clears the cached reflection`() = runTest {
        val missing = mockk<DocumentSnapshot> { every { exists() } returns false }
        val repository = repository(note(), task(missing))
        repository.refreshReflection()
        assertNull(repository.reflection.value)
    }

    @Test fun `a revision replaces the saved text without reading the feed`() = runTest {
        val repository = repository(note(), task(note("Make a little time")))
        repository.refreshReflection()
        assertEquals("Make a little time", repository.reflection.value?.title)
        verify(exactly = 0) { collection.orderBy(any<String>(), any()) }
    }

    @Test fun `expiry future dates and unsupported content fall back safely`() = runTest {
        listOf(note(expires = wall - 1), note(start = wall + 60_000), note(locale = "hi"),
            note(schema = 2), note(type = "html"), note(published = false), note(title = "")).forEach {
            val repository = repository(it, task(it))
            repository.refreshReflection()
            assertNull(repository.reflection.value)
        }
    }

    @Test fun `fresh install without internet leaves the local notes available`() = runTest {
        val repository = repository()
        repository.refreshReflection()
        assertNull(repository.reflection.value)
    }

    @Test fun `entry and resume share a cooldown including failed attempts`() = runTest {
        val repository = repository()
        (1..10).map { async { repository.refreshReflection() } }.awaitAll()
        verify(exactly = 1) { document.get(Source.SERVER) }
        elapsed += 10_000
        repository.refreshReflection()
        verify(exactly = 2) { document.get(Source.SERVER) }
    }

    @Test fun `cancellation keeps cached text and does not bypass the cooldown`() = runTest {
        val repository = repository(note(), task(pending = true))
        val job = launch { repository.refreshReflection() }
        runCurrent()
        job.cancelAndJoin()
        repository.refreshReflection()
        assertEquals("Begin again", repository.reflection.value?.title)
        verify(exactly = 1) { document.get(Source.SERVER) }
    }
}
