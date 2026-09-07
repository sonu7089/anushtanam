/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.repository

import com.mantrasiddhi.domain.model.AuthAccount
import kotlinx.coroutines.flow.Flow

/**
 * Cloud authentication over Firebase Auth + a Firestore `users` mirror.
 *
 * Parents use a real email + password. Children have no email: they use a
 * display name + a 6-digit PIN, and are issued a unique 12-digit ID that both
 * serves as their login username and as the code a parent enters to link.
 */
interface AuthRepository {
    /** Reflects the Firebase Auth state; null when signed out. */
    val currentAccount: Flow<AuthAccount?>

    suspend fun signUpParent(email: String, password: String, displayName: String): Result<AuthAccount>
    suspend fun signInParent(email: String, password: String): Result<AuthAccount>

    /** Creates a child account, generating a unique 12-digit ID. PIN must be 6 digits. */
    suspend fun signUpChild(displayName: String, pin: String): Result<AuthAccount>
    suspend fun signInChild(code: String, pin: String): Result<AuthAccount>

    suspend fun signOut()
    suspend fun sendParentPasswordReset(email: String): Result<Unit>

    /** Fetches the current FCM token and persists it to the signed-in user's doc. */
    suspend fun refreshFcmToken()
}
