package com.mantrasiddhi.presentation.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.mantrasiddhi.data.remote.awaitResult
import com.mantrasiddhi.presentation.screens.auth.GoogleCredentialPicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class AccountDeletionState(val busy: Boolean = false, val accepted: Boolean = false, val failed: Boolean = false)

@HiltViewModel
class AccountDeletionViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val picker: GoogleCredentialPicker
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountDeletionState())
    val state = mutableState.asStateFlow()
    val passwordRequired: Boolean get() = auth.currentUser?.providerData?.none { it.providerId == "google.com" } == true

    fun request(context: Context, expectedUid: String, password: String) {
        if (state.value.busy || state.value.accepted) return
        mutableState.value = AccountDeletionState(busy = true)
        viewModelScope.launch {
            try {
                val user = requireNotNull(auth.currentUser?.takeIf { it.uid == expectedUid })
                val request = firestore.collection("accountDeletionRequests").document(expectedUid)
                // A timed-out request may already have committed. Never overwrite it.
                val existing = withTimeout(15_000) { request.get(Source.SERVER).awaitResult() }
                if (!existing.exists()) {
                    val credential = if (user.providerData.any { it.providerId == "google.com" }) {
                        val token = picker.pick(context)
                        if (token == null) {
                            mutableState.value = AccountDeletionState()
                            return@launch
                        }
                        GoogleAuthProvider.getCredential(token, null)
                    } else EmailAuthProvider.getCredential(requireNotNull(user.email), password)
                    withTimeout(30_000) {
                        user.reauthenticate(credential).awaitResult()
                        user.getIdToken(true).awaitResult()
                        check(auth.currentUser?.uid == expectedUid)
                        request.set(mapOf("state" to "pending", "requestedAt" to FieldValue.serverTimestamp())).awaitResult()
                    }
                }
                mutableState.value = AccountDeletionState(accepted = true)
            } catch (cancelled: CancellationException) {
                mutableState.value = AccountDeletionState(failed = true)
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = AccountDeletionState(failed = true)
            }
        }
    }
}
