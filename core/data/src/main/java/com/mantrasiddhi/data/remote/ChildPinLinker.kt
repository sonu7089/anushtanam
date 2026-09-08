package com.mantrasiddhi.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.mantrasiddhi.domain.model.FamilyLinkFailure
import com.mantrasiddhi.domain.model.FamilyLinkProblem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** A separate Firebase session verifies the PIN. The parent's session is never replaced. */
@Singleton
class ChildPinLinker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parentAuth: FirebaseAuth
) {
    private val mutex = Mutex()
    private val app by lazy {
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(context, parentAuth.app.options, APP_NAME)
    }
    private val childAuth by lazy { FirebaseAuth.getInstance(app) }
    private val childStore by lazy {
        FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
        }
    }

    suspend fun link(parentUid: String, parentName: String, code: String, pin: String) = mutex.withLock {
        childAuth.signOut()
        try {
            val child = childAuth.signInWithEmailAndPassword(FirebaseContract.childEmail(code), pin)
                .awaitResult().user ?: throw FamilyLinkFailure(FamilyLinkProblem.INVALID_CREDENTIALS)
            if (child.uid == parentUid || parentAuth.currentUser?.uid != parentUid) {
                throw FamilyLinkFailure(FamilyLinkProblem.WRONG_ACCOUNT)
            }
            val profile = childStore.collection("users").document(child.uid).get(Source.SERVER).awaitResult()
            if (profile.getString("role") != "CHILD" || profile.getString("linkCode") != code) {
                throw FamilyLinkFailure(FamilyLinkProblem.INVALID_CREDENTIALS)
            }
            val ref = childStore.collection("links").document(FirebaseContract.linkId(parentUid, child.uid))
            childStore.runTransaction { transaction ->
                val existing = transaction.get(ref)
                if (parentAuth.currentUser?.uid != parentUid) throw FamilyLinkFailure(FamilyLinkProblem.WRONG_ACCOUNT)
                if (existing.getString("status") != "APPROVED") {
                    transaction.set(ref, mapOf(
                        "parentUid" to parentUid, "childUid" to child.uid,
                        "parentName" to parentName, "childName" to profile.getString("displayName").orEmpty(),
                        "status" to "APPROVED", "method" to "PIN",
                        "createdAt" to (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }.awaitResult()
        } finally {
            childAuth.signOut()
        }
    }

    private companion object { const val APP_NAME = "child-pin-link" }
}
