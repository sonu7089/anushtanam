/*
 * Anushtanam - Vedic mantra recitation app
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.ClearCredentialStateRequest
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import com.mantrasiddhi.domain.model.GoogleAuthFailure
import com.mantrasiddhi.domain.model.GoogleAuthProblem
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.domain.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import com.mantrasiddhi.di.AccountScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.core.app.NotificationManagerCompat
import com.mantrasiddhi.data.sandhya.SandhyaScheduler
import com.mantrasiddhi.domain.repository.UserRepository
import com.mantrasiddhi.domain.model.ProfileType

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.mantrasiddhi.domain.model.AccountRole
import com.mantrasiddhi.domain.model.AuthAccount
import com.mantrasiddhi.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.mantrasiddhi.domain.model.AccountResolution
import com.mantrasiddhi.domain.diagnostics.WorkProbe
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuthRepository] over Firebase Auth + a Firestore `users` mirror + FCM.
 * Children sign in exclusively with Google. Family QR tokens are separate from credentials.
 */
@Singleton
class FirebaseAuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val sandhyaPreferences: SandhyaPreferences,
    private val sandhyaScheduler: SandhyaScheduler,
    private val profiles: UserRepository,
    @AccountScope scope: CoroutineScope
) : AuthRepository {

    private val interactiveMutex = Mutex()
    private val accounts = AccountStateProducer(
        scope = scope,
        currentUid = { auth.currentUser?.uid },
        load = { uid ->
            val user = requireUser(uid)
            loadAccount(uid, user.email, user.displayName).withIdentity(user)
        },
        reload = { uid -> refreshFromServer(requireUser(uid)) }
    )
    override val accountResolution = accounts.state
    override val currentAccount: Flow<AuthAccount?> = accountResolution.map {
        (it as? AccountResolution.Resolved)?.account
    }.distinctUntilChanged()

    // This singleton owns exactly one listener for the process, irrespective of UI collectors.
    private val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        accounts.identityChanged(firebaseAuth.currentUser?.uid)
    }
    init {
        WorkProbe.increment("auth.listener.attach")
        auth.addAuthStateListener(listener)
        accounts.identityChanged(auth.currentUser?.uid)
        scope.coroutineContext[Job]?.invokeOnCompletion {
            auth.removeAuthStateListener(listener)
            WorkProbe.increment("auth.listener.detach")
        }
    }

    private fun requireUser(uid: String): FirebaseUser = auth.currentUser?.takeIf { it.uid == uid }
        ?: throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)

    private suspend fun interactiveAccount(block: suspend () -> AuthAccount): AuthAccount =
        interactiveMutex.withLock {
            accounts.beginInteractive()
            var result: AuthAccount? = null
            try {
                block().also {
                    requireUser(it.uid)
                    result = it
                }
            } finally {
                accounts.finishInteractive(result)
            }
        }

    private suspend fun accountResult(block: suspend () -> AuthAccount): Result<AuthAccount> = try {
        Result.success(interactiveAccount(block))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Result.failure(failure)
    }

    private suspend fun googleInteractiveResult(block: suspend () -> AuthAccount): Result<AuthAccount> =
        googleResult { interactiveAccount(block) }

    override suspend fun signUpParent(
        email: String,
        password: String,
        displayName: String
    ): Result<AuthAccount> = accountResult {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
        val user = result.signedInUser()
        val uid = user.uid
        user.updateProfile(
            UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
        ).awaitResult()

        val data = hashMapOf(
            FirebaseContract.FIELD_ROLE to AccountRole.PARENT.name,
            FirebaseContract.FIELD_DISPLAY_NAME to displayName.take(100),
            FirebaseContract.FIELD_CREATED_AT to FieldValue.serverTimestamp()
        )
        firestore.collection(FirebaseContract.COLLECTION_USERS).document(uid)
            .set(data).awaitResult()
        persistTokenBestEffort(uid)

        AuthAccount(uid = uid, role = AccountRole.PARENT, displayName = displayName, email = email.trim())
    }

    override suspend fun signInParent(email: String, password: String): Result<AuthAccount> =
        accountResult {
            val user = auth.signInWithEmailAndPassword(email.trim(), password).awaitResult().signedInUser()
            persistTokenBestEffort(user.uid)
            accounts.resolve(user.uid)
        }

    override suspend fun signOut() {
        // Clear the local owner first. Outbox rows keep the UID captured when saved.
        sessionRepository.logout()
        try { sandhyaPreferences.setUserId(0L) } finally {
            auth.signOut()
            accounts.identityChanged(auth.currentUser?.uid)
        }
        sandhyaScheduler.cancelAll()
        NotificationManagerCompat.from(context).cancelAll()
        withTimeoutOrNull(3_000) {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Firebase is already signed out. An unavailable provider must not trap the user.
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String, newAccountRole: AccountRole): Result<AuthAccount> =
        googleInteractiveResult {
            if (auth.currentUser != null) throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
            val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .awaitResult().signedInUser()
            try {
                // Resolve the saved role online before opening a profile. A parent's Google
                // account must never become a child account by choosing the child sign-in page.
                WorkProbe.increment("auth.profile.get")
                val saved = firestore.collection(FirebaseContract.COLLECTION_USERS)
                    .document(user.uid).get(Source.SERVER).awaitResult()
                val role = saved.getString(FirebaseContract.FIELD_ROLE)
                    ?.let { value -> AccountRole.entries.firstOrNull { it.name == value } }
                if (role != null && (role == AccountRole.CHILD) != (newAccountRole == AccountRole.CHILD)) {
                    throw GoogleAuthFailure(GoogleAuthProblem.ROLE_MISMATCH)
                }
                val account = googleAccount(user, role ?: newAccountRole)
                if (!saved.exists() && account.profileNeedsSync) {
                    throw GoogleAuthFailure(GoogleAuthProblem.OFFLINE)
                }
                account
            } catch (failure: Exception) {
                if (auth.currentUser?.uid == user.uid) auth.signOut()
                throw failure
            }
        }

    override suspend fun linkGoogle(idToken: String, expectedUid: String): Result<AuthAccount> = googleInteractiveResult {
        val user = auth.currentUser
            ?.takeIf { it.uid == expectedUid }
            ?: throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
        val before = accounts.resolve(user.uid)
        val linkedUser = if (user.providerData.none { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
            user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitResult().signedInUser()
        } else {
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).awaitResult()
            user
        }
        linkedUser.getIdToken(true).awaitResult()
        check(linkedUser.uid == expectedUid)
        // Linking preserves the existing UID, cloud role, name and family documents.
        googleAccount(linkedUser, before.role)
    }

    override suspend fun refreshAccount(): Result<AuthAccount> = googleResult {
        val user = auth.currentUser ?: throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
        accounts.refresh(user.uid)
    }

    private suspend fun refreshFromServer(user: FirebaseUser): AuthAccount {
        WorkProbe.increment("auth.refresh")
        // Never invent a role for a Google sign-in interrupted before profile creation.
        WorkProbe.increment("auth.profile.get")
        val saved = firestore.collection(FirebaseContract.COLLECTION_USERS).document(user.uid)
            .get(Source.SERVER).awaitResult()
        if (!saved.exists()) throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
        val existing = saved.toAuthAccount(user.uid, user.email, user.displayName).withIdentity(user)
        return if (existing.googleLinked) googleAccount(user, existing.role) else existing
    }

    private suspend fun googleAccount(user: FirebaseUser, fallbackRole: AccountRole): AuthAccount {
        val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        val photo = google?.photoUrl?.toString()?.takeIf { it.startsWith("https://") }
        // A failed profile write is recoverable on the next sign-in. Never wait for a photo download.
        val mirrored = withTimeoutOrNull(8_000) {
            try {
                val ref = firestore.collection(FirebaseContract.COLLECTION_USERS).document(user.uid)
                firestore.runTransaction { transaction ->
                    val existing = transaction.get(ref)
                    val fields = mutableMapOf<String, Any?>(FirebaseContract.FIELD_FCM_TOKEN to FieldValue.delete())
                    if (!existing.exists()) {
                        fields[FirebaseContract.FIELD_ROLE] = fallbackRole.name
                        fields[FirebaseContract.FIELD_DISPLAY_NAME] = (user.displayName ?: google?.displayName.orEmpty()).take(100)
                        fields[FirebaseContract.FIELD_CREATED_AT] = FieldValue.serverTimestamp()
                    }
                    if (existing.getString("photoSource") in listOf(null, "GOOGLE")) {
                        fields["photoUrl"] = photo
                        fields["photoSource"] = "GOOGLE"
                    }
                    transaction.set(ref, fields, SetOptions.merge())
                }.awaitResult()
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
        } == true
        return loadAccount(user.uid, user.email, user.displayName).let {
            // The mirror is authoritative for returning accounts.
            it.copy(photoUrl = it.photoUrl ?: photo,
                role = if (it.profileNeedsSync) fallbackRole else it.role,
                profileNeedsSync = !mirrored || it.profileNeedsSync).withIdentity(user)
        }
    }

    private suspend fun googleResult(block: suspend () -> AuthAccount): Result<AuthAccount> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        val problem = when (failure) {
            is GoogleAuthFailure -> failure.problem
            is FirebaseNetworkException -> GoogleAuthProblem.OFFLINE
            is FirebaseAuthUserCollisionException -> GoogleAuthProblem.COLLISION
            is FirebaseAuthException -> when (failure.errorCode) {
                "ERROR_INVALID_CREDENTIAL", "ERROR_USER_TOKEN_EXPIRED", "ERROR_REQUIRES_RECENT_LOGIN" -> GoogleAuthProblem.EXPIRED
                "ERROR_OPERATION_NOT_ALLOWED", "ERROR_APP_NOT_AUTHORIZED" -> GoogleAuthProblem.UNAVAILABLE
                "ERROR_USER_MISMATCH" -> GoogleAuthProblem.WRONG_ACCOUNT
                else -> GoogleAuthProblem.UNKNOWN
            }
            else -> GoogleAuthProblem.UNKNOWN
        }
        Result.failure(GoogleAuthFailure(problem, failure))
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

    /**
     * The account an auth call just established.
     *
     * Firebase types `AuthResult.user` as nullable even though a successful sign-in or
     * sign-up always carries one. Failing with a sentence rather than a bare
     * `NullPointerException` matters because every caller wraps this in `runCatching` and
     * the message is what the sadhak is shown.
     */
    private fun AuthResult.signedInUser(): FirebaseUser =
        user ?: error("Firebase reported a successful sign-in with no account attached")

    private suspend fun loadAccount(uid: String, email: String?, displayName: String?): AuthAccount {
        WorkProbe.increment("auth.loadAccount")
        return (withTimeoutOrNull(4_000) { runCatching {
            WorkProbe.increment("auth.profile.get")
            val snap = firestore.collection(FirebaseContract.COLLECTION_USERS)
                .document(uid).get().awaitResult()
            snap.toAuthAccount(uid, email, displayName)
        }.getOrNull() } ?: inferAccount(uid, email, displayName).copy(profileNeedsSync = true)).let { account ->
            val localRole = if (account.profileNeedsSync) runCatching {
                profiles.getByFirebaseUid(uid)?.profileType
            }.getOrNull() else null
            val resolved = account.copy(role = when (localRole) {
                ProfileType.INDIVIDUAL -> AccountRole.INDIVIDUAL
                ProfileType.PARENT -> AccountRole.PARENT
                ProfileType.CHILD -> AccountRole.CHILD
                null -> account.role
            })
            auth.currentUser?.takeIf { it.uid == uid }?.let { resolved.withIdentity(it) } ?: resolved
        }
    }

    private fun DocumentSnapshot.toAuthAccount(
        uid: String,
        email: String?,
        fallbackName: String?
    ): AuthAccount {
        if (!exists()) return inferAccount(uid, email, fallbackName).copy(profileNeedsSync = true)
        val roleName = getString(FirebaseContract.FIELD_ROLE)
        val role = AccountRole.entries.firstOrNull { it.name == roleName }
            ?: inferRole()
        val displayName = getString(FirebaseContract.FIELD_DISPLAY_NAME)
            ?: fallbackName ?: ""
        val linkCode = getString(FirebaseContract.FIELD_LINK_CODE)
        return AuthAccount(
            uid = uid,
            role = role,
            displayName = displayName,
            linkCode = if (role == AccountRole.CHILD) linkCode else null,
            email = email,
            photoUrl = getString("photoUrl"),
            photoSource = getString("photoSource")
        )
    }

    private fun inferAccount(uid: String, email: String?, fallbackName: String?): AuthAccount {
        val role = inferRole()
        return AuthAccount(
            uid = uid,
            role = role,
            displayName = fallbackName ?: "",
            linkCode = null,
            email = email
        )
    }

    private fun inferRole(): AccountRole =
        if (auth.currentUser?.providerData?.any { it.providerId == "password" } == true) AccountRole.PARENT
        else AccountRole.INDIVIDUAL

    private fun AuthAccount.withIdentity(user: FirebaseUser): AuthAccount {
        val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        return copy(googleLinked = google != null,
            photoSource = photoSource ?: if (google != null) "GOOGLE" else null,
            photoUrl = photoUrl ?: google?.photoUrl?.toString()?.takeIf { it.startsWith("https://") })
    }

    private suspend fun persistTokenBestEffort(uid: String) {
        runCatching {
            val token = messaging.token.awaitResult()
            firestore.collection(FirebaseContract.COLLECTION_USERS).document(uid)
                .collection("private").document("messaging")
                .set(mapOf(FirebaseContract.FIELD_FCM_TOKEN to token)).awaitResult()
            firestore.collection(FirebaseContract.COLLECTION_USERS).document(uid)
                .update(FirebaseContract.FIELD_FCM_TOKEN, FieldValue.delete()).awaitResult()
        }
    }

}
