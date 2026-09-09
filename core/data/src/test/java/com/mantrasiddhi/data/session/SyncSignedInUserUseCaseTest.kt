package com.mantrasiddhi.data.session

import com.google.firebase.auth.FirebaseAuth
import com.mantrasiddhi.data.preferences.*
import com.mantrasiddhi.data.sandhya.SandhyaScheduler
import com.mantrasiddhi.domain.model.*
import com.mantrasiddhi.domain.repository.*
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SyncSignedInUserUseCaseTest {
    private val users = mockk<UserRepository>()
    private val session = mockk<SessionRepository>(relaxed = true)
    private val preferences = mockk<UserPreferencesDataStore>(relaxed = true)
    private val auth = mockk<FirebaseAuth>()
    private val records = mutableMapOf<Long, UserProfile>()
    private val account = AuthAccount("owner", AccountRole.INDIVIDUAL, "Google name",
        photoUrl = "https://example.com/photo", googleLinked = true, photoSource = "GOOGLE")
    private val sync = SyncSignedInUserUseCase(users, session, preferences,
        mockk<SandhyaPreferences>(relaxed = true), mockk<TargetRepository>(relaxed = true),
        mockk<TargetSyncRepository>(relaxed = true), mockk<PurashcharanaRepository>(relaxed = true),
        mockk<PurashcharanaSyncRepository>(relaxed = true), auth, mockk<SandhyaScheduler>(relaxed = true), mockk(relaxed = true))

    init {
        every { auth.currentUser } returns mockk { every { uid } returns "owner" }
        coEvery { preferences.getLocalProfileId(any()) } returns null
        coEvery { preferences.getOwnerOfLocalProfile(any()) } returns null
        coEvery { users.getByFirebaseUid(any()) } answers { records.values.firstOrNull { it.firebaseUid == firstArg<String>() } }
        coEvery { users.getById(any()) } answers { records[firstArg<Long>()] }
        coEvery { users.insert(any()) } answers {
            val profile = firstArg<UserProfile>()
            val id = profile.id.takeIf { it != 0L } ?: 100L
            records[id] = profile.copy(id = id)
            id
        }
        every { users.getAll() } answers { flowOf(records.values.toList()) }
        coEvery { users.hasPractice(any()) } returns true
    }

    @Test fun `linking reuses legacy id and preserves name family role parent and photo choice`() = runTest {
        val local = UserProfile(7, "Amma", ProfileType.PARENT, 3, 2,
            photoUrl = "https://example.com/chosen", photoSource = "LOCAL", useGooglePhoto = false)
        records[7] = local
        coEvery { preferences.getLocalProfileId("owner") } returns 7
        assertEquals(7L, sync(account))
        assertEquals(local.copy(firebaseUid = "owner"), records[7])
        coVerify { session.login(local.copy(firebaseUid = "owner")) }
    }

    @Test fun `retry after interrupted preference write reuses durable Room owner association`() = runTest {
        coEvery { preferences.setLocalProfileId(any(), any()) } throws IllegalStateException("disk unavailable")
        assertTrue(runCatching { sync(account) }.isFailure)
        assertEquals(1, records.size)
        coEvery { preferences.setLocalProfileId(any(), any()) } returns Unit
        assertEquals(100L, sync(account))
        assertEquals(1, records.size)
        assertEquals("owner", records[100]?.firebaseUid)
    }

    @Test fun `import retry keeps same id and does not create another history`() = runTest {
        records[7] = UserProfile(7, "Meera", ProfileType.INDIVIDUAL, avatarIndex = 1)
        assertEquals(7L, sync.importProfile(account, 7))
        assertEquals(7L, sync.importProfile(account, 7))
        assertEquals(1, records.size)
        assertEquals("Meera", records[7]?.name)
        assertEquals("owner", records[7]?.firebaseUid)
    }

    @Test fun `other owners local profiles are not offered or reassigned`() = runTest {
        records[7] = UserProfile(7, "Someone else", ProfileType.INDIVIDUAL, avatarIndex = 0)
        coEvery { preferences.getOwnerOfLocalProfile(7) } returns "another-owner"
        assertTrue(sync.profilesForImport(account).isEmpty())
        assertTrue(runCatching { sync.importProfile(account, 7) }.isFailure)
        assertNull(records[7]?.firebaseUid)
        coVerify(exactly = 0) { session.login(any()) }
    }

    @Test fun `signed out account cannot activate a profile or import history`() = runTest {
        every { auth.currentUser } returns null
        assertTrue(runCatching { sync(account) }.isFailure)
        assertTrue(records.isEmpty())
        coVerify(exactly = 0) { session.login(any()) }
    }
}
