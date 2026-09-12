/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.model.AccountResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cloud authentication over Firebase Auth + a Firestore `users` mirror.
 *
 * Adults and children can use their own Google account. Adults can also use
 * email and password; children can use their unique 12-digit ID and 6-digit PIN.
 */
interface AuthRepository {
    /** Shared resolved profile; null while resolving or signed out, so old account data clears. */
    val currentAccount: Flow<AuthAccount?>

    /** Routing must distinguish unresolved identity from a confirmed sign-out. */
    val accountResolution: Flow<AccountResolution>
        get() = currentAccount.map { account ->
            account?.let { AccountResolution.Resolved(it) } ?: AccountResolution.SignedOut
        }

    suspend fun signUpParent(email: String, password: String, displayName: String): Result<AuthAccount>
    suspend fun signInParent(email: String, password: String): Result<AuthAccount>

    suspend fun signInWithGoogle(idToken: String, newAccountRole: com.mantrasiddhi.domain.model.AccountRole): Result<AuthAccount>
    /** Links to this authenticated UID. Never switches accounts on a collision. */
    suspend fun linkGoogle(idToken: String, expectedUid: String): Result<AuthAccount>
    suspend fun refreshAccount(): Result<AuthAccount>

    /** Creates a child account, generating a unique 12-digit ID. PIN must be 6 digits. */
    suspend fun signUpChild(displayName: String, pin: String): Result<AuthAccount>
    suspend fun signInChild(code: String, pin: String): Result<AuthAccount>

    /** Adds ID/PIN sign-in to a Google child account without changing its UID. */
    suspend fun completeChildGoogleSetup(pin: String, expectedUid: String): Result<AuthAccount>

    suspend fun signOut()
    suspend fun sendParentPasswordReset(email: String): Result<Unit>

    /** Fetches the current FCM token and persists it to the signed-in user's doc. */
    suspend fun refreshFcmToken()
}
