/*
 * Anushtanam - Gayatri Mantra Recitation App
 * Copyright (c) 2026. All rights reserved.
 *
 * This is a demo application. Not for production use.
 */
package com.mantrasiddhi.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.data.notification.GoalNotifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives FCM pushes (delivered by the optional Blaze Cloud Function) and new-token
 * events. On a message it posts a local notification via the existing [GoalNotifier];
 * on a new token it persists it to the signed-in user's Firestore doc.
 *
 * Push delivery requires a server (Cloud Function) — see `firebase/functions/index.js`.
 * Without Blaze, the same-device Firestore listener path still surfaces new goals; this
 * service simply lies dormant until a function is deployed.
 */
@AndroidEntryPoint
class AppFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var goalNotifier: GoalNotifier
    @Inject lateinit var firestore: FirebaseFirestore

    override fun onMessageReceived(message: RemoteMessage) {
        goalNotifier.ensureChannel()
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.goal_notif_title)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(R.string.goal_push_generic_body)
        goalNotifier.showGeneric(title, body)
    }

    override fun onNewToken(token: String) {
        // Persist directly (the service may run without an active ViewModel scope).
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        firestore.collection(FirebaseContract.COLLECTION_USERS).document(uid)
            .update(FirebaseContract.FIELD_FCM_TOKEN, token)
    }
}
