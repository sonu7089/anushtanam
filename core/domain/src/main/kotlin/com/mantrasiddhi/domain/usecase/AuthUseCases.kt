/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.domain.usecase

import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.repository.AuthRepository
import com.mantrasiddhi.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Observes the signed-in cloud account (null when signed out). */
class ObserveAuthAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    operator fun invoke(): Flow<AuthAccount?> = authRepository.currentAccount
}

class SignUpParentUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String, displayName: String): Result<AuthAccount> =
        authRepository.signUpParent(email, password, displayName)
}

class SignInParentUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): Result<AuthAccount> =
        authRepository.signInParent(email, password)
}

class SendParentPasswordResetUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> =
        authRepository.sendParentPasswordReset(email)
}

/** Signs out of Firebase and clears the local session. */
class SignOutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke() {
        authRepository.signOut()
        sessionRepository.logout()
    }
}
