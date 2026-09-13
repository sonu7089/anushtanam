/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.session

import com.mantrasiddhi.domain.usecase.PurashcharanaReconciler
import com.mantrasiddhi.domain.usecase.PurashcharanaReconcileAction

import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.data.preferences.UserPreferencesDataStore
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.model.ProfileType
import com.mantrasiddhi.domain.model.UserProfile
import com.mantrasiddhi.domain.repository.PurashcharanaRepository
import com.mantrasiddhi.domain.repository.PurashcharanaSyncRepository
import com.mantrasiddhi.domain.repository.SessionRepository
import com.mantrasiddhi.domain.repository.TargetRepository
import com.mantrasiddhi.domain.repository.TargetSyncRepository
import com.mantrasiddhi.domain.repository.UserRepository
import javax.inject.Inject
import javax.inject.Singleton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.mantrasiddhi.data.sandhya.SandhyaScheduler

/**
 * Bridges a signed-in cloud [AuthAccount] to the local Room [UserProfile] world that
 * the rest of the app (recitation, practice, progress, targets, sandhya) is keyed by.
 *
 * It ensures a local profile exists for the account (creating one on first login on
 * this device, reusing it thereafter via a DataStore firebaseUid -> localId mapping),
 * sets it as the active [SessionRepository] user, and attributes Sandhya reminders to
 * it — mirroring what `LoginViewModel.selectProfile` does for the old local flow.
 *
 * @return the local Room profile id now active in the session.
 */
@Singleton
class SyncSignedInUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val sandhyaPreferences: SandhyaPreferences,
    private val targetRepository: TargetRepository,
    private val targetSyncRepository: TargetSyncRepository,
    private val purashcharanaRepository: PurashcharanaRepository,
    private val purashcharanaSyncRepository: PurashcharanaSyncRepository,
    private val auth: FirebaseAuth,
    private val sandhyaScheduler: SandhyaScheduler,
    private val japaSync: com.mantrasiddhi.data.sync.JapaCloudSync
) {
    private val mutex = Mutex()

    /** Only profiles with real saved practice are offered, never the seeded demo profiles. */
    suspend fun profilesForImport(account: AuthAccount): List<UserProfile> = mutex.withLock {
        if (account.role == AccountRole.CHILD) return@withLock emptyList()
        if (mappedProfile(account.uid) != null) return@withLock emptyList()
        userRepository.getAll().first().filter {
            it.firebaseUid == null && it.profileType != ProfileType.CHILD &&
                userPreferencesDataStore.getOwnerOfLocalProfile(it.id) == null && userRepository.hasPractice(it.id)
        }
    }

    private suspend fun mappedProfile(uid: String): UserProfile? =
        userRepository.getByFirebaseUid(uid)
            ?: userPreferencesDataStore.getLocalProfileId(uid)?.let { userRepository.getById(it) }

    suspend operator fun invoke(account: AuthAccount): Long = sync(account, null)

    /** Reuses the same row, so history and local family links are associated exactly once. */
    suspend fun importProfile(account: AuthAccount, profileId: Long): Long = sync(account, profileId)

    private suspend fun sync(account: AuthAccount, importId: Long?): Long = mutex.withLock {
        check(account.role != AccountRole.CHILD || importId == null)
        check(auth.currentUser?.uid == account.uid) { "Please sign in again to open your practice." }
        val profileType = when (account.role) {
            AccountRole.INDIVIDUAL -> ProfileType.INDIVIDUAL
            AccountRole.PARENT -> ProfileType.PARENT
            AccountRole.CHILD -> ProfileType.CHILD
        }
        val name = account.displayName.ifBlank {
            if (account.role == AccountRole.CHILD) "Child" else "Friend"
        }

        // Reuse the local profile previously mapped to this cloud account, if present.
        val mapped = mappedProfile(account.uid)
        check(mapped?.firebaseUid == null || mapped.firebaseUid == account.uid)
        check(importId == null || mapped == null || mapped.id == importId) {
            "This account already has a profile. Your saved practice has not been moved."
        }
        val existing = mapped ?: importId?.let { id ->
            val candidate = userRepository.getById(id) ?: error("Please choose your profile again.")
            check(candidate.firebaseUid == null && candidate.profileType != ProfileType.CHILD &&
                userPreferencesDataStore.getOwnerOfLocalProfile(id) == null)
            candidate
        }

        val localId: Long = if (existing != null) {
            // Existing names and family roles may have been chosen locally. Keep them.
            userRepository.insert(
                existing.copy(
                    name = existing.name.ifBlank { name },
                    firebaseUid = account.uid,
                    photoUrl = if (existing.photoSource in listOf(null, "GOOGLE")) account.photoUrl ?: existing.photoUrl else existing.photoUrl,
                    photoSource = existing.photoSource ?: account.photoSource
                )
            )
            existing.id
        } else {
            val newId = userRepository.insert(
                UserProfile(
                    id = 0,
                    name = name,
                    profileType = profileType,
                    parentId = null,
                    avatarIndex = Math.floorMod(account.uid.hashCode(), 4),
                    firebaseUid = account.uid,
                    photoUrl = account.photoUrl,
                    photoSource = account.photoSource
                )
            )
            newId
        }

        // Room owns the association. A crash before this compatibility write cannot duplicate it.
        userPreferencesDataStore.setLocalProfileId(account.uid, localId)

        val profile = userRepository.getById(localId)
            ?: UserProfile(id = localId, name = name, profileType = profileType, parentId = null, avatarIndex = 0)
        currentCoroutineContext().ensureActive()
        check(auth.currentUser?.uid == account.uid)
        sandhyaPreferences.setUserId(localId)
        check(auth.currentUser?.uid == account.uid)
        sessionRepository.login(profile)

        // MF-08 restores the versioned history. Recreating goals from old aggregate
        // mirrors here would duplicate their baselines and undo corrected totals.
        currentCoroutineContext().ensureActive()
        check(auth.currentUser?.uid == account.uid)
        japaSync.requestRecovery(account.uid)
        if (importId != null) {
            japaSync.importOwnedProfile(localId)
        }
        currentCoroutineContext().ensureActive()
        check(auth.currentUser?.uid == account.uid)
        sandhyaScheduler.requestStartupRearm(localId)
        localId
    }
}
