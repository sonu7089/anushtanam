package com.mantrasiddhi.presentation.screens.settings

import android.content.Context
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import com.mantrasiddhi.presentation.screens.auth.GoogleCredentialPicker
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDeletionViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val cloud = mockk<FirebaseFirestore>()
    private val picker = mockk<GoogleCredentialPicker>()
    private val context = mockk<Context>()
    private val ref = mockk<DocumentReference>()
    private var existing = false
    private lateinit var model: AccountDeletionViewModel

    private fun <T> task(value: T?, failure: Exception? = null): Task<T> {
        val result = mockk<Task<T>>()
        every { result.exception } returns failure
        every { result.isCanceled } returns false
        @Suppress("UNCHECKED_CAST")
        every { result.result } answers { value as T }
        every { result.addOnCompleteListener(any<OnCompleteListener<T>>()) } answers {
            firstArg<OnCompleteListener<T>>().onComplete(result); result
        }
        return result
    }

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { auth.currentUser } returns user
        every { user.uid } returns "owner"
        every { user.providerData } returns listOf(mockk<UserInfo> { every { providerId } returns "google.com" })
        every { cloud.collection("accountDeletionRequests") } returns mockk {
            every { document("owner") } returns ref
        }
        every { ref.get(Source.SERVER) } answers { task(mockk<DocumentSnapshot> { every { exists() } answers { existing } }) }
        every { ref.set(any()) } returns task<Void>(null)
        every { user.reauthenticate(any()) } returns task<Void>(null)
        every { user.getIdToken(true) } returns task(mockk<GetTokenResult>())
        coEvery { picker.pick(context) } returns "test-token"
        model = AccountDeletionViewModel(auth, cloud, picker)
    }
    @AfterEach fun teardown() { Dispatchers.resetMain() }

    @Test fun `canceling Google verification never submits a deletion request`() = runTest(dispatcher) {
        coEvery { picker.pick(context) } returns null
        model.request(context, "owner", "")
        assertEquals(AccountDeletionState(), model.state.value)
        verify(exactly = 0) { user.reauthenticate(any()); ref.set(any()) }
    }
    @Test fun `wrong account and failed reauthentication never enqueue deletion`() = runTest(dispatcher) {
        model.request(context, "other", "")
        assertTrue(model.state.value.failed)
        every { user.reauthenticate(any()) } returns task(null, IllegalStateException("Wrong Google account"))
        model.request(context, "owner", "")
        assertTrue(model.state.value.failed)
        verify(exactly = 0) { ref.set(any()) }
    }
    @Test fun `fresh verification and token refresh precede the own-account request`() = runTest(dispatcher) {
        model.request(context, "owner", "")
        assertTrue(model.state.value.accepted)
        verifyOrder { user.reauthenticate(any()); user.getIdToken(true); ref.set(any()) }
        verify { ref.set(match<Map<String, Any>> { it.keys == setOf("state", "requestedAt") && it["state"] == "pending" }) }
    }
    @Test fun `retry of an acknowledged request does not overwrite the job or authenticate again`() = runTest(dispatcher) {
        existing = true
        model.request(context, "owner", "")
        assertTrue(model.state.value.accepted)
        verify(exactly = 0) { user.reauthenticate(any()); ref.set(any()) }
        coVerify(exactly = 0) { picker.pick(any()) }
    }
}
