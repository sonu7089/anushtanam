package com.mantrasiddhi.data.remote

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.*
import com.google.firebase.firestore.snapshots
import com.mantrasiddhi.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectFamilyLinkService @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val pinLinker: ChildPinLinker
) {
    private val random = SecureRandom()

    suspend fun linkWithPin(code: String, pin: String): Result<Unit> = familyResult {
        val trimmed = code.trim()
        if (!trimmed.matches(Regex("[0-9]{12}")) || !pin.matches(Regex("[0-9]{6}"))) {
            throw FamilyLinkFailure(FamilyLinkProblem.INVALID_CREDENTIALS)
        }
        val parent = ownProfile("PARENT")
        pinLinker.link(parent.id, parent.getString("displayName").orEmpty(), trimmed, pin)
        Unit
    }

    suspend fun createQrInvite(): Result<FamilyQrInvite> = familyResult {
        val child = ownProfile("CHILD")
        val token = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val ref = firestore.collection("familyInvites").document(token)
        val state = firestore.collection("familyInviteState").document(child.id)
        ensureOwner(child.id)
        firestore.runBatch { batch ->
            batch.set(ref, mapOf("childUid" to child.id,
                "childName" to child.getString("displayName").orEmpty(),
                "createdAt" to FieldValue.serverTimestamp(),
                "usedBy" to ""))
            batch.set(state, mapOf("token" to token))
        }.awaitResult()
        val createdAt = ref.get(Source.SERVER).awaitResult().getTimestamp("createdAt")
            ?: throw FamilyLinkFailure(FamilyLinkProblem.UNAVAILABLE)
        FamilyQrInvite(token, createdAt.toDate().time + FamilyQrInvite.LIFETIME_MILLIS)
    }

    suspend fun linkWithQr(payload: String): Result<Unit> = familyResult(qr = true) {
        val token = FamilyQrInvite.tokenFrom(payload) ?: throw FamilyLinkFailure(FamilyLinkProblem.INVALID_QR)
        val parent = ownProfile("PARENT")
        val inviteRef = firestore.collection("familyInvites").document(token)
        firestore.runTransaction { transaction ->
            val invite = transaction.get(inviteRef)
            val childUid = invite.getString("childUid") ?: throw FamilyLinkFailure(FamilyLinkProblem.QR_UNAVAILABLE)
            val linkRef = firestore.collection("links").document(FirebaseContract.linkId(parent.id, childUid))
            val existing = transaction.get(linkRef)
            ensureOwner(parent.id)
            if (invite.getString("usedBy") == parent.id && existing.getString("status") == "APPROVED") {
                return@runTransaction // Retry after a successful commit whose response was interrupted.
            }
            if (childUid == parent.id ||
                invite.getString("usedBy") != "" ||
                invite.getTimestamp("createdAt") == null) {
                throw FamilyLinkFailure(FamilyLinkProblem.QR_UNAVAILABLE)
            }
            transaction.update(inviteRef, mapOf("usedBy" to parent.id, "usedAt" to FieldValue.serverTimestamp()))
            transaction.set(linkRef, mapOf(
                "parentUid" to parent.id, "childUid" to childUid,
                "parentName" to parent.getString("displayName").orEmpty(),
                "childName" to invite.getString("childName").orEmpty(),
                "status" to "APPROVED", "method" to "QR", "inviteToken" to token,
                "createdAt" to (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.awaitResult()
        Unit
    }

    fun observeQrInvite(token: String): Flow<FamilyQrInvite?> =
        firestore.collection("familyInvites").document(token).snapshots().map { snapshot ->
            snapshot.getTimestamp("createdAt")?.toDate()?.time?.let {
                FamilyQrInvite(token, it + FamilyQrInvite.LIFETIME_MILLIS, !snapshot.getString("usedBy").isNullOrEmpty())
            }
        }.catch { emit(null) }

    private suspend fun ownProfile(role: String): DocumentSnapshot {
        val uid = auth.currentUser?.uid ?: throw FamilyLinkFailure(FamilyLinkProblem.WRONG_ACCOUNT)
        val profile = firestore.collection("users").document(uid).get(Source.SERVER).awaitResult()
        ensureOwner(uid)
        if (profile.getString("role") != role) throw FamilyLinkFailure(FamilyLinkProblem.WRONG_ACCOUNT)
        return profile
    }

    private fun ensureOwner(uid: String) {
        if (auth.currentUser?.uid != uid) throw FamilyLinkFailure(FamilyLinkProblem.WRONG_ACCOUNT)
    }

    private suspend fun <T> familyResult(qr: Boolean = false, block: suspend () -> T): Result<T> = try {
        Result.success(withTimeout(30_000) { block() })
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) {
        val problem = generateSequence<Throwable>(failure) { it.cause }.filterIsInstance<FamilyLinkFailure>()
            .firstOrNull()?.problem ?: when (failure) {
            is FamilyLinkFailure -> failure.problem
            is FirebaseNetworkException -> FamilyLinkProblem.OFFLINE
            is FirebaseTooManyRequestsException -> FamilyLinkProblem.TOO_MANY_ATTEMPTS
            is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> FamilyLinkProblem.INVALID_CREDENTIALS
            is FirebaseFirestoreException -> when {
                failure.code == FirebaseFirestoreException.Code.UNAVAILABLE -> FamilyLinkProblem.OFFLINE
                qr && failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> FamilyLinkProblem.QR_UNAVAILABLE
                else -> FamilyLinkProblem.UNAVAILABLE
            }
            else -> FamilyLinkProblem.UNAVAILABLE
        }
        Result.failure(FamilyLinkFailure(problem, failure))
    }
}
