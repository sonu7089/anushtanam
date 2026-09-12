package com.mantrasiddhi.data.remote

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import com.mantrasiddhi.domain.model.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GoogleAccountLinkingTest {
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val firestore = mockk<FirebaseFirestore>()
    private val ref = mockk<DocumentReference>()
    private val transaction = mockk<Transaction>()
    private val provider = mockk<UserInfo>(relaxed = true)
    private val fields = mutableMapOf<String, Any?>("role" to "PARENT", "displayName" to "Amma", "photoSource" to "LOCAL")
    private var linked = false
    private lateinit var repository: FirebaseAuthRepositoryImpl
    private fun TestScope.createRepository() {
        repository = FirebaseAuthRepositoryImpl(auth, firestore, mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), backgroundScope)
    }

    private fun <T> task(value: T? = null, failure: Exception? = null): Task<T> {
        val task = mockk<Task<T>>()
        every { task.exception } returns failure
        every { task.isCanceled } returns false
        every { task.result } answers { value as T }
        every { task.addOnCompleteListener(any<OnCompleteListener<T>>()) } answers {
            firstArg<OnCompleteListener<T>>().onComplete(task); task
        }
        return task
    }

    init {
        every { auth.addAuthStateListener(any()) } just Runs
        every { auth.removeAuthStateListener(any()) } just Runs
        every { auth.currentUser } returns user
        every { user.uid } returns "original-owner"
        every { user.email } returns "meera@example.com"
        every { user.displayName } returns "Meera from Google"
        every { user.providerData } answers { if (linked) listOf(provider) else emptyList() }
        every { provider.providerId } returns "google.com"
        every { provider.photoUrl } returns null
        every { firestore.collection("users") } returns mockk { every { document("original-owner") } returns ref }
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.exists() } returns true
        every { snapshot.getString(any()) } answers { fields[firstArg<String>()] as? String }
        every { ref.get() } answers { task(snapshot) }
        every { ref.get(Source.SERVER) } answers { task(snapshot) }
        every { transaction.get(ref) } returns snapshot
        every { transaction.set(ref, any<Map<String, Any?>>(), any<SetOptions>()) } answers {
            fields.putAll(secondArg<Map<String, Any?>>()); transaction
        }
        every { firestore.runTransaction(any<Transaction.Function<Any>>()) } answers {
            task(firstArg<Transaction.Function<Any>>().apply(transaction))
        }
    }

    @Test fun `linking never replaces the uid name role or chosen cloud photo`() = runTest {
        createRepository()
        every { user.linkWithCredential(any()) } answers {
            linked = true
            task(mockk<AuthResult> { every { user } returns this@GoogleAccountLinkingTest.user })
        }
        val result = repository.linkGoogle("test-token", "original-owner").getOrThrow()
        assertEquals("original-owner", result.uid)
        assertEquals("Amma", result.displayName)
        assertEquals(AccountRole.PARENT, result.role)
        assertEquals("LOCAL", fields["photoSource"])
        assertTrue(result.googleLinked)
        verify(exactly = 0) { auth.signInWithCredential(any()) }
    }

    @Test fun `credential already owned elsewhere returns collision without switching accounts`() = runTest {
        createRepository()
        every { user.linkWithCredential(any()) } returns task(failure = mockk<FirebaseAuthUserCollisionException>(relaxed = true))
        val error = repository.linkGoogle("test-token", "original-owner").exceptionOrNull()
        assertEquals(GoogleAuthProblem.COLLISION, (error as GoogleAuthFailure).problem)
        verify(exactly = 0) { auth.signInWithCredential(any()) }
        assertEquals("original-owner", auth.currentUser?.uid)
    }

    @Test fun `stale settings screen cannot link to a different signed in uid`() = runTest {
        createRepository()
        val error = repository.linkGoogle("test-token", "someone-else").exceptionOrNull()
        assertEquals(GoogleAuthProblem.WRONG_ACCOUNT, (error as GoogleAuthFailure).problem)
        verify(exactly = 0) { user.linkWithCredential(any()); auth.signInWithCredential(any()) }
    }

    @Test fun `existing ID and PIN child can link Google without changing ID role or UID`() = runTest {
        createRepository()
        fields["role"] = "CHILD"
        fields["linkCode"] = "123456789012"
        every { user.email } returns "c123456789012@child.anushtanam.app"
        val password = mockk<UserInfo>(relaxed = true) { every { providerId } returns "password" }
        every { user.providerData } answers { if (linked) listOf(password, provider) else listOf(password) }
        every { user.linkWithCredential(any()) } answers {
            linked = true
            task(mockk<AuthResult> { every { user } returns this@GoogleAccountLinkingTest.user })
        }
        val result = repository.linkGoogle("test-token", "original-owner").getOrThrow()
        assertEquals(AccountRole.CHILD, result.role)
        assertEquals("123456789012", result.linkCode)
        assertEquals("original-owner", result.uid)
        assertFalse(result.needsChildPinSetup)
        verify(exactly = 0) { auth.signInWithCredential(any()) }
    }

    @Test fun `child entry rejects an existing adult Google account and signs it out`() = runTest {
        createRepository()
        assertWrongRole(AccountRole.CHILD)
    }

    @Test fun `adult entry rejects an existing child Google account and preserves its role`() = runTest {
        createRepository()
        fields["role"] = "CHILD"
        assertWrongRole(AccountRole.PARENT)
        assertEquals("CHILD", fields["role"])
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `profile observers refresh after linking even when Firebase UID does not change`() = runTest {
        createRepository()
        every { auth.addAuthStateListener(any()) } answers {
            firstArg<FirebaseAuth.AuthStateListener>().onAuthStateChanged(auth)
        }
        every { auth.removeAuthStateListener(any()) } just Runs
        val observed = mutableListOf<AuthAccount?>()
        backgroundScope.launch { repository.currentAccount.toList(observed) }
        runCurrent()
        assertFalse(observed.last()!!.googleLinked)
        every { user.linkWithCredential(any()) } answers {
            linked = true
            task(mockk<AuthResult> { every { user } returns this@GoogleAccountLinkingTest.user })
        }
        repository.linkGoogle("test-token", "original-owner").getOrThrow()
        runCurrent()
        assertTrue(observed.last()!!.googleLinked)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun `seven subscribers and twenty correct attempts attach and resolve only once`() = runTest {
        fields["role"] = "CHILD"
        createRepository()
        repeat(7) { backgroundScope.launch { repository.currentAccount.collect { } } }
        runCurrent()
        val goals = mockk<com.mantrasiddhi.domain.repository.GoalRepository>(relaxed = true)
        coEvery { goals.incrementActiveGoalProgress("original-owner") } returns Result.success(false)
        val increment = com.mantrasiddhi.domain.usecase.IncrementActiveCloudGoalUseCase(repository, goals)
        repeat(20) { assertTrue(increment("original-owner").isSuccess) }
        verify(exactly = 1) { auth.addAuthStateListener(any()); ref.get() }
        verify(exactly = 0) { ref.get(Source.SERVER) }
        coVerify(exactly = 20) { goals.incrementActiveGoalProgress("original-owner") }
    }

    private suspend fun assertWrongRole(requested: AccountRole) {
        var current: FirebaseUser? = null
        every { auth.currentUser } answers { current }
        every { auth.signInWithCredential(any()) } answers {
            current = user
            task(mockk<AuthResult> { every { user } returns this@GoogleAccountLinkingTest.user })
        }
        every { auth.signOut() } answers { current = null }
        val failure = repository.signInWithGoogle("test-token", requested).exceptionOrNull() as GoogleAuthFailure
        assertEquals(GoogleAuthProblem.ROLE_MISMATCH, failure.problem)
        assertNull(auth.currentUser)
        verify(exactly = 0) { transaction.set(any<DocumentReference>(), any<Map<String, Any?>>(), any<SetOptions>()) }
    }
}
