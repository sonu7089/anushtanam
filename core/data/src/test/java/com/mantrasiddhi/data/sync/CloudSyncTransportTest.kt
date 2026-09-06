package com.mantrasiddhi.data.sync

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import com.mantrasiddhi.data.local.entity.CloudSyncEntity
import com.mantrasiddhi.data.remote.FirebaseContract as F
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Date

class CloudSyncTransportTest {
    private val firestore = mockk<FirebaseFirestore>()
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val transaction = mockk<Transaction>()
    private val server = mutableMapOf<DocumentReference, Map<String, Any?>>()
    private val references = mutableMapOf<String, DocumentReference>()
    private val writes = mutableListOf<DocumentReference>()

    private fun <T> completedTask(value: T): Task<T> {
        val task = mockk<Task<T>>()
        every { task.exception } returns null
        every { task.isCanceled } returns false
        every { task.result } returns value
        every { task.addOnCompleteListener(any<OnCompleteListener<T>>()) } answers {
            firstArg<OnCompleteListener<T>>().onComplete(task)
            task
        }
        return task
    }

    private fun doc(collection: String) = references.getOrPut(collection) {
        mockk<DocumentReference>(name = "$collection/owner")
    }

    private fun transport(): CloudSyncTransport {
        every { auth.currentUser } returns user
        every { user.uid } returns "owner"
        every { firestore.collection(any()) } answers {
            val name = firstArg<String>()
            mockk<CollectionReference> { every { document("owner") } returns doc(name) }
        }
        every { transaction.get(any()) } answers {
            val value = server[firstArg<DocumentReference>()]
            mockk<DocumentSnapshot> { every { data } returns value }
        }
        every { transaction.set(any(), any<Map<String, Any?>>()) } answers {
            val ref = firstArg<DocumentReference>()
            server[ref] = secondArg()
            writes += ref
            transaction
        }
        every { transaction.set(any(), any<Map<String, Any?>>(), any<SetOptions>()) } answers {
            val ref = firstArg<DocumentReference>()
            server[ref] = server[ref].orEmpty() + secondArg<Map<String, Any?>>()
            writes += ref
            transaction
        }
        every { transaction.delete(any()) } answers {
            server.remove(firstArg<DocumentReference>())
            writes += firstArg<DocumentReference>()
            transaction
        }
        every { firestore.runTransaction(any<Transaction.Function<Unit>>()) } answers {
            firstArg<Transaction.Function<Unit>>().apply(transaction)
            completedTask(Unit)
        }
        return CloudSyncTransport(firestore, auth)
    }

    private fun event(id: Long, kind: String, payload: Map<String, Any?> = emptyMap()) =
        CloudSyncEntity(id, "owner", "device", kind, JSONObject(payload).toString(), 1_000)

    @Test fun `a minute of target increments writes each document only once and replay is harmless`() = runTest {
        val transport = transport()
        val batch = listOf(
            event(1, SyncKind.TARGET_CREATE, mapOf(F.FIELD_TARGET_COUNT to 108, F.FIELD_CURRENT_COUNT to 0, F.FIELD_IS_COMPLETED to false)),
            event(2, SyncKind.TARGET_INCREMENT), event(3, SyncKind.TARGET_INCREMENT)
        )
        transport.deliver(batch)
        assertEquals(2L, server.getValue(doc(F.COLLECTION_SELF_TARGETS))[F.FIELD_CURRENT_COUNT])
        assertEquals(1, writes.count { it == doc(F.COLLECTION_SELF_TARGETS) })
        assertEquals(1, writes.count { it == doc(F.COLLECTION_USERS) })
        writes.clear()
        // The server committed but Room never saw the acknowledgement.
        transport.deliver(batch)
        assertTrue(writes.isEmpty())
        transport.deliver(batch + event(4, SyncKind.TARGET_INCREMENT))
        assertEquals(3L, server.getValue(doc(F.COLLECTION_SELF_TARGETS))[F.FIELD_CURRENT_COUNT])
    }

