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
class SyncSignedInUserUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val sandhyaPreferences: SandhyaPreferences,
    private val targetRepository: TargetRepository,
    private val targetSyncRepository: TargetSyncRepository,
    private val purashcharanaRepository: PurashcharanaRepository,
    private val purashcharanaSyncRepository: PurashcharanaSyncRepository
) {
    suspend operator fun invoke(account: AuthAccount): Long {
        val profileType = when (account.role) {
            AccountRole.PARENT -> ProfileType.PARENT
            AccountRole.CHILD -> ProfileType.CHILD
        }
        val name = account.displayName.ifBlank {
            if (account.role == AccountRole.CHILD) "Child" else "Parent"
        }

        // Reuse the local profile previously mapped to this cloud account, if present.
        val mappedId = userPreferencesDataStore.getLocalProfileId(account.uid)
        val existing = mappedId?.let { userRepository.getById(it) }

        val localId: Long = if (existing != null) {
            // Keep the local profile in sync with the cloud display name/role.
            userRepository.insert(
                existing.copy(name = name, profileType = profileType)
            )
            existing.id
        } else {
            val newId = userRepository.insert(
                UserProfile(
                    id = 0,
                    name = name,
                    profileType = profileType,
                    parentId = null,
                    avatarIndex = account.uid.hashCode().let { kotlin.math.abs(it) % 4 }
                )
            )
            userPreferencesDataStore.setLocalProfileId(account.uid, newId)
            newId
        }

        val profile = userRepository.getById(localId)
            ?: UserProfile(id = localId, name = name, profileType = profileType, parentId = null, avatarIndex = 0)
        sessionRepository.login(profile)
        sandhyaPreferences.setUserId(localId)

        // Restore the mirrored active self-target after a reinstall / new device:
        // only when Room has no active target (never clobber local state), and
        // best-effort — an offline login simply skips the restore.
        runCatching {
            if (targetRepository.getActiveForUser(localId) == null) {
                targetSyncRepository.fetchActive()?.let { mirrored ->
                    targetRepository.create(
                        mirrored.copy(userId = localId, setByUserId = localId)
                    )
                }
            }
        }

        // Restore the mirrored Purashcharana. A sankalpa is 24,00,000 japa — years of
        // work — so unlike the target restore this one also runs when a local sankalpa
        // already exists, to raise a count the cloud knows to be higher. The decision
        // itself is pure (see PurashcharanaReconciler) and can only ever raise; an
        // offline login simply skips it.
        runCatching {
            // Drop anything the mirror's throttle is still holding first. It may have been
            // captured before this account signed in, and writing it after the reconcile
            // would push a superseded count back over the record we are about to adopt.
            purashcharanaSyncRepository.discardPending()
            val local = purashcharanaRepository.getActive(localId)
            val remote = purashcharanaSyncRepository.fetchMirrored()
            when (val action = PurashcharanaReconciler.resolve(local, remote)) {
                is PurashcharanaReconcileAction.Restore ->
                    purashcharanaRepository.adoptRemote(localId, action.remote)
                is PurashcharanaReconcileAction.RaiseLocal ->
                    purashcharanaRepository.adoptRemote(localId, action.merged)
                PurashcharanaReconcileAction.None -> Unit
            }
        }

        return localId
    }
}
