package com.mantrasiddhi.presentation.screens.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.mantrasiddhi.resources.R
import com.mantrasiddhi.data.sync.CloudSyncNetwork
import javax.inject.Inject

class GooglePickerFailure(val messageRes: Int) : Exception()

/** The Activity context belongs to this request only, never to a retained field. */
class GoogleCredentialPicker @Inject constructor(private val network: CloudSyncNetwork) {
    suspend fun pick(context: Context): String? {
        val clientId = context.getString(com.mantrasiddhi.R.string.google_sign_in_web_client_id)
        if (clientId.isBlank()) throw GooglePickerFailure(R.string.google_not_ready)
        if (!network.isConnected()) throw GooglePickerFailure(R.string.google_offline)
        try {
            val option = GetSignInWithGoogleOption.Builder(clientId).build()
            val result = CredentialManager.create(context).getCredential(
                context, GetCredentialRequest.Builder().addCredentialOption(option).build()
            ).credential
            if (result !is CustomCredential || result.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                throw GooglePickerFailure(R.string.google_try_again)
            }
            return GoogleIdTokenCredential.createFrom(result.data).idToken
        } catch (_: GetCredentialCancellationException) {
            return null
        } catch (_: NoCredentialException) {
            throw GooglePickerFailure(R.string.google_no_account)
        } catch (_: GetCredentialProviderConfigurationException) {
            throw GooglePickerFailure(R.string.google_unavailable)
        } catch (_: GetCredentialUnsupportedException) {
            throw GooglePickerFailure(R.string.google_unavailable)
        }
    }
}
