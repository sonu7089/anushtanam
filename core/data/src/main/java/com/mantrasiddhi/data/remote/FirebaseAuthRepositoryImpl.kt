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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.SetOptions
import com.mantrasiddhi.domain.model.GoogleAuthFailure
import com.mantrasiddhi.domain.model.GoogleAuthProblem
import com.mantrasiddhi.data.preferences.SandhyaPreferences
import com.mantrasiddhi.domain.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private val messaging: FirebaseMessaging,
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val sandhyaPreferences: SandhyaPreferences,
    private val sandhyaScheduler: SandhyaScheduler,
    private val profiles: UserRepository
) : AuthRepository {

    private val random = SecureRandom()
    private val accountChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val currentAccount: Flow<AuthAccount?> = com.mantrasiddhi.domain.diagnostics.WorkProbe.observe("auth.account", callbackFlow {
        var loading: Job? = null
        fun publish(user: FirebaseUser?) {
            loading?.cancel()
            if (user == null) {
                trySend(null)
            } else {
                loading = launch {
                    val account = loadAccount(user.uid, user.email, user.displayName)
                    if (auth.currentUser?.uid == user.uid) {
                        trySend(account.withIdentity(user))
                    }
                }
            }
        }
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth -> publish(firebaseAuth.currentUser) }
        val changes = launch { accountChanges.collect { publish(auth.currentUser) } }
        auth.addAuthStateListener(listener)
        awaitClose { loading?.cancel(); changes.cancel(); auth.removeAuthStateListener(listener) }
    })

    override suspend fun signUpParent(
        email: String,
        password: String,
        displayName: String
    ): Result<AuthAccount> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).awaitResult()
        val user = result.signedInUser()
        val uid = user.uid
        user.updateProfile(
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

        accountChanges.tryEmit(Unit)
        AuthAccount(uid = uid, role = AccountRole.PARENT, displayName = displayName, email = email.trim())
    }

    override suspend fun signInParent(email: String, password: String): Result<AuthAccount> =
        runCatching {
            val user = auth.signInWithEmailAndPassword(email.trim(), password).awaitResult().signedInUser()
            persistTokenBestEffort(user.uid)
            loadAccount(user.uid, user.email, user.displayName)
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
                    uid = result.signedInUser().uid
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

            accountChanges.tryEmit(Unit)
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
            val user = result.signedInUser()
            persistTokenBestEffort(user.uid)
            loadAccount(user.uid, user.email, user.displayName)
        }

    override suspend fun signOut() {
        // Clear the local owner first. Outbox rows keep the UID captured when saved.
        sessionRepository.logout()
        try { sandhyaPreferences.setUserId(0L) } finally { auth.signOut() }
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
        googleResult {
            if (auth.currentUser != null) throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
            val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .awaitResult().signedInUser()
            try {
                // Resolve the saved role online before opening a profile. A parent's Google
                // account must never become a child account by choosing the child sign-in page.
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

    override suspend fun linkGoogle(idToken: String, expectedUid: String): Result<AuthAccount> = googleResult {
        val user = auth.currentUser
            ?.takeIf { it.uid == expectedUid }
            ?: throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
        val before = loadAccount(user.uid, user.email, user.displayName)
        val linkedUser = if (user.providerData.none { it.providerId == GoogleAuthProvider.PROVIDER_ID }) {
            user.linkWithCredential(GoogleAuthProvider.getCredential(idToken, null)).awaitResult().signedInUser()
        } else {
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).awaitResult()
            user
        }
        check(linkedUser.uid == expectedUid)
        // Linking preserves the existing UID, cloud role, name and family documents.
        googleAccount(linkedUser, before.role)
    }

    override suspend fun refreshAccount(): Result<AuthAccount> = googleResult {
        val user = auth.currentUser ?: throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
        // Never invent a role for a Google sign-in interrupted before profile creation.
        val saved = firestore.collection(FirebaseContract.COLLECTION_USERS).document(user.uid)
            .get(Source.SERVER).awaitResult()
        if (!saved.exists()) throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
        val existing = saved.toAuthAccount(user.uid, user.email, user.displayName).withIdentity(user)
        if (existing.googleLinked) googleAccount(user, existing.role) else existing
    }

    override suspend fun completeChildGoogleSetup(pin: String, expectedUid: String): Result<AuthAccount> = googleResult {
        require(pin.matches(Regex("[0-9]{6}")))
        val user = auth.currentUser?.takeIf { it.uid == expectedUid }
            ?: throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
        val snapshot = firestore.collection(FirebaseContract.COLLECTION_USERS)
            .document(user.uid).get(Source.SERVER).awaitResult()
        if (snapshot.getString(FirebaseContract.FIELD_ROLE) != AccountRole.CHILD.name) {
            throw GoogleAuthFailure(GoogleAuthProblem.WRONG_ACCOUNT)
        }
        val account = googleAccount(user, AccountRole.CHILD)
        val code = account.linkCode ?: throw GoogleAuthFailure(GoogleAuthProblem.OFFLINE)
        if (user.providerData.none { it.providerId == EmailAuthProvider.PROVIDER_ID }) {
            user.linkWithCredential(EmailAuthProvider.getCredential(FirebaseContract.childEmail(code), pin))
                .awaitResult()
        }
        // A retry after an interrupted response must reuse the same ID and Firebase UID.
        user.reload().awaitResult()
        accountChanges.tryEmit(Unit)
        return@googleResult loadAccount(user.uid, user.email, user.displayName).withIdentity(user)
    }

    private suspend fun googleAccount(user: FirebaseUser, fallbackRole: AccountRole): AuthAccount {
        val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        val photo = google?.photoUrl?.toString()?.takeIf { it.startsWith("https://") }
        // A failed profile write is recoverable on the next sign-in. Never wait for a photo download.
        val mirrored = withTimeoutOrNull(8_000) {
            try {
                if (fallbackRole == AccountRole.CHILD) ensureGoogleChildIdentity(user)
                val ref = firestore.collection(FirebaseContract.COLLECTION_USERS).document(user.uid)
                firestore.runTransaction { transaction ->
                    val existing = transaction.get(ref)
                    val fields = mutableMapOf<String, Any?>()
                    if (!existing.exists()) {
                        fields[FirebaseContract.FIELD_ROLE] = fallbackRole.name
                        fields[FirebaseContract.FIELD_DISPLAY_NAME] = user.displayName ?: google?.displayName.orEmpty()
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
        }.also {
            accountChanges.tryEmit(Unit)
        }
    }

    private suspend fun ensureGoogleChildIdentity(user: FirebaseUser) {
        val ref = firestore.collection(FirebaseContract.COLLECTION_USERS).document(user.uid)
        repeat(MAX_CODE_ATTEMPTS) {
            val candidate = generateCode()
            val claimed = firestore.runTransaction { transaction ->
                val existing = transaction.get(ref)
                if (existing.exists()) {
                    check(existing.getString(FirebaseContract.FIELD_ROLE) == AccountRole.CHILD.name)
                    if (existing.getString(FirebaseContract.FIELD_LINK_CODE) != null) return@runTransaction true
                }
                val code = FirebaseContract.codeFromChildEmail(user.email) ?: candidate
                val codeRef = firestore.collection(FirebaseContract.COLLECTION_CHILD_CODES).document(code)
                val owner = transaction.get(codeRef)
                if (owner.exists() && owner.getString(FirebaseContract.FIELD_UID) != user.uid) {
                    return@runTransaction false
                }
                val fields = mutableMapOf<String, Any?>(FirebaseContract.FIELD_LINK_CODE to code)
                if (!existing.exists()) {
                    fields[FirebaseContract.FIELD_ROLE] = AccountRole.CHILD.name
                    fields[FirebaseContract.FIELD_DISPLAY_NAME] = user.displayName.orEmpty()
                    fields[FirebaseContract.FIELD_CREATED_AT] = FieldValue.serverTimestamp()
                }
                if (!owner.exists()) transaction.set(codeRef, mapOf(FirebaseContract.FIELD_UID to user.uid))
                transaction.set(ref, fields, SetOptions.merge())
                true
            }.awaitResult()
            if (claimed) return
        }
        error("Please try setting up your child ID again.")
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
        return (withTimeoutOrNull(4_000) { runCatching {
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
            email = email,
            photoUrl = getString("photoUrl"),
            photoSource = getString("photoSource")
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
        if (FirebaseContract.isChildEmail(email)) AccountRole.CHILD
        else if (auth.currentUser?.providerData?.any { it.providerId == "password" } == true) AccountRole.PARENT
        else AccountRole.INDIVIDUAL

    private fun AuthAccount.withIdentity(user: FirebaseUser): AuthAccount {
        val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        return copy(googleLinked = google != null,
            needsChildPinSetup = role == AccountRole.CHILD && google != null &&
                (linkCode == null || user.providerData.none { it.providerId == EmailAuthProvider.PROVIDER_ID }),
            photoSource = photoSource ?: if (google != null) "GOOGLE" else null,
            photoUrl = photoUrl ?: google?.photoUrl?.toString()?.takeIf { it.startsWith("https://") })
    }

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