    @Test fun `progress seeds once and checkpoint prevents duplicate recitations`() = runTest {
        val transport = transport()
        fun progress(id: Long, score: Double) = event(id, SyncKind.PROGRESS, mapOf(
            "score" to score, "correct" to true, "date" to "2026-09-06",
            "seed" to mapOf(F.FIELD_TOTAL_SESSIONS to id, F.FIELD_CORRECT_SESSIONS to id,
                F.FIELD_SCORE_SUM to score, F.FIELD_BEST_SCORE to score)))
        val batch = listOf(progress(1, 0.9), progress(2, 1.0))
        transport.deliver(batch)
        transport.deliver(batch)
        val result = server.getValue(doc(F.COLLECTION_PROGRESS))
        assertEquals(2L, result[F.FIELD_TOTAL_SESSIONS])
        assertEquals(1.9, result[F.FIELD_SCORE_SUM])
        assertEquals(mapOf("device" to 2L), result[CloudSyncPolicy.CHECKPOINTS])
        assertEquals(1, writes.count { it == doc(F.COLLECTION_PROGRESS) })
    }

    @Test fun `cleared target stays deleted when its batch is replayed`() = runTest {
        val transport = transport()
        val batch = listOf(event(1, SyncKind.TARGET_CREATE, mapOf(F.FIELD_TARGET_COUNT to 108)),
            event(2, SyncKind.TARGET_CLEAR))
        transport.deliver(batch)
        writes.clear()
        transport.deliver(batch)
        assertFalse(server.containsKey(doc(F.COLLECTION_SELF_TARGETS)))
        assertTrue(writes.isEmpty())
    }

    @Test fun `different account cannot upload a queued batch`() = runTest {
        val transport = transport()
        every { user.uid } returns "different-owner"
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { transport.deliver(listOf(event(1, SyncKind.TARGET_CLEAR))) }
        }
        verify(exactly = 0) { firestore.runTransaction(any<Transaction.Function<Unit>>()) }
    }

    @Test fun `goal completion replay does not credit the next goal or one created after recitation`() = runTest {
        val transport = transport()
        val goalRefs = listOf(doc("first-goal"), doc("next-goal"), doc("future-goal"))
        for ((index, ref) in goalRefs.withIndex()) server[ref] = mapOf(
            F.FIELD_CHILD_UID to "owner", F.FIELD_PARENT_UID to "parent",
            F.FIELD_IS_COMPLETED to false, F.FIELD_CURRENT_COUNT to 0L,
            F.FIELD_TARGET_COUNT to 1L, F.FIELD_CREATED_AT to Timestamp(Date(if (index == 2) 2_000 else index.toLong())))
        val query = mockk<Query>()
        val collection = mockk<CollectionReference>()
        every { firestore.collection(F.COLLECTION_GOALS) } returns collection
        every { collection.whereEqualTo(F.FIELD_CHILD_UID, "owner") } returns query
        every { query.whereEqualTo(F.FIELD_IS_COMPLETED, false) } returns query
        every { query.get(Source.SERVER) } answers {
            val docs = goalRefs.filter { server[it]?.get(F.FIELD_IS_COMPLETED) == false }.map { ref ->
                mockk<DocumentSnapshot> { every { reference } returns ref }
            }
            completedTask(mockk<QuerySnapshot> { every { documents } returns docs })
        }
        val batch = listOf(event(1, SyncKind.CHILD_GOAL), event(2, SyncKind.CHILD_GOAL), event(3, SyncKind.CHILD_GOAL))
        transport.deliver(batch)
        assertEquals(true, server.getValue(goalRefs[0])[F.FIELD_IS_COMPLETED])
        assertEquals(true, server.getValue(goalRefs[1])[F.FIELD_IS_COMPLETED])
        assertEquals(0L, server.getValue(goalRefs[2])[F.FIELD_CURRENT_COUNT])
        writes.clear()
        transport.deliver(batch)
        assertTrue(writes.isEmpty())
    }
}
