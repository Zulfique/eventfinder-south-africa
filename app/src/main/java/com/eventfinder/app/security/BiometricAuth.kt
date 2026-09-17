package com.eventfinder.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.eventfinder.app.R
import com.eventfinder.app.utils.AppLogger
import java.util.concurrent.Executor

/**
 * Biometric authentication wrapper (FR-01). Wraps the AndroidX BiometricPrompt
 * SDK so the login flow can unlock the app with fingerprint or face unlock.
 *
 * The prototype guards against unavailable hardware gracefully: callers check
 * [isAvailable] and fall back to password login.
 *
 * References:
 *  - Android Developers, "Biometric Authentication":
 *    https://developer.android.com/training/sign-in/biometric-auth
 */
class BiometricAuth(
    private val activity: FragmentActivity,
    private val onAuthenticated: () -> Unit,
    private val onError: (String) -> Unit
) {

    private val executor: Executor = ContextCompat.getMainExecutor(activity)
    private val title: String = activity.getString(R.string.login_with_biometrics)
    private val negativeButtonText: String = activity.getString(R.string.cancel)

    companion object {
        private const val TAG = "BiometricAuth"

        /** True when strong biometrics or device credentials are enrolled. */
        fun isAvailable(context: Context): Boolean {
            val manager = BiometricManager.from(context)
            return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS,
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> true
                else -> false
            }
        }
    }

    @Suppress("MissingPermission")
    fun authenticate() {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(activity.getString(R.string.biometric_hint))
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLogger.i(TAG, "Biometric authentication succeeded")
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                AppLogger.w(TAG, "Biometric authentication error $errorCode: $errString")
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                AppLogger.w(TAG, "Biometric authentication failed (retry)")
            }
        })

        prompt.authenticate(promptInfo)
    }
}