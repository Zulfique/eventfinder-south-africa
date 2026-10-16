package com.eventfinder.app.security

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.eventfinder.app.BuildConfig
import com.eventfinder.app.utils.AppLogger
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Google sign-in through AndroidX **Credential Manager**. The flow returns a
 * Google ID token that the EventFinder API verifies server-side (audience +
 * signature + expiry) before issuing a JWT — nothing about the account is
 * trusted from the device alone.
 *
 * Requires [BuildConfig.GOOGLE_SERVER_CLIENT_ID] (the OAuth *web* client ID
 * from the Google Cloud console). When it is blank the button is hidden and
 * this helper reports failure immediately.
 */
class GoogleSignInHelper(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    /** Fetches a Google ID token, or null when cancelled/unavailable. */
    suspend fun getCredential(): String? {
        val serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID
        if (serverClientId.isBlank()) {
            AppLogger.w(TAG, "GOOGLE_SERVER_CLIENT_ID is not configured")
            return null
        }

        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                AppLogger.w(TAG, "Unexpected credential type: ${credential.type}")
                null
            }
        } catch (e: GetCredentialException) {
            AppLogger.w(TAG, "Google sign-in cancelled or failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "GoogleSignInHelper"

        /** True when the build is configured with a web client ID. */
        val isConfigured: Boolean get() = BuildConfig.GOOGLE_SERVER_CLIENT_ID.isNotBlank()
    }
}
