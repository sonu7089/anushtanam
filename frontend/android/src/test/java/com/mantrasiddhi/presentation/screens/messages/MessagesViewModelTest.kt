package com.mantrasiddhi.presentation.screens.messages

import androidx.lifecycle.ViewModelStore
import com.mantrasiddhi.data.messages.*
import com.mantrasiddhi.domain.model.TeachingMessage
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = mockk<TeachingRepository>()
    private lateinit var vm: MessagesViewModel
    private val cursor = mockk<DocumentSnapshot>()
    private fun message(id: String) = TeachingMessage(id, 1, id, "summary", "body", "Teacher", 1, Long.MAX_VALUE, "river", false)
    @BeforeEach fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.feedReadyAt } returns MutableStateFlow(0)
        every { repository.refreshDelayMillis() } returns 0
        vm = MessagesViewModel(repository, mockk())
    }
    @AfterEach fun cleanup() { ViewModelStore().apply { put("messages", vm); clear() }; Dispatchers.resetMain() }

    @Test fun `cached list is readable while refresh waits and remains after failure`() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        every { repository.observePage() } returns flow {
            emit(TeachingPage(listOf(message("cached")), true, cursor, true, refreshing = true))
            finish.await(); error("offline")
        }
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        vm.refresh()
        assertEquals("cached", vm.state.value.items.single().id)
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.refreshing)
        vm.refresh()
        verify(exactly = 1) { repository.observePage() }
        finish.complete(Unit)
        assertTrue(vm.state.value.refreshFailed)
        assertEquals("cached", vm.state.value.items.single().id)
        every { repository.observePage() } returns flowOf(TeachingPage(emptyList(), false, null, false))
        vm.refresh()
        assertTrue(vm.state.value.items.isEmpty())
    }
    @Test fun `append failure retains cursor and retry merges stable identities`() = runTest(dispatcher) {
        every { repository.observePage() } returns flowOf(TeachingPage(listOf(message("one")), false, cursor, true))
        coEvery { repository.page(cursor) } throws IllegalStateException("offline")
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        vm.refresh(); vm.refresh(more = true)
        assertTrue(vm.state.value.appendFailed)
        assertEquals(1, vm.state.value.items.size)
        coEvery { repository.page(cursor) } returns TeachingPage(listOf(message("one"), message("two")), false, null, false)
        vm.refresh(more = true)
        assertEquals(listOf("one", "two"), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        coVerify(exactly = 2) { repository.page(cursor) }
    }
    @Test fun `replaced detail and hidden destination cancel their producers`() = runTest(dispatcher) {
        var active = 0
        every { repository.observeDetail(any()) } answers {
            val id = firstArg<String>()
            flow {
                active++
                try { emit(TeachingDetail(message(id), true, true)); awaitCancellation() } finally { active-- }
            }
        }
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        vm.refresh("A"); vm.refresh("B")
        assertEquals(1, active)
        assertEquals("B", vm.state.value.detail!!.id)
        vm.stop()
        assertEquals(0, active)
    }

    @Test fun `failed first page refresh preserves already appended rows and successful refresh replaces them`() = runTest(dispatcher) {
        every { repository.observePage() } returns flowOf(TeachingPage(listOf(message("one")), false, cursor, true))
        coEvery { repository.page(cursor) } returns TeachingPage(listOf(message("two")), false, cursor, true)
        backgroundScope.launch(dispatcher) { vm.state.collect {} }
        vm.refresh(); vm.refresh(more = true)
        every { repository.observePage() } returns flowOf(TeachingPage(listOf(message("one")), true, cursor, true))
        vm.refresh()
        assertEquals(listOf("one", "two"), vm.state.value.items.map { it.id })
        every { repository.observePage() } returns flowOf(TeachingPage(emptyList(), false, null, false))
        vm.refresh()
        assertTrue(vm.state.value.items.isEmpty())
    }
}
