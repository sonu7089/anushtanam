package com.mantrasiddhi.data.remote

import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SharedChildGoalsTest {
    private fun child(uid: String) = AuthAccount(uid, AccountRole.CHILD, uid)
    private fun goal(id: String, uid: String) = CloudGoal(id, "parent", uid, RewardType.OTHER, "", 10, 0, false)

    @Test fun `Main and Family share one query and metadata edits keep it alive`() = runTest {
        val accounts = MutableStateFlow<AuthAccount?>(child("A"))
        val source = MutableStateFlow(listOf(goal("old", "A")))
        var starts = 0
        var active = 0
        val shared = SharedChildGoals(accounts, backgroundScope, { accounts.value?.uid }) {
            flow { starts++; active++; try { emitAll(source) } finally { active-- } }
        }
        val main = mutableListOf<List<CloudGoal>>()
        val family = mutableListOf<List<CloudGoal>>()
        val first = backgroundScope.launch { shared.observe("A").toList(main) }
        runCurrent()
        val second = backgroundScope.launch { shared.observe("A").toList(family) }
        runCurrent()
        assertEquals(1, starts)
        assertEquals(main.last(), family.last())
        accounts.value = child("A").copy(displayName = "Renamed", photoUrl = "photo")
        runCurrent()
        second.cancel(); runCurrent()
        source.value += goal("new", "A")
        runCurrent()
        assertEquals(1, starts)
        assertEquals(2, main.last().size)
        assertEquals(1, active)
        first.cancel(); runCurrent()
        assertEquals(0, active)
    }

    @Test fun `no synthetic empty baseline and no other account replay`() = runTest {
        val accounts = MutableStateFlow<AuthAccount?>(child("A"))
        val sources = mapOf("A" to MutableSharedFlow<List<CloudGoal>>(), "B" to MutableSharedFlow())
        val active = mutableSetOf<String>()
        val shared = SharedChildGoals(accounts, backgroundScope, { accounts.value?.uid }) { uid ->
            flow { active += uid; try { emitAll(sources.getValue(uid)) } finally { active -= uid } }
        }
        val seenA = mutableListOf<List<CloudGoal>>()
        val seenB = mutableListOf<List<CloudGoal>>()
        backgroundScope.launch { shared.observe("A").toList(seenA) }
        runCurrent()
        assertTrue(seenA.isEmpty())
        sources.getValue("A").emit(listOf(goal("old", "A")))
        runCurrent()
        accounts.value = child("B")
        backgroundScope.launch { shared.observe("B").toList(seenB) }
        runCurrent()
        assertTrue(seenB.isEmpty())
        assertEquals(setOf("B"), active)
        sources.getValue("B").emit(listOf(goal("b", "B")))
        runCurrent()
        assertEquals("B", seenB.single().single().childUid)
        assertEquals(1, seenA.size)
        accounts.value = null
        runCurrent()
        assertTrue(active.isEmpty())
    }

    @Test fun `after all consumers leave a new subscription gets a new baseline not stale replay`() = runTest {
        val accounts = MutableStateFlow<AuthAccount?>(child("A"))
        val source = MutableSharedFlow<List<CloudGoal>>()
        val shared = SharedChildGoals(accounts, backgroundScope, { "A" }) { source }
        val first = backgroundScope.launch { shared.observe("A").collect() }
        runCurrent()
        source.emit(listOf(goal("old", "A")))
        first.cancel(); runCurrent()
        val seen = mutableListOf<List<CloudGoal>>()
        backgroundScope.launch { shared.observe("A").toList(seen) }
        runCurrent()
        assertTrue(seen.isEmpty())
        source.emit(listOf(goal("new-baseline", "A")))
        runCurrent()
        assertEquals("new-baseline", seen.single().single().id)
    }
}
