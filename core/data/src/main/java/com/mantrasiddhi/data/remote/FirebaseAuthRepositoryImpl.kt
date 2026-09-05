/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuthRepository] over Firebase Auth + a Firestore `users` mirror + FCM.
 *
 * Child accounts have no email: a synthetic credential (`c<12digits>@child.anushtanam.app`
 * + the 6-digit PIN) lets a child sign in on any device with (12-digit ID + PIN).
 * Code uniqueness is enforced by Firebase Auth itself — the email is derived from the
 * code, so a colliding code fails account creation and we simply retry a fresh code.
 */
@Singleton
class FirebaseAuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging
) : AuthRepository {

    private val random = SecureRandom()

    override val currentAccount: Flow<AuthAccount?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                trySend(null)
            } else {
                // Load the mirror doc; if it can't be read (e.g. offline), fall back to
                // inferring the account shape from the email so auto-login still works.
                firestore.collection(FirebaseContract.COLLECTION_USERS)
                    .document(user.uid)
                    .get()
                    .addOnSuccessListener { snap ->
                        trySend(snap.toAuthAccount(user.uid, user.email, user.displayName))
                    }
                    .addOnFailureListener {
                        trySend(inferAccount(user.uid, user.email, user.displayName))
                    }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signUpParent(
        email: String,
        password: String,
        displayName: String
    ): Result<AuthAccount> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
        val uid = result.user!!.uid
        result.user!!.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        ).awaitResult()

        val token = fetchTokenOrNull()
        val data = hashMapOf(
            FirebaseContract.FIELD_ROLE to AccountRole.PARENT.name,
            FirebaseContract.FIELD_DISPLAY_NAME to displayName,
            FirebaseContract.FIELD_FCM_TOKEN to token,
            FirebaseContract.FIELD_CREATED_AT to FieldValue.serverTimestamp()
        )
        firestore.collection(FirebaseContract.COLLECTION_USERS).document(uid)
            .set(data).awaitResult()

        AuthAccount(uid = uid, role = AccountRole.PARENT, displayName = displayName, email = email.trim())
    }

    override suspend fun signInParent(email: String, password: String): Result<AuthAccount> =
        runCatching {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).awaitResult()
            val uid = result.user!!.uid
            persistTokenBestEffort(uid)
            loadAccount(uid, result.user!!.email, result.user!!.displayName)
        }

    override suspend fun signUpChild(displayName: String, pin: String): Result<AuthAccount> =
        runCatching {
            require(pin.length == FirebaseContract.PIN_LENGTH && pin.all(Char::isDigit)) {
                "PIN must be exactly ${FirebaseContract.PIN_LENGTH} digits"
            }
            require(displayName.isNotBlank()) { "Name is required" }

            var uid: String? = null
            var code = ""
            var attempts = 0
            while (uid == null) {
                attempts++
                code = generateCode()
                try {
                    val result = auth
                        .createUserWithEmailAndPassword(FirebaseContract.childEmail(code), pin)
                        .awaitResult()
                    uid = result.user!!.uid
                } catch (_: FirebaseAuthUserCollisionException) {
                    // Code already in use (email collision) — retry with a fresh code.
                    if (attempts >= MAX_CODE_ATTEMPTS) {
                        throw IllegalStateException("Could not allocate a unique child ID; please retry")
                    }
                }
            }

            auth.currentUser?.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
            )?.awaitResult()

            // Claim the code doc so a parent can resolve code -> uid.
            firestore.collection(FirebaseContract.COLLECTION_CHILD_CODES).document(code)
                .set(hashMapOf(FirebaseContract.FIELD_UID to uid)).awaitResult()

            val token = fetchTokenOrNull()
            val data = hashMapOf(
                FirebaseContract.FIELD_ROLE to AccountRole.CHILD.name,
                FirebaseContract.FIELD_DISPLAY_NAME to displayName,
                FirebaseContract.FIELD_LINK_CODE to code,
                FirebaseContract.FIELD_FCM_TOKEN to token,
                FirebaseContract.FIELD_CREATED_AT to FieldValue.serverTimestamp()
            )
            firestore.collection(FirebaseContract.COLLECTION_USERS).document(uid)
                .set(data).awaitResult()

            AuthAccount(
                uid = uid,
                role = AccountRole.CHILD,
                displayName = displayName,
                linkCode = code,
                email = FirebaseContract.childEmail(code)
            )
        }

    override suspend fun signInChild(code: String, pin: String): Result<AuthAccount> =
        runCatching {
            val trimmed = code.trim()
            require(trimmed.length == FirebaseContract.CODE_LENGTH && trimmed.all(Char::isDigit)) {
                "ID must be exactly ${FirebaseContract.CODE_LENGTH} digits"
            }
            require(pin.length == FirebaseContract.PIN_LENGTH && pin.all(Char::isDigit)) {
                "PIN must be exactly ${FirebaseContract.PIN_LENGTH} digits"
            }
            val result = auth
                .signInWithEmailAndPassword(FirebaseContract.childEmail(trimmed), pin)
                .awaitResult()
            val uid = result.user!!.uid
            persistTokenBestEffort(uid)
            loadAccount(uid, result.user!!.email, result.user!!.displayName)
        }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun sendParentPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).awaitResult()
        Unit
    }

    override suspend fun refreshFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        persistTokenBestEffort(uid)
    }

    // --- helpers ---

    private suspend fun loadAccount(uid: String, email: String?, displayName: String?): AuthAccount {
        return runCatching {
            val snap = firestore.collection(FirebaseContract.COLLECTION_USERS)
                .document(uid).get().awaitResult()
            snap.toAuthAccount(uid, email, displayName)
        }.getOrElse { inferAccount(uid, email, displayName) }
    }

    private fun DocumentSnapshot.toAuthAccount(
        uid: String,
        email: String?,
        fallbackName: String?
    ): AuthAccount {
        if (!exists()) return inferAccount(uid, email, fallbackName)
        val roleName = getString(FirebaseContract.FIELD_ROLE)
        val role = AccountRole.entries.firstOrNull { it.name == roleName }
            ?: inferRole(email)
        val displayName = getString(FirebaseContract.FIELD_DISPLAY_NAME)
            ?: fallbackName ?: ""
        val linkCode = getString(FirebaseContract.FIELD_LINK_CODE)
            ?: FirebaseContract.codeFromChildEmail(email)
        return AuthAccount(
            uid = uid,
            role = role,
            displayName = displayName,
            linkCode = if (role == AccountRole.CHILD) linkCode else null,
            email = email
        )
    }

    private fun inferAccount(uid: String, email: String?, fallbackName: String?): AuthAccount {
        val role = inferRole(email)
        return AuthAccount(
            uid = uid,
            role = role,
            displayName = fallbackName ?: "",
            linkCode = if (role == AccountRole.CHILD) FirebaseContract.codeFromChildEmail(email) else null,
            email = email
        )
    }

    private fun inferRole(email: String?): AccountRole =
        if (FirebaseContract.isChildEmail(email)) AccountRole.CHILD else AccountRole.PARENT

    private suspend fun fetchTokenOrNull(): String? =
        runCatching { messaging.token.awaitResult() }.getOrNull()

    private suspend fun persistTokenBestEffort(uid: String) {
        runCatching {
            val token = messaging.token.awaitResult()
            firestore.collection(FirebaseContract.COLLECTION_USERS).document(uid)
                .update(FirebaseContract.FIELD_FCM_TOKEN, token).awaitResult()
        }
    }

    private fun generateCode(): String {
        val sb = StringBuilder(FirebaseContract.CODE_LENGTH)
        // First digit 1-9 so the code is always a full 12-digit string.
        sb.append(1 + random.nextInt(9))
        repeat(FirebaseContract.CODE_LENGTH - 1) { sb.append(random.nextInt(10)) }
        return sb.toString()
    }

    private companion object {
        const val MAX_CODE_ATTEMPTS = 8
    }
}
