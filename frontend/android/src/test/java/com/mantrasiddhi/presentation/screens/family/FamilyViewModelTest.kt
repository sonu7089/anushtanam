package com.mantrasiddhi.presentation.screens.family

import android.content.Context
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.LinkRepository
import com.mantrasiddhi.resources.R
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class FamilyViewModelTest {

    private val observers = mutableListOf<kotlinx.coroutines.Job>()
    private fun watch(vm: FamilyViewModel) {
        observers += kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch { vm.uiState.collect {} }
    }
    private val dispatcher = UnconfinedTestDispatcher()
    private val account = MutableStateFlow<AuthAccount?>(AuthAccount("parent", AccountRole.PARENT, "Amma"))
    private val links = mockk<LinkRepository>()
    private val scanner = mockk<FamilyQrScanner>()
    private val context = mockk<Context>()
    private lateinit var model: FamilyViewModel

    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { links.approvedParentsForChild() } returns flowOf(emptyList())
        model = FamilyViewModel(
            observeAuthAccountUseCase = mockk { every { this@mockk.invoke() } returns account },
            approveLinkUseCase = mockk(), rejectLinkUseCase = mockk(), createChildGoalUseCase = mockk(),
            observeLinkedChildrenUseCase = mockk { every { this@mockk.invoke() } returns flowOf(emptyList()) },
            observePendingLinkRequestsUseCase = mockk { every { this@mockk.invoke() } returns flowOf(emptyList()) },
            observeChildGoalsUseCase = mockk { every { this@mockk.invoke(any()) } returns flowOf(emptyList()) },
            observeGoalsForChildUseCase = mockk(), links = links, scanner = scanner
        )
        watch(model)
    }
    @AfterEach fun teardown() { ViewModelStore().apply { put("family", model); clear() }; observers.forEach { it.cancel() }; Dispatchers.resetMain() }

    @Test fun `cancelled scan makes no link and leaves form available`() = runTest(dispatcher) {
        coEvery { scanner.scan(context) } returns null
        model.scanQr(context)
        assertFalse(model.uiState.value.isBusy)
        assertNull(model.uiState.value.errorRes)
        coVerify(exactly = 0) { links.linkWithQr(any()) }
    }

    @Test fun `wrong PIN has a clear message and never creates a pending request`() = runTest(dispatcher) {
        coEvery { links.linkWithPin("123456789012", "000000") } returns Result.failure(FamilyLinkFailure(FamilyLinkProblem.INVALID_CREDENTIALS))
        model.requestLink("123456789012", "000000")
        assertEquals(R.string.family_pin_invalid, model.uiState.value.errorRes)
        assertFalse(model.uiState.value.isBusy)
        coVerify(exactly = 0) { links.requestLink(any()) }
    }

    @Test fun `account switch while scanner is open discards its result`() = runTest(dispatcher) {
        val result = CompletableDeferred<String?>()
        coEvery { scanner.scan(context) } coAnswers { result.await() }
        model.scanQr(context)
        assertTrue(model.uiState.value.isBusy)
        account.value = AuthAccount("another-parent", AccountRole.PARENT, "Appa")
        result.complete(FamilyQrInvite("a".repeat(64), 1000).payload)
        coVerify(exactly = 0) { links.linkWithQr(any()) }
        assertFalse(model.uiState.value.isBusy)
    }

    @Test fun `repeated taps verify the PIN only once`() = runTest(dispatcher) {
        val result = CompletableDeferred<Result<Unit>>()
        coEvery { links.linkWithPin(any(), any()) } coAnswers { result.await() }
        model.requestLink("123456789012", "123456")
        model.requestLink("123456789012", "123456")
        result.complete(Result.success(Unit))
        coVerify(exactly = 1) { links.linkWithPin(any(), any()) }
        assertFalse(model.uiState.value.isBusy)
    }

    @Test fun `child sees when their QR has been used and loses it on sign-out`() = runTest(dispatcher) {
        account.value = AuthAccount("child", AccountRole.CHILD, "Aarav")
        val invite = FamilyQrInvite("a".repeat(64), 99999)
        val updates = MutableStateFlow<FamilyQrInvite?>(invite)
        coEvery { links.createQrInvite() } returns Result.success(invite)
        every { links.observeQrInvite(invite.token) } returns updates
        model.createQr()
        assertEquals(invite, model.uiState.value.qrInvite)
        updates.value = invite.copy(used = true)
        assertTrue(model.uiState.value.qrInvite!!.used)
        account.value = null
        assertNull(model.uiState.value.qrInvite)
    }
    @Test fun `same child metadata retains sources and role loss cancels them`() = runTest(dispatcher) {
        var active = 0
        var starts = 0
        every { links.approvedParentsForChild() } returns flow {
            starts++; active++
            try { emit(emptyList()); awaitCancellation() } finally { active-- }
        }
        account.value = AuthAccount("child", AccountRole.CHILD, "Aarav")
        assertEquals(1, active)
        account.value = account.value!!.copy(displayName = "Updated", photoUrl = "photo")
        assertEquals(1, starts)
        assertEquals("Updated", model.uiState.value.account?.displayName)
        account.value = account.value!!.copy(role = AccountRole.PARENT)
        assertEquals(0, active)
        assertTrue(model.uiState.value.linkedParents.isEmpty())
        account.value = AuthAccount("another-child", AccountRole.CHILD, "B")
        assertEquals(1, active)
        account.value = null
        assertEquals(0, active)
        assertNull(model.uiState.value.account)
    }

    @Test fun `failing child section does not stop account replacement and sign out`() = runTest(dispatcher) {
        every { links.approvedParentsForChild() } throws IllegalStateException("unavailable")
        account.value = AuthAccount("child", AccountRole.CHILD, "A")
        assertTrue(model.uiState.value.linkedParents.isEmpty())
        account.value = AuthAccount("B", AccountRole.CHILD, "B")
        assertEquals("B", model.uiState.value.account?.uid)
        account.value = null
        assertNull(model.uiState.value.account)
    }

}
